package com.orbitcrm.audit.dto;

import java.time.Instant;
import java.util.UUID;

import com.orbitcrm.audit.model.AuditAction;

/**
 * Filter criteria for searching the audit log; {@code null} criteria are ignored.
 *
 * <p>Purpose: carry the query parameters of {@code GET /api/v1/audit-logs} to {@code AuditService.search}.
 *
 * @author Abhay
 * @param moduleKey only entries for this module
 * @param recordId  only entries for this record
 * @param actorId   only entries made by this user
 * @param action    only entries of this action
 * @param from      only entries at or after this instant
 * @param to        only entries before this instant
 */
public record AuditQuery(String moduleKey, UUID recordId, UUID actorId, AuditAction action, Instant from, Instant to) {
}
