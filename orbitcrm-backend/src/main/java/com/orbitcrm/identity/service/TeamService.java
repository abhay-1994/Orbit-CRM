package com.orbitcrm.identity.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbitcrm.identity.dto.TeamRequest;
import com.orbitcrm.identity.dto.TeamResponse;
import com.orbitcrm.identity.entity.Team;
import com.orbitcrm.identity.repository.TeamRepository;
import com.orbitcrm.identity.repository.UserRepository;
import com.orbitcrm.identity.security.PrincipalService;
import com.orbitcrm.identity.util.TeamHierarchy;
import com.orbitcrm.shared.exception.ApiException;
import com.orbitcrm.shared.tenant.TenantContext;

/**
 * Business logic for team administration.
 *
 * <p>Purpose: maintain the team hierarchy that TEAM-scoped permissions rely on. Team names are unique per tenant;
 * a parent must exist and may not create a cycle (a team cannot be placed under itself or one of its sub-teams);
 * only teams without members and without sub-teams can be deleted. Every change evicts the tenant's cached
 * principals, because users' team subtrees may have changed.
 *
 * @author Abhay
 */
@Service
public class TeamService {

    private final TeamRepository teams;
    private final UserRepository users;
    private final PrincipalService principals;

    TeamService(TeamRepository teams, UserRepository users, PrincipalService principals) {
        this.teams = teams;
        this.users = users;
        this.principals = principals;
    }

    @Transactional(readOnly = true)
    public List<TeamResponse> list() {
        return teams.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public TeamResponse get(UUID id) {
        return toResponse(find(id));
    }

    @Transactional
    public TeamResponse create(TeamRequest request) {
        String name = request.name().trim();
        if (teams.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("TEAM_NAME_TAKEN", "A team with this name already exists");
        }
        if (request.parentId() != null) {
            find(request.parentId());
        }
        Team team = teams.save(new Team(name, request.description(), request.parentId()));
        principals.evictTenant(TenantContext.requireTenantId());
        return toResponse(team);
    }

    @Transactional
    public TeamResponse update(UUID id, TeamRequest request) {
        Team team = find(id);
        String name = request.name().trim();
        if (teams.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw ApiException.conflict("TEAM_NAME_TAKEN", "A team with this name already exists");
        }
        if (request.parentId() != null) {
            find(request.parentId());
            if (new TeamHierarchy(teams.findAll()).wouldCreateCycle(id, request.parentId())) {
                throw ApiException.validation("parentId", "CYCLE", "A team cannot be placed under itself or its sub-team");
            }
        }
        team.update(name, request.description(), request.parentId());
        teams.flush();
        principals.evictTenant(TenantContext.requireTenantId());
        return toResponse(team);
    }

    @Transactional
    public void delete(UUID id) {
        Team team = find(id);
        if (users.countByTeamId(id) > 0) {
            throw ApiException.conflict("TEAM_HAS_MEMBERS", "Move the team's members before deleting it");
        }
        if (teams.existsByParentId(id)) {
            throw ApiException.conflict("TEAM_HAS_CHILDREN", "Delete or move the sub-teams first");
        }
        teams.delete(team);
        principals.evictTenant(TenantContext.requireTenantId());
    }

    private Team find(UUID id) {
        return teams.findById(id).orElseThrow(() -> ApiException.notFound("TEAM_NOT_FOUND", "Team does not exist"));
    }

    private TeamResponse toResponse(Team team) {
        return new TeamResponse(team.getId(), team.getName(), team.getDescription(), team.getParentId(),
                users.countByTeamId(team.getId()), team.getCreatedAt(), team.getUpdatedAt());
    }
}
