package com.orbitcrm.view.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import com.orbitcrm.metadata.service.ModuleRegistry;
import com.orbitcrm.record.dto.RecordView;
import com.orbitcrm.record.dto.SearchRequest;
import com.orbitcrm.record.service.RecordService;
import com.orbitcrm.shared.dto.PageResponse;
import com.orbitcrm.shared.exception.ApiException;
import com.orbitcrm.shared.security.AccessControl;
import com.orbitcrm.shared.security.Action;
import com.orbitcrm.shared.security.CurrentUser;
import com.orbitcrm.shared.security.SystemResources;
import com.orbitcrm.shared.tenant.TenantContext;
import com.orbitcrm.shared.util.UuidV7;
import com.orbitcrm.tenant.event.TenantCreatedEvent;
import com.orbitcrm.view.dto.UpdateViewRequest;
import com.orbitcrm.view.dto.ViewRequest;
import com.orbitcrm.view.dto.ViewResponse;

/**
 * Business logic of saved list views.
 *
 * <p>Purpose: store named filter + sort + column sets per module and run them.
 * <ul>
 *   <li>Private views belong to their creator; shared views are visible to everyone in the tenant who can read the
 *   module and are managed by users with the settings permission.</li>
 *   <li>Filters and sorts are validated before saving by running them through the record engine.</li>
 *   <li>Running a view goes through the record engine with the caller's own scope, so the same shared view shows each
 *   user only what they may see.</li>
 *   <li>On {@code TenantCreatedEvent} it seeds built-in views: My Open Deals, Closing This Month, My Open Leads, New
 *   This Week, My Open Tasks and Overdue.</li>
 * </ul>
 *
 * @author Abhay
 */
@Service
public class SavedViewService {

    private static final TypeReference<Object> ANY = new TypeReference<>() { };
    private static final String COLUMNS = "id, module_key, name, owner_user_id, visibility, system, filter, sort, "
            + "columns, position, created_at, updated_at";

    private final NamedParameterJdbcTemplate jdbc;
    private final RecordService records;
    private final ModuleRegistry registry;
    private final JsonMapper json;

    SavedViewService(NamedParameterJdbcTemplate jdbc, RecordService records, ModuleRegistry registry, JsonMapper json) {
        this.jdbc = jdbc;
        this.records = records;
        this.registry = registry;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public List<ViewResponse> list(String moduleKey) {
        registry.require(moduleKey);
        AccessControl.require(moduleKey, Action.READ);
        CurrentUser user = AccessControl.requireUser();
        return jdbc.query("SELECT " + COLUMNS + " FROM saved_views WHERE tenant_id = :tenantId AND module_key = :module "
                        + "AND (visibility = 'SHARED' OR owner_user_id = :me) ORDER BY system DESC, position, name",
                new MapSqlParameterSource("tenantId", user.tenantId()).addValue("module", moduleKey)
                        .addValue("me", user.userId()), mapper());
    }

    @Transactional
    public ViewResponse create(ViewRequest request) {
        registry.require(request.module());
        AccessControl.require(request.module(), Action.READ);
        CurrentUser user = AccessControl.requireUser();
        String visibility = visibility(request.visibility());
        validateQuery(request.module(), request.filter(), request.sort());
        UUID id = UuidV7.generate();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("""
                INSERT INTO saved_views (id, tenant_id, module_key, name, owner_user_id, visibility, system, filter, sort,
                                         columns, position, created_at, updated_at, version)
                VALUES (:id, :tenantId, :module, :name, :owner, :visibility, false, CAST(:filter AS jsonb),
                        CAST(:sort AS jsonb), CAST(:columns AS jsonb), 100, :now, :now, 0)
                """, new MapSqlParameterSource()
                .addValue("id", id).addValue("tenantId", user.tenantId()).addValue("module", request.module())
                .addValue("name", request.name().trim()).addValue("owner", user.userId())
                .addValue("visibility", visibility).addValue("filter", write(request.filter()))
                .addValue("sort", write(request.sort())).addValue("columns", write(request.columns()))
                .addValue("now", now));
        return get(id);
    }

    @Transactional
    public ViewResponse update(UUID id, UpdateViewRequest request) {
        ViewResponse existing = requireEditable(id);
        String visibility = request.visibility() == null ? existing.visibility() : visibility(request.visibility());
        Object filter = request.filter() != null ? request.filter() : existing.filter();
        Object sort = request.sort() != null ? request.sort() : existing.sort();
        validateQuery(existing.module(), filter, sort);
        jdbc.update("""
                UPDATE saved_views SET name = :name, visibility = :visibility, filter = CAST(:filter AS jsonb),
                       sort = CAST(:sort AS jsonb), columns = CAST(:columns AS jsonb), updated_at = :now,
                       version = version + 1
                WHERE id = :id AND tenant_id = :tenantId
                """, new MapSqlParameterSource()
                .addValue("id", id).addValue("tenantId", TenantContext.requireTenantId())
                .addValue("name", request.name() == null ? existing.name() : request.name().trim())
                .addValue("visibility", visibility).addValue("filter", write(filter)).addValue("sort", write(sort))
                .addValue("columns", write(request.columns() != null ? request.columns() : existing.columns()))
                .addValue("now", OffsetDateTime.now(ZoneOffset.UTC)));
        return get(id);
    }

    @Transactional
    public void delete(UUID id) {
        ViewResponse view = requireEditable(id);
        if (view.system()) {
            throw ApiException.conflict("VIEW_LOCKED", "Built-in views cannot be deleted");
        }
        jdbc.update("DELETE FROM saved_views WHERE id = :id AND tenant_id = :tenantId",
                new MapSqlParameterSource("id", id).addValue("tenantId", TenantContext.requireTenantId()));
    }

    /** Runs the view: its filter and sort through the record engine, with the caller's scope. */
    @Transactional(readOnly = true)
    public PageResponse<RecordView> run(UUID id, String q, Integer page, Integer size) {
        ViewResponse view = requireVisible(id);
        return records.search(view.module(), new SearchRequest(view.filter(), q, sorts(view.sort()), page, size));
    }

    @EventListener
    void seedDefaults(TenantCreatedEvent event) {
        seed("deals", "My Open Deals", Map.of("and", List.of(
                Map.of("field", "ownerId", "op", "eq", "value", "$me"),
                Map.of("field", "status", "op", "eq", "value", "open"))), "expectedCloseDate", "asc", 1);
        seed("deals", "Closing This Month", Map.of("and", List.of(
                Map.of("field", "status", "op", "eq", "value", "open"),
                Map.of("field", "expectedCloseDate", "op", "between", "value", List.of("$startOfMonth", "$endOfMonth")))),
                "amount", "desc", 2);
        seed("leads", "My Open Leads", Map.of("and", List.of(
                Map.of("field", "ownerId", "op", "eq", "value", "$me"),
                Map.of("field", "status", "op", "not_in", "value", List.of("converted", "unqualified")))),
                "createdAt", "desc", 1);
        seed("leads", "New This Week", Map.of("field", "createdAt", "op", "within_last_days", "value", 7),
                "createdAt", "desc", 2);
        seed("activities", "My Open Tasks", Map.of("and", List.of(
                Map.of("field", "ownerId", "op", "eq", "value", "$me"),
                Map.of("field", "status", "op", "in", "value", List.of("not_started", "in_progress")))),
                "endAt", "asc", 1);
        seed("activities", "Overdue", Map.of("and", List.of(
                Map.of("field", "status", "op", "in", "value", List.of("not_started", "in_progress")),
                Map.of("field", "endAt", "op", "lt", "value", "$now"))), "endAt", "asc", 2);
    }

    private void seed(String module, String name, Object filter, String sortField, String direction, int position) {
        if (registry.find(module).isEmpty()) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("""
                INSERT INTO saved_views (id, tenant_id, module_key, name, owner_user_id, visibility, system, filter, sort,
                                         columns, position, created_at, updated_at, version)
                VALUES (:id, :tenantId, :module, :name, NULL, 'SHARED', true, CAST(:filter AS jsonb), CAST(:sort AS jsonb),
                        NULL, :position, :now, :now, 0)
                """, new MapSqlParameterSource()
                .addValue("id", UuidV7.generate()).addValue("tenantId", TenantContext.requireTenantId())
                .addValue("module", module).addValue("name", name).addValue("filter", write(filter))
                .addValue("sort", write(List.of(Map.of("field", sortField, "direction", direction))))
                .addValue("position", position).addValue("now", now));
    }

    private void validateQuery(String module, Object filter, Object sort) {
        // Compiling (count) proves every field, operator and value in the filter is valid for the module.
        records.search(module, new SearchRequest(filter, null, sorts(sort), 0, 1));
    }

    private ViewResponse requireVisible(UUID id) {
        ViewResponse view = get(id);
        CurrentUser user = AccessControl.requireUser();
        if ("PRIVATE".equals(view.visibility()) && !user.userId().equals(view.ownerUserId())) {
            throw ApiException.notFound("VIEW_NOT_FOUND", "View does not exist");
        }
        AccessControl.require(view.module(), Action.READ);
        return view;
    }

    private ViewResponse requireEditable(UUID id) {
        ViewResponse view = requireVisible(id);
        CurrentUser user = AccessControl.requireUser();
        boolean owner = user.userId().equals(view.ownerUserId());
        if (!owner && !user.can(SystemResources.SETTINGS, Action.UPDATE)) {
            throw ApiException.forbidden("ACCESS_DENIED", "Only the owner or an administrator can change this view");
        }
        return view;
    }

    private String visibility(String requested) {
        String visibility = requested == null ? "PRIVATE" : requested.toUpperCase();
        if (!visibility.equals("PRIVATE") && !visibility.equals("SHARED")) {
            throw ApiException.validation("visibility", "INVALID", "Visibility must be PRIVATE or SHARED");
        }
        if (visibility.equals("SHARED")) {
            AccessControl.require(SystemResources.SETTINGS, Action.UPDATE);
        }
        return visibility;
    }

    private ViewResponse get(UUID id) {
        List<ViewResponse> rows = jdbc.query("SELECT " + COLUMNS + " FROM saved_views WHERE id = :id AND tenant_id = :tenantId",
                new MapSqlParameterSource("id", id).addValue("tenantId", TenantContext.requireTenantId()), mapper());
        if (rows.isEmpty()) {
            throw ApiException.notFound("VIEW_NOT_FOUND", "View does not exist");
        }
        return rows.get(0);
    }

    @SuppressWarnings("unchecked")
    private static List<SearchRequest.Sort> sorts(Object sort) {
        if (!(sort instanceof List<?> list)) {
            return null;
        }
        return list.stream().map(item -> {
            Map<String, Object> map = (Map<String, Object>) item;
            return new SearchRequest.Sort(String.valueOf(map.get("field")),
                    map.get("direction") == null ? "asc" : String.valueOf(map.get("direction")));
        }).toList();
    }

    private String write(Object value) {
        return value == null ? null : json.writeValueAsString(value);
    }

    private RowMapper<ViewResponse> mapper() {
        return (rs, n) -> new ViewResponse(rs.getObject("id", UUID.class), rs.getString("module_key"),
                rs.getString("name"), rs.getObject("owner_user_id", UUID.class), rs.getString("visibility"),
                rs.getBoolean("system"), read(rs.getString("filter")), read(rs.getString("sort")),
                read(rs.getString("columns")), rs.getInt("position"));
    }

    private Object read(String raw) {
        return raw == null ? null : json.readValue(raw, ANY);
    }
}
