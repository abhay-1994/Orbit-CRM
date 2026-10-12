package com.orbitcrm.identity.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.orbitcrm.identity.dto.InviteUserRequest;
import com.orbitcrm.identity.dto.UpdateUserRequest;
import com.orbitcrm.identity.dto.UserLookup;
import com.orbitcrm.identity.dto.UserResponse;
import com.orbitcrm.identity.entity.User;
import com.orbitcrm.identity.service.UserService;
import com.orbitcrm.shared.dto.ApiResponse;
import com.orbitcrm.shared.dto.PageParams;
import com.orbitcrm.shared.dto.PageResponse;
import com.orbitcrm.shared.security.AccessControl;
import com.orbitcrm.shared.security.Action;
import com.orbitcrm.shared.security.SystemResources;

/**
 * REST controller for user administration and user pickers at {@code /api/v1/users}.
 *
 * <p>Purpose: let administrators manage the people in their workspace and let every user pick record owners.
 * <ul>
 *   <li>{@code GET /} - paged list with search by name or email and filters by status, role and team
 *   ({@code users READ}).</li>
 *   <li>{@code GET /lookup} - active users for owner and assignee pickers; open to every signed-in user and
 *   limited to id, name and email.</li>
 *   <li>{@code GET /{id}} - one user ({@code users READ}).</li>
 *   <li>{@code POST /} - invite a user by email ({@code users CREATE}).</li>
 *   <li>{@code POST /{id}/resend-invitation} - send a fresh invitation link ({@code users UPDATE}).</li>
 *   <li>{@code PATCH /{id}} - change profile, role, team or status; disabling a user signs them out of every
 *   device ({@code users UPDATE}).</li>
 * </ul>
 *
 * @author Abhay
 */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "User administration and pickers")
public class UserController {

    private final UserService userService;

    UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "List users (search by name or email; filter by status, role, team)")
    ApiResponse<PageResponse<UserResponse>> list(@RequestParam(required = false) String q,
                                                 @RequestParam(required = false) User.Status status,
                                                 @RequestParam(required = false) UUID roleId,
                                                 @RequestParam(required = false) UUID teamId,
                                                 @RequestParam(required = false) Integer page,
                                                 @RequestParam(required = false) Integer size) {
        AccessControl.require(SystemResources.USERS, Action.READ);
        return ApiResponse.ok(userService.list(q, status, roleId, teamId, PageParams.of(page, size)));
    }

    @GetMapping("/lookup")
    @Operation(summary = "Active users for owner/assignee pickers (any signed-in user)")
    ApiResponse<List<UserLookup>> lookup(@RequestParam(required = false) String q,
                                         @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(userService.lookup(q, limit));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a user")
    ApiResponse<UserResponse> get(@PathVariable UUID id) {
        AccessControl.require(SystemResources.USERS, Action.READ);
        return ApiResponse.ok(userService.get(id));
    }

    @PostMapping
    @Operation(summary = "Invite a user by email")
    ResponseEntity<ApiResponse<UserResponse>> invite(@Valid @RequestBody InviteUserRequest request) {
        AccessControl.require(SystemResources.USERS, Action.CREATE);
        UserResponse created = userService.invite(request);
        return ResponseEntity.created(URI.create("/api/v1/users/" + created.id()))
                .body(ApiResponse.ok(created, "Invitation sent"));
    }

    @PostMapping("/{id}/resend-invitation")
    @Operation(summary = "Send a fresh invitation link")
    ApiResponse<Void> resendInvitation(@PathVariable UUID id) {
        AccessControl.require(SystemResources.USERS, Action.UPDATE);
        userService.resendInvitation(id);
        return ApiResponse.message("Invitation sent");
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update a user's profile, role, team or status (DISABLED signs them out)")
    ApiResponse<UserResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        AccessControl.require(SystemResources.USERS, Action.UPDATE);
        return ApiResponse.ok(userService.update(id, request), "User updated");
    }
}
