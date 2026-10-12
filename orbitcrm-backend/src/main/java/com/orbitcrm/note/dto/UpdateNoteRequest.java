package com.orbitcrm.note.dto;

/**
 * Request body of {@code PATCH /api/v1/notes/{noteId}}.
 *
 * <p>Purpose: partially update a note; {@code null} fields are left unchanged. Only the author or an administrator
 * may update a note.
 *
 * @author Abhay
 * @param body   new note text
 * @param pinned pin ({@code true}) or unpin ({@code false}) the note
 */
public record UpdateNoteRequest(String body, Boolean pinned) {
}
