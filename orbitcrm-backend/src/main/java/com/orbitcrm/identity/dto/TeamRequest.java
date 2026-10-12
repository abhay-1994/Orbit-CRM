package com.orbitcrm.identity.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating or updating a team.
 *
 * <p>Purpose: name a team and place it in the hierarchy. A parent must exist and may not be the team itself or one
 * of its sub-teams.
 *
 * @author Abhay
 * @param name        unique team name (max 120 characters)
 * @param description optional description (max 500 characters)
 * @param parentId    optional parent team; {@code null} makes it a top-level team
 */
public record TeamRequest(@NotBlank @Size(max = 120) String name, @Size(max = 500) String description,
                          UUID parentId) {
}
