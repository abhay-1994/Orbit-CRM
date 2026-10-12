package com.orbitcrm.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
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
 * Integration tests of global search, saved views, CSV import and CSV export (phase 6).
 *
 * <p>Purpose: verify that
 * <ul>
 *   <li>global search groups results by module and respects each module's scope;</li>
 *   <li>saved views include the seeded defaults and respect private / shared visibility;</li>
 *   <li>CSV import maps columns, validates rows, skips duplicates and reports per-row errors;</li>
 *   <li>import accepts semicolon-separated files with a BOM, Indian date formats (dd/MM/yyyy) and lookups by
 *   name;</li>
 *   <li>CSV export uses the EXPORT scope, writes labels instead of ids and neutralizes spreadsheet formulas.</li>
 * </ul>
 *
 * @author Abhay
 */
class SearchViewsImportExportIntegrationTest extends IntegrationTest {

    @Test
    void globalSearchGroupsByModuleAndRespectsScopes() throws Exception {
        Session admin = TestAuth.register(mockMvc);
        Session rep = TestUsers.inviteAndAccept(mockMvc, mail, admin, "Sales User", "searcher", null);
        create(admin, "leads", Json.map("firstName", "Abhay", "lastName", "Kumar", "phone", "+91 99887 76655"));
        create(admin, "contacts", Json.map("firstName", "Abhay", "lastName", "Sharma"));
        create(admin, "companies", Json.map("name", "Abhay Traders"));
        create(admin, "deals", Json.map("name", "Unrelated"));

        mockMvc.perform(get("/api/v1/search").header("Authorization", admin.bearer()).param("q", "abhay"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.groups[?(@.module == 'leads')].count").value(1))
                .andExpect(jsonPath("$.data.groups[?(@.module == 'contacts')].results[0].title").value("Abhay Sharma"))
                .andExpect(jsonPath("$.data.groups[?(@.module == 'companies')].results[0].recordNumber").value("CMP-1"));

        mockMvc.perform(get("/api/v1/search").header("Authorization", admin.bearer()).param("q", "9988776655"))
                .andExpect(jsonPath("$.data.groups[0].module").value("leads"));

        // A Sales User sees shared companies/contacts but not someone else's lead.
        MvcResult repSearch = mockMvc.perform(get("/api/v1/search").header("Authorization", rep.bearer())
                        .param("q", "abhay"))
                .andExpect(status().isOk()).andReturn();
        List<String> modules = Json.read(repSearch, "$.data.groups[*].module");
        assertThat(modules).contains("companies", "contacts").doesNotContain("leads");

        mockMvc.perform(get("/api/v1/search").header("Authorization", admin.bearer()).param("q", "a"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void savedViewsIncludeDefaultsAndRespectVisibility() throws Exception {
        Session admin = TestAuth.register(mockMvc);
        Session rep = TestUsers.inviteAndAccept(mockMvc, mail, admin, "Sales User", "viewer", null);
        create(admin, "deals", Json.map("name", "Admin deal"));
        create(rep, "deals", Json.map("name", "Rep deal"));

        MvcResult views = mockMvc.perform(get("/api/v1/views").header("Authorization", rep.bearer()).param("module", "deals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].name", hasItem("My Open Deals")))
                .andReturn();
        String myOpenDeals = Json.<List<String>>read(views, "$.data[?(@.name == 'My Open Deals')].id").get(0);
        mockMvc.perform(get("/api/v1/views/" + myOpenDeals + "/records").header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("Admin deal"));

        mockMvc.perform(post("/api/v1/views").header("Authorization", rep.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("module", "deals", "name", "Broken",
                                "filter", Map.of("field", "nope", "op", "eq", "value", 1)))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/views").header("Authorization", rep.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("module", "deals", "name", "Team view", "visibility", "SHARED"))))
                .andExpect(status().isForbidden());
        MvcResult mine = mockMvc.perform(post("/api/v1/views").header("Authorization", rep.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("module", "deals", "name", "Big ones",
                                "filter", Map.of("field", "amount", "op", "gte", "value", 100000),
                                "sort", List.of(Map.of("field", "amount", "direction", "desc"))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.visibility").value("PRIVATE"))
                .andReturn();
        String privateView = Json.read(mine, "$.data.id");
        mockMvc.perform(get("/api/v1/views/" + privateView + "/records").header("Authorization", admin.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void csvImportMapsValidatesSkipsDuplicatesAndReportsRowErrors() throws Exception {
        Session admin = TestAuth.register(mockMvc);
        Session rep = TestUsers.inviteAndAccept(mockMvc, mail, admin, "Sales User", "importee", null);
        create(admin, "leads", Json.map("lastName", "Existing", "email", "dup@example.test"));
        String csv = """
                First Name,Last Name,Company,E-mail,Lead Source,Owner,Unmapped Notes
                Neha,Gupta,Gupta Exports,neha@gupta.test,Social Media,%s,x
                Arjun,Mehta,Mehta & Sons,arjun@mehta.test,referral,,y
                Kavya,,No Last Name Ltd,kavya@nolast.test,Website,,z
                Dup,Licate,Dup Co,DUP@example.test,Email,,w
                Ravi,Iyer,Iyer Pvt Ltd,ravi@iyer.test,Carrier Pigeon,,v
                """.formatted(rep.email());

        MvcResult uploaded = mockMvc.perform(multipart("/api/v1/imports")
                        .file(new MockMultipartFile("file", "leads.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                        .param("module", "leads").header("Authorization", admin.bearer()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("UPLOADED"))
                .andExpect(jsonPath("$.data.totalRows").value(5))
                .andExpect(jsonPath("$.data.mapping['First Name']").value("firstName"))
                .andExpect(jsonPath("$.data.mapping['E-mail']").value("email"))
                .andExpect(jsonPath("$.data.mapping['Lead Source']").value("leadSource"))
                .andExpect(jsonPath("$.data.mapping['Owner']").value("ownerId"))
                .andExpect(jsonPath("$.data.mapping['Unmapped Notes']").doesNotExist())
                .andReturn();
        String importId = Json.read(uploaded, "$.data.id");

        Map<String, Object> mapping = Json.map("First Name", "firstName", "Last Name", "lastName",
                "Company", "companyName", "E-mail", "email", "Lead Source", "leadSource", "Owner", "ownerId");
        mockMvc.perform(put("/api/v1/imports/" + importId + "/mapping").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("mapping", mapping, "duplicateField", "email",
                                "duplicateStrategy", "SKIP", "defaults", Map.of("rating", "warm")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.job.status").value("MAPPED"))
                .andExpect(jsonPath("$.data.rows[0].values.leadSource").value("social_media"))
                .andExpect(jsonPath("$.data.rows[0].errors.length()").value(0))
                .andExpect(jsonPath("$.data.rows[2].errors[0]").value(org.hamcrest.Matchers.containsString("Last name")))
                .andExpect(jsonPath("$.data.rows[4].errors[0]").value(org.hamcrest.Matchers.containsString("Lead source")));

        mockMvc.perform(post("/api/v1/imports/" + importId + "/start").header("Authorization", admin.bearer()))
                .andExpect(status().isOk());
        MvcResult done = awaitImport(admin, importId);
        assertThat(Json.<String>read(done, "$.data.status")).isEqualTo("COMPLETED");
        assertThat(Json.<Integer>read(done, "$.data.created")).isEqualTo(2);
        assertThat(Json.<Integer>read(done, "$.data.skipped")).isEqualTo(1);
        assertThat(Json.<Integer>read(done, "$.data.failed")).isEqualTo(2);
        assertThat(Json.<List<Integer>>read(done, "$.data.errors[*].row")).containsExactly(4, 6);

        mockMvc.perform(post("/api/v1/leads/search").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("filter", Map.of("field", "email", "op", "eq", "value", "neha@gupta.test")))))
                .andExpect(jsonPath("$.data.content[0].values.ownerId").value(rep.userId().toString()))
                .andExpect(jsonPath("$.data.content[0].values.rating").value("warm"))
                .andExpect(jsonPath("$.data.content[0].values.leadSource").value("social_media"));
        // Imports do not flood the new owner with assignment notifications.
        mockMvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", rep.bearer()))
                .andExpect(jsonPath("$.data.unread").value(0));
        mockMvc.perform(get("/api/v1/audit-logs").header("Authorization", admin.bearer()).param("action", "IMPORT"))
                .andExpect(jsonPath("$.data.content[0].metadata.created").value(2));
    }

    @Test
    void importAcceptsSemicolonsBomIndianDatesAndLookupsByName() throws Exception {
        Session admin = TestAuth.register(mockMvc);
        create(admin, "companies", Json.map("name", "Tata Motors"));
        String csv = "﻿First Name;Last Name;Company;Birthday;Tags\n"
                + "Ratan;Naval;Tata Motors;28/12/1937;vip; board\n";

        MvcResult uploaded = mockMvc.perform(multipart("/api/v1/imports")
                        .file(new MockMultipartFile("file", "contacts.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                        .param("module", "contacts").header("Authorization", admin.bearer()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.columns[0]").value("First Name"))
                .andReturn();
        String importId = Json.read(uploaded, "$.data.id");
        mockMvc.perform(put("/api/v1/imports/" + importId + "/mapping").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("mapping", Map.of("First Name", "firstName", "Last Name", "lastName",
                                "Company", "companyId", "Birthday", "birthday")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rows[0].values.birthday").value("1937-12-28"))
                .andExpect(jsonPath("$.data.rows[0].errors.length()").value(0));
        mockMvc.perform(post("/api/v1/imports/" + importId + "/start").header("Authorization", admin.bearer()))
                .andExpect(status().isOk());
        assertThat(Json.<Integer>read(awaitImport(admin, importId), "$.data.created")).isEqualTo(1);
        mockMvc.perform(get("/api/v1/contacts").header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$.data.content[0].labels.companyId").value("Tata Motors"));
    }

    @Test
    void csvExportUsesExportScopeLabelsAndNeutralisesFormulas() throws Exception {
        Session admin = TestAuth.register(mockMvc);
        Session rep = TestUsers.inviteAndAccept(mockMvc, mail, admin, "Sales User", "exporter", null);
        create(admin, "leads", Json.map("firstName", "Safe", "lastName", "Lead", "leadSource", "website"));
        create(admin, "leads", Json.map("lastName", "=HYPERLINK(\"http://evil\")", "status", "contacted"));

        MvcResult result = mockMvc.perform(post("/api/v1/leads/export").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("fields", List.of("firstName", "lastName", "leadSource", "status", "ownerId"),
                                "sort", List.of(Map.of("field", "createdAt", "direction", "asc"))))))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<String> lines = body.lines().toList();
        assertThat(lines.get(0)).contains("Record number,First name,Last name,Lead source,Lead status,Owner");
        assertThat(lines.get(1)).contains("LEAD-1,Safe,Lead,Website,New,Asha Owner");
        assertThat(lines.get(2)).contains("'=HYPERLINK").contains("Contacted");

        mockMvc.perform(get("/api/v1/audit-logs").header("Authorization", admin.bearer()).param("action", "EXPORT"))
                .andExpect(jsonPath("$.data.content[0].metadata.rows").value(2));
        // Sales Users have no EXPORT permission on leads by default.
        mockMvc.perform(post("/api/v1/leads/export").header("Authorization", rep.bearer()))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ helpers

    private MvcResult awaitImport(Session session, String importId) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            MvcResult result = mockMvc.perform(get("/api/v1/imports/" + importId).header("Authorization", session.bearer()))
                    .andExpect(status().isOk()).andReturn();
            String state = Json.read(result, "$.data.status");
            if (state.equals("COMPLETED") || state.equals("FAILED")) {
                return result;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Import did not finish in time");
    }

    private ResultActions create(Session session, String module, Map<String, Object> values) throws Exception {
        return mockMvc.perform(post("/api/v1/" + module).header("Authorization", session.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(Json.write(values)))
                .andExpect(status().isCreated());
    }
}
