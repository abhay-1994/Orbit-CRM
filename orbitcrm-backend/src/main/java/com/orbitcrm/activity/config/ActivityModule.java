package com.orbitcrm.activity.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.orbitcrm.metadata.model.FieldDefinition;
import com.orbitcrm.metadata.model.FieldType;
import com.orbitcrm.metadata.model.ModuleDefinition;
import com.orbitcrm.metadata.model.PicklistDefinition;
import com.orbitcrm.metadata.model.PicklistOption;

/**
 * Defines the Activities record module (PRD sections 15-16): tasks, calls, meetings, events and reminders.
 *
 * <p>Purpose: register the {@code activities} {@link com.orbitcrm.metadata.model.ModuleDefinition} with the generic
 * record engine. The definition declares:
 * <ul>
 *   <li>record numbers {@code ACT-n}, title "subject", subtitle type and start, default sort by start time;</li>
 *   <li>fields: subject, type (task, call, meeting, event, reminder), status (not started, in progress,
 *   {@link #STATUS_COMPLETED}, {@link #STATUS_CANCELLED}), priority, start, end / due, all day, location, the
 *   polymorphic "related to" link (a lead, contact, company or deal), participants, reminder time, and the read-only
 *   reminder-sent and completed-at timestamps;</li>
 *   <li>the picklist {@code activity_priority}.</li>
 * </ul>
 * Participants form the module's sharing field: invited users can see an activity even when their scope is OWN or
 * TEAM.
 *
 * @author Abhay
 */
@Configuration(proxyBeanMethods = false)
public class ActivityModule {

    public static final String KEY = "activities";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_CANCELLED = "cancelled";

    @Bean
    ModuleDefinition activityModuleDefinition() {
        return ModuleDefinition.builder(KEY)
                .labels("Activity", "Activities")
                .recordPrefix("ACT")
                .icon("calendar")
                .title("subject")
                .subtitle("type", "startAt")
                .defaultSort("startAt,asc")
                .sharedWith("participantIds")
                .picklist(PicklistDefinition.of("activity_priority", "Priority",
                        PicklistOption.of("low", "Low"),
                        PicklistOption.defaultOption("normal", "Normal"),
                        PicklistOption.of("high", "High"),
                        PicklistOption.of("urgent", "Urgent")))
                .field(FieldDefinition.of("subject", "Subject", FieldType.TEXT).required().searchable().listDefault())
                .field(FieldDefinition.of("type", "Type", FieldType.PICKLIST).required().listDefault().options(List.of(
                        PicklistOption.defaultOption("task", "Task"),
                        PicklistOption.of("call", "Call"),
                        PicklistOption.of("meeting", "Meeting"),
                        PicklistOption.of("event", "Event"),
                        PicklistOption.of("reminder", "Reminder"))))
                .field(FieldDefinition.of("status", "Status", FieldType.PICKLIST).required().listDefault().options(List.of(
                        PicklistOption.defaultOption("not_started", "Not Started"),
                        PicklistOption.of("in_progress", "In Progress"),
                        PicklistOption.of(STATUS_COMPLETED, "Completed"),
                        PicklistOption.of(STATUS_CANCELLED, "Cancelled"))))
                .field(FieldDefinition.of("priority", "Priority", FieldType.PICKLIST).picklist("activity_priority"))
                .field(FieldDefinition.of("startAt", "Start", FieldType.DATETIME).listDefault())
                .field(FieldDefinition.of("endAt", "End / due", FieldType.DATETIME))
                .field(FieldDefinition.of("allDay", "All day", FieldType.BOOLEAN))
                .field(FieldDefinition.of("location", "Location", FieldType.TEXT))
                .field(FieldDefinition.of("relatedModule", "Related to (type)", FieldType.PICKLIST).options(List.of(
                        PicklistOption.of("leads", "Lead"),
                        PicklistOption.of("contacts", "Contact"),
                        PicklistOption.of("companies", "Company"),
                        PicklistOption.of("deals", "Deal"))))
                .field(FieldDefinition.of("relatedId", "Related to", FieldType.LOOKUP)
                        .polymorphicLookup("relatedModule").listDefault())
                .field(FieldDefinition.of("participantIds", "Participants", FieldType.MULTI_USER))
                .field(FieldDefinition.of("reminderAt", "Reminder", FieldType.DATETIME))
                .field(FieldDefinition.of("reminderSentAt", "Reminder sent", FieldType.DATETIME).readOnly())
                .field(FieldDefinition.of("completedAt", "Completed at", FieldType.DATETIME).readOnly())
                .field(FieldDefinition.of("description", "Description", FieldType.TEXTAREA))
                .build();
    }
}
