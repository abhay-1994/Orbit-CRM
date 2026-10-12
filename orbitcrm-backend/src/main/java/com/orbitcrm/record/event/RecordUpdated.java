package com.orbitcrm.record.event;

import java.util.List;
import java.util.UUID;

import com.orbitcrm.record.dto.RecordChange;
import com.orbitcrm.record.model.ChangeOrigin;

/**
 * Domain event published inside the transaction after a record has been updated.
 *
 * <p>Purpose: let other modules react to specific field changes, e.g. notify a new owner. {@link #changed} tells
 * whether a given field is among the changes.
 *
 * @author Abhay
 * @param tenantId  tenant of the record
 * @param moduleKey module of the record
 * @param recordId  id of the record
 * @param title     display title after the update
 * @param ownerId   owner after the update
 * @param actorId   user who made the change
 * @param origin    where the change came from (API, bulk, import, ...)
 * @param changes   the fields that changed, with old and new values
 */
public record RecordUpdated(UUID tenantId, String moduleKey, UUID recordId, String title, UUID ownerId,
                            UUID actorId, ChangeOrigin origin, List<RecordChange> changes) {

    public boolean changed(String field) {
        return changes.stream().anyMatch(c -> c.field().equals(field));
    }
}
