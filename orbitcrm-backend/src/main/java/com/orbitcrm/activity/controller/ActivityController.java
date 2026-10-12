package com.orbitcrm.activity.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.orbitcrm.activity.config.ActivityModule;
import com.orbitcrm.audit.service.AuditService;
import com.orbitcrm.record.controller.AbstractRecordController;
import com.orbitcrm.record.service.RecordService;

/**
 * REST controller for activities at {@code /api/v1/activities}.
 *
 * <p>Purpose: expose the standard record API for activities - list, search, CRUD, restore, bulk operations and
 * history - inherited from {@link com.orbitcrm.record.controller.AbstractRecordController}. Activity-specific rules
 * live in {@code ActivityHooks}; the calendar view is served by {@link CalendarController}.
 *
 * @author Abhay
 */
@RestController
@RequestMapping("/api/v1/activities")
@Tag(name = "Activities", description = "Tasks, calls, meetings, events and reminders")
public class ActivityController extends AbstractRecordController {

    ActivityController(RecordService records, AuditService audit) {
        super(ActivityModule.KEY, records, audit);
    }

    @Override
    protected String singular() {
        return "Activity";
    }
}
