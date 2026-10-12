package com.orbitcrm.search.service.impl;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import com.orbitcrm.search.dto.IndexDocument;
import com.orbitcrm.search.service.SearchIndexer;
import com.orbitcrm.shared.tenant.TenantContext;

/**
 * JDBC implementation of {@link com.orbitcrm.search.service.SearchIndexer} on the {@code search_index} table.
 *
 * <p>Purpose: write index entries with an {@code INSERT ... ON CONFLICT DO UPDATE} per record (one row per tenant,
 * module and record). Content is lower-cased for case-insensitive trigram matching and length-limited (title and
 * subtitle 500 characters, content 20,000 characters); an empty title is stored as "(untitled)".
 *
 * @author Abhay
 */
@Component
public class JdbcSearchIndexer implements SearchIndexer {

    private static final int MAX_CONTENT = 20_000;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcSearchIndexer(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void upsert(IndexDocument doc) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", TenantContext.requireTenantId())
                .addValue("module", doc.moduleKey())
                .addValue("recordId", doc.recordId())
                .addValue("ownerId", doc.ownerId())
                .addValue("recordNumber", doc.recordNumber())
                .addValue("title", truncate(doc.title() == null || doc.title().isBlank() ? "(untitled)" : doc.title(), 500))
                .addValue("subtitle", truncate(doc.subtitle(), 500))
                .addValue("content", truncate(doc.content() == null ? "" : doc.content().toLowerCase(), MAX_CONTENT))
                .addValue("now", OffsetDateTime.now(ZoneOffset.UTC));
        jdbc.update("""
                INSERT INTO search_index (tenant_id, module_key, record_id, owner_id, record_number, title, subtitle,
                                          content, updated_at)
                VALUES (:tenantId, :module, :recordId, :ownerId, :recordNumber, :title, :subtitle, :content, :now)
                ON CONFLICT (tenant_id, module_key, record_id) DO UPDATE
                   SET owner_id = EXCLUDED.owner_id, record_number = EXCLUDED.record_number,
                       title = EXCLUDED.title, subtitle = EXCLUDED.subtitle, content = EXCLUDED.content,
                       updated_at = EXCLUDED.updated_at
                """, params);
    }

    @Override
    public void remove(String moduleKey, UUID recordId) {
        jdbc.update("DELETE FROM search_index WHERE tenant_id = :tenantId AND module_key = :module AND record_id = :id",
                new MapSqlParameterSource()
                        .addValue("tenantId", TenantContext.requireTenantId())
                        .addValue("module", moduleKey)
                        .addValue("id", recordId));
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
