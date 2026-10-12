package com.orbitcrm.attachment.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.orbitcrm.attachment.dto.AttachmentView;

/**
 * Public API of the attachment module for other modules.
 *
 * <p>Purpose: let the record timeline include recent uploads without depending on the attachment internals.
 * {@link #recent} returns the files of a record uploaded before a point in time, newest first. The caller is
 * responsible for checking access to the record.
 *
 * @author Abhay
 */
public interface AttachmentService {

    List<AttachmentView> recent(String moduleKey, UUID recordId, Instant before, int limit);
}
