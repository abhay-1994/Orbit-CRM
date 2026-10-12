package com.orbitcrm.audit.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One stored audit log entry as returned by the audit and history endpoints.
 *
 * <p>Purpose: present audit history to users and feed record timelines.
 *
 * @author Abhay
 * @param id          entry id
 * @param occurredAt  when the event happened (UTC)
 * @param actorId     acting user, if any
 * @param actorName   acting user's name at the time
 * @param action      audited action, e.g. {@code UPDATE}, {@code LOGIN_FAILED}
 * @param moduleKey   module of the affected record, if any
 * @param recordId    affected record, if any
 * @param recordLabel record title at the time of the event
 * @param changes     field changes as {@code {field, label, oldValue, newValue}} objects
 * @param metadata    additional context
 * @param ipAddress   client IP address of the request that caused the event
 * @param requestId   correlation id of that request
 */
public record AuditLogView(UUID id, Instant occurredAt, UUID actorId, String actorName, String action, String moduleKey,
                    UUID recordId, String recordLabel, List<Map<String, Object>> changes,
                    Map<String, Object> metadata, String ipAddress, String requestId) {
}
