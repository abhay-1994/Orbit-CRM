package com.orbitcrm.shared.security;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.orbitcrm.shared.exception.ApiException;

/**
 * Static access to the authenticated user and coarse permission checks.
 *
 * <p>Purpose: answer "may the current user perform action X on resource Y at all?" anywhere in the code without
 * passing the user around. {@link #requireUser()} fails with 401 when unauthenticated, {@link #require} fails with
 * 403 when the role grants no scope for the action and otherwise returns the granted
 * {@link AccessScope}, and {@link #requireAdministrator()} guards admin-only operations. Row-level visibility
 * (OWN / TEAM / ALL) is enforced separately by the record engine's SQL predicates.
 *
 * @author Abhay
 */
public final class AccessControl {

    private AccessControl() {
    }

    public static Optional<CurrentUser> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CurrentUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public static CurrentUser requireUser() {
        return currentUser().orElseThrow(() ->
                ApiException.unauthorized("UNAUTHENTICATED", "Authentication is required"));
    }

    /** Throws 403 unless the user holds {@code action} on {@code resource} with any scope. */
    public static AccessScope require(String resource, Action action) {
        CurrentUser user = requireUser();
        AccessScope scope = user.scope(resource, action);
        if (!scope.allows()) {
            throw ApiException.forbidden("ACCESS_DENIED",
                    "You do not have permission to " + action.name().toLowerCase() + " " + resource);
        }
        return scope;
    }

    public static void requireAdministrator() {
        if (!requireUser().administrator()) {
            throw ApiException.forbidden("ACCESS_DENIED", "Administrator privileges are required");
        }
    }
}
