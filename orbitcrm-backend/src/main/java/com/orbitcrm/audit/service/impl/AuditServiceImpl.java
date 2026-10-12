package com.orbitcrm.audit.service.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import com.orbitcrm.audit.dto.AuditEntry;
import com.orbitcrm.audit.dto.AuditLogView;
import com.orbitcrm.audit.dto.AuditQuery;
import com.orbitcrm.audit.service.AuditService;
import com.orbitcrm.shared.dto.PageParams;
import com.orbitcrm.shared.dto.PageResponse;
import com.orbitcrm.shared.security.AccessControl;
import com.orbitcrm.shared.security.CurrentUser;
import com.orbitcrm.shared.tenant.TenantContext;
import com.orbitcrm.shared.util.UuidV7;
import com.orbitcrm.shared.web.CurrentRequest;
import com.orbitcrm.shared.web.RequestIdFilter;

/**
 * JDBC implementation of {@link com.orbitcrm.audit.service.AuditService} on the {@code audit_logs} table.
 *
 * <p>Purpose: store and query audit entries efficiently. Entries are inserted with a UUIDv7 id, the current tenant,
 * the acting user (explicit or from the security context), client IP and request id; changes and metadata are stored
 * as JSONB. The table is append-only - the application role has no UPDATE or DELETE privilege and a trigger blocks
 * modifications even for the owner - and it is partitioned by year. Queries are always tenant-filtered, newest first,
 * and paged.
 *
 * @author Abhay
 */
@Service
public class AuditServiceImpl implements AuditService {

    private static final TypeReference<List<Map<String, Object>>> CHANGES = new TypeReference<>() { };
    private static final TypeReference<Map<String, Object>> METADATA = new TypeReference<>() { };
    private static final String COLUMNS = "id, occurred_at, actor_id, actor_name, action, module_key, record_id, "
            + "record_label, changes, metadata, ip_address, request_id";

    private final NamedParameterJdbcTemplate jdbc;
    private final JsonMapper json;

    AuditServiceImpl(NamedParameterJdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    @Transactional
    public void record(AuditEntry entry) {
        UUID actorId = entry.actorId();
        String actorName = entry.actorName();
        if (actorId == null) {
            CurrentUser user = AccessControl.currentUser().orElse(null);
            if (user != null) {
                actorId = user.userId();
                actorName = user.displayName();
            }
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", UuidV7.generate())
                .addValue("tenantId", TenantContext.requireTenantId())
                .addValue("occurredAt", OffsetDateTime.now(ZoneOffset.UTC))
                .addValue("actorId", actorId)
                .addValue("actorName", truncate(actorName, 200))
                .addValue("action", entry.action().name())
                .addValue("moduleKey", entry.moduleKey())
                .addValue("recordId", entry.recordId())
                .addValue("recordLabel", truncate(entry.recordLabel(), 300))
                .addValue("changes", entry.changes() == null || entry.changes().isEmpty() ? null
                        : json.writeValueAsString(entry.changes()))
                .addValue("metadata", entry.metadata() == null || entry.metadata().isEmpty() ? null
                        : json.writeValueAsString(entry.metadata()))
                .addValue("ip", truncate(CurrentRequest.clientIp(), 64))
                .addValue("requestId", RequestIdFilter.currentRequestId());
        jdbc.update("""
                INSERT INTO audit_logs (id, tenant_id, occurred_at, actor_id, actor_name, action, module_key,
                                        record_id, record_label, changes, metadata, ip_address, request_id)
                VALUES (:id, :tenantId, :occurredAt, :actorId, :actorName, :action, :moduleKey, :recordId,
                        :recordLabel, CAST(:changes AS jsonb), CAST(:metadata AS jsonb), :ip, :requestId)
                """, params);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuditLogView> search(AuditQuery query, PageParams page) {
        List<String> where = new ArrayList<>();
        MapSqlParameterSource params = new MapSqlParameterSource("tenantId", TenantContext.requireTenantId());
        where.add("tenant_id = :tenantId");
        if (query.moduleKey() != null) {
            where.add("module_key = :moduleKey");
            params.addValue("moduleKey", query.moduleKey());
        }
        if (query.recordId() != null) {
            where.add("record_id = :recordId");
            params.addValue("recordId", query.recordId());
        }
        if (query.actorId() != null) {
            where.add("actor_id = :actorId");
            params.addValue("actorId", query.actorId());
        }
        if (query.action() != null) {
            where.add("action = :action");
            params.addValue("action", query.action().name());
        }
        if (query.from() != null) {
            where.add("occurred_at >= :from");
            params.addValue("from", OffsetDateTime.ofInstant(query.from(), ZoneOffset.UTC));
        }
        if (query.to() != null) {
            where.add("occurred_at < :to");
            params.addValue("to", OffsetDateTime.ofInstant(query.to(), ZoneOffset.UTC));
        }
        return page(String.join(" AND ", where), params, page);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuditLogView> forRecord(String moduleKey, UUID recordId, PageParams page) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", TenantContext.requireTenantId())
                .addValue("moduleKey", moduleKey)
                .addValue("recordId", recordId);
        return page("tenant_id = :tenantId AND module_key = :moduleKey AND record_id = :recordId", params, page);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditLogView> recentForRecord(String moduleKey, UUID recordId, Instant before, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", TenantContext.requireTenantId())
                .addValue("moduleKey", moduleKey)
                .addValue("recordId", recordId)
                .addValue("before", OffsetDateTime.ofInstant(before, ZoneOffset.UTC))
                .addValue("limit", limit);
        return jdbc.query("SELECT " + COLUMNS + " FROM audit_logs WHERE tenant_id = :tenantId "
                + "AND module_key = :moduleKey AND record_id = :recordId AND occurred_at < :before "
                + "ORDER BY occurred_at DESC LIMIT :limit", params, mapper());
    }

    private PageResponse<AuditLogView> page(String where, MapSqlParameterSource params, PageParams page) {
        Long total = jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE " + where, params, Long.class);
        params.addValue("limit", page.size()).addValue("offset", page.offset());
        List<AuditLogView> rows = jdbc.query("SELECT " + COLUMNS + " FROM audit_logs WHERE " + where
                + " ORDER BY occurred_at DESC, id DESC LIMIT :limit OFFSET :offset", params, mapper());
        return PageResponse.of(rows, page.page(), page.size(), total == null ? 0 : total);
    }

    private RowMapper<AuditLogView> mapper() {
        return (ResultSet rs, int rowNum) -> new AuditLogView(
                rs.getObject("id", UUID.class),
                rs.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                rs.getObject("actor_id", UUID.class),
                rs.getString("actor_name"),
                rs.getString("action"),
                rs.getString("module_key"),
                rs.getObject("record_id", UUID.class),
                rs.getString("record_label"),
                parse(rs, "changes", CHANGES),
                parse(rs, "metadata", METADATA),
                rs.getString("ip_address"),
                rs.getString("request_id"));
    }

    private <T> T parse(ResultSet rs, String column, TypeReference<T> type) throws SQLException {
        String raw = rs.getString(column);
        return raw == null ? null : json.readValue(raw, type);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
