package com.orbitcrm.audit.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.orbitcrm.audit.model.AuditAction;

/**
 * One audit event to be written, passed to {@code AuditService.record}.
 *
 * <p>Purpose: a single structure for both kinds of audit events:
 * <ul>
 *   <li>record events ({@link #record} factory) - create, update, delete, restore, convert, import - with field-level
 *   changes;</li>
 *   <li>security and administration events ({@link #security} factory) - logins, password and MFA changes, user and
 *   role administration, settings changes - with free-form metadata.</li>
 * </ul>
 * Actor, client IP and request id are filled from the current request when absent. The nested {@code Change} record
 * holds one field change; sensitive values are masked by the caller before they get here.
 *
 * @author Abhay
 * @param action      what happened
 * @param moduleKey   module of the affected record (record events only)
 * @param recordId    id of the affected record (record events only)
 * @param recordLabel display title of the record at the time of the event
 * @param changes     field-level changes (old and new values with field labels)
 * @param metadata    additional context, e.g. {@code origin}, {@code reason}, {@code roleId}
 * @param actorId     acting user; defaults to the current user
 * @param actorName   acting user's name, stored so history stays readable after the user is renamed or removed
 */
public record AuditEntry(
        AuditAction action,
        String moduleKey,
        UUID recordId,
        String recordLabel,
        List<Change> changes,
        Map<String, Object> metadata,
        UUID actorId,
        String actorName) {

    /**
     * One field-level change inside an audit entry.
     *
     * <p>Purpose: record exactly what changed in a record, readable even after the field is renamed. Sensitive values
     * (such as a contact's birthday) are masked by the caller before they get here.
     *
     * @param field    field key
     * @param label    field label at the time of the change
     * @param oldValue value before the change ({@code null} on create)
     * @param newValue value after the change
     */
    public record Change(String field, String label, Object oldValue, Object newValue) {
    }

    public static AuditEntry record(AuditAction action, String moduleKey, UUID recordId, String recordLabel,
                                    List<Change> changes) {
        return new AuditEntry(action, moduleKey, recordId, recordLabel, changes, null, null, null);
    }

    public static AuditEntry security(AuditAction action, UUID actorId, String actorName, Map<String, Object> metadata) {
        return new AuditEntry(action, null, null, null, null, metadata, actorId, actorName);
    }

    public AuditEntry withMetadata(Map<String, Object> newMetadata) {
        return new AuditEntry(action, moduleKey, recordId, recordLabel, changes, newMetadata, actorId, actorName);
    }
}
