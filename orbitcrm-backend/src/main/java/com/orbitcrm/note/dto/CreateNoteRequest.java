package com.orbitcrm.note.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for adding a note to a record.
 *
 * <p>Purpose: carry the note text and the users mentioned in it. Mentioned users who are active receive a
 * {@code MENTION} notification with a snippet of the note.
 *
 * @author Abhay
 * @param body             note text (plain text or Markdown, max 20,000 characters)
 * @param mentionedUserIds users mentioned with @ in the text
 */
public record CreateNoteRequest(@NotBlank String body, List<UUID> mentionedUserIds) {
}
