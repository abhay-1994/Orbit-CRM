package com.orbitcrm.identity.dto;

import java.util.Set;

import com.orbitcrm.shared.security.Action;

/**
 * One resource in the permission catalog returned by {@code GET /api/v1/roles/permission-catalog}.
 *
 * <p>Purpose: describe a row of the role editor's permission matrix: which actions can be granted on the resource
 * and whether grants carry a scope.
 *
 * @author Abhay
 * @param key      resource key used in permissions, e.g. {@code leads}, {@code users}
 * @param label    human-readable name
 * @param category {@code crm} for record modules, {@code system} for administration
 * @param actions  actions applicable to this resource
 * @param scoped   whether grants take an OWN/TEAM/ALL scope ({@code true}) or are simply on/off
 */
public record CatalogResource(String key, String label, String category, Set<Action> actions, boolean scoped) {
}
