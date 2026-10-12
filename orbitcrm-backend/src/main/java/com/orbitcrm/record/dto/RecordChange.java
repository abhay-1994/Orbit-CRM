package com.orbitcrm.record.dto;

/**
 * One field that changed in a record update.
 *
 * <p>Purpose: describe changes precisely in {@code RecordUpdated} events and to {@code RecordHooks.afterUpdate}, so
 * listeners can react to specific fields (e.g. a new owner triggers an assignment notification, a new stage writes
 * deal stage history).
 *
 * @author Abhay
 * @param field    field key
 * @param oldValue value before the update (typed)
 * @param newValue value after the update (typed)
 */
public record RecordChange(String field, Object oldValue, Object newValue) {
}
