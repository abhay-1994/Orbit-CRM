package com.orbitcrm.record.event;

import java.util.UUID;

import com.orbitcrm.record.model.ChangeOrigin;

/**
 * Domain event published inside the transaction after a record has been created.
 *
 * <p>Purpose: let other modules react to new records without the record engine knowing them, e.g. notify the owner
 * of a record assigned to them by someone else.
 *
 * @author Abhay
 * @param tenantId  tenant of the record
 * @param moduleKey module of the record
 * @param recordId  id of the new record
 * @param title     display title of the record
 * @param ownerId   owner of the record
 * @param actorId   user who created it
 * @param origin    where the change came from (API, import, conversion, ...)
 */
public record RecordCreated(UUID tenantId, String moduleKey, UUID recordId, String title, UUID ownerId,
                            UUID actorId, ChangeOrigin origin) {
}
