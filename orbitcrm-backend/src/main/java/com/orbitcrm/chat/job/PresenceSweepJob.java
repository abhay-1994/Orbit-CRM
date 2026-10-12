package com.orbitcrm.chat.job;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.orbitcrm.activity.service.MeetingSchedule;
import com.orbitcrm.chat.repository.PresenceRepository.TenantUser;
import com.orbitcrm.chat.service.PresenceService;
import com.orbitcrm.shared.tenant.TenantContext;

/**
 * Scheduled job that keeps presence correct when nothing else would announce a change (every 30 seconds by default,
 * {@code orbitcrm.jobs.presence-sweep-interval}).
 *
 * <p>Purpose: handle the two changes no request triggers.
 * <ul>
 *   <li>Connections of a server that crashed or was stopped stop receiving heartbeats; they are deleted once
 *   expired and their users' presence is broadcast (usually Offline).</li>
 *   <li>Meetings start and end on their own; for every tenant with people online, attendees of meetings that
 *   started or ended within the last minute get their presence broadcast ("In a meeting" on and off). The window
 *   is twice the interval, so a delayed run misses nothing; a repeated broadcast is harmless.</li>
 * </ul>
 * The scans run with row-level security bypassed; broadcasts run inside each tenant's own context. ShedLock runs it
 * on one instance at a time, and a failure in one tenant does not stop the others. Tests call {@link #run} directly.
 *
 * @author Abhay
 */
@Component
public class PresenceSweepJob {

    private static final Logger log = LoggerFactory.getLogger(PresenceSweepJob.class);
    private static final Duration MEETING_WINDOW = Duration.ofMinutes(1);

    private final PresenceService presence;
    private final MeetingSchedule meetings;

    PresenceSweepJob(PresenceService presence, MeetingSchedule meetings) {
        this.presence = presence;
        this.meetings = meetings;
    }

    @Scheduled(fixedDelayString = "${orbitcrm.jobs.presence-sweep-interval:PT30S}")
    @SchedulerLock(name = "chat-presence-sweep", lockAtMostFor = "PT2M")
    public void sweep() {
        run(Instant.now());
    }

    /** Sweeps expired connections and meeting changes up to {@code now}; returns the number of users broadcast. */
    public int run(Instant now) {
        Map<UUID, Set<UUID>> changed = new LinkedHashMap<>();
        List<TenantUser> expired = TenantContext.callAsSystem(presence::removeStaleConnections);
        expired.forEach(u -> changed.computeIfAbsent(u.tenantId(), k -> new HashSet<>()).add(u.userId()));
        for (UUID tenantId : TenantContext.callAsSystem(presence::tenantsOnline)) {
            try {
                Set<UUID> attendees = TenantContext.callAs(tenantId,
                        () -> meetings.meetingChangesBetween(now.minus(MEETING_WINDOW), now));
                if (!attendees.isEmpty()) {
                    changed.computeIfAbsent(tenantId, k -> new HashSet<>()).addAll(attendees);
                }
            } catch (RuntimeException ex) {
                log.error("Failed to check meetings for tenant {}", tenantId, ex);
            }
        }
        int broadcast = 0;
        for (Map.Entry<UUID, Set<UUID>> tenant : changed.entrySet()) {
            try {
                TenantContext.runAs(tenant.getKey(), () -> presence.publish(tenant.getValue()));
                broadcast += tenant.getValue().size();
            } catch (RuntimeException ex) {
                log.error("Failed to broadcast presence for tenant {}", tenant.getKey(), ex);
            }
        }
        return broadcast;
    }
}
