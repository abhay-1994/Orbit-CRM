package com.orbitcrm.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.orbitcrm.shared.tenant.TenantContext;
import com.orbitcrm.support.IntegrationTest;
import com.orbitcrm.support.Json;
import com.orbitcrm.support.TestAuth;
import com.orbitcrm.support.TestAuth.Session;

/**
 * Integration tests of lead conversion (phase 4).
 *
 * <p>Purpose: verify the conversion flow end to end:
 * <ul>
 *   <li>the default conversion creates a company and a contact and freezes the lead;</li>
 *   <li>a conversion can also create a deal with overrides and links all records together;</li>
 *   <li>a conversion can link an existing company instead of creating one;</li>
 *   <li>a conversion is all-or-nothing: when one part fails, nothing is created and the lead stays unconverted;</li>
 *   <li>a conversion needs at least a contact or a company.</li>
 * </ul>
 *
 * @author Abhay
 */
class LeadConversionIntegrationTest extends IntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void defaultConversionCreatesCompanyAndContactAndFreezesTheLead() throws Exception {
        Session admin = TestAuth.register(mockMvc);
        String lead = newLead(admin, Json.map("firstName", "Anita", "lastName", "Desai", "companyName", "Desai Foods",
                "email", "anita@desaifoods.test", "phone", "+91 98200 11111", "industry", "retail",
                "leadSource", "referral", "city", "Mumbai", "tags", List.of("expo-2026")));

        MvcResult result = convert(admin, lead, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Lead converted successfully"))
                .andExpect(jsonPath("$.data.companyCreated").value(true))
                .andExpect(jsonPath("$.data.contactCreated").value(true))
                .andExpect(jsonPath("$.data.dealCreated").value(false))
                .andExpect(jsonPath("$.data.company.values.name").value("Desai Foods"))
                .andExpect(jsonPath("$.data.company.values.industry").value("retail"))
                .andExpect(jsonPath("$.data.company.values.billingCity").value("Mumbai"))
                .andExpect(jsonPath("$.data.contact.title").value("Anita Desai"))
                .andExpect(jsonPath("$.data.contact.values.leadSource").value("referral"))
                .andExpect(jsonPath("$.data.contact.values.tags[0]").value("expo-2026"))
                .andExpect(jsonPath("$.data.contact.labels.companyId").value("Desai Foods"))
                .andReturn();
        String contactId = Json.read(result, "$.data.contact.id");

        mockMvc.perform(get("/api/v1/leads/" + lead).header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$.data.values.status").value("converted"))
                .andExpect(jsonPath("$.data.values.convertedAt").value(notNullValue()))
                .andExpect(jsonPath("$.data.values.convertedContactId").value(contactId))
                .andExpect(jsonPath("$.data.labels.convertedContactId").value("Anita Desai"));

        mockMvc.perform(patch("/api/v1/leads/" + lead).header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(Json.obj("phone", "+91 1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("LEAD_CONVERTED"));
        convert(admin, lead, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("LEAD_ALREADY_CONVERTED"));

        mockMvc.perform(get("/api/v1/leads/" + lead + "/history").header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$.data.content[0].action").value("CONVERT"))
                .andExpect(jsonPath("$.data.content[0].metadata.contactId").value(contactId));
        Integer history = TenantContext.callAs(admin.tenantId(), () -> jdbc.queryForObject(
                "select count(*) from lead_conversions where lead_id = ?::uuid and lead_snapshot->>'email' = ?",
                Integer.class, lead, "anita@desaifoods.test"));
        assertThat(history).isEqualTo(1);
    }

    @Test
    void conversionCanCreateADealWithOverridesAndLinkEverything() throws Exception {
        Session admin = TestAuth.register(mockMvc);
        String lead = newLead(admin, Json.map("firstName", "Vikram", "lastName", "Rao", "companyName", "Rao Logistics",
                "leadSource", "campaign"));

        convert(admin, lead, Map.of("deal", Map.of("action", "CREATE",
                        "values", Map.of("amount", 750000, "expectedCloseDate", "2026-12-15"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dealCreated").value(true))
                .andExpect(jsonPath("$.data.deal.values.name").value("Rao Logistics - Deal"))
                .andExpect(jsonPath("$.data.deal.values.amount").value(750000.0))
                .andExpect(jsonPath("$.data.deal.values.leadSource").value("campaign"))
                .andExpect(jsonPath("$.data.deal.values.leadId").value(lead))
                .andExpect(jsonPath("$.data.deal.labels.companyId").value("Rao Logistics"))
                .andExpect(jsonPath("$.data.deal.labels.contactId").value("Vikram Rao"))
                .andExpect(jsonPath("$.data.deal.labels.stageId").value("New"));
    }

    @Test
    void conversionCanLinkAnExistingCompany() throws Exception {
        Session admin = TestAuth.register(mockMvc);
        MvcResult existing = mockMvc.perform(post("/api/v1/companies").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(Json.obj("name", "Globex Corporation")))
                .andExpect(status().isCreated()).andReturn();
        String companyId = Json.read(existing, "$.data.id");
        String lead = newLead(admin, Json.map("lastName", "Simpson", "companyName", "Globex",
                "email", "homer@globex.test"));

        mockMvc.perform(get("/api/v1/leads/" + lead + "/conversion-preview").header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matchingCompanies[0].id").value(companyId))
                .andExpect(jsonPath("$.data.contact.lastName").value("Simpson"));

        convert(admin, lead, Map.of("company", Map.of("action", "EXISTING", "id", companyId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.companyCreated").value(false))
                .andExpect(jsonPath("$.data.company.id").value(companyId))
                .andExpect(jsonPath("$.data.contact.values.companyId").value(companyId));
        mockMvc.perform(get("/api/v1/companies").header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void conversionIsAllOrNothing() throws Exception {
        Session admin = TestAuth.register(mockMvc);
        String lead = newLead(admin, Json.map("lastName", "Atomic", "companyName", "Atomic Ltd"));

        convert(admin, lead, Map.of("deal", Map.of("action", "CREATE",
                        "values", Map.of("stageId", UUID.randomUUID().toString()))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/companies").header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(get("/api/v1/contacts").header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(get("/api/v1/leads/" + lead).header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$.data.values.status").value("new"));
    }

    @Test
    void conversionNeedsAContactOrCompany() throws Exception {
        Session admin = TestAuth.register(mockMvc);
        String lead = newLead(admin, Json.map("lastName", "Nobody"));
        convert(admin, lead, Map.of("contact", Map.of("action", "NONE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].code").value("CONVERSION_TARGET_REQUIRED"));
        // Without a company name the default is contact only.
        convert(admin, lead, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.companyCreated").value(false))
                .andExpect(jsonPath("$.data.contactCreated").value(true));
    }

    private String newLead(Session session, Map<String, Object> values) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/leads").header("Authorization", session.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(Json.write(values)))
                .andExpect(status().isCreated()).andReturn();
        return Json.read(result, "$.data.id");
    }

    private ResultActions convert(Session session, String leadId, Map<String, Object> body) throws Exception {
        var request = post("/api/v1/leads/" + leadId + "/convert").header("Authorization", session.bearer());
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(Json.write(body));
        }
        return mockMvc.perform(request);
    }
}
