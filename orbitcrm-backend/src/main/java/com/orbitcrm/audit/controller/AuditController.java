package com.orbitcrm.audit.controller;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.orbitcrm.audit.dto.AuditLogView;
import com.orbitcrm.audit.dto.AuditQuery;
import com.orbitcrm.audit.model.AuditAction;
import com.orbitcrm.audit.service.AuditService;
import com.orbitcrm.shared.dto.ApiResponse;
import com.orbitcrm.shared.dto.PageParams;
import com.orbitcrm.shared.dto.PageResponse;
import com.orbitcrm.shared.security.AccessControl;
import com.orbitcrm.shared.security.Action;
import com.orbitcrm.shared.security.SystemResources;

/**
 * REST controller for the tenant's audit log at {@code /api/v1/audit-logs}.
 *
 * <p>Purpose: let administrators and auditors (users with the {@code audit READ} permission) investigate who did what
 * and when (PRD: audit trail). {@code GET /} returns a newest-first page of entries, filterable by module, record,
 * actor, action and time range ({@code from} inclusive, {@code to} exclusive). The history of a single record is also
 * available to users who can read that record, via {@code GET /api/v1/<module>/{id}/history}.
 *
 * @author Abhay
 */
@RestController
@RequestMapping("/api/v1/audit-logs")
@Tag(name = "Audit log", description = "Immutable history of changes and security events")
public class AuditController {

    private final AuditService audit;

    AuditController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping
    @Operation(summary = "Search the audit log (requires audit READ)")
    ApiResponse<PageResponse<AuditLogView>> search(@RequestParam(required = false) String module,
                                                   @RequestParam(required = false) UUID recordId,
                                                   @RequestParam(required = false) UUID actorId,
                                                   @RequestParam(required = false) AuditAction action,
                                                   @RequestParam(required = false) Instant from,
                                                   @RequestParam(required = false) Instant to,
                                                   @RequestParam(required = false) Integer page,
                                                   @RequestParam(required = false) Integer size) {
        AccessControl.require(SystemResources.AUDIT, Action.READ);
        return ApiResponse.ok(audit.search(new AuditQuery(module, recordId, actorId, action, from, to),
                PageParams.of(page, size)));
    }
}
