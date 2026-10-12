package com.orbitcrm.metadata.dto;

import java.util.List;

/**
 * A picklist with all its values, returned by the picklist endpoints.
 *
 * <p>Purpose: show the configuration screen of one picklist.
 *
 * @author Abhay
 * @param key    picklist key, e.g. {@code lead_source}
 * @param label  picklist name
 * @param values all values in display order, including inactive ones
 */
public record PicklistResponse(String key, String label, List<PicklistValueView> values) {
}
