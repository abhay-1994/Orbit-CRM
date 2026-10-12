package com.orbitcrm.dashboard.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.orbitcrm.dashboard.dto.Dashboard;
import com.orbitcrm.dashboard.service.DashboardService;
import com.orbitcrm.shared.dto.ApiResponse;

/**
 * REST controller for the home dashboard at {@code GET /api/v1/dashboard} (PRD section 8).
 *
 * <p>Purpose: return all dashboard widgets in one call for the given {@code range}: {@code today},
 * {@code this_week}, {@code this_month} (default), {@code last_30_days}, {@code this_quarter} or {@code this_year}
 * (quarters and years follow the tenant's fiscal year). All numbers are scoped to what the caller can see.
 *
 * @author Abhay
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard", description = "KPIs, funnel, revenue trend and activity summary for the current user")
public class DashboardController {

    private final DashboardService dashboards;

    DashboardController(DashboardService dashboards) {
        this.dashboards = dashboards;
    }

    @GetMapping
    @Operation(summary = "Dashboard widgets, scoped to what the caller can see",
            description = "range: today | this_week | this_month (default) | last_30_days | this_quarter | this_year "
                    + "(quarters and years follow the tenant's fiscal year)")
    ApiResponse<Dashboard> dashboard(@RequestParam(defaultValue = "this_month") String range) {
        return ApiResponse.ok(dashboards.build(range));
    }
}
