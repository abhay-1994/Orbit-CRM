package com.orbitcrm.search.dto;

import java.util.UUID;

/**
 * One entry of the global search index, produced by the record engine for every created or updated record.
 *
 * <p>Purpose: carry the denormalized, searchable representation of a record to the {@code SearchIndexer}.
 *
 * @author Abhay
 * @param moduleKey    module of the record
 * @param recordId     id of the record
 * @param ownerId      owner, used to apply OWN / TEAM scope at search time
 * @param recordNumber human-readable number, e.g. {@code CON-17}
 * @param title        display title
 * @param subtitle     short context line shown under the title (e.g. company and email)
 * @param content      free text to match: title, emails, phone numbers (with a digits-only copy), names
 */
public record IndexDocument(String moduleKey, UUID recordId, UUID ownerId, String recordNumber, String title,
                     String subtitle, String content) {
}
