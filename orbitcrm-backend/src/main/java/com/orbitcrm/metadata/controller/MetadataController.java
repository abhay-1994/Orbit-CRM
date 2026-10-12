package com.orbitcrm.metadata.controller;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.orbitcrm.metadata.dto.FieldMeta;
import com.orbitcrm.metadata.dto.FieldOption;
import com.orbitcrm.metadata.dto.ModuleDetail;
import com.orbitcrm.metadata.dto.ModuleSummary;
import com.orbitcrm.metadata.model.FieldDefinition;
import com.orbitcrm.metadata.model.FieldType;
import com.orbitcrm.metadata.model.ModuleDefinition;
import com.orbitcrm.metadata.service.ModuleRegistry;
import com.orbitcrm.metadata.service.PicklistService;
import com.orbitcrm.metadata.service.ReferenceResolvers;
import com.orbitcrm.shared.dto.ApiResponse;
import com.orbitcrm.shared.exception.ApiException;
import com.orbitcrm.shared.security.AccessControl;
import com.orbitcrm.shared.security.AccessScope;
import com.orbitcrm.shared.security.Action;
import com.orbitcrm.shared.security.CurrentUser;

/**
 * REST controller that publishes module and field metadata at {@code /api/v1/metadata}.
 *
 * <p>Purpose: let the frontend render list columns, forms, filters and validation generically from metadata -
 * including tenant-defined custom fields once they exist (V3) - instead of hard-coding every module.
 * <ul>
 *   <li>{@code GET /modules} - the record modules the current user can read, with labels, icon, record number
 *   prefix and the user's scope for every action (used to build navigation and to show or hide buttons).</li>
 *   <li>{@code GET /modules/{key}} - full metadata of one module: title fields, default sort, permissions and every
 *   field with its type, validation rules (required, read-only, length, range), UI hints (group, help text, list
 *   default), capabilities (searchable, filterable, sortable), lookup target, the current tenant's picklist options
 *   or reference options, and the filter operators the field supports.</li>
 * </ul>
 *
 * @author Abhay
 */
@RestController
@RequestMapping("/api/v1/metadata")
@Tag(name = "Metadata", description = "Modules, fields, picklist options and filter operators for generic UIs")
public class MetadataController {

    private final ModuleRegistry registry;
    private final PicklistService picklists;
    private final ReferenceResolvers references;

    MetadataController(ModuleRegistry registry, PicklistService picklists, ReferenceResolvers references) {
        this.registry = registry;
        this.picklists = picklists;
        this.references = references;
    }

    @GetMapping("/modules")
    @Operation(summary = "Modules the current user can read, with their permissions")
    ApiResponse<List<ModuleSummary>> modules() {
        CurrentUser user = AccessControl.requireUser();
        return ApiResponse.ok(registry.all().stream()
                .filter(m -> user.can(m.key(), Action.READ))
                .map(m -> new ModuleSummary(m.key(), m.singularLabel(), m.pluralLabel(), m.icon(), m.recordPrefix(),
                        permissions(user, m)))
                .toList());
    }

    @GetMapping("/modules/{key}")
    @Operation(summary = "Full field metadata for one module")
    ApiResponse<ModuleDetail> module(@PathVariable String key) {
        CurrentUser user = AccessControl.requireUser();
        ModuleDefinition module = registry.require(key);
        if (!user.can(key, Action.READ)) {
            throw ApiException.forbidden("ACCESS_DENIED", "You do not have access to " + key);
        }
        List<FieldMeta> fields = module.fields().stream().map(this::fieldMeta).toList();
        return ApiResponse.ok(new ModuleDetail(module.key(), module.singularLabel(), module.pluralLabel(),
                module.icon(), module.recordPrefix(), module.titleFields(), module.defaultSort(),
                permissions(user, module), fields));
    }

    private FieldMeta fieldMeta(FieldDefinition field) {
        List<FieldOption> options = null;
        if (field.picklist() != null) {
            options = picklists.values(field.picklist()).stream()
                    .map(v -> new FieldOption(v.key(), v.label(), v.color(), v.active(), v.isDefault(), null))
                    .toList();
        } else if (field.fixedOptions() != null && !field.fixedOptions().isEmpty()) {
            options = field.fixedOptions().stream()
                    .map(o -> new FieldOption(o.key(), o.label(), o.color(), true, o.isDefault(), null))
                    .toList();
        } else if (field.type() == FieldType.REFERENCE) {
            options = references.find(field.referenceType()).map(r -> r.options().stream()
                    .map(o -> new FieldOption(o.id().toString(), o.label(), null, true, false, o.group()))
                    .toList()).orElse(List.of());
        }
        return new FieldMeta(field.key(), field.label(), field.type(), field.required(), field.readOnly(),
                field.maxLength(), field.min(), field.max(), field.group(), field.helpText(), field.searchable(),
                field.filterable(), field.sortable(), field.listDefault(), field.lookupModule(),
                field.lookupModuleField(), field.referenceType(), field.picklist(), options,
                field.filterable() ? field.type().operators() : Set.of());
    }

    private static Map<Action, AccessScope> permissions(CurrentUser user, ModuleDefinition module) {
        Map<Action, AccessScope> map = new EnumMap<>(Action.class);
        for (Action action : Action.values()) {
            map.put(action, user.scope(module.key(), action));
        }
        return map;
    }
}
