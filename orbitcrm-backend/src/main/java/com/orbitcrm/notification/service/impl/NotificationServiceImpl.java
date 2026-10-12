package com.orbitcrm.notification.service.impl;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbitcrm.notification.dto.Notification;
import com.orbitcrm.notification.dto.NotificationView;
import com.orbitcrm.notification.service.NotificationService;
import com.orbitcrm.shared.dto.PageParams;
import com.orbitcrm.shared.dto.PageResponse;
import com.orbitcrm.shared.exception.ApiException;
import com.orbitcrm.shared.security.AccessControl;
import com.orbitcrm.shared.security.CurrentUser;
import com.orbitcrm.shared.tenant.TenantContext;
import com.orbitcrm.shared.util.AfterCommit;
import com.orbitcrm.shared.util.UuidV7;

/**
 * JDBC implementation of {@link com.orbitcrm.notification.service.NotificationService} plus the per-user
 * notification queries.
 *
 * <p>Purpose: store one notification row per recipient (skipping {@code null} and the acting user), record the actor,
 * and after commit push each notification to the recipient's live connections through
 * {@link NotificationStreams}. It also lists a user's notifications, counts unread ones and marks them read - always
 * restricted to the current user.
 *
 * @author Abhay
 */
@Service
public class NotificationServiceImpl implements NotificationService {

    private static final String COLUMNS = "id, type, title, body, module_key, record_id, actor_id, actor_name, "
            + "read_at, created_at";

    private final NamedParameterJdbcTemplate jdbc;
    private final NotificationStreams streams;

    NotificationServiceImpl(NamedParameterJdbcTemplate jdbc, NotificationStreams streams) {
        this.jdbc = jdbc;
        this.streams = streams;
    }

    @Override
    @Transactional
    public void notify(Notification notification) {
        CurrentUser actor = AccessControl.currentUser().orElse(null);
        Set<UUID> recipients = new LinkedHashSet<>(notification.recipients());
        recipients.remove(null);
        if (actor != null) {
            recipients.remove(actor.userId());
        }
        UUID tenantId = TenantContext.requireTenantId();
        Instant now = Instant.now();
        for (UUID recipient : recipients) {
            UUID id = UuidV7.generate();
            jdbc.update("""
                    INSERT INTO notifications (id, tenant_id, user_id, type, title, body, module_key, record_id, actor_id,
                                               actor_name, created_at)
                    VALUES (:id, :tenantId, :userId, :type, :title, :body, :module, :recordId, :actorId, :actorName, :now)
                    """, new MapSqlParameterSource()
                    .addValue("id", id)
                    .addValue("tenantId", tenantId)
                    .addValue("userId", recipient)
                    .addValue("type", notification.type())
                    .addValue("title", truncate(notification.title(), 300))
                    .addValue("body", notification.body())
                    .addValue("module", notification.moduleKey())
                    .addValue("recordId", notification.recordId())
                    .addValue("actorId", actor == null ? null : actor.userId())
                    .addValue("actorName", actor == null ? null : actor.displayName())
                    .addValue("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC)));
            NotificationView view = new NotificationView(id, notification.type(), notification.title(),
                    notification.body(), notification.moduleKey(), notification.recordId(),
                    actor == null ? null : actor.userId(), actor == null ? null : actor.displayName(), null, now);
            AfterCommit.run(() -> streams.push(recipient, view));
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationView> list(boolean unreadOnly, PageParams page) {
        MapSqlParameterSource params = mine();
        String where = "tenant_id = :tenantId AND user_id = :userId" + (unreadOnly ? " AND read_at IS NULL" : "");
        Long total = jdbc.queryForObject("SELECT count(*) FROM notifications WHERE " + where, params, Long.class);
        params.addValue("limit", page.size()).addValue("offset", page.offset());
        List<NotificationView> rows = jdbc.query("SELECT " + COLUMNS + " FROM notifications WHERE " + where
                + " ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset", params, mapper());
        return PageResponse.of(rows, page.page(), page.size(), total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        Long count = jdbc.queryForObject("SELECT count(*) FROM notifications WHERE tenant_id = :tenantId "
                + "AND user_id = :userId AND read_at IS NULL", mine(), Long.class);
        return count == null ? 0 : count;
    }

    @Transactional
    public void markRead(UUID id) {
        int updated = jdbc.update("UPDATE notifications SET read_at = coalesce(read_at, :now) WHERE id = :id "
                + "AND tenant_id = :tenantId AND user_id = :userId", mine().addValue("id", id)
                .addValue("now", OffsetDateTime.now(ZoneOffset.UTC)));
        if (updated == 0) {
            throw ApiException.notFound("NOTIFICATION_NOT_FOUND", "Notification does not exist");
        }
    }

    @Transactional
    public int markAllRead() {
        return jdbc.update("UPDATE notifications SET read_at = :now WHERE tenant_id = :tenantId AND user_id = :userId "
                + "AND read_at IS NULL", mine().addValue("now", OffsetDateTime.now(ZoneOffset.UTC)));
    }

    private static MapSqlParameterSource mine() {
        CurrentUser user = AccessControl.requireUser();
        return new MapSqlParameterSource("tenantId", user.tenantId()).addValue("userId", user.userId());
    }

    private static RowMapper<NotificationView> mapper() {
        return (rs, n) -> {
            OffsetDateTime readAt = rs.getObject("read_at", OffsetDateTime.class);
            return new NotificationView(rs.getObject("id", UUID.class), rs.getString("type"), rs.getString("title"),
                    rs.getString("body"), rs.getString("module_key"), rs.getObject("record_id", UUID.class),
                    rs.getObject("actor_id", UUID.class), rs.getString("actor_name"),
                    readAt == null ? null : readAt.toInstant(),
                    rs.getObject("created_at", OffsetDateTime.class).toInstant());
        };
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
