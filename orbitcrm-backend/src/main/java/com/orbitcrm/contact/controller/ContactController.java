package com.orbitcrm.contact.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.orbitcrm.audit.service.AuditService;
import com.orbitcrm.contact.config.ContactModule;
import com.orbitcrm.record.controller.AbstractRecordController;
import com.orbitcrm.record.service.RecordService;

/**
 * REST controller for contacts at {@code /api/v1/contacts}.
 *
 * <p>Purpose: expose the standard record API for the Contacts module - list, search, CRUD, restore, bulk operations
 * and history - inherited from {@link com.orbitcrm.record.controller.AbstractRecordController}. It has no
 * contact-specific endpoints.
 *
 * @author Abhay
 */
@RestController
@RequestMapping("/api/v1/contacts")
@Tag(name = "Contacts", description = "People at customer companies")
public class ContactController extends AbstractRecordController {

    ContactController(RecordService records, AuditService audit) {
        super(ContactModule.KEY, records, audit);
    }

    @Override
    protected String singular() {
        return "Contact";
    }
}
