package com.orbitcrm.attachment.dto;

import java.io.InputStream;

/**
 * An attachment's metadata together with an open stream of its content.
 *
 * <p>Purpose: returned by the attachment service after the access check, so the controller can stream the file with
 * the right headers. The caller must close the stream.
 *
 * @author Abhay
 * @param attachment metadata of the file (name, type, size)
 * @param content    the file content from object storage
 */
public record AttachmentDownload(AttachmentView attachment, InputStream content) {
}
