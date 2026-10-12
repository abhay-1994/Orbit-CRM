package com.orbitcrm.identity.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Representation of a team returned by the team endpoints.
 *
 * <p>Purpose: render the team hierarchy (flat list with parent ids) together with member counts.
 *
 * @author Abhay
 * @param id          team id
 * @param name        team name
 * @param description description
 * @param parentId    parent team id, or {@code null} for a top-level team
 * @param memberCount number of users in this team (not counting sub-teams)
 * @param createdAt   creation time
 * @param updatedAt   last modification time
 */
public record TeamResponse(UUID id, String name, String description, UUID parentId, long memberCount,
                           Instant createdAt, Instant updatedAt) {
}
