package com.orbitcrm.note.service.impl;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbitcrm.identity.service.UserDirectory;
import com.orbitcrm.note.dto.NoteView;
import com.orbitcrm.note.service.NoteService;
import com.orbitcrm.notification.dto.Notification;
import com.orbitcrm.notification.service.NotificationService;
import com.orbitcrm.record.dto.RecordView;
import com.orbitcrm.record.event.RecordsRelinked;
import com.orbitcrm.record.service.RecordService;
import com.orbitcrm.shared.dto.PageParams;
import com.orbitcrm.shared.dto.PageResponse;
import com.orbitcrm.shared.exception.ApiException;
import com.orbitcrm.shared.security.AccessControl;
import com.orbitcrm.shared.security.Action;
import com.orbitcrm.shared.security.CurrentUser;
import com.orbitcrm.shared.tenant.TenantContext;
import com.orbitcrm.shared.util.UuidV7;

/**
 * Implementation of notes on records.
 *
 * <p>Purpose: store and manage notes with the right access rules:
 * <ul>
 *   <li>reading and adding notes require read access to the record;</li>
 *   <li>editing, pinning and deleting (soft delete) are limited to the author or an administrator;</li>
 *   <li>note text is required and limited to 20,000 characters;</li>
 *   <li>mentioned users who are active receive a notification;</li>
 *   <li>on {@code RecordsRelinked} (e.g. lead conversion) notes move to the target record.</li>
 * </ul>
 *
 * @author Abhay
 */
@Service
public class NoteServiceImpl implements NoteService {

    public static final int MAX_BODY = 20_000;
    private static final String COLUMNS = "id, module_key, record_id, body, pinned, created_by, created_at, updated_at, "
            + "version";

    private final NamedParameterJdbcTemplate jdbc;
    private final RecordService records;
    private final UserDirectory users;
    private final NotificationService notifications;

    NoteServiceImpl(NamedParameterJdbcTemplate jdbc, RecordService records, UserDirectory users,
                    NotificationService notifications) {
        this.jdbc = jdbc;
        this.records = records;
        this.users = users;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public PageResponse<NoteView> list(String moduleKey, UUID recordId, PageParams page) {
        records.requireAccess(moduleKey, recordId, Action.READ);
        MapSqlParameterSource params = forRecord(moduleKey, recordId);
        String where = "tenant_id = :tenantId AND module_key = :module AND record_id = :recordId AND deleted_at IS NULL";
        Long total = jdbc.queryForObject("SELECT count(*) FROM notes WHERE " + where, params, Long.class);
        params.addValue("limit", page.size()).addValue("offset", page.offset());
        List<NoteView> rows = withNames(jdbc.query("SELECT " + COLUMNS + " FROM notes WHERE " + where
                + " ORDER BY pinned DESC, created_at DESC LIMIT :limit OFFSET :offset", params, mapper()));
        return PageResponse.of(rows, page.page(), page.size(), total == null ? 0 : total);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NoteView> recent(String moduleKey, UUID recordId, Instant before, int limit) {
        MapSqlParameterSource params = forRecord(moduleKey, recordId)
                .addValue("before", OffsetDateTime.ofInstant(before, ZoneOffset.UTC)).addValue("limit", limit);
        return withNames(jdbc.query("SELECT " + COLUMNS + " FROM notes WHERE tenant_id = :tenantId "
                + "AND module_key = :module AND record_id = :recordId AND deleted_at IS NULL AND created_at < :before "
                + "ORDER BY created_at DESC LIMIT :limit", params, mapper()));
    }

    @Transactional
    public NoteView create(String moduleKey, UUID recordId, String body, List<UUID> mentionedUserIds) {
        RecordView record = records.requireAccess(moduleKey, recordId, Action.READ);
        CurrentUser user = AccessControl.requireUser();
        String text = validBody(body);
        UUID id = UuidV7.generate();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("""
                INSERT INTO notes (id, tenant_id, module_key, record_id, body, pinned, created_by, updated_by,
                                   created_at, updated_at, version)
                VALUES (:id, :tenantId, :module, :recordId, :body, false, :user, :user, :now, :now, 0)
                """, forRecord(moduleKey, recordId).addValue("id", id).addValue("body", text)
                .addValue("user", user.userId()).addValue("now", now));
        notifyMentions(record, text, mentionedUserIds);
        return get(id);
    }

    @Transactional
    public NoteView update(UUID noteId, String body, Boolean pinned) {
        NoteView note = requireOwnNote(noteId);
        MapSqlParameterSource params = new MapSqlParameterSource("id", noteId)
                .addValue("tenantId", TenantContext.requireTenantId())
                .addValue("body", body == null ? note.body() : validBody(body))
                .addValue("pinned", pinned == null ? note.pinned() : pinned)
                .addValue("user", AccessControl.requireUser().userId())
                .addValue("now", OffsetDateTime.now(ZoneOffset.UTC));
        jdbc.update("UPDATE notes SET body = :body, pinned = :pinned, updated_by = :user, updated_at = :now, "
                + "version = version + 1 WHERE id = :id AND tenant_id = :tenantId", params);
        return get(noteId);
    }

    @Transactional
    public void delete(UUID noteId) {
        requireOwnNote(noteId);
        jdbc.update("UPDATE notes SET deleted_at = :now WHERE id = :id AND tenant_id = :tenantId",
                new MapSqlParameterSource("id", noteId).addValue("tenantId", TenantContext.requireTenantId())
                        .addValue("now", OffsetDateTime.now(ZoneOffset.UTC)));
    }

    /** Notes follow a converted lead to its contact, or a merged duplicate to the survivor. */
    @EventListener
    void onRelinked(RecordsRelinked event) {
        jdbc.update("UPDATE notes SET module_key = :toModule, record_id = :toId WHERE tenant_id = :tenantId "
                        + "AND module_key = :fromModule AND record_id = :fromId",
                new MapSqlParameterSource("tenantId", event.tenantId()).addValue("toModule", event.toModule())
                        .addValue("toId", event.toId()).addValue("fromModule", event.fromModule())
                        .addValue("fromId", event.fromId()));
    }

    private NoteView requireOwnNote(UUID noteId) {
        NoteView note = get(noteId);
        records.requireAccess(note.moduleKey(), note.recordId(), Action.READ);
        CurrentUser user = AccessControl.requireUser();
        if (!user.administrator() && !user.userId().equals(note.createdBy())) {
            throw ApiException.forbidden("ACCESS_DENIED", "Only the author or an administrator can change this note");
        }
        return note;
    }

    private NoteView get(UUID noteId) {
        List<NoteView> rows = jdbc.query("SELECT " + COLUMNS + " FROM notes WHERE id = :id AND tenant_id = :tenantId "
                        + "AND deleted_at IS NULL",
                new MapSqlParameterSource("id", noteId).addValue("tenantId", TenantContext.requireTenantId()), mapper());
        if (rows.isEmpty()) {
            throw ApiException.notFound("NOTE_NOT_FOUND", "Note does not exist");
        }
        return withNames(rows).get(0);
    }

    private void notifyMentions(RecordView record, String body, List<UUID> mentioned) {
        if (mentioned == null || mentioned.isEmpty()) {
            return;
        }
        List<UUID> recipients = mentioned.stream().distinct().filter(users::isActive).toList();
        String snippet = body.length() > 140 ? body.substring(0, 140) + "..." : body;
        notifications.notify(new Notification(recipients, NotificationService.MENTION,
                AccessControl.requireUser().displayName() + " mentioned you on " + record.title(), snippet,
                record.module(), record.id()));
    }

    private List<NoteView> withNames(List<NoteView> notes) {
        Set<UUID> authors = new HashSet<>();
        notes.forEach(n -> authors.add(n.createdBy()));
        Map<UUID, String> names = users.displayNames(authors);
        return notes.stream().map(n -> new NoteView(n.id(), n.moduleKey(), n.recordId(), n.body(), n.pinned(),
                n.createdBy(), names.get(n.createdBy()), n.createdAt(), n.updatedAt(), n.version())).toList();
    }

    private static String validBody(String body) {
        if (body == null || body.isBlank()) {
            throw ApiException.validation("body", "REQUIRED", "Note text is required");
        }
        if (body.length() > MAX_BODY) {
            throw ApiException.validation("body", "INVALID_LENGTH", "Notes are limited to " + MAX_BODY + " characters");
        }
        return body.strip();
    }

    private static MapSqlParameterSource forRecord(String moduleKey, UUID recordId) {
        return new MapSqlParameterSource("tenantId", TenantContext.requireTenantId())
                .addValue("module", moduleKey).addValue("recordId", recordId);
    }

    private static RowMapper<NoteView> mapper() {
        return (rs, n) -> new NoteView(rs.getObject("id", UUID.class), rs.getString("module_key"),
                rs.getObject("record_id", UUID.class), rs.getString("body"), rs.getBoolean("pinned"),
                rs.getObject("created_by", UUID.class), null,
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant(), rs.getLong("version"));
    }
}
