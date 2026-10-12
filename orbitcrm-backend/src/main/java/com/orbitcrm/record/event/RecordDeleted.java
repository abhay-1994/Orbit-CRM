package com.orbitcrm.record.event;

import java.util.UUID;

/**
 * Domain event published inside the transaction after a record has been moved to the recycle bin.
 *
 * <p>Purpose: let other modules react to deletions (for example clean up or hide related data) without coupling to
 * the record engine.
 *
 * @author Abhay
 * @param tenantId  tenant of the record
 * @param moduleKey module of the record
 * @param recordId  id of the deleted record
 * @param title     display title of the record
 * @param actorId   user who deleted it
 */
public record RecordDeleted(UUID tenantId, String moduleKey, UUID recordId, String title, UUID actorId) {
}
