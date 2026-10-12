package com.orbitcrm.note.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * A note on a record as returned by the API and used by the timeline.
 *
 * <p>Purpose: display a note with its author. The body is stored as written (plain text or Markdown) and must be
 * rendered escaped by clients.
 *
 * @author Abhay
 * @param id            note id
 * @param moduleKey     module of the record
 * @param recordId      record the note belongs to
 * @param body          note text
 * @param pinned        whether the note is pinned to the top
 * @param createdBy     author id
 * @param createdByName author name
 * @param createdAt     creation time
 * @param updatedAt     last edit time
 * @param version       edit counter
 */
public record NoteView(UUID id, String moduleKey, UUID recordId, String body, boolean pinned, UUID createdBy,
                String createdByName, Instant createdAt, Instant updatedAt, long version) {
}
