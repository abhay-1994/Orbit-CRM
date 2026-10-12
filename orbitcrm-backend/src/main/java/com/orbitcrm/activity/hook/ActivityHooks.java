package com.orbitcrm.activity.hook;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.orbitcrm.activity.config.ActivityModule;
import com.orbitcrm.notification.dto.Notification;
import com.orbitcrm.notification.service.NotificationService;
import com.orbitcrm.record.dto.RecordChange;
import com.orbitcrm.record.dto.RecordView;
import com.orbitcrm.record.hook.RecordHooks;
import com.orbitcrm.shared.exception.ApiException;

/**
 * Record hooks with the business rules of activities.
 *
 * <p>Purpose: keep activities consistent and inform the people involved:
 * <ul>
 *   <li>meetings, calls and events need a start time, and the end may not be before the start;</li>
 *   <li>{@code completedAt} is set when the status becomes completed and cleared when it changes back;</li>
 *   <li>changing the reminder time re-arms the reminder (clears {@code reminderSentAt});</li>
 *   <li>newly added participants (other than the owner) receive an {@code ACTIVITY_INVITE} notification.</li>
 * </ul>
 *
 * @author Abhay
 */
@Component
public class ActivityHooks implements RecordHooks {

    private static final Set<String> SCHEDULED_TYPES = Set.of("meeting", "call", "event");

    private final NotificationService notifications;

    ActivityHooks(NotificationService notifications) {
        this.notifications = notifications;
    }

    @Override
    public String moduleKey() {
        return ActivityModule.KEY;
    }

    @Override
    public void beforeCreate(HookContext context, Map<String, Object> values) {
        validate(values);
        if (ActivityModule.STATUS_COMPLETED.equals(values.get("status"))) {
            values.put("completedAt", Instant.now());
        }
    }

    @Override
    public void beforeUpdate(HookContext context, RecordView existing, Map<String, Object> changes) {
        Map<String, Object> merged = new java.util.HashMap<>(existing.values());
        merged.putAll(changes);
        validate(merged);
        if (changes.containsKey("status") && !Objects.equals(changes.get("status"), existing.value("status"))) {
            changes.put("completedAt", ActivityModule.STATUS_COMPLETED.equals(changes.get("status")) ? Instant.now() : null);
        }
        if (changes.containsKey("reminderAt") && !Objects.equals(changes.get("reminderAt"), existing.value("reminderAt"))) {
            changes.put("reminderSentAt", null);
        }
    }

    @Override
    public void afterCreate(HookContext context, RecordView created) {
        invite(created, participants(created.value("participantIds")));
    }

    @Override
    public void afterUpdate(HookContext context, RecordView before, RecordView after, List<RecordChange> changes) {
        if (changes.stream().anyMatch(c -> c.field().equals("participantIds"))) {
            List<UUID> added = new ArrayList<>(participants(after.value("participantIds")));
            added.removeAll(participants(before.value("participantIds")));
            invite(after, added);
        }
    }

    private void invite(RecordView activity, List<UUID> participants) {
        List<UUID> recipients = new ArrayList<>(participants);
        recipients.remove(activity.uuid("ownerId"));
        if (!recipients.isEmpty()) {
            notifications.notify(new Notification(recipients, NotificationService.ACTIVITY_INVITE,
                    "You were invited: " + activity.title(), null, ActivityModule.KEY, activity.id()));
        }
    }

    private static void validate(Map<String, Object> values) {
        Instant start = (Instant) values.get("startAt");
        Instant end = (Instant) values.get("endAt");
        if (start == null && SCHEDULED_TYPES.contains(values.get("type"))) {
            throw ApiException.validation("startAt", "REQUIRED", "A start time is required for " + values.get("type") + "s");
        }
        if (start != null && end != null && end.isBefore(start)) {
            throw ApiException.validation("endAt", "BEFORE_START", "End must not be before start");
        }
    }

    @SuppressWarnings("unchecked")
    private static List<UUID> participants(Object value) {
        return value instanceof List<?> list ? (List<UUID>) list : List.of();
    }
}
