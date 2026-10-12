package com.orbitcrm.company.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.orbitcrm.audit.service.AuditService;
import com.orbitcrm.company.config.CompanyModule;
import com.orbitcrm.record.controller.AbstractRecordController;
import com.orbitcrm.record.service.RecordService;

/**
 * REST controller for companies at {@code /api/v1/companies}.
 *
 * <p>Purpose: expose the standard record API for the Companies module - list, search, CRUD, restore, bulk operations
 * and history - inherited from {@link com.orbitcrm.record.controller.AbstractRecordController}. It has no
 * company-specific endpoints; all rules come from the module definition and the generic engine.
 *
 * @author Abhay
 */
@RestController
@RequestMapping("/api/v1/companies")
@Tag(name = "Companies", description = "Customer organizations")
public class CompanyController extends AbstractRecordController {

    CompanyController(RecordService records, AuditService audit) {
        super(CompanyModule.KEY, records, audit);
    }

    @Override
    protected String singular() {
        return "Company";
    }
}
