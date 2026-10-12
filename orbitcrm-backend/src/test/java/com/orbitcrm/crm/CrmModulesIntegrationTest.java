package com.orbitcrm.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.orbitcrm.support.IntegrationTest;
import com.orbitcrm.support.Json;
import com.orbitcrm.support.TestAuth;
import com.orbitcrm.support.TestAuth.Session;
import com.orbitcrm.support.TestUsers;

/**
 * Integration tests of the core CRM modules: companies, contacts, leads, deals and pipelines (phase 3).
 *
 * <p>Purpose: verify that the module definitions, hooks and default data behave as the PRD requires:
 * <ul>
 *   <li>a new tenant receives the default pipeline and picklists;</li>
 *   <li>companies, contacts and deals link to each other and show readable labels;</li>
 *   <li>stage moves drive probability, status, close date and the stage history;</li>
 *   <li>a deal's stage must belong to its pipeline;</li>
 *   <li>the pipeline board shows counts, totals and deals per stage;</li>
 *   <li>lead status defaults apply and "converted" cannot be set manually;</li>
 *   <li>pipeline administration enforces its invariants (stage types, default pipeline, stages in use);</li>
 *   <li>the default roles give sales users shared companies and contacts but only their own leads.</li>
 * </ul>
 *
 * @author Abhay
 */
class CrmModulesIntegrationTest extends IntegrationTest {

    @Test
    void newTenantGetsDefaultPipelineAndPicklists() throws Exception {
        Session admin = TestAuth.register(mockMvc);
        mockMvc.perform(get("/api/v1/pipelines").header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Sales Pipeline"))
                .andExpect(jsonPath("$.data[0].isDefault").value(true))
                .andExpect(jsonPath("$.data[0].stages.length()").value(7))
                .andExpect(jsonPath("$.data[0].stages[5].type").value("WON"))
                .andExpect(jsonPath("$.data[0].stages[3].probability").value(60.0));
        mockMvc.perform(get("/api/v1/picklists/lead_status").header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$.data.values[?(@.key == 'converted')].system").value(true));
        mockMvc.perform(get("/api/v1/metadata/modules/deals").header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$.data.fields[?(@.key == 'stageId')].options[0].group").value("Sales Pipeline"));
    }

    @Test
    void companyContactAndDealAreLinkedWithLabels() throws Exception {
        Session admin = TestAuth.register(mockMvc);
        String company = id(create(admin, "companies", Json.map("name", "Acme Industries", "industry", "manufacturing",
                "billingCity", "Pune", "annualRevenue", 25000000)));
        String contact = id(create(admin, "contacts", Json.map("firstName", "Priya", "lastName", "Nair",
                "email", "priya@acme.test", "companyId", company, "jobTitle", "CTO")));

        mockMvc.perform(get("/api/v1/contacts/" + contact).header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$.data.title").value("Priya Nair"))
                .andExpect(jsonPath("$.data.recordNumber").value("CON-1"))
                .andExpect(jsonPath("$.data.labels.companyId").value("Acme Industries"));

        create(admin, "deals", Json.map("name", "Acme ERP rollout", "companyId", company, "contactId", contact,
                "amount", 500000))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.recordNumber").value("DEAL-1"))
                .andExpect(jsonPath("$.data.labels.stageId").value("New"))
                .andExpect(jsonPath("$.data.labels.pipelineId").value("Sales Pipeline"))
                .andExpect(jsonPath("$.data.labels.companyId").value("Acme Industries"))
                .andExpect(jsonPath("$.data.values.probability").value(10.0))
                .andExpect(jsonPath("$.data.values.weightedAmount").value(50000.0))
                .andExpect(jsonPath("$.data.values.status").value("open"))
                .andExpect(jsonPath("$.data.values.currency").value("INR"))
                .andExpect(jsonPath("$.data.values.closedAt").value(nullValue()));

        // Related list: contacts of a company via the filter language.
        mockMvc.perform(post("/api/v1/contacts/search").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("filter", Map.of("field", "companyId", "op", "eq", "value", company)))))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void stageMovesDriveProbabilityStatusCloseDateAndHistory() throws Exception {
        Session admin = TestAuth.register(mockMvc);
        Map<String, String> stages = stageIds(admin);
        String deal = id(create(admin, "deals", Json.map("name", "Big one", "amount", 1000000)));

        patchDeal(admin, deal, Json.map("stageId", stages.get("Proposal")))
                .andExpect(jsonPath("$.data.values.probability").value(60.0))
                .andExpect(jsonPath("$.data.values.weightedAmount").value(600000.0));
        patchDeal(admin, deal, Json.map("stageId", stages.get("Negotiation"), "probability", 70))
                .andExpect(jsonPath("$.data.values.probability").value(70.0));
        patchDeal(admin, deal, Json.map("stageId", stages.get("Won")))
                .andExpect(jsonPath("$.data.values.status").value("won"))
                .andExpect(jsonPath("$.data.values.probability").value(100.0))
                .andExpect(jsonPath("$.data.values.closedAt").value(notNullValue()));
        patchDeal(admin, deal, Json.map("stageId", stages.get("Negotiation")))
                .andExpect(jsonPath("$.data.values.status").value("open"))
                .andExpect(jsonPath("$.data.values.closedAt").value(nullValue()));
        // Status is derived and cannot be set directly.
        patchDeal(admin, deal, Json.map("status", "won")).andExpect(jsonPath("$.data.values.status").value("open"));

        MvcResult history = mockMvc.perform(get("/api/v1/deals/" + deal + "/stage-history")
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(5))
                .andExpect(jsonPath("$.data[0].toStage").value("New"))
                .andExpect(jsonPath("$.data[0].fromStage").value(nullValue()))
                .andExpect(jsonPath("$.data[3].toStage").value("Won"))
                .andReturn();
        List<Object> seconds = Json.read(history, "$.data[1:].secondsInPriorStage");
        assertThat(seconds).doesNotContainNull();
    }

    @Test
    void stagesMustBelongToTheDealsPipeline() throws Exception {
        Session admin = TestAuth.register(mockMvc);
        MvcResult pipeline = mockMvc.perform(post("/api/v1/pipelines").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("name", "Partnerships", "stages", List.of(
                                Map.of("name", "Intro", "probability", 5, "type", "OPEN"),
                                Map.of("name", "Signed", "probability", 100, "type", "WON"),
                                Map.of("name", "Dropped", "probability", 0, "type", "LOST"))))))
                .andExpect(status().isCreated())
                .andReturn();
        String partnerIntro = Json.<List<String>>read(pipeline, "$.data.stages[?(@.name == 'Intro')].id").get(0);
        String partnerPipeline = Json.read(pipeline, "$.data.id");

        String deal = id(create(admin, "deals", Json.map("name", "Default pipeline deal")));
        patchDeal(admin, deal, Json.map("stageId", partnerIntro))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].code").value("STAGE_NOT_IN_PIPELINE"));

        // Moving to another pipeline without a stage lands in its first open stage.
        patchDeal(admin, deal, Json.map("pipelineId", partnerPipeline))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.labels.stageId").value("Intro"))
                .andExpect(jsonPath("$.data.values.probability").value(5.0));

        // A stage alone identifies its pipeline on create.
        create(admin, "deals", Json.map("name", "Partner deal", "stageId", partnerIntro))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.labels.pipelineId").value("Partnerships"));
    }

    @Test
    void pipelineBoardShowsCountsTotalsAndDealsPerStage() throws Exception {
        Session admin = TestAuth.register(mockMvc);
        Map<String, String> stages = stageIds(admin);
        create(admin, "deals", Json.map("name", "A", "amount", 100000));
        create(admin, "deals", Json.map("name", "B", "amount", 200000));
        create(admin, "deals", Json.map("name", "C", "amount", 300000, "stageId", stages.get("Proposal")));

        mockMvc.perform(get("/api/v1/deals/board").header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pipelineName").value("Sales Pipeline"))
                .andExpect(jsonPath("$.data.columns.length()").value(7))
                .andExpect(jsonPath("$.data.columns[0].stage.name").value("New"))
                .andExpect(jsonPath("$.data.columns[0].count").value(2))
                .andExpect(jsonPath("$.data.columns[0].totalAmount").value(300000.0))
                .andExpect(jsonPath("$.data.columns[0].weightedAmount").value(30000.0))
                .andExpect(jsonPath("$.data.columns[0].deals.length()").value(2))
                .andExpect(jsonPath("$.data.columns[3].count").value(1))
                .andExpect(jsonPath("$.data.columns[3].weightedAmount").value(180000.0))
                .andExpect(jsonPath("$.data.columns[1].count").value(0));

        mockMvc.perform(post("/api/v1/deals/board").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("filter", Map.of("field", "amount", "op", "gte", "value", 200000)))))
                .andExpect(jsonPath("$.data.columns[0].count").value(1));
    }

    @Test
    void leadStatusDefaultsAndConvertedCannotBeSetManually() throws Exception {
        Session admin = TestAuth.register(mockMvc);
        create(admin, "leads", Json.map("firstName", "Rahul", "lastName", "Verma", "companyName", "Verma Traders",
                "leadSource", "website"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.recordNumber").value("LEAD-1"))
                .andExpect(jsonPath("$.data.values.status").value("new"))
                .andExpect(jsonPath("$.data.labels.status").value("New"))
                .andExpect(jsonPath("$.data.labels.leadSource").value("Website"));
        create(admin, "leads", Json.map("lastName", "Sneaky", "status", "converted"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].code").value("USE_CONVERT_ACTION"));
    }

    @Test
    void pipelineAdministrationEnforcesInvariants() throws Exception {
        Session admin = TestAuth.register(mockMvc);
        mockMvc.perform(post("/api/v1/pipelines").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("name", "Broken", "stages", List.of(
                                Map.of("name", "Only", "probability", 10, "type", "OPEN"))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].code").value("MISSING_STAGE_TYPE"));

        MvcResult pipelines = mockMvc.perform(get("/api/v1/pipelines").header("Authorization", admin.bearer()))
                .andReturn();
        String pipelineId = Json.read(pipelines, "$.data[0].id");
        List<Map<String, Object>> stages = Json.read(pipelines, "$.data[0].stages");
        create(admin, "deals", Json.map("name", "Keeps New busy"));

        // Dropping the "New" stage (which has a deal) is refused.
        List<Map<String, Object>> withoutNew = stages.stream().filter(s -> !"New".equals(s.get("name")))
                .map(s -> Json.map("id", s.get("id"), "name", s.get("name"), "probability", s.get("probability"),
                        "type", s.get("type")))
                .toList();
        mockMvc.perform(put("/api/v1/pipelines/" + pipelineId).header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("name", "Sales Pipeline", "stages", withoutNew))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("STAGE_IN_USE"));

        mockMvc.perform(delete("/api/v1/pipelines/" + pipelineId).header("Authorization", admin.bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PIPELINE_IS_DEFAULT"));
    }

    @Test
    void defaultRolesGiveSalesUsersSharedCustomersButOwnLeads() throws Exception {
        Session admin = TestAuth.register(mockMvc);
        Session rep = TestUsers.inviteAndAccept(mockMvc, mail, admin, "Sales User", "sonia", null);
        create(admin, "companies", Json.map("name", "Shared Customer"));
        create(admin, "leads", Json.map("lastName", "Admin's lead"));
        create(rep, "leads", Json.map("lastName", "Rep's lead"));

        mockMvc.perform(get("/api/v1/companies").header("Authorization", rep.bearer()))
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mockMvc.perform(get("/api/v1/leads").header("Authorization", rep.bearer()))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].values.lastName").value("Rep's lead"));
        mockMvc.perform(get("/api/v1/metadata/modules").header("Authorization", rep.bearer()))
                .andExpect(jsonPath("$.data[*].key", hasItem("deals")))
                .andExpect(jsonPath("$.data[?(@.key == 'leads')].permissions.READ").value("OWN"));
    }

    // ------------------------------------------------------------------ helpers

    private ResultActions create(Session session, String module, Map<String, Object> values) throws Exception {
        return mockMvc.perform(post("/api/v1/" + module).header("Authorization", session.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(Json.write(values)));
    }

    private ResultActions patchDeal(Session session, String id, Map<String, Object> values) throws Exception {
        return mockMvc.perform(patch("/api/v1/deals/" + id).header("Authorization", session.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(Json.write(values)));
    }

    private static String id(ResultActions actions) throws Exception {
        return Json.read(actions.andExpect(status().isCreated()).andReturn(), "$.data.id");
    }

    private Map<String, String> stageIds(Session admin) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/pipelines").header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andReturn();
        List<Map<String, Object>> stages = Json.read(result, "$.data[?(@.isDefault == true)].stages[*]");
        Map<String, String> ids = new java.util.HashMap<>();
        stages.forEach(s -> ids.put((String) s.get("name"), (String) s.get("id")));
        return ids;
    }
}
