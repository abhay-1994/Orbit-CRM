package com.orbitcrm.identity.listener;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.orbitcrm.identity.entity.PermissionGrant;
import com.orbitcrm.identity.entity.Role;
import com.orbitcrm.identity.repository.RoleRepository;
import com.orbitcrm.identity.service.PermissionCatalog;
import com.orbitcrm.shared.security.AccessScope;
import com.orbitcrm.shared.security.Action;
import com.orbitcrm.shared.security.SecuredResource;
import com.orbitcrm.shared.security.SystemResources;
import com.orbitcrm.tenant.event.TenantCreatedEvent;

/**
 * Seeds the standard roles for every new tenant, listening to {@code TenantCreatedEvent}.
 *
 * <p>Purpose: give a new workspace sensible, editable roles that match the user types of PRD section 5, so an
 * administrator can invite colleagues right away:
 * <ul>
 *   <li><b>Administrator</b> - full access to every module and setting (locked);</li>
 *   <li><b>Sales Manager</b> - CRUD and export with TEAM scope on all CRM modules, import, and read access to users
 *   and teams;</li>
 *   <li><b>Sales User</b> - works their own records (OWN scope) and reads all companies and contacts;</li>
 *   <li><b>Support Agent</b> - reads and maintains all companies and contacts (no delete) and their own
 *   activities;</li>
 *   <li><b>Marketing User</b> - full access to leads including import/export, read and export of companies and
 *   contacts, own activities;</li>
 *   <li><b>Management</b> - read and export everything, plus read access to users, teams and the audit log.</li>
 * </ul>
 * Grants are derived from the permission catalog, so any CRM module present in the application automatically
 * receives defaults.
 *
 * @author Abhay
 */
@Component
public class DefaultRoles {

    public static final String ADMINISTRATOR = "Administrator";
    public static final String SALES_MANAGER = "Sales Manager";
    public static final String SALES_USER = "Sales User";

    /** Customer master data that front-line users share across the organization. */
    private static final Set<String> SHARED_CUSTOMER_DATA = Set.of("companies", "contacts");

    private final RoleRepository roles;
    private final PermissionCatalog catalog;

    DefaultRoles(RoleRepository roles, PermissionCatalog catalog) {
        this.roles = roles;
        this.catalog = catalog;
    }

    @EventListener
    void onTenantCreated(TenantCreatedEvent event) {
        roles.save(new Role(ADMINISTRATOR, "Full access to every module and setting", true, true));

        save(SALES_MANAGER, "Manages the team's leads, deals and pipeline", resource -> {
            List<PermissionGrant> grants = crud(resource, AccessScope.TEAM);
            grants.add(grant(resource, Action.EXPORT, AccessScope.TEAM));
            grants.add(grant(resource, Action.IMPORT, AccessScope.ALL));
            return grants;
        }, List.of(
                grant(SystemResources.USERS, Action.READ, AccessScope.ALL),
                grant(SystemResources.TEAMS, Action.READ, AccessScope.ALL)));

        save(SALES_USER, "Works their own leads, deals and activities", resource ->
                SHARED_CUSTOMER_DATA.contains(resource)
                        ? withRead(crud(resource, AccessScope.OWN), resource, AccessScope.ALL)
                        : crud(resource, AccessScope.OWN), List.of());

        save("Support Agent", "Serves customers; reads and maintains customer data", resource -> {
            if (SHARED_CUSTOMER_DATA.contains(resource)) {
                return crud(resource, AccessScope.ALL).stream()
                        .filter(g -> g.action() != Action.DELETE).toList();
            }
            return "activities".equals(resource) ? crud(resource, AccessScope.OWN) : List.of();
        }, List.of());

        save("Marketing User", "Generates and nurtures leads; imports and exports lists", resource -> {
            if ("leads".equals(resource)) {
                List<PermissionGrant> grants = crud(resource, AccessScope.ALL);
                grants.add(grant(resource, Action.IMPORT, AccessScope.ALL));
                grants.add(grant(resource, Action.EXPORT, AccessScope.ALL));
                return grants;
            }
            if (SHARED_CUSTOMER_DATA.contains(resource)) {
                return List.of(grant(resource, Action.READ, AccessScope.ALL),
                        grant(resource, Action.EXPORT, AccessScope.ALL));
            }
            return "activities".equals(resource) ? crud(resource, AccessScope.OWN) : List.of();
        }, List.of());

        save("Management", "Read-only visibility of all records, dashboards and reports", resource ->
                List.of(grant(resource, Action.READ, AccessScope.ALL), grant(resource, Action.EXPORT, AccessScope.ALL)),
                List.of(grant(SystemResources.USERS, Action.READ, AccessScope.ALL),
                        grant(SystemResources.TEAMS, Action.READ, AccessScope.ALL),
                        grant(SystemResources.AUDIT, Action.READ, AccessScope.ALL)));
    }

    private void save(String name, String description, Function<String, List<PermissionGrant>> crmGrants,
                      List<PermissionGrant> systemGrants) {
        Role role = new Role(name, description, false, true);
        List<PermissionGrant> grants = new ArrayList<>(systemGrants);
        for (SecuredResource resource : catalog.byCategory(SecuredResource.CATEGORY_CRM)) {
            grants.addAll(crmGrants.apply(resource.key()));
        }
        role.replaceGrants(grants);
        roles.save(role);
    }

    private static List<PermissionGrant> crud(String resource, AccessScope scope) {
        List<PermissionGrant> grants = new ArrayList<>();
        grants.add(grant(resource, Action.CREATE, AccessScope.ALL));
        grants.add(grant(resource, Action.READ, scope));
        grants.add(grant(resource, Action.UPDATE, scope));
        grants.add(grant(resource, Action.DELETE, scope));
        return grants;
    }

    private static List<PermissionGrant> withRead(List<PermissionGrant> grants, String resource, AccessScope scope) {
        grants.removeIf(g -> g.action() == Action.READ);
        grants.add(grant(resource, Action.READ, scope));
        return grants;
    }

    private static PermissionGrant grant(String resource, Action action, AccessScope scope) {
        return new PermissionGrant(resource, action, scope);
    }
}
