package com.orbitcrm.identity.service;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.orbitcrm.identity.dto.UserSummary;

/**
 * Public API of the identity module for other modules.
 *
 * <p>Purpose: let modules resolve user information without depending on identity internals. Always scoped to the
 * current tenant:
 * <ul>
 *   <li>{@link #displayNames} - names for ids (timelines, notes, audit views, record owner labels);</li>
 *   <li>{@link #find} / {@link #summaries} - summary of one or many users (status, email, team);</li>
 *   <li>{@link #isActive} - whether a user may own or be assigned records;</li>
 *   <li>{@link #findActiveId} - resolve an active user by email or unique full name, e.g. an "Owner" column in a
 *   CSV import.</li>
 * </ul>
 *
 * @author Abhay
 */
public interface UserDirectory {

    /** Display names for the given ids; unknown ids are omitted. */
    Map<UUID, String> displayNames(Collection<UUID> userIds);

    Optional<UserSummary> find(UUID userId);

    /** Summaries for the given ids in one query; unknown ids are omitted. */
    Map<UUID, UserSummary> summaries(Collection<UUID> userIds);

    /** True if the user exists in the current tenant and is active. */
    boolean isActive(UUID userId);

    /** Resolves an active user by email or exact full name (case-insensitive), e.g. for CSV imports. */
    Optional<UUID> findActiveId(String emailOrName);
}
