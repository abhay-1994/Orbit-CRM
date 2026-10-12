package com.orbitcrm.metadata.dto;

import java.util.UUID;

/**
 * One value of a tenant picklist.
 *
 * <p>Purpose: expose picklist values to the configuration screen, to field metadata and, through
 * {@code PicklistService}, to validation in the record engine.
 *
 * @author Abhay
 * @param id        value id (used to update and reorder)
 * @param key       stable key stored in records
 * @param label     display label (may be renamed freely)
 * @param color     optional display color
 * @param position  display order (1-based)
 * @param active    whether the value may be chosen for new data
 * @param isDefault whether the value is preselected for new records
 * @param system    whether the value carries platform meaning (e.g. lead status "converted") and cannot be deactivated
 */
public record PicklistValueView(UUID id, String key, String label, String color, int position, boolean active,
                         boolean isDefault, boolean system) {
}
