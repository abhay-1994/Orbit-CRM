package com.orbitcrm.identity.config;

import java.util.List;

import org.springframework.stereotype.Component;

import com.orbitcrm.shared.security.Action;
import com.orbitcrm.shared.security.SecuredResource;
import com.orbitcrm.shared.security.SecuredResourceProvider;
import com.orbitcrm.shared.security.SystemResources;

/**
 * Contributes the administrative (system) permission resources to the role permission catalog.
 *
 * <p>Purpose: declare which administration areas roles can be granted access to, so they appear in the role
 * editor and in default-role seeding next to the CRM modules. The resources are:
 * <ul>
 *   <li>{@code users} - create (invite), read, update, delete users;</li>
 *   <li>{@code roles} - manage roles and their permission matrix;</li>
 *   <li>{@code teams} - manage the team hierarchy;</li>
 *   <li>{@code settings} - read and update organization settings (tenant, picklists, pipelines, shared views);</li>
 *   <li>{@code audit} - read the audit log.</li>
 * </ul>
 * System resources are not scoped: a grant is simply on or off.
 *
 * @author Abhay
 */
@Component
public class IdentityResources implements SecuredResourceProvider {

    @Override
    public List<SecuredResource> securedResources() {
        return List.of(
                SecuredResource.system(SystemResources.USERS, "Users",
                        Action.CREATE, Action.READ, Action.UPDATE, Action.DELETE),
                SecuredResource.system(SystemResources.ROLES, "Roles & permissions",
                        Action.CREATE, Action.READ, Action.UPDATE, Action.DELETE),
                SecuredResource.system(SystemResources.TEAMS, "Teams",
                        Action.CREATE, Action.READ, Action.UPDATE, Action.DELETE),
                SecuredResource.system(SystemResources.SETTINGS, "Organization settings",
                        Action.READ, Action.UPDATE),
                SecuredResource.system(SystemResources.AUDIT, "Audit log", Action.READ));
    }
}
