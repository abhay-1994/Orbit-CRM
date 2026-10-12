package com.orbitcrm.attachment.controller;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.orbitcrm.attachment.dto.AttachmentDownload;
import com.orbitcrm.attachment.dto.AttachmentView;
import com.orbitcrm.attachment.service.impl.AttachmentServiceImpl;
import com.orbitcrm.shared.dto.ApiResponse;

/**
 * REST controller for files attached to records (PRD section 39).
 *
 * <p>Purpose: let users attach documents to any record and retrieve them securely.
 * <ul>
 *   <li>{@code GET /api/v1/{module}/{recordId}/attachments} - files of a record, newest first (requires read access to
 *   the record).</li>
 *   <li>{@code POST /api/v1/{module}/{recordId}/attachments} - upload a file as multipart field {@code file}, at most
 *   25 MB (requires edit access to the record).</li>
 *   <li>{@code GET /api/v1/attachments/{id}/download} - streams the file. It is always sent as a download
 *   ({@code Content-Disposition: attachment}), with {@code nosniff} and {@code no-store}, so a stored file can never be
 *   rendered as active content in the browser.</li>
 *   <li>{@code DELETE /api/v1/attachments/{id}} - allowed for the uploader, or for anyone who can edit the record.</li>
 * </ul>
 *
 * @author Abhay
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Attachments", description = "Files attached to records")
public class AttachmentController {

    private final AttachmentServiceImpl attachments;

    AttachmentController(AttachmentServiceImpl attachments) {
        this.attachments = attachments;
    }

    @GetMapping("/{module}/{recordId}/attachments")
    @Operation(summary = "Files attached to a record")
    ApiResponse<List<AttachmentView>> list(@PathVariable String module, @PathVariable UUID recordId) {
        return ApiResponse.ok(attachments.list(module, recordId));
    }

    @PostMapping(value = "/{module}/{recordId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload a file (multipart field 'file', max 25 MB)")
    ApiResponse<AttachmentView> upload(@PathVariable String module, @PathVariable UUID recordId,
                                       @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(attachments.upload(module, recordId, file), "File uploaded");
    }

    @GetMapping("/attachments/{id}/download")
    @Operation(summary = "Download a file (always as an attachment, never rendered inline)")
    ResponseEntity<StreamingResponseBody> download(@PathVariable UUID id) {
        AttachmentDownload download = attachments.open(id);
        AttachmentView file = download.attachment();
        StreamingResponseBody body = out -> {
            try (InputStream in = download.content()) {
                in.transferTo(out);
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName(), java.nio.charset.StandardCharsets.UTF_8)
                                .build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    @DeleteMapping("/attachments/{id}")
    @Operation(summary = "Delete a file (uploader, or anyone who can edit the record)")
    ApiResponse<Void> delete(@PathVariable UUID id) {
        attachments.delete(id);
        return ApiResponse.message("File deleted");
    }
}
