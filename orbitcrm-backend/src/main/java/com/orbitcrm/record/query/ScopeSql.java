package com.orbitcrm.record.query;

import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import com.orbitcrm.metadata.model.ModuleDefinition;
import com.orbitcrm.shared.security.AccessScope;
import com.orbitcrm.shared.security.CurrentUser;

/**
 * Turns a permission scope into a SQL predicate on the record owner.
 *
 * <p>Purpose: the single place where row-level visibility is enforced. The predicate is applied on every read path -
 * lists, detail, counts, search, aggregates, exports, lookup validation - so no path can bypass OWN / TEAM visibility:
 * <ul>
 *   <li>{@code ALL} - no restriction ({@code null});</li>
 *   <li>{@code OWN} - {@code owner_id} is the user;</li>
 *   <li>{@code TEAM} - the owner is the user or anyone currently in the user's team or its sub-teams;</li>
 *   <li>{@code NONE} - {@code FALSE}.</li>
 * </ul>
 * For modules with a sharing field (e.g. meeting participants) users listed in that field also see the record. All
 * values are bound as parameters.
 *
 * @author Abhay
 */
public final class ScopeSql {

    private ScopeSql() {
    }

    /** Returns {@code null} when no restriction applies (ALL). */
    public static String predicate(AccessScope scope, CurrentUser user, MapSqlParameterSource params, String alias,
                            String paramPrefix, ModuleDefinition module) {
        String owner = alias + ".owner_id";
        String userParam = paramPrefix + "User";
        String base = switch (scope) {
            case ALL -> null;
            case NONE -> "FALSE";
            case OWN -> {
                params.addValue(userParam, user.userId());
                yield owner + " = :" + userParam;
            }
            case TEAM -> {
                params.addValue(userParam, user.userId());
                if (user.teamIds().isEmpty()) {
                    yield owner + " = :" + userParam;
                }
                String teamParam = paramPrefix + "Teams";
                params.addValue(teamParam, List.copyOf(user.teamIds()));
                yield "(" + owner + " = :" + userParam + " OR " + owner
                        + " IN (SELECT su.id FROM users su WHERE su.team_id IN (:" + teamParam + ")))";
            }
        };
        String sharing = module == null ? null : module.sharingColumn();
        if (base == null || scope == AccessScope.NONE || sharing == null) {
            return base;
        }
        return "(" + base + " OR :" + userParam + " = ANY(" + alias + "." + sharing + "))";
    }
}
