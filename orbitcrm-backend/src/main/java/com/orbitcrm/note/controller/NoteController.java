package com.orbitcrm.note.controller;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.orbitcrm.note.dto.CreateNoteRequest;
import com.orbitcrm.note.dto.NoteView;
import com.orbitcrm.note.dto.UpdateNoteRequest;
import com.orbitcrm.note.service.impl.NoteServiceImpl;
import com.orbitcrm.shared.dto.ApiResponse;
import com.orbitcrm.shared.dto.PageParams;
import com.orbitcrm.shared.dto.PageResponse;

/**
 * REST controller for notes on records.
 *
 * <p>Purpose: let users write notes on any record and mention colleagues.
 * <ul>
 *   <li>{@code GET /api/v1/{module}/{recordId}/notes} - paged notes, pinned first then newest.</li>
 *   <li>{@code POST /api/v1/{module}/{recordId}/notes} - add a note; users in {@code mentionedUserIds} are notified.</li>
 *   <li>{@code PATCH /api/v1/notes/{noteId}} - edit the text or pin / unpin.</li>
 *   <li>{@code DELETE /api/v1/notes/{noteId}} - delete.</li>
 * </ul>
 * Anyone who can see the record can read and add notes; only the author or an administrator can edit or delete one.
 *
 * @author Abhay
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Notes", description = "Notes on any record, with @mentions")
public class NoteController {

    private final NoteServiceImpl notes;

    NoteController(NoteServiceImpl notes) {
        this.notes = notes;
    }

    @GetMapping("/{module}/{recordId}/notes")
    @Operation(summary = "Notes on a record (pinned first)")
    ApiResponse<PageResponse<NoteView>> list(@PathVariable String module, @PathVariable UUID recordId,
                                             @RequestParam(required = false) Integer page,
                                             @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(notes.list(module, recordId, PageParams.of(page, size)));
    }

    @PostMapping("/{module}/{recordId}/notes")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a note; mentionedUserIds receive a notification")
    ApiResponse<NoteView> create(@PathVariable String module, @PathVariable UUID recordId,
                                 @Valid @RequestBody CreateNoteRequest request) {
        return ApiResponse.ok(notes.create(module, recordId, request.body(), request.mentionedUserIds()), "Note added");
    }

    @PatchMapping("/notes/{noteId}")
    @Operation(summary = "Edit or pin your note")
    ApiResponse<NoteView> update(@PathVariable UUID noteId, @RequestBody UpdateNoteRequest request) {
        return ApiResponse.ok(notes.update(noteId, request.body(), request.pinned()), "Note updated");
    }

    @DeleteMapping("/notes/{noteId}")
    @Operation(summary = "Delete your note")
    ApiResponse<Void> delete(@PathVariable UUID noteId) {
        notes.delete(noteId);
        return ApiResponse.message("Note deleted");
    }
}
