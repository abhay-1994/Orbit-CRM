package com.orbitcrm.notification.service;

import java.util.Collection;
import java.util.UUID;

import com.orbitcrm.notification.dto.Notification;

/**
 * Public API of the notification module: create in-app notifications (PRD section 31).
 *
 * <p>Purpose: let any module notify users without knowing how notifications are stored or delivered. Notifications
 * are written in the caller's transaction (a rolled-back change never notifies anyone) and pushed to connected clients
 * after commit. The acting user is never notified about their own action. The constants name the notification types
 * used today: record assigned, mention, activity invitation and activity reminder.
 *
 * @author Abhay
 */
public interface NotificationService {

    String RECORD_ASSIGNED = "RECORD_ASSIGNED";
    String MENTION = "MENTION";
    String ACTIVITY_INVITE = "ACTIVITY_INVITE";
    String ACTIVITY_REMINDER = "ACTIVITY_REMINDER";

    void notify(Notification notification);
}
