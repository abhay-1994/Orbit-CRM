package com.orbitcrm.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.orbitcrm.support.IntegrationTest;
import com.orbitcrm.support.Json;
import com.orbitcrm.support.TestAuth;
import com.orbitcrm.support.TestAuth.Session;
import com.orbitcrm.support.TestUsers;

/**
 * Integration tests of the dashboard and of cross-cutting hardening (phase 7).
 *
 * <p>Purpose: verify that
 * <ul>
 *   <li>the dashboard computes KPIs, funnel, revenue trend and activity statistics scoped to the viewer;</li>
 *   <li>a user of another tenant cannot reach any record or sub-resource (notes, attachments, timeline, history) by
 *   id - they behave as if they do not exist;</li>
 *   <li>API responses carry the expected security headers (Content-Security-Policy, frame options, referrer policy,
 *   nosniff and no-store caching).</li>
 * </ul>
 *
 * @author Abhay
 */
class DashboardAndHardeningIntegrationTest extends IntegrationTest {

    @Test
    void dashboardComputesScopedKpisFunnelRevenueAndActivities() throws Exception {
        Session admin = TestAuth.register(mockMvc);
        Session rep = TestUsers.inviteAndAccept(mockMvc, mail, admin, "Sales User", "kpi", null);
        Map<String, String> stages = stageIds(admin);

        String lead = id(create(admin, "leads", Json.map("lastName", "One", "leadSource", "website")));
        create(admin, "leads", Json.map("lastName", "Two", "leadSource", "website"));
        mockMvc.perform(post("/api/v1/leads/" + lead + "/convert").header("Authorization", admin.bearer()))
                .andExpect(status().isOk());
        create(admin, "deals", Json.map("name", "Early", "amount", 100000));
        create(admin, "deals", Json.map("name", "Mid", "amount", 300000, "stageId", stages.get("Proposal")));
        create(admin, "deals", Json.map("name", "Won", "amount", 500000, "stageId", stages.get("Won")));
        create(admin, "deals", Json.map("name", "Lost", "amount", 50000, "stageId", stages.get("Lost")));
        Instant soon = Instant.now().plus(2, ChronoUnit.DAYS);
        create(admin, "activities", Json.map("subject", "Demo", "type", "meeting", "startAt", soon.toString()));
        create(admin, "activities", Json.map("subject", "Late task", "endAt", Instant.now().minus(1, ChronoUnit.DAYS).toString()));

        MvcResult dashboard = mockMvc.perform(get("/api/v1/dashboard").header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.range").value("this_month"))
                .andExpect(jsonPath("$.data.leads.created").value(2))
                .andExpect(jsonPath("$.data.leads.converted").value(1))
                .andExpect(jsonPath("$.data.leads.conversionRatePercent").value(50.0))
                .andExpect(jsonPath("$.data.deals.openCount").value(2))
                .andExpect(jsonPath("$.data.deals.pipelineValue").value(400000.0))
                .andExpect(jsonPath("$.data.deals.weightedPipeline").value(190000.0))
                .andExpect(jsonPath("$.data.deals.wonCount").value(1))
                .andExpect(jsonPath("$.data.deals.wonRevenue").value(500000.0))
                .andExpect(jsonPath("$.data.deals.lostCount").value(1))
                .andExpect(jsonPath("$.data.deals.winRatePercent").value(50.0))
                .andExpect(jsonPath("$.data.funnel[0].stage").value("New"))
                .andExpect(jsonPath("$.data.funnel[0].count").value(1))
                .andExpect(jsonPath("$.data.revenueByMonth.length()").value(12))
                .andExpect(jsonPath("$.data.revenueByMonth[11].revenue").value(500000.0))
                .andExpect(jsonPath("$.data.leadSources[0].label").value("Website"))
                .andExpect(jsonPath("$.data.activities.overdue").value(1))
                .andExpect(jsonPath("$.data.activities.upcomingMeetings[0].subject").value("Demo"))
                .andReturn();
        assertThat(Json.<Integer>read(dashboard, "$.data.leadSources[0].count")).isEqualTo(2);

        // The rep only sees their own (empty) pipeline.
        mockMvc.perform(get("/api/v1/dashboard").header("Authorization", rep.bearer()).param("range", "this_year"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deals.openCount").value(0))
                .andExpect(jsonPath("$.data.leads.created").value(0));
        mockMvc.perform(get("/api/v1/dashboard").header("Authorization", admin.bearer()).param("range", "forever"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anotherTenantCannotReachAnyRecordOrSubResource() throws Exception {
        Session a = TestAuth.register(mockMvc);
        Session b = TestAuth.register(mockMvc);
        Map<String, String> ids = new java.util.LinkedHashMap<>();
        ids.put("companies", id(create(a, "companies", Json.map("name", "Secret Co"))));
        ids.put("contacts", id(create(a, "contacts", Json.map("lastName", "Secret"))));
        ids.put("leads", id(create(a, "leads", Json.map("lastName", "Secret Lead"))));
        ids.put("deals", id(create(a, "deals", Json.map("name", "Secret Deal", "amount", 1))));
        ids.put("activities", id(create(a, "activities", Json.map("subject", "Secret Task"))));
        MvcResult note = mockMvc.perform(post("/api/v1/leads/" + ids.get("leads") + "/notes")
                        .header("Authorization", a.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(Json.obj("body", "secret")))
                .andExpect(status().isCreated()).andReturn();
        MvcResult file = mockMvc.perform(multipart("/api/v1/leads/" + ids.get("leads") + "/attachments")
                        .file(new MockMultipartFile("file", "s.txt", "text/plain", "secret".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", a.bearer()))
                .andExpect(status().isCreated()).andReturn();

        for (Map.Entry<String, String> entry : ids.entrySet()) {
            String base = "/api/v1/" + entry.getKey() + "/" + entry.getValue();
            mockMvc.perform(get(base).header("Authorization", b.bearer())).andExpect(status().isNotFound());
            mockMvc.perform(patch(base).header("Authorization", b.bearer()).contentType(MediaType.APPLICATION_JSON)
                    .content("{}")).andExpect(status().isNotFound());
            mockMvc.perform(delete(base).header("Authorization", b.bearer())).andExpect(status().isNotFound());
            mockMvc.perform(get(base + "/history").header("Authorization", b.bearer())).andExpect(status().isNotFound());
            mockMvc.perform(get(base + "/timeline").header("Authorization", b.bearer())).andExpect(status().isNotFound());
            mockMvc.perform(get(base + "/notes").header("Authorization", b.bearer())).andExpect(status().isNotFound());
            mockMvc.perform(get(base + "/attachments").header("Authorization", b.bearer())).andExpect(status().isNotFound());
        }
        mockMvc.perform(patch("/api/v1/notes/" + Json.read(note, "$.data.id")).header("Authorization", b.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(Json.obj("body", "x"))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/attachments/" + Json.read(file, "$.data.id") + "/download")
                .header("Authorization", b.bearer())).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/leads/" + ids.get("leads") + "/convert").header("Authorization", b.bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/search").header("Authorization", b.bearer()).param("q", "secret"))
                .andExpect(jsonPath("$.data.total").value(0));
        mockMvc.perform(get("/api/v1/deals/board").header("Authorization", b.bearer()))
                .andExpect(jsonPath("$.data.columns[0].count").value(0));
        mockMvc.perform(get("/api/v1/dashboard").header("Authorization", b.bearer()))
                .andExpect(jsonPath("$.data.deals.openCount").value(0));
    }

    @Test
    void apiResponsesCarrySecurityHeaders() throws Exception {
        Session admin = TestAuth.register(mockMvc);
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    }

    // ------------------------------------------------------------------ helpers

    private ResultActions create(Session session, String module, Map<String, Object> values) throws Exception {
        return mockMvc.perform(post("/api/v1/" + module).header("Authorization", session.bearer())
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
