package com.orbitcrm.lead.dto;

import java.util.UUID;

/**
 * An existing record suggested as a link target in the conversion preview.
 *
 * <p>Purpose: let the user pick an existing company or contact instead of creating a duplicate.
 *
 * @author Abhay
 * @param id           record id
 * @param title        display title
 * @param recordNumber human-readable number
 */
public record ConversionSuggestion(UUID id, String title, String recordNumber) {
}
