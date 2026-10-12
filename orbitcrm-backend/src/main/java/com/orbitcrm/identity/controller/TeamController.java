package com.orbitcrm.identity.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.orbitcrm.identity.dto.TeamRequest;
import com.orbitcrm.identity.dto.TeamResponse;
import com.orbitcrm.identity.service.TeamService;
import com.orbitcrm.shared.dto.ApiResponse;
import com.orbitcrm.shared.security.AccessControl;
import com.orbitcrm.shared.security.Action;
import com.orbitcrm.shared.security.SystemResources;

/**
 * REST controller for the team hierarchy at {@code /api/v1/teams}.
 *
 * <p>Purpose: manage teams, which drive TEAM-scoped permissions (a user with TEAM scope sees records owned by
 * members of their team and all sub-teams).
 * <ul>
 *   <li>{@code GET /} and {@code GET /{id}} - available to every signed-in user, because team lists are needed
 *   for filters and pickers.</li>
 *   <li>{@code POST /}, {@code PUT /{id}}, {@code DELETE /{id}} - require the corresponding {@code teams}
 *   permission; a team can only be deleted when it has no members and no sub-teams.</li>
 * </ul>
 *
 * @author Abhay
 */
@RestController
@RequestMapping("/api/v1/teams")
@Tag(name = "Teams", description = "Team hierarchy used for TEAM-scoped permissions")
public class TeamController {

    private final TeamService teamService;

    TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @GetMapping
    @Operation(summary = "List teams (flat, with parentId) - available to every signed-in user for filters")
    ApiResponse<List<TeamResponse>> list() {
        return ApiResponse.ok(teamService.list());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a team")
    ApiResponse<TeamResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(teamService.get(id));
    }

    @PostMapping
    @Operation(summary = "Create a team")
    ResponseEntity<ApiResponse<TeamResponse>> create(@Valid @RequestBody TeamRequest request) {
        AccessControl.require(SystemResources.TEAMS, Action.CREATE);
        TeamResponse team = teamService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/teams/" + team.id()))
                .body(ApiResponse.ok(team, "Team created"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Rename, describe or move a team")
    ApiResponse<TeamResponse> update(@PathVariable UUID id, @Valid @RequestBody TeamRequest request) {
        AccessControl.require(SystemResources.TEAMS, Action.UPDATE);
        return ApiResponse.ok(teamService.update(id, request), "Team updated");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an empty team without sub-teams")
    ApiResponse<Void> delete(@PathVariable UUID id) {
        AccessControl.require(SystemResources.TEAMS, Action.DELETE);
        teamService.delete(id);
        return ApiResponse.message("Team deleted");
    }
}
