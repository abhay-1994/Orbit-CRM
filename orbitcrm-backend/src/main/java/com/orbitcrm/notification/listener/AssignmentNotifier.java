package com.orbitcrm.notification.listener;

import java.util.List;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.orbitcrm.metadata.service.ModuleRegistry;
import com.orbitcrm.notification.dto.Notification;
import com.orbitcrm.notification.service.NotificationService;
import com.orbitcrm.record.event.RecordCreated;
import com.orbitcrm.record.event.RecordUpdated;
import com.orbitcrm.record.model.ChangeOrigin;

/**
 * Notifies users when a record is assigned to them (PRD section 31: "Deal assigned", "New lead").
 *
 * <p>Purpose: listen to {@code RecordCreated} and {@code RecordUpdated} events and send a {@code RECORD_ASSIGNED}
 * notification to the new owner when someone else creates a record for them or changes a record's owner to them.
 * It runs synchronously inside the write transaction, so a rolled-back change never notifies; changes made by CSV
 * imports do not notify, to avoid flooding users.
 *
 * @author Abhay
 */
@Component
public class AssignmentNotifier {

    private final NotificationService notifications;
    private final ModuleRegistry registry;

    AssignmentNotifier(NotificationService notifications, ModuleRegistry registry) {
        this.notifications = notifications;
        this.registry = registry;
    }

    @EventListener
    void onCreated(RecordCreated event) {
        if (event.origin() != ChangeOrigin.IMPORT && event.ownerId() != null && !event.ownerId().equals(event.actorId())) {
            notifyAssigned(event.moduleKey(), event.recordId(), event.title(), event.ownerId());
        }
    }

    @EventListener
    void onUpdated(RecordUpdated event) {
        if (event.origin() != ChangeOrigin.IMPORT && event.changed("ownerId") && event.ownerId() != null
                && !event.ownerId().equals(event.actorId())) {
            notifyAssigned(event.moduleKey(), event.recordId(), event.title(), event.ownerId());
        }
    }

    private void notifyAssigned(String moduleKey, java.util.UUID recordId, String title, java.util.UUID owner) {
        String singular = registry.find(moduleKey).map(m -> m.singularLabel()).orElse("Record");
        notifications.notify(new Notification(List.of(owner), NotificationService.RECORD_ASSIGNED,
                singular + " assigned to you: " + title, null, moduleKey, recordId));
    }
}
