package com.orbitcrm.search.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.orbitcrm.metadata.model.ModuleDefinition;
import com.orbitcrm.metadata.service.ModuleRegistry;
import com.orbitcrm.search.dto.SearchGroup;
import com.orbitcrm.search.dto.SearchHit;
import com.orbitcrm.search.dto.SearchResult;
import com.orbitcrm.shared.dto.ApiResponse;
import com.orbitcrm.shared.exception.ApiException;
import com.orbitcrm.shared.security.AccessControl;
import com.orbitcrm.shared.security.AccessScope;
import com.orbitcrm.shared.security.Action;
import com.orbitcrm.shared.security.CurrentUser;

/**
 * REST controller for global search across all modules at {@code /api/v1/search} (PRD section 32).
 *
 * <p>Purpose: the search box in the top bar - one query that finds leads, contacts, companies, deals, activities and
 * any future module at once. It searches the denormalized {@code search_index} table with trigram-indexed substring
 * matching on titles, emails, phone digits and names, applies each module's READ scope (OWN / TEAM / ALL) directly in
 * SQL, and returns per-module counts plus the best matches per module (title prefix matches first, then similarity,
 * then most recently updated). Parameters: {@code q} (2-100 characters), optional {@code modules} to restrict the
 * search, and {@code limit} results per module (1-20, default 5).
 *
 * @author Abhay
 */
@RestController
@RequestMapping("/api/v1/search")
@Tag(name = "Search", description = "Search across all modules")
public class GlobalSearchController {

    private final NamedParameterJdbcTemplate jdbc;
    private final ModuleRegistry registry;

    GlobalSearchController(NamedParameterJdbcTemplate jdbc, ModuleRegistry registry) {
        this.jdbc = jdbc;
        this.registry = registry;
    }

    @GetMapping
    @Operation(summary = "Search leads, contacts, companies, deals, activities... at once",
            description = "Returns per-module counts and the best matches (title prefix matches first)")
    @Transactional(readOnly = true)
    public ApiResponse<SearchResult> search(@RequestParam String q,
                                            @RequestParam(required = false) List<String> modules,
                                            @RequestParam(defaultValue = "5") int limit) {
        String term = q == null ? "" : q.trim();
        if (term.length() < 2 || term.length() > 100) {
            throw ApiException.validation("q", "INVALID_LENGTH", "Search text must be 2-100 characters");
        }
        int perModule = Math.min(Math.max(limit, 1), 20);
        CurrentUser user = AccessControl.requireUser();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", user.tenantId())
                .addValue("pattern", "%" + escape(term.toLowerCase()) + "%")
                .addValue("prefix", escape(term) + "%")
                .addValue("q", term)
                .addValue("limit", perModule)
                .addValue("me", user.userId());

        List<String> moduleClauses = new ArrayList<>();
        int i = 0;
        for (ModuleDefinition module : registry.all()) {
            if (modules != null && !modules.isEmpty() && !modules.contains(module.key())) {
                continue;
            }
            AccessScope scope = user.scope(module.key(), Action.READ);
            if (!scope.allows()) {
                continue;
            }
            String moduleParam = "m" + (i++);
            params.addValue(moduleParam, module.key());
            String clause = "s.module_key = :" + moduleParam;
            switch (scope) {
                case OWN -> clause += " AND s.owner_id = :me";
                case TEAM -> {
                    if (user.teamIds().isEmpty()) {
                        clause += " AND s.owner_id = :me";
                    } else {
                        params.addValue("teams", List.copyOf(user.teamIds()));
                        clause += " AND (s.owner_id = :me OR s.owner_id IN (SELECT su.id FROM users su "
                                + "WHERE su.team_id IN (:teams)))";
                    }
                }
                default -> {
                }
            }
            moduleClauses.add("(" + clause + ")");
        }
        if (moduleClauses.isEmpty()) {
            return ApiResponse.ok(new SearchResult(term, 0, List.of()));
        }
        String where = "s.tenant_id = :tenantId AND s.content LIKE :pattern ESCAPE '\\' AND ("
                + String.join(" OR ", moduleClauses) + ")";

        Map<String, Long> counts = new LinkedHashMap<>();
        jdbc.query("SELECT s.module_key, count(*) AS total FROM search_index s WHERE " + where
                + " GROUP BY s.module_key", params, rs -> {
                    counts.put(rs.getString("module_key"), rs.getLong("total"));
                });
        Map<String, List<SearchHit>> hits = new LinkedHashMap<>();
        jdbc.query("""
                SELECT module_key, record_id, record_number, title, subtitle FROM (
                    SELECT s.*, row_number() OVER (PARTITION BY s.module_key
                        ORDER BY (s.title ILIKE :prefix ESCAPE '\\') DESC, similarity(s.title, :q) DESC,
                                 s.updated_at DESC) AS rank
                    FROM search_index s WHERE %s
                ) ranked WHERE rank <= :limit
                """.formatted(where), params, rs -> {
                    hits.computeIfAbsent(rs.getString("module_key"), k -> new ArrayList<>()).add(new SearchHit(
                            rs.getObject("record_id", UUID.class), rs.getString("record_number"),
                            rs.getString("title"), rs.getString("subtitle")));
                });

        List<SearchGroup> groups = new ArrayList<>();
        long total = 0;
        for (ModuleDefinition module : registry.all()) {
            Long count = counts.get(module.key());
            if (count == null || count == 0) {
                continue;
            }
            total += count;
            groups.add(new SearchGroup(module.key(), module.pluralLabel(), count, hits.getOrDefault(module.key(), List.of())));
        }
        return ApiResponse.ok(new SearchResult(term, total, groups));
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
