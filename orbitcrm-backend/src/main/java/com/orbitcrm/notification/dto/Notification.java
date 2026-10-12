package com.orbitcrm.notification.dto;

import java.util.Collection;
import java.util.UUID;

/**
 * A notification to create, passed to {@code NotificationService.notify}.
 *
 * <p>Purpose: describe one event to announce to one or more users, with an optional link to a record. The acting user
 * is removed from the recipients automatically.
 *
 * @author Abhay
 * @param recipients users to notify
 * @param type       notification type, e.g. {@code RECORD_ASSIGNED}, {@code MENTION}, {@code ACTIVITY_INVITE},
 *                   {@code ACTIVITY_REMINDER}
 * @param title      short text shown in the list
 * @param body       optional longer text (e.g. a note snippet)
 * @param moduleKey  module of the linked record, if any
 * @param recordId   linked record, if any
 */
public record Notification(Collection<UUID> recipients, String type, String title, String body, String moduleKey,
                    UUID recordId) {
}
