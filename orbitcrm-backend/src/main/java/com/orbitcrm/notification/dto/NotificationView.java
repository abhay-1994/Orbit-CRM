package com.orbitcrm.notification.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * A stored notification as returned by the API and pushed over the event stream.
 *
 * <p>Purpose: display a notification and link to its record.
 *
 * @author Abhay
 * @param id        notification id
 * @param type      notification type
 * @param title     short text
 * @param body      optional longer text
 * @param moduleKey module of the linked record
 * @param recordId  linked record
 * @param actorId   user who caused the notification, if any
 * @param actorName that user's name
 * @param readAt    when the user read it, or {@code null} if unread
 * @param createdAt creation time
 */
public record NotificationView(UUID id, String type, String title, String body, String moduleKey, UUID recordId,
                        UUID actorId, String actorName, Instant readAt, Instant createdAt) {
}
