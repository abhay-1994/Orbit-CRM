package com.orbitcrm.shared.security;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The authenticated user as seen by business code; the Spring Security principal of every API request.
 *
 * <p>Purpose: carry everything needed for authorization decisions without further database lookups. It is
 * built from a validated access token by {@code PrincipalService} (and cached for a few seconds), then read
 * through {@link AccessControl}. {@link #scope} and {@link #can} evaluate permissions; administrators implicitly
 * hold every permission with {@code ALL} scope, including on modules added later.
 *
 * @author Abhay
 * @param userId        id of the user
 * @param tenantId      id of the user's tenant (workspace)
 * @param sessionId     id of the login session the access token belongs to
 * @param email         the user's email address
 * @param displayName   first and last name, used for audit entries and notifications
 * @param roleId        id of the user's role
 * @param roleName      name of the user's role
 * @param administrator whether the role is the Administrator role
 * @param teamId        the user's own team, or {@code null}
 * @param teamIds       the user's team plus all descendant teams, used for TEAM scope
 * @param permissions   resource key to action to granted scope
 */
public record CurrentUser(
        UUID userId,
        UUID tenantId,
        UUID sessionId,
        String email,
        String displayName,
        UUID roleId,
        String roleName,
        boolean administrator,
        UUID teamId,
        Set<UUID> teamIds,
        Map<String, Map<Action, AccessScope>> permissions) {

    public AccessScope scope(String resource, Action action) {
        if (administrator) {
            return AccessScope.ALL;
        }
        Map<Action, AccessScope> actions = permissions.get(resource);
        if (actions == null) {
            return AccessScope.NONE;
        }
        return actions.getOrDefault(action, AccessScope.NONE);
    }

    public boolean can(String resource, Action action) {
        return scope(resource, action).allows();
    }
}
