package com.orbitcrm.shared.security;

import java.util.List;
import java.util.Set;

/**
 * One entry of the permission catalog: something a role can be granted permissions on.
 *
 * <p>Purpose: describe resources such as {@code leads} or {@code users} to the role editor and to default-role
 * seeding. Record modules are {@code crm} resources with scoped actions; administrative resources are
 * {@code system} resources whose grants are on/off.
 *
 * @author Abhay
 * @param key      resource key used in permissions, e.g. {@code leads}, {@code users}
 * @param label    human-readable name for the role editor
 * @param category {@code crm} for business records, {@code system} for administration
 * @param actions  the actions that make sense for this resource
 * @param scoped   whether grants carry an OWN/TEAM/ALL scope (record modules) or are simply granted or not
 */
public record SecuredResource(String key, String label, String category, Set<Action> actions, boolean scoped) {

    public static final String CATEGORY_CRM = "crm";
    public static final String CATEGORY_SYSTEM = "system";

    public static SecuredResource crm(String key, String label) {
        return new SecuredResource(key, label, CATEGORY_CRM, Set.of(Action.values()), true);
    }

    public static SecuredResource system(String key, String label, Action... actions) {
        return new SecuredResource(key, label, CATEGORY_SYSTEM, Set.copyOf(List.of(actions)), false);
    }
}
