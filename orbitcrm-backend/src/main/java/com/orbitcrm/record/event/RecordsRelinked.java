package com.orbitcrm.record.event;

import java.util.UUID;

import com.orbitcrm.record.model.ChangeOrigin;

/**
 * Domain event published synchronously (inside the transaction) when a record's related items must move to another
 * record.
 *
 * <p>Purpose: keep collaboration data attached to the right record when records are transformed, for example when a
 * converted lead's notes, activities and attachments move to the new contact, or (later) when a merged duplicate
 * hands its items to the surviving record. Listeners in the note, activity and attachment modules re-point their
 * rows; because they run in the same transaction, a failure rolls the whole operation back.
 *
 * @author Abhay
 * @param tenantId   tenant of both records
 * @param fromModule module of the source record
 * @param fromId     id of the source record
 * @param toModule   module of the target record
 * @param toId       id of the target record
 * @param origin     the operation that caused the relink (e.g. {@code CONVERSION}, {@code MERGE})
 */
public record RecordsRelinked(UUID tenantId, String fromModule, UUID fromId, String toModule, UUID toId,
                              ChangeOrigin origin) {
}
