package com.orbitcrm.lead.dto;

import java.util.Map;
import java.util.UUID;

/**
 * Instruction for one target (company, contact or deal) of a lead conversion.
 *
 * <p>Purpose: describe whether to create a new record from the lead's data (optionally overriding some values), link
 * an existing record, or skip the target.
 *
 * @author Abhay
 * @param action {@code CREATE}, {@code EXISTING} or {@code NONE}
 * @param id     id of the existing record to link (required for {@code EXISTING})
 * @param values field values that override the ones mapped from the lead (for {@code CREATE})
 */
public record ConversionTarget(TargetAction action, UUID id, Map<String, Object> values) {
}
