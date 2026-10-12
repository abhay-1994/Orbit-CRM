package com.orbitcrm.lead.controller;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.orbitcrm.audit.service.AuditService;
import com.orbitcrm.lead.config.LeadModule;
import com.orbitcrm.lead.dto.ConversionPreview;
import com.orbitcrm.lead.dto.ConversionRequest;
import com.orbitcrm.lead.dto.ConversionResult;
import com.orbitcrm.lead.service.LeadConversionService;
import com.orbitcrm.record.controller.AbstractRecordController;
import com.orbitcrm.record.service.RecordService;
import com.orbitcrm.shared.dto.ApiResponse;

/**
 * REST controller for leads at {@code /api/v1/leads}.
 *
 * <p>Purpose: expose the standard record API for leads (inherited from
 * {@link com.orbitcrm.record.controller.AbstractRecordController}) plus lead conversion (PRD section 10):
 * <ul>
 *   <li>{@code GET /{id}/conversion-preview} - the company, contact and deal values a conversion would create, and
 *   existing companies (by name) and contacts (by email) that look like matches, so users can link instead of
 *   creating duplicates;</li>
 *   <li>{@code POST /{id}/convert} - convert the lead in one all-or-nothing transaction. Without a body the defaults
 *   apply: create a contact, create a company if the lead has a company name, no deal.</li>
 * </ul>
 *
 * @author Abhay
 */
@RestController
@RequestMapping("/api/v1/leads")
@Tag(name = "Leads", description = "Prospects, lead qualification and conversion")
public class LeadController extends AbstractRecordController {

    private final LeadConversionService conversion;

    LeadController(RecordService records, AuditService audit, LeadConversionService conversion) {
        super(LeadModule.KEY, records, audit);
        this.conversion = conversion;
    }

    @Override
    protected String singular() {
        return "Lead";
    }

    @GetMapping("/{id}/conversion-preview")
    @Operation(summary = "Values a conversion would create, plus existing companies/contacts that look like matches")
    ApiResponse<ConversionPreview> conversionPreview(@PathVariable UUID id) {
        return ApiResponse.ok(conversion.preview(id));
    }

    @PostMapping("/{id}/convert")
    @Operation(summary = "Convert the lead into a contact, company and optional deal (all-or-nothing)",
            description = "Each of company/contact is {action: CREATE|EXISTING|NONE, id?, values?}; deal is "
                    + "{action: CREATE|NONE, values?}. Defaults: create a contact, create a company if the lead has a "
                    + "company name, no deal.")
    ApiResponse<ConversionResult> convert(@PathVariable UUID id, @RequestBody(required = false) ConversionRequest request) {
        return ApiResponse.ok(conversion.convert(id, request), "Lead converted successfully");
    }
}
