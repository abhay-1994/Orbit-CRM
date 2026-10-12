package com.orbitcrm.metadata.model;

import java.util.List;

/**
 * A tenant-editable picklist and the values seeded for every new tenant.
 *
 * <p>Purpose: declared by modules inside their {@link ModuleDefinition} (for example {@code lead_source} or
 * {@code industry}). When a tenant is created - or when a new release adds a picklist - its default options are
 * copied into the tenant's {@code picklist_values}. From then on tenants can rename, recolor, reorder, deactivate
 * and add values; system values stay active.
 *
 * @author Abhay
 * @param key      picklist key referenced by fields, e.g. {@code lead_status}
 * @param label    picklist name shown in settings
 * @param defaults values seeded for new tenants, in display order
 */
public record PicklistDefinition(String key, String label, List<PicklistOption> defaults) {

    public static PicklistDefinition of(String key, String label, PicklistOption... defaults) {
        return new PicklistDefinition(key, label, List.of(defaults));
    }
}
