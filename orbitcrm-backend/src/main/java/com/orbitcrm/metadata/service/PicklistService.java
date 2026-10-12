package com.orbitcrm.metadata.service;

import java.util.List;
import java.util.UUID;

import com.orbitcrm.metadata.dto.PicklistValueView;

/**
 * Public API for reading tenant picklists.
 *
 * <p>Purpose: let the record engine and other modules validate and display picklist values of the current tenant:
 * all values in display order ({@link #values}), whether a key is an active value ({@link #isActiveValue}, used for
 * input validation), the label for a stored key ({@link #label}, used for display, export and search) and the
 * default value ({@link #defaultValue}, applied to new records).
 *
 * @author Abhay
 */
public interface PicklistService {

    /** All values of a picklist in display order, including inactive ones. */
    List<PicklistValueView> values(String picklistKey);

    /** True if {@code valueKey} is an active value of the picklist in the current tenant. */
    boolean isActiveValue(String picklistKey, String valueKey);

    /** Label for a stored key, falling back to the key itself for unknown values. */
    String label(String picklistKey, String valueKey);

    /** Default value key of the picklist, if one is flagged. */
    String defaultValue(String picklistKey);
}
