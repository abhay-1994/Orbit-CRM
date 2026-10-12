package com.orbitcrm.activity.job;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.orbitcrm.activity.config.ActivityModule;
import com.orbitcrm.notification.dto.Notification;
import com.orbitcrm.notification.service.NotificationService;
import com.orbitcrm.shared.tenant.TenantContext;

/**
 * Scheduled job that sends due activity reminders (every minute by default, {@code orbitcrm.jobs.reminder-interval}).
 *
 * <p>Purpose: notify owners and participants when an open activity's reminder time has come. The job scans all
 * tenants in one query with row-level security bypassed (for the scan only), then processes each tenant inside its own
 * tenant context and transaction. Each reminder is claimed with a conditional update of {@code reminder_sent_at}, so a
 * retry or a second instance can never send it twice; ShedLock ensures only one instance runs the job at a time.
 * Completed, cancelled and deleted activities are skipped, and a failure in one tenant does not stop the others. Tests
 * call {@link #run} directly.
 *
 * @author Abhay
 */
@Component
public class ReminderJob {

    private static final Logger log = LoggerFactory.getLogger(ReminderJob.class);
    private static final int BATCH = 500;

    private final NamedParameterJdbcTemplate jdbc;
    private final NotificationService notifications;
    private final TransactionTemplate tx;

    ReminderJob(NamedParameterJdbcTemplate jdbc, NotificationService notifications, PlatformTransactionManager txManager) {
        this.jdbc = jdbc;
        this.notifications = notifications;
        this.tx = new TransactionTemplate(txManager);
    }

    @Scheduled(fixedDelayString = "${orbitcrm.jobs.reminder-interval:PT1M}")
    @SchedulerLock(name = "activity-reminders", lockAtMostFor = "PT5M")
    public void sendDueReminders() {
        int sent = run(Instant.now());
        if (sent > 0) {
            log.info("Sent {} activity reminders", sent);
        }
    }

    /** Processes reminders due at {@code now}; returns the number of activities reminded. */
    public int run(Instant now) {
        OffsetDateTime cutoff = OffsetDateTime.ofInstant(now, ZoneOffset.UTC);
        List<Due> due = TenantContext.callAsSystem(() -> jdbc.query("""
                SELECT tenant_id, id, owner_id, participant_ids, subject FROM activities
                WHERE reminder_at <= :now AND reminder_sent_at IS NULL AND deleted_at IS NULL
                  AND status NOT IN ('completed', 'cancelled')
                ORDER BY reminder_at LIMIT :limit
                """, new MapSqlParameterSource("now", cutoff).addValue("limit", BATCH), (rs, n) -> {
                    java.sql.Array array = rs.getArray("participant_ids");
                    List<UUID> participants = array == null ? List.of() : List.of((UUID[]) array.getArray());
                    return new Due(rs.getObject("tenant_id", UUID.class), rs.getObject("id", UUID.class),
                            rs.getObject("owner_id", UUID.class), participants, rs.getString("subject"));
                }));
        Map<UUID, List<Due>> byTenant = new LinkedHashMap<>();
        due.forEach(d -> byTenant.computeIfAbsent(d.tenantId(), k -> new ArrayList<>()).add(d));
        int sent = 0;
        for (Map.Entry<UUID, List<Due>> tenant : byTenant.entrySet()) {
            try {
                sent += TenantContext.callAs(tenant.getKey(), () -> tx.execute(status -> remind(tenant.getValue(), cutoff)));
            } catch (RuntimeException ex) {
                log.error("Failed to send reminders for tenant {}", tenant.getKey(), ex);
            }
        }
        return sent;
    }

    private int remind(List<Due> activities, OffsetDateTime now) {
        int sent = 0;
        for (Due activity : activities) {
            int claimed = jdbc.update("UPDATE activities SET reminder_sent_at = :now WHERE id = :id "
                    + "AND tenant_id = :tenantId AND reminder_sent_at IS NULL", new MapSqlParameterSource()
                    .addValue("now", now).addValue("id", activity.id()).addValue("tenantId", activity.tenantId()));
            if (claimed == 0) {
                continue;
            }
            List<UUID> recipients = new ArrayList<>(activity.participants());
            recipients.add(0, activity.ownerId());
            notifications.notify(new Notification(recipients, NotificationService.ACTIVITY_REMINDER,
                    "Reminder: " + activity.subject(), null, ActivityModule.KEY, activity.id()));
            sent++;
        }
        return sent;
    }

    /**
     * An activity whose reminder is due, as found by the cross-tenant scan.
     *
     * <p>Purpose: carry what is needed to claim and send one reminder inside the activity's own tenant context.
     *
     * @param tenantId     tenant of the activity
     * @param id           activity id
     * @param ownerId      owner, who is always reminded
     * @param participants invited participants, who are reminded as well
     * @param subject      activity subject, used in the notification title
     */
    private record Due(UUID tenantId, UUID id, UUID ownerId, List<UUID> participants, String subject) {
    }
}
