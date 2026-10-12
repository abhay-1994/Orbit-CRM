package com.orbitcrm.contact.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.orbitcrm.metadata.model.FieldDefinition;
import com.orbitcrm.metadata.model.FieldType;
import com.orbitcrm.metadata.model.ModuleDefinition;

/**
 * Defines the Contacts record module (PRD section 11): people at customer companies.
 *
 * <p>Purpose: register the {@code contacts} {@link com.orbitcrm.metadata.model.ModuleDefinition} with the generic
 * record engine. The definition declares:
 * <ul>
 *   <li>record numbers {@code CON-n}, title "first name last name", subtitle job title, company and email;</li>
 *   <li>fields: first and last name (last name required), email, phone, mobile, job title, department, company
 *   (lookup), reports-to (self lookup for org charts), lead source, mailing address, birthday (masked in the audit
 *   log as personal data), email opt-out (consent withdrawn) and description.</li>
 * </ul>
 * Contacts are created manually, imported, or produced by lead conversion.
 *
 * @author Abhay
 */
@Configuration(proxyBeanMethods = false)
public class ContactModule {

    public static final String KEY = "contacts";

    @Bean
    ModuleDefinition contactModuleDefinition() {
        return ModuleDefinition.builder(KEY)
                .labels("Contact", "Contacts")
                .recordPrefix("CON")
                .icon("user")
                .title("firstName", "lastName")
                .subtitle("jobTitle", "companyId", "email")
                .field(FieldDefinition.of("firstName", "First name", FieldType.TEXT).maxLength(80).searchable()
                        .listDefault())
                .field(FieldDefinition.of("lastName", "Last name", FieldType.TEXT).maxLength(80).required()
                        .searchable().listDefault())
                .field(FieldDefinition.of("email", "Email", FieldType.EMAIL).searchable().listDefault())
                .field(FieldDefinition.of("phone", "Phone", FieldType.PHONE).searchable().listDefault())
                .field(FieldDefinition.of("mobile", "Mobile", FieldType.PHONE).searchable())
                .field(FieldDefinition.of("jobTitle", "Job title", FieldType.TEXT).maxLength(120).searchable())
                .field(FieldDefinition.of("department", "Department", FieldType.TEXT).maxLength(120))
                .field(FieldDefinition.of("companyId", "Company", FieldType.LOOKUP).lookup("companies").listDefault()
                        .searchable())
                .field(FieldDefinition.of("reportsToId", "Reports to", FieldType.LOOKUP).lookup(KEY))
                .field(FieldDefinition.of("leadSource", "Lead source", FieldType.PICKLIST).picklist("lead_source"))
                .field(FieldDefinition.of("mailingStreet", "Mailing street", FieldType.TEXT).group("address"))
                .field(FieldDefinition.of("mailingCity", "Mailing city", FieldType.TEXT).maxLength(120).group("address"))
                .field(FieldDefinition.of("mailingState", "Mailing state", FieldType.TEXT).maxLength(120).group("address"))
                .field(FieldDefinition.of("mailingPostalCode", "Mailing postal code", FieldType.TEXT).maxLength(20)
                        .group("address"))
                .field(FieldDefinition.of("mailingCountry", "Mailing country", FieldType.TEXT).maxLength(120)
                        .group("address"))
                .field(FieldDefinition.of("birthday", "Birthday", FieldType.DATE).sensitive())
                .field(FieldDefinition.of("emailOptOut", "Email opt-out", FieldType.BOOLEAN)
                        .help("Do not send marketing email (consent withdrawn)"))
                .field(FieldDefinition.of("description", "Description", FieldType.TEXTAREA))
                .build();
    }
}
