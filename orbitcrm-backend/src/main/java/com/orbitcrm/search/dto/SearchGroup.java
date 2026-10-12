package com.orbitcrm.search.dto;

import java.util.List;

/**
 * The results of a global search for one module.
 *
 * <p>Purpose: let the search dropdown show a section per module with a total count and the top matches.
 *
 * @author Abhay
 * @param module  module key
 * @param label   plural module label, e.g. "Contacts"
 * @param count   total number of visible matches in this module
 * @param results best matches, limited per module
 */
public record SearchGroup(String module, String label, long count, List<SearchHit> results) {
}
