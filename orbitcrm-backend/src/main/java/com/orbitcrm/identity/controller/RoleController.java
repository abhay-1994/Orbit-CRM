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

import com.orbitcrm.identity.dto.PermissionCatalogResponse;
import com.orbitcrm.identity.dto.RoleRequest;
import com.orbitcrm.identity.dto.RoleResponse;
import com.orbitcrm.identity.service.RoleService;
import com.orbitcrm.shared.dto.ApiResponse;
import com.orbitcrm.shared.security.AccessControl;
import com.orbitcrm.shared.security.Action;
import com.orbitcrm.shared.security.SystemResources;

/**
 * REST controller for roles and their permission matrix at {@code /api/v1/roles}.
 *
 * <p>Purpose: let administrators define what each group of users may do. A role grants actions
 * (create, read, update, delete, export, import) per resource with an OWN / TEAM / ALL scope.
 * <ul>
 *   <li>{@code GET /} and {@code GET /{id}} - list roles or get one with its permissions ({@code roles READ}).</li>
 *   <li>{@code GET /permission-catalog} - every resource, its applicable actions and the available scopes, used
 *   to render the permission matrix editor.</li>
 *   <li>{@code POST /} - create a custom role ({@code roles CREATE}).</li>
 *   <li>{@code PUT /{id}} - replace name, description and permissions; takes effect immediately for all users of
 *   the role ({@code roles UPDATE}).</li>
 *   <li>{@code DELETE /{id}} - delete a custom role that no user holds ({@code roles DELETE}).</li>
 * </ul>
 *
 * @author Abhay
 */
@RestController
@RequestMapping("/api/v1/roles")
@Tag(name = "Roles", description = "Roles and their permission matrix (action x OWN/TEAM/ALL scope)")
public class RoleController {

    private final RoleService roleService;

    RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    @Operation(summary = "List roles")
    ApiResponse<List<RoleResponse>> list() {
        AccessControl.require(SystemResources.ROLES, Action.READ);
        return ApiResponse.ok(roleService.list());
    }

    @GetMapping("/permission-catalog")
    @Operation(summary = "Resources, actions and scopes available for the permission matrix")
    ApiResponse<PermissionCatalogResponse> catalog() {
        AccessControl.require(SystemResources.ROLES, Action.READ);
        return ApiResponse.ok(roleService.catalog());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a role with its permissions")
    ApiResponse<RoleResponse> get(@PathVariable UUID id) {
        AccessControl.require(SystemResources.ROLES, Action.READ);
        return ApiResponse.ok(roleService.get(id));
    }

    @PostMapping
    @Operation(summary = "Create a role")
    ResponseEntity<ApiResponse<RoleResponse>> create(@Valid @RequestBody RoleRequest request) {
        AccessControl.require(SystemResources.ROLES, Action.CREATE);
        RoleResponse role = roleService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/roles/" + role.id()))
                .body(ApiResponse.ok(role, "Role created"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace a role's name, description and permissions (applies immediately)")
    ApiResponse<RoleResponse> update(@PathVariable UUID id, @Valid @RequestBody RoleRequest request) {
        AccessControl.require(SystemResources.ROLES, Action.UPDATE);
        return ApiResponse.ok(roleService.update(id, request), "Role updated");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a custom role that has no users")
    ApiResponse<Void> delete(@PathVariable UUID id) {
        AccessControl.require(SystemResources.ROLES, Action.DELETE);
        roleService.delete(id);
        return ApiResponse.message("Role deleted");
    }
}
