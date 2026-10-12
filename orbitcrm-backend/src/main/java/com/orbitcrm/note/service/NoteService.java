package com.orbitcrm.note.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.orbitcrm.note.dto.NoteView;

/**
 * Public API of the note module for other modules.
 *
 * <p>Purpose: let the record timeline include notes without depending on the note internals. {@link #recent}
 * returns the notes of a record created before a point in time, newest first. The caller is responsible for checking
 * access to the record.
 *
 * @author Abhay
 */
public interface NoteService {

    List<NoteView> recent(String moduleKey, UUID recordId, Instant before, int limit);
}
