package com.orbitcrm.testmodule;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.orbitcrm.audit.service.AuditService;
import com.orbitcrm.metadata.model.FieldDefinition;
import com.orbitcrm.metadata.model.FieldType;
import com.orbitcrm.metadata.model.ModuleDefinition;
import com.orbitcrm.metadata.model.PicklistDefinition;
import com.orbitcrm.metadata.model.PicklistOption;
import com.orbitcrm.record.controller.AbstractRecordController;
import com.orbitcrm.record.service.RecordService;

/**
 * Test-only record module ("widgets") with one field of every type.
 *
 * <p>Purpose: verify the generic record engine independently of the CRM modules built on it. The module defines text,
 * textarea, email, phone, URL, integer, decimal, currency, percent, date, date-time, boolean, picklist (with a system
 * value), multi-picklist, multi-user and self-lookup fields, and exposes the standard record API at
 * {@code /api/v1/widgets} through a nested controller. It exists only in the test sources, so it never ships with the
 * application.
 *
 * @author Abhay
 */
@Configuration(proxyBeanMethods = false)
public class WidgetModule {

    public static final String KEY = "widgets";

    @Bean
    ModuleDefinition widgetModuleDefinition() {
        return ModuleDefinition.builder(KEY)
                .labels("Widget", "Widgets")
                .recordPrefix("WID")
                .title("name")
                .subtitle("email", "status")
                .picklist(PicklistDefinition.of("widget_status", "Widget status",
                        PicklistOption.defaultOption("draft", "Draft"),
                        PicklistOption.of("active", "Active"),
                        PicklistOption.system("retired", "Retired")))
                .field(FieldDefinition.of("name", "Name", FieldType.TEXT).required().searchable().listDefault())
                .field(FieldDefinition.of("notes", "Notes", FieldType.TEXTAREA).maxLength(2000))
                .field(FieldDefinition.of("email", "Email", FieldType.EMAIL).searchable())
                .field(FieldDefinition.of("phone", "Phone", FieldType.PHONE).searchable())
                .field(FieldDefinition.of("website", "Website", FieldType.URL))
                .field(FieldDefinition.of("quantity", "Quantity", FieldType.INTEGER).min(0))
                .field(FieldDefinition.of("weight", "Weight", FieldType.DECIMAL))
                .field(FieldDefinition.of("price", "Price", FieldType.CURRENCY))
                .field(FieldDefinition.of("discount", "Discount", FieldType.PERCENT))
                .field(FieldDefinition.of("launchDate", "Launch date", FieldType.DATE))
                .field(FieldDefinition.of("lastSeenAt", "Last seen", FieldType.DATETIME))
                .field(FieldDefinition.of("active", "Active", FieldType.BOOLEAN))
                .field(FieldDefinition.of("status", "Status", FieldType.PICKLIST).picklist("widget_status"))
                .field(FieldDefinition.of("colors", "Colors", FieldType.MULTI_PICKLIST).options(List.of(
                        PicklistOption.of("red", "Red"), PicklistOption.of("green", "Green"),
                        PicklistOption.of("blue", "Blue"))))
                .field(FieldDefinition.of("reviewerIds", "Reviewers", FieldType.MULTI_USER))
                .field(FieldDefinition.of("parentId", "Parent widget", FieldType.LOOKUP).lookup(KEY))
                .build();
    }

    /**
     * REST controller of the test-only widgets module at {@code /api/v1/widgets}.
     *
     * <p>Purpose: expose the standard record API for widgets, inherited from {@code AbstractRecordController}, so the
     * engine can be tested end to end through HTTP.
     */
    @RestController
    @RequestMapping("/api/v1/widgets")
    static class WidgetController extends AbstractRecordController {
        WidgetController(RecordService records, AuditService audit) {
            super(KEY, records, audit);
        }
    }
}
