package com.orbitcrm.dashboard.dto;

/**
 * One slice of the "leads by source" chart.
 *
 * <p>Purpose: show which channels bring in leads during the selected range.
 *
 * @author Abhay
 * @param key   picklist key of the source, or {@code null} when not set
 * @param label display label ("Not set" when empty)
 * @param count number of leads created in the range from this source
 */
public record LeadSourceStat(String key, String label, long count) {
}
