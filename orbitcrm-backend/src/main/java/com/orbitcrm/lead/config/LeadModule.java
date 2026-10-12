package com.orbitcrm.lead.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.orbitcrm.metadata.model.FieldDefinition;
import com.orbitcrm.metadata.model.FieldType;
import com.orbitcrm.metadata.model.ModuleDefinition;
import com.orbitcrm.metadata.model.PicklistDefinition;
import com.orbitcrm.metadata.model.PicklistOption;

/**
 * Defines the Leads record module (PRD section 9): unqualified prospects that are worked and then converted into a
 * contact, a company and optionally a deal.
 *
 * <p>Purpose: register the {@code leads} {@link com.orbitcrm.metadata.model.ModuleDefinition} with the generic record
 * engine. The definition declares:
 * <ul>
 *   <li>record numbers {@code LEAD-n}, title "first name last name", subtitle company name and status;</li>
 *   <li>fields: name, company name, email, phone, mobile, website, job title, lead source, status (required),
 *   industry, rating, address, email opt-out, description, and the read-only conversion fields (converted at,
 *   converted contact, company and deal);</li>
 *   <li>the picklists {@code lead_source} (shared with contacts and deals), {@code lead_status} (new, contacted,
 *   qualified, unqualified and the system value {@link #STATUS_CONVERTED}) and {@code lead_rating} (hot, warm,
 *   cold).</li>
 * </ul>
 *
 * @author Abhay
 */
@Configuration(proxyBeanMethods = false)
public class LeadModule {

    public static final String KEY = "leads";
    public static final String STATUS_CONVERTED = "converted";

    @Bean
    ModuleDefinition leadModuleDefinition() {
        return ModuleDefinition.builder(KEY)
                .labels("Lead", "Leads")
                .recordPrefix("LEAD")
                .icon("target")
                .title("firstName", "lastName")
                .subtitle("companyName", "status")
                .picklist(PicklistDefinition.of("lead_source", "Lead source",
                        PicklistOption.of("website", "Website"),
                        PicklistOption.of("referral", "Referral"),
                        PicklistOption.of("advertisement", "Advertisement"),
                        PicklistOption.of("email", "Email"),
                        PicklistOption.of("social_media", "Social Media"),
                        PicklistOption.of("campaign", "Campaign"),
                        PicklistOption.of("event", "Event"),
                        PicklistOption.of("cold_call", "Cold Call"),
                        PicklistOption.of("import", "Import"),
                        PicklistOption.of("manual", "Manual"),
                        PicklistOption.of("other", "Other")))
                .picklist(PicklistDefinition.of("lead_status", "Lead status",
                        PicklistOption.defaultOption("new", "New").withColor("#3b82f6"),
                        PicklistOption.of("contacted", "Contacted").withColor("#8b5cf6"),
                        PicklistOption.of("qualified", "Qualified").withColor("#10b981"),
                        PicklistOption.of("unqualified", "Unqualified").withColor("#6b7280"),
                        PicklistOption.system(STATUS_CONVERTED, "Converted").withColor("#059669")))
                .picklist(PicklistDefinition.of("lead_rating", "Lead rating",
                        PicklistOption.of("hot", "Hot").withColor("#ef4444"),
                        PicklistOption.of("warm", "Warm").withColor("#f59e0b"),
                        PicklistOption.of("cold", "Cold").withColor("#60a5fa")))
                .field(FieldDefinition.of("firstName", "First name", FieldType.TEXT).maxLength(80).searchable()
                        .listDefault())
                .field(FieldDefinition.of("lastName", "Last name", FieldType.TEXT).maxLength(80).required()
                        .searchable().listDefault())
                .field(FieldDefinition.of("companyName", "Company", FieldType.TEXT).searchable().listDefault())
                .field(FieldDefinition.of("email", "Email", FieldType.EMAIL).searchable().listDefault())
                .field(FieldDefinition.of("phone", "Phone", FieldType.PHONE).searchable())
                .field(FieldDefinition.of("mobile", "Mobile", FieldType.PHONE).searchable())
                .field(FieldDefinition.of("website", "Website", FieldType.URL))
                .field(FieldDefinition.of("jobTitle", "Job title", FieldType.TEXT).maxLength(120).searchable())
                .field(FieldDefinition.of("leadSource", "Lead source", FieldType.PICKLIST).picklist("lead_source")
                        .listDefault())
                .field(FieldDefinition.of("status", "Lead status", FieldType.PICKLIST).picklist("lead_status")
                        .required().listDefault())
                .field(FieldDefinition.of("industry", "Industry", FieldType.PICKLIST).picklist("industry"))
                .field(FieldDefinition.of("rating", "Rating", FieldType.PICKLIST).picklist("lead_rating"))
                .field(FieldDefinition.of("street", "Street", FieldType.TEXT).group("address"))
                .field(FieldDefinition.of("city", "City", FieldType.TEXT).maxLength(120).searchable().group("address"))
                .field(FieldDefinition.of("state", "State", FieldType.TEXT).maxLength(120).group("address"))
                .field(FieldDefinition.of("postalCode", "Postal code", FieldType.TEXT).maxLength(20).group("address"))
                .field(FieldDefinition.of("country", "Country", FieldType.TEXT).maxLength(120).group("address"))
                .field(FieldDefinition.of("emailOptOut", "Email opt-out", FieldType.BOOLEAN))
                .field(FieldDefinition.of("description", "Description", FieldType.TEXTAREA))
                .field(FieldDefinition.of("convertedAt", "Converted at", FieldType.DATETIME).readOnly()
                        .group("conversion"))
                .field(FieldDefinition.of("convertedContactId", "Converted contact", FieldType.LOOKUP)
                        .lookup("contacts").readOnly().group("conversion"))
                .field(FieldDefinition.of("convertedCompanyId", "Converted company", FieldType.LOOKUP)
                        .lookup("companies").readOnly().group("conversion"))
                .field(FieldDefinition.of("convertedDealId", "Converted deal", FieldType.LOOKUP)
                        .lookup("deals").readOnly().group("conversion"))
                .build();
    }
}
