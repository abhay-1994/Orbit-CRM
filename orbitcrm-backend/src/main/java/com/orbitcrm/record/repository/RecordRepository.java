package com.orbitcrm.record.repository;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Repository;

import com.orbitcrm.metadata.model.FieldDefinition;
import com.orbitcrm.metadata.model.ModuleDefinition;
import com.orbitcrm.shared.tenant.TenantContext;

/**
 * JDBC access to the module tables, driven entirely by {@link com.orbitcrm.metadata.model.ModuleDefinition}.
 *
 * <p>Purpose: the storage layer of the generic record engine. One repository serves every module: it builds
 * {@code INSERT}, {@code UPDATE}, soft-delete / restore, lookup, search, count and aggregate statements from module
 * metadata, maps rows to typed values (text, numbers, dates, UUIDs, arrays) and back, and allocates sequential
 * per-tenant record numbers from {@code record_sequences}. SQL identifiers come only from code-defined metadata and all
 * values are bound parameters. Every statement filters by tenant in addition to PostgreSQL row-level security, soft
 * deleted rows are excluded unless explicitly requested, and updates can check the expected version for optimistic
 * locking.
 *
 * @author Abhay
 */
@Repository
public class RecordRepository {

    private final NamedParameterJdbcTemplate jdbc;

    RecordRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * One raw row of a module table as read by the repository.
     *
     * <p>Purpose: the storage-level representation of a record, before labels and titles are added by the
     * {@code RecordPresenter}.
     *
     * @param id           record id
     * @param recordNumber sequential number within the tenant and module
     * @param version      optimistic locking version
     * @param deletedAt    when the record was moved to the recycle bin, or {@code null}
     * @param values       field key to typed value
     */
    public record Row(UUID id, long recordNumber, long version, Instant deletedAt, Map<String, Object> values) {
    }

    public long nextRecordNumber(String moduleKey) {
        Long value = jdbc.queryForObject("""
                INSERT INTO record_sequences (tenant_id, module_key, next_value) VALUES (:tenantId, :module, 2)
                ON CONFLICT (tenant_id, module_key) DO UPDATE SET next_value = record_sequences.next_value + 1
                RETURNING next_value - 1
                """, new MapSqlParameterSource("tenantId", TenantContext.requireTenantId()).addValue("module", moduleKey),
                Long.class);
        return value == null ? 1 : value;
    }

    public void insert(ModuleDefinition module, UUID id, long recordNumber, Map<String, Object> values) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("tenantId", TenantContext.requireTenantId())
                .addValue("recordNumber", recordNumber);
        List<String> columns = new ArrayList<>(List.of("id", "tenant_id", "record_number", "version"));
        List<String> placeholders = new ArrayList<>(List.of(":id", ":tenantId", ":recordNumber", "0"));
        int i = 0;
        for (FieldDefinition field : module.fields()) {
            if (values.get(field.key()) == null) {
                continue; // let column defaults apply
            }
            String name = "v" + (i++);
            columns.add(field.column());
            placeholders.add(":" + name);
            params.addValue(name, toSql(field, values.get(field.key())));
        }
        jdbc.update("INSERT INTO " + module.table() + " (" + String.join(", ", columns) + ") VALUES ("
                + String.join(", ", placeholders) + ")", params);
    }

    /** Returns rows affected: 0 means not found (or version mismatch when {@code expectedVersion} is set). */
    public int update(ModuleDefinition module, UUID id, Map<String, Object> changes, Long expectedVersion) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("tenantId", TenantContext.requireTenantId());
        List<String> sets = new ArrayList<>();
        int i = 0;
        for (Map.Entry<String, Object> change : changes.entrySet()) {
            FieldDefinition field = module.requireField(change.getKey());
            String name = "v" + (i++);
            sets.add(field.column() + " = :" + name);
            params.addValue(name, toSql(field, change.getValue()));
        }
        sets.add("version = version + 1");
        String sql = "UPDATE " + module.table() + " SET " + String.join(", ", sets)
                + " WHERE id = :id AND tenant_id = :tenantId AND deleted_at IS NULL";
        if (expectedVersion != null) {
            sql += " AND version = :expectedVersion";
            params.addValue("expectedVersion", expectedVersion);
        }
        return jdbc.update(sql, params);
    }

    public int setDeleted(ModuleDefinition module, UUID id, Instant deletedAt, UUID actorId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("tenantId", TenantContext.requireTenantId())
                .addValue("deletedAt", deletedAt == null ? null : ts(deletedAt))
                .addValue("actor", actorId)
                .addValue("now", ts(Instant.now()));
        String condition = deletedAt == null ? "deleted_at IS NOT NULL" : "deleted_at IS NULL";
        return jdbc.update("UPDATE " + module.table() + " SET deleted_at = :deletedAt, updated_by = :actor, "
                + "updated_at = :now, version = version + 1 WHERE id = :id AND tenant_id = :tenantId AND " + condition,
                params);
    }

    public Optional<Row> findById(ModuleDefinition module, UUID id, String scopeSql, MapSqlParameterSource scopeParams,
                           boolean deleted) {
        MapSqlParameterSource params = scopeParams == null ? new MapSqlParameterSource() : scopeParams;
        params.addValue("id", id).addValue("tenantId", TenantContext.requireTenantId());
        String sql = select(module) + " WHERE t.id = :id AND t.tenant_id = :tenantId AND t.deleted_at IS "
                + (deleted ? "NOT NULL" : "NULL") + (scopeSql == null ? "" : " AND (" + scopeSql + ")");
        List<Row> rows = jdbc.query(sql, params, mapper(module));
        return rows.stream().findFirst();
    }

    public Optional<Long> currentVersion(ModuleDefinition module, UUID id) {
        List<Long> versions = jdbc.queryForList("SELECT version FROM " + module.table()
                        + " WHERE id = :id AND tenant_id = :tenantId AND deleted_at IS NULL",
                new MapSqlParameterSource("id", id).addValue("tenantId", TenantContext.requireTenantId()), Long.class);
        return versions.stream().findFirst();
    }

    public boolean matches(ModuleDefinition module, UUID id, String scopeSql, MapSqlParameterSource params) {
        params.addValue("id", id).addValue("tenantId", TenantContext.requireTenantId());
        Boolean exists = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM " + module.table()
                + " t WHERE t.id = :id AND t.tenant_id = :tenantId AND t.deleted_at IS NULL"
                + (scopeSql == null ? "" : " AND (" + scopeSql + ")") + ")", params, Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    public List<Row> search(ModuleDefinition module, String where, MapSqlParameterSource params, String orderBy,
                     int limit, long offset) {
        params.addValue("limit", limit).addValue("offset", offset);
        return jdbc.query(select(module) + " WHERE " + where + " ORDER BY " + orderBy
                + " LIMIT :limit OFFSET :offset", params, mapper(module));
    }

    public long count(ModuleDefinition module, String where, MapSqlParameterSource params) {
        Long count = jdbc.queryForObject("SELECT count(*) FROM " + module.table() + " t WHERE " + where, params,
                Long.class);
        return count == null ? 0 : count;
    }

    public List<Map<String, Object>> aggregate(String sql, MapSqlParameterSource params) {
        return jdbc.query(sql, params, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            int columns = rs.getMetaData().getColumnCount();
            for (int i = 1; i <= columns; i++) {
                Object value = rs.getObject(i);
                if (value instanceof java.sql.Date date) {
                    value = date.toLocalDate();
                } else if (value instanceof java.sql.Timestamp timestamp) {
                    value = timestamp.toInstant();
                }
                row.put(rs.getMetaData().getColumnLabel(i), value);
            }
            return row;
        });
    }

    public Map<UUID, String> titles(ModuleDefinition module, Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> result = new HashMap<>();
        jdbc.query("SELECT t.id, " + module.titleSql("t") + " AS title FROM " + module.table()
                        + " t WHERE t.tenant_id = :tenantId AND t.id IN (:ids)",
                new MapSqlParameterSource("tenantId", TenantContext.requireTenantId()).addValue("ids", ids),
                rs -> {
                    result.put(rs.getObject("id", UUID.class), rs.getString("title"));
                });
        return result;
    }

    // ------------------------------------------------------------------ mapping

    private static String select(ModuleDefinition module) {
        String columns = module.fields().stream().map(f -> "t." + f.column()).collect(Collectors.joining(", "));
        return "SELECT t.id, t.record_number, t.version, t.deleted_at, " + columns + " FROM " + module.table() + " t";
    }

    private static RowMapper<Row> mapper(ModuleDefinition module) {
        return (rs, rowNum) -> {
            Map<String, Object> values = new LinkedHashMap<>();
            for (FieldDefinition field : module.fields()) {
                values.put(field.key(), read(rs, field));
            }
            OffsetDateTime deletedAt = rs.getObject("deleted_at", OffsetDateTime.class);
            return new Row(rs.getObject("id", UUID.class), rs.getLong("record_number"), rs.getLong("version"),
                    deletedAt == null ? null : deletedAt.toInstant(), values);
        };
    }

    private static Object read(ResultSet rs, FieldDefinition field) throws SQLException {
        String col = field.column();
        return switch (field.type()) {
            case TEXT, TEXTAREA, EMAIL, PHONE, URL, PICKLIST -> rs.getString(col);
            case INTEGER -> {
                long v = rs.getLong(col);
                yield rs.wasNull() ? null : v;
            }
            case DECIMAL, CURRENCY, PERCENT -> rs.getBigDecimal(col);
            case DATE -> rs.getObject(col, LocalDate.class);
            case DATETIME -> {
                OffsetDateTime v = rs.getObject(col, OffsetDateTime.class);
                yield v == null ? null : v.toInstant();
            }
            case BOOLEAN -> {
                boolean v = rs.getBoolean(col);
                yield rs.wasNull() ? null : v;
            }
            case USER, LOOKUP, REFERENCE -> rs.getObject(col, UUID.class);
            case MULTI_PICKLIST, TAGS -> {
                Array array = rs.getArray(col);
                yield array == null ? null : List.of((String[]) array.getArray());
            }
            case MULTI_USER -> {
                Array array = rs.getArray(col);
                yield array == null ? null : List.of((UUID[]) array.getArray());
            }
        };
    }

    public static Object toSql(FieldDefinition field, Object value) {
        if (value == null) {
            return null;
        }
        return switch (field.type()) {
            case DATETIME -> ts((Instant) value);
            case MULTI_PICKLIST, TAGS -> new SqlArrayValue("text", ((List<?>) value).toArray());
            case MULTI_USER -> new SqlArrayValue("uuid", ((List<?>) value).toArray());
            case DECIMAL, CURRENCY, PERCENT -> (BigDecimal) value;
            default -> value;
        };
    }

    private static OffsetDateTime ts(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

}
