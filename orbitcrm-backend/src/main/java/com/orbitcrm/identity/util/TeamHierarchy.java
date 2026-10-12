package com.orbitcrm.identity.util;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.orbitcrm.identity.entity.Team;

/**
 * In-memory view of a tenant's team tree.
 *
 * <p>Purpose: answer hierarchy questions without recursive SQL: {@link #subtree} returns a team and all its
 * descendants (used to build a user's TEAM scope), and {@link #wouldCreateCycle} checks whether moving a team under
 * a new parent would create a loop. Tenants have tens of teams, not thousands, so loading the whole tree is cheaper
 * than a recursive query per request.
 *
 * @author Abhay
 */
public final class TeamHierarchy {

    private final Map<UUID, List<UUID>> children = new HashMap<>();
    private final Map<UUID, UUID> parents = new HashMap<>();

    public TeamHierarchy(Collection<Team> teams) {
        for (Team team : teams) {
            parents.put(team.getId(), team.getParentId());
            if (team.getParentId() != null) {
                children.computeIfAbsent(team.getParentId(), k -> new java.util.ArrayList<>()).add(team.getId());
            }
        }
    }

    /** The team itself plus every descendant. */
    public Set<UUID> subtree(UUID teamId) {
        Set<UUID> result = new HashSet<>();
        if (teamId == null) {
            return result;
        }
        Deque<UUID> queue = new ArrayDeque<>();
        queue.add(teamId);
        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            if (result.add(current)) {
                queue.addAll(children.getOrDefault(current, List.of()));
            }
        }
        return result;
    }

    /** True if making {@code newParentId} the parent of {@code teamId} would create a cycle. */
    public boolean wouldCreateCycle(UUID teamId, UUID newParentId) {
        UUID cursor = newParentId;
        Set<UUID> seen = new HashSet<>();
        while (cursor != null && seen.add(cursor)) {
            if (cursor.equals(teamId)) {
                return true;
            }
            cursor = parents.get(cursor);
        }
        return false;
    }
}
