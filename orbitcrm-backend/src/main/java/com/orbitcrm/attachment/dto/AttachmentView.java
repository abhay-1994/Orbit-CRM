package com.orbitcrm.attachment.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadata of a file attached to a record.
 *
 * <p>Purpose: returned by the attachment endpoints and used by the record timeline.
 *
 * @author Abhay
 * @param id             attachment id (used to download or delete)
 * @param moduleKey      module of the record the file belongs to
 * @param recordId       record the file belongs to
 * @param fileName       sanitized original file name
 * @param contentType    content type verified from the file's bytes
 * @param sizeBytes      size in bytes
 * @param sha256         SHA-256 checksum of the content (integrity, duplicate detection)
 * @param uploadedBy     id of the uploading user
 * @param uploadedByName name of the uploading user
 * @param createdAt      upload time
 */
public record AttachmentView(UUID id, String moduleKey, UUID recordId, String fileName, String contentType, long sizeBytes,
                      String sha256, UUID uploadedBy, String uploadedByName, Instant createdAt) {
}
