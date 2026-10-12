package com.orbitcrm.tenant.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.orbitcrm.shared.dto.ApiResponse;
import com.orbitcrm.shared.security.AccessControl;
import com.orbitcrm.shared.security.Action;
import com.orbitcrm.shared.security.SystemResources;
import com.orbitcrm.tenant.dto.TenantInfo;
import com.orbitcrm.tenant.dto.TenantSettingsUpdate;
import com.orbitcrm.tenant.service.TenantService;

/**
 * REST controller for the current tenant's (workspace's) settings at {@code /api/v1/tenant}.
 *
 * <p>Purpose: let users read and administrators change organization-wide settings.
 * <ul>
 *   <li>{@code GET /api/v1/tenant} - any signed-in user reads the workspace name, time zone, currency, locale and
 *   fiscal year start.</li>
 *   <li>{@code PATCH /api/v1/tenant} - requires the {@code settings UPDATE} permission; partial update.</li>
 * </ul>
 *
 * @author Abhay
 */
@RestController
@RequestMapping("/api/v1/tenant")
@Tag(name = "Tenant", description = "Current organization (workspace) settings")
public class TenantController {

    private final TenantService tenantService;

    TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @GetMapping
    @Operation(summary = "Get the current tenant's settings")
    ApiResponse<TenantInfo> current() {
        return ApiResponse.ok(tenantService.current());
    }

    @PatchMapping
    @Operation(summary = "Update tenant settings (name, time zone, currency, locale, fiscal year)")
    ApiResponse<TenantInfo> update(@Valid @RequestBody TenantSettingsUpdate update) {
        AccessControl.require(SystemResources.SETTINGS, Action.UPDATE);
        return ApiResponse.ok(tenantService.updateSettings(update), "Settings updated");
    }
}
