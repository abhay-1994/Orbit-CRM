package com.orbitcrm.search.dto;

import java.util.UUID;

/**
 * One record found by global search.
 *
 * <p>Purpose: enough to display a result and navigate to the record.
 *
 * @author Abhay
 * @param id           record id
 * @param recordNumber human-readable number
 * @param title        display title
 * @param subtitle     context line
 */
public record SearchHit(UUID id, String recordNumber, String title, String subtitle) {
}
