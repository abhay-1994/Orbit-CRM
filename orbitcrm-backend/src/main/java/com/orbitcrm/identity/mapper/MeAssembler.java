package com.orbitcrm.identity.mapper;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.orbitcrm.identity.dto.MeResponse;
import com.orbitcrm.identity.dto.Ref;
import com.orbitcrm.identity.dto.TenantSummary;
import com.orbitcrm.identity.entity.User;
import com.orbitcrm.identity.repository.TeamRepository;
import com.orbitcrm.identity.repository.UserRepository;
import com.orbitcrm.identity.service.PermissionCatalog;
import com.orbitcrm.shared.exception.ApiException;
import com.orbitcrm.shared.security.AccessScope;
import com.orbitcrm.shared.security.Action;
import com.orbitcrm.shared.security.CurrentUser;
import com.orbitcrm.shared.security.SecuredResource;
import com.orbitcrm.tenant.dto.TenantInfo;
import com.orbitcrm.tenant.service.TenantService;

/**
 * Builds the {@link com.orbitcrm.identity.dto.MeResponse} ("who am I") payload for a principal.
 *
 * <p>Purpose: combine the user entity, the tenant settings, the team and the effective permissions into the single
 * object the frontend needs after login and on {@code GET /api/v1/auth/me}. It resolves the effective time zone and
 * locale (user preference, otherwise tenant default) and, for administrators, spells out every catalog permission
 * with {@code ALL} scope so clients never need to special-case administrators.
 *
 * @author Abhay
 */
@Component
public class MeAssembler {

    private final UserRepository users;
    private final TeamRepository teams;
    private final TenantService tenants;
    private final PermissionCatalog catalog;

    MeAssembler(UserRepository users, TeamRepository teams, TenantService tenants, PermissionCatalog catalog) {
        this.users = users;
        this.teams = teams;
        this.tenants = tenants;
        this.catalog = catalog;
    }

    @Transactional(readOnly = true)
    public MeResponse assemble(CurrentUser principal) {
        User user = users.findById(principal.userId())
                .orElseThrow(() -> ApiException.unauthorized("UNAUTHENTICATED", "User no longer exists"));
        TenantInfo tenant = tenants.get(principal.tenantId());
        Ref team = user.getTeamId() == null ? null
                : teams.findById(user.getTeamId()).map(t -> new Ref(t.getId(), t.getName())).orElse(null);
        return new MeResponse(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                user.displayName(), user.getJobTitle(), user.getPhone(),
                user.getTimezone() != null ? user.getTimezone() : tenant.timezone(),
                user.getLocale() != null ? user.getLocale() : tenant.locale(),
                user.isMfaEnabled(), principal.administrator(),
                new TenantSummary(tenant.id(), tenant.slug(), tenant.name(), tenant.timezone(), tenant.currency(),
                        tenant.locale(), tenant.fiscalYearStartMonth()),
                new Ref(principal.roleId(), principal.roleName()), team, effectivePermissions(principal));
    }

    /** Administrators get every catalog permission spelled out, so clients need no special casing. */
    private Map<String, Map<Action, AccessScope>> effectivePermissions(CurrentUser principal) {
        if (!principal.administrator()) {
            return principal.permissions();
        }
        Map<String, Map<Action, AccessScope>> all = new LinkedHashMap<>();
        for (SecuredResource resource : catalog.all()) {
            Map<Action, AccessScope> actions = new EnumMap<>(Action.class);
            resource.actions().forEach(a -> actions.put(a, AccessScope.ALL));
            all.put(resource.key(), actions);
        }
        return all;
    }
}
