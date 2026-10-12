package com.orbitcrm.company.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.orbitcrm.metadata.model.FieldDefinition;
import com.orbitcrm.metadata.model.FieldType;
import com.orbitcrm.metadata.model.ModuleDefinition;
import com.orbitcrm.metadata.model.PicklistDefinition;
import com.orbitcrm.metadata.model.PicklistOption;

/**
 * Defines the Companies record module (PRD section 12): the customer organizations that contacts, deals and, later,
 * tickets, quotes and invoices belong to.
 *
 * <p>Purpose: register the {@code companies} {@link com.orbitcrm.metadata.model.ModuleDefinition} with the generic
 * record engine, which then provides CRUD, validation, search, filters, audit, import/export and permissions for it.
 * The definition declares:
 * <ul>
 *   <li>record numbers {@code CMP-n}, title "name", subtitle industry and billing city, default sort by name;</li>
 *   <li>fields: name (required), industry, type, website, phone, email, employees, annual revenue, parent company
 *   (self lookup for hierarchies), billing and shipping addresses and description;</li>
 *   <li>the tenant-editable picklists {@code industry} (also used by leads) and {@code company_type} (prospect,
 *   customer, partner, vendor, competitor, other).</li>
 * </ul>
 *
 * @author Abhay
 */
@Configuration(proxyBeanMethods = false)
public class CompanyModule {

    public static final String KEY = "companies";

    @Bean
    ModuleDefinition companyModuleDefinition() {
        return ModuleDefinition.builder(KEY)
                .labels("Company", "Companies")
                .recordPrefix("CMP")
                .icon("building")
                .title("name")
                .subtitle("industry", "billingCity")
                .defaultSort("name,asc")
                .picklist(PicklistDefinition.of("industry", "Industry",
                        PicklistOption.of("technology", "Technology"),
                        PicklistOption.of("finance", "Finance & Banking"),
                        PicklistOption.of("healthcare", "Healthcare"),
                        PicklistOption.of("manufacturing", "Manufacturing"),
                        PicklistOption.of("retail", "Retail & E-commerce"),
                        PicklistOption.of("education", "Education"),
                        PicklistOption.of("real_estate", "Real Estate"),
                        PicklistOption.of("government", "Government"),
                        PicklistOption.of("telecom", "Telecommunications"),
                        PicklistOption.of("media", "Media & Entertainment"),
                        PicklistOption.of("energy", "Energy & Utilities"),
                        PicklistOption.of("hospitality", "Hospitality"),
                        PicklistOption.of("logistics", "Transportation & Logistics"),
                        PicklistOption.of("professional_services", "Professional Services"),
                        PicklistOption.of("other", "Other")))
                .picklist(PicklistDefinition.of("company_type", "Company type",
                        PicklistOption.defaultOption("prospect", "Prospect"),
                        PicklistOption.of("customer", "Customer"),
                        PicklistOption.of("partner", "Partner"),
                        PicklistOption.of("vendor", "Vendor"),
                        PicklistOption.of("competitor", "Competitor"),
                        PicklistOption.of("other", "Other")))
                .field(FieldDefinition.of("name", "Company name", FieldType.TEXT).required().searchable().listDefault())
                .field(FieldDefinition.of("industry", "Industry", FieldType.PICKLIST).picklist("industry").listDefault())
                .field(FieldDefinition.of("companyType", "Type", FieldType.PICKLIST).picklist("company_type").listDefault())
                .field(FieldDefinition.of("website", "Website", FieldType.URL).searchable())
                .field(FieldDefinition.of("phone", "Phone", FieldType.PHONE).searchable().listDefault())
                .field(FieldDefinition.of("email", "Email", FieldType.EMAIL).searchable())
                .field(FieldDefinition.of("employees", "Employees", FieldType.INTEGER).min(0))
                .field(FieldDefinition.of("annualRevenue", "Annual revenue", FieldType.CURRENCY).min(0))
                .field(FieldDefinition.of("parentCompanyId", "Parent company", FieldType.LOOKUP).lookup(KEY))
                .field(address("billing", "Billing"))
                .field(FieldDefinition.of("billingCity", "Billing city", FieldType.TEXT).maxLength(120)
                        .searchable().group("address"))
                .field(FieldDefinition.of("billingState", "Billing state", FieldType.TEXT).maxLength(120).group("address"))
                .field(FieldDefinition.of("billingPostalCode", "Billing postal code", FieldType.TEXT).maxLength(20)
                        .group("address"))
                .field(FieldDefinition.of("billingCountry", "Billing country", FieldType.TEXT).maxLength(120)
                        .group("address"))
                .field(address("shipping", "Shipping"))
                .field(FieldDefinition.of("shippingCity", "Shipping city", FieldType.TEXT).maxLength(120).group("address"))
                .field(FieldDefinition.of("shippingState", "Shipping state", FieldType.TEXT).maxLength(120).group("address"))
                .field(FieldDefinition.of("shippingPostalCode", "Shipping postal code", FieldType.TEXT).maxLength(20)
                        .group("address"))
                .field(FieldDefinition.of("shippingCountry", "Shipping country", FieldType.TEXT).maxLength(120)
                        .group("address"))
                .field(FieldDefinition.of("description", "Description", FieldType.TEXTAREA))
                .build();
    }

    private static FieldDefinition.Builder address(String prefix, String label) {
        return FieldDefinition.of(prefix + "Street", label + " street", FieldType.TEXT).group("address");
    }
}
