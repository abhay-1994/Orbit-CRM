package com.orbitcrm.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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
import com.orbitcrm.support.TestUsers;

/**
 * Integration tests of the generic record engine through the test-only "widgets" module.
 *
 * <p>Purpose: verify the engine independently of the CRM modules built on it (phase 2), using
 * {@link com.orbitcrm.testmodule.WidgetModule}, which has a field of every type:
 * <ul>
 *   <li>creating records with typed values, labels, record numbers and audit entries;</li>
 *   <li>field-level validation errors for invalid input;</li>
 *   <li>partial updates with optimistic locking, ignoring read-only fields;</li>
 *   <li>every operator family of the filter language, and rejection of invalid filters before execution;</li>
 *   <li>sorting, pagination and quick search;</li>
 *   <li>OWN and TEAM scopes limiting visibility and writes;</li>
 *   <li>soft delete and restore, with the search index following the record;</li>
 *   <li>bulk operations reporting per-record outcomes;</li>
 *   <li>references that must point at visible records and active users;</li>
 *   <li>deactivated picklist values that stay on old records but cannot be chosen;</li>
 *   <li>metadata describing fields, options and operators;</li>
 *   <li>the audit log being searchable and append-only.</li>
 * </ul>
 *
 * @author Abhay
 */
class RecordEngineIntegrationTest extends IntegrationTest {

    private static final String WIDGETS = "/api/v1/widgets";

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void createsRecordWithTypedValuesLabelsNumberAndAudit() throws Exception {
        Session admin = TestAuth.register(mockMvc);
        MvcResult result = create(admin, Json.map(
                "name", "  Sprocket  ", "email", "Sales@Example.COM", "phone", "+91 98765 43210",
                "website", "example.com", "quantity", 12, "weight", 1.25, "price", "1999.999", "discount", 12.5,
                "launchDate", "2026-10-01", "lastSeenAt", "2026-09-28T10:15:30+05:30", "active", true,
                "colors", List.of("red", "blue", "red"), "tags", List.of("VIP", "priority"),
                "reviewerIds", List.of(admin.userId().toString())))
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.message").value("widgets created successfully"))
                .andExpect(jsonPath("$.data.recordNumber").value("WID-1"))
                .andExpect(jsonPath("$.data.title").value("Sprocket"))
                .andExpect(jsonPath("$.data.values.name").value("Sprocket"))
                .andExpect(jsonPath("$.data.values.email").value("sales@example.com"))
                .andExpect(jsonPath("$.data.values.website").value("https://example.com"))
                .andExpect(jsonPath("$.data.values.price").value(2000.00))
                .andExpect(jsonPath("$.data.values.status").value("draft"))
                .andExpect(jsonPath("$.data.values.colors.length()").value(2))
                .andExpect(jsonPath("$.data.values.tags[0]").value("vip"))
                .andExpect(jsonPath("$.data.values.ownerId").value(admin.userId().toString()))
                .andExpect(jsonPath("$.data.values.lastSeenAt").value("2026-09-28T04:45:30Z"))
                .andExpect(jsonPath("$.data.labels.ownerId").value("Asha Owner"))
                .andExpect(jsonPath("$.data.labels.status").value("Draft"))
                .andExpect(jsonPath("$.data.labels.colors").value("Red, Blue"))
                .andReturn();
        String id = Json.read(result, "$.data.id");

        mockMvc.perform(get(WIDGETS + "/" + id + "/history").header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].action").value("CREATE"))
                .andExpect(jsonPath("$.data.content[0].changes[*].field", hasItem("price")));

        create(admin, Json.map("name", "Second")).andExpect(jsonPath("$.data.recordNumber").value("WID-2"));
    }

    @Test
    void rejectsInvalidInputWithFieldLevelErrors() throws Exception {
        Session admin = TestAuth.register(mockMvc);
        create(admin, Json.map("email", "not-an-email", "website", "javascript:alert(1)", "discount", 150,
                "quantity", -1, "status", "bogus", "colors", List.of("purple"), "spaceship", true,
                "launchDate", "28/09/2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details[*].field", hasItem("name")))
                .andExpect(jsonPath("$.error.details[*].field", hasItem("email")))
                .andExpect(jsonPath("$.error.details[*].field", hasItem("website")))
                .andExpect(jsonPath("$.error.details[*].field", hasItem("discount")))
                .andExpect(jsonPath("$.error.details[*].field", hasItem("quantity")))
                .andExpect(jsonPath("$.error.details[*].field", hasItem("status")))
                .andExpect(jsonPath("$.error.details[*].field", hasItem("colors")))
                .andExpect(jsonPath("$.error.details[*].field", hasItem("launchDate")))
                .andExpect(jsonPath("$.error.details[?(@.field == 'spaceship')].code").value("UNKNOWN_FIELD"));

        create(admin, Json.map("name", "x".repeat(256)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].code").value("INVALID_LENGTH"));
    }

    @Test
    void partialUpdateWithOptimisticLockingAndReadOnlyFieldsIgnored() throws Exception {
        Session admin = TestAuth.register(mockMvc);
        String id = id(create(admin, Json.map("name", "Gear", "quantity", 1)));

        mockMvc.perform(patch(WIDGETS + "/" + id).header("Authorization", admin.bearer()).header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.obj("quantity", 5, "createdAt", "2000-01-01T00:00:00Z")))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.data.values.quantity").value(5))
                .andExpect(jsonPath("$.data.values.name").value("Gear"))
                .andExpect(jsonPath("$.data.values.createdAt").value(org.hamcrest.Matchers.not("2000-01-01T00:00:00Z")));

        mockMvc.perform(patch(WIDGETS + "/" + id).header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(Json.obj("quantity", 9, "version", 0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONCURRENT_MODIFICATION"));

        mockMvc.perform(patch(WIDGETS + "/" + id).header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(Json.obj("name", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].code").value("REQUIRED"));

        mockMvc.perform(get(WIDGETS + "/" + id + "/history").header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$.data.content[0].action").value("UPDATE"))
                .andExpect(jsonPath("$.data.content[0].changes[0].field").value("quantity"))
                .andExpect(jsonPath("$.data.content[0].changes[0].oldValue").value(1))
                .andExpect(jsonPath("$.data.content[0].changes[0].newValue").value(5));
    }

    @Test
    void filterLanguageSupportsEveryOperatorFamily() throws Exception {
        Session admin = TestAuth.register(mockMvc);
        String today = LocalDate.now().toString();
        String alpha = id(create(admin, Json.map("name", "Alpha Unit", "email", "alpha@acme.io", "quantity", 10,
                "price", 100, "launchDate", today, "active", true, "colors", List.of("red"), "tags", List.of("hot"),
                "status", "active")));
        create(admin, Json.map("name", "Beta Unit", "email", "beta@other.io", "quantity", 20, "price", 250.50,
                "launchDate", "2020-01-15", "active", false, "colors", List.of("green", "blue")));
        create(admin, Json.map("name", "Gamma", "quantity", 30, "parentId", alpha));

        assertCount(admin, Json.map("field", "name", "op", "contains", "value", "unit"), 2);
        assertCount(admin, Json.map("field", "name", "op", "eq", "value", "ALPHA UNIT"), 1);
        assertCount(admin, Json.map("field", "name", "op", "starts_with", "value", "gam"), 1);
        assertCount(admin, Json.map("field", "email", "op", "is_empty"), 1);
        assertCount(admin, Json.map("field", "email", "op", "not_contains", "value", "acme"), 2);
        assertCount(admin, Json.map("field", "quantity", "op", "gt", "value", 10), 2);
        assertCount(admin, Json.map("field", "quantity", "op", "between", "value", List.of(10, 20)), 2);
        assertCount(admin, Json.map("field", "price", "op", "gte", "value", "250.5"), 1);
        assertCount(admin, Json.map("field", "launchDate", "op", "eq", "value", "$today"), 1);
        assertCount(admin, Json.map("field", "launchDate", "op", "within_last_days", "value", 7), 1);
        assertCount(admin, Json.map("field", "launchDate", "op", "lt", "value", "2021-01-01"), 1);
        assertCount(admin, Json.map("field", "createdAt", "op", "eq", "value", "$today"), 3);
        assertCount(admin, Json.map("field", "active", "op", "eq", "value", false), 1);
        assertCount(admin, Json.map("field", "status", "op", "in", "value", List.of("active", "retired")), 1);
        assertCount(admin, Json.map("field", "status", "op", "neq", "value", "active"), 2);
        assertCount(admin, Json.map("field", "colors", "op", "contains", "value", List.of("blue", "red")), 2);
        assertCount(admin, Json.map("field", "colors", "op", "contains_all", "value", List.of("green", "blue")), 1);
        assertCount(admin, Json.map("field", "tags", "op", "contains", "value", "HOT"), 1);
        assertCount(admin, Json.map("field", "ownerId", "op", "eq", "value", "$me"), 3);
        assertCount(admin, Json.map("field", "parentId", "op", "eq", "value", alpha), 1);
        assertCount(admin, Json.map("or", List.of(
                Json.map("field", "quantity", "op", "eq", "value", 30),
                Json.map("and", List.of(
                        Json.map("field", "active", "op", "eq", "value", true),
                        Json.map("field", "price", "op", "lt", "value", 1000))))), 2);
    }

    @Test
    void invalidFiltersAreRejectedNotExecuted() throws Exception {
        Session admin = TestAuth.register(mockMvc);
        search(admin, Map.of("filter", Json.map("field", "password", "op", "eq", "value", "x")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].code").value("UNKNOWN_FIELD"));
        search(admin, Map.of("filter", Json.map("field", "active", "op", "contains", "value", "x")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].code").value("UNSUPPORTED_OPERATOR"));
        search(admin, Map.of("filter", Json.map("field", "quantity", "op", "gt", "value", "1; DROP TABLE widgets")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].code").value("INVALID_FILTER_VALUE"));
        search(admin, Map.of("filter", Json.map("field", "name", "op", "explode", "value", "x")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].code").value("INVALID_FILTER"));
        search(admin, Map.of("sort", List.of(Map.of("field", "notes", "direction", "asc"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sortingPaginationAndQuickSearch() throws Exception {
        Session admin = TestAuth.register(mockMvc);
        for (int i = 1; i <= 7; i++) {
            create(admin, Json.map("name", "Item " + i, "quantity", i, "phone", "+91 90000 0000" + i));
        }
        MvcResult page = mockMvc.perform(get(WIDGETS).header("Authorization", admin.bearer())
                        .param("sort", "quantity,desc").param("page", "1").param("size", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(7))
                .andExpect(jsonPath("$.data.totalPages").value(3))
                .andExpect(jsonPath("$.data.page").value(1))
                .andReturn();
        List<Integer> quantities = Json.read(page, "$.data.content[*].values.quantity");
        assertThat(quantities).containsExactly(4, 3, 2);

        mockMvc.perform(get(WIDGETS).header("Authorization", admin.bearer()).param("q", "item 5"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mockMvc.perform(get(WIDGETS).header("Authorization", admin.bearer()).param("q", "WID-3"))
                .andExpect(jsonPath("$.data.content[0].recordNumber").value("WID-3"));
        mockMvc.perform(get(WIDGETS).header("Authorization", admin.bearer()).param("q", "9000000006"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mockMvc.perform(get(WIDGETS).header("Authorization", admin.bearer()).param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_PAGE_SIZE"));
    }

    @Test
    void ownAndTeamScopesLimitVisibilityAndWrites() throws Exception {
        Session admin = TestAuth.register(mockMvc);
        String sales = TestUsers.createTeam(mockMvc, admin, "Sales", null);
        String north = TestUsers.createTeam(mockMvc, admin, "Sales North", sales);
        Session manager = TestUsers.inviteAndAccept(mockMvc, mail, admin, "Sales Manager", "maya", sales);
        Session rep1 = TestUsers.inviteAndAccept(mockMvc, mail, admin, "Sales User", "ravi", north);
        Session rep2 = TestUsers.inviteAndAccept(mockMvc, mail, admin, "Sales User", "ritu", north);
        Session outsider = TestUsers.inviteAndAccept(mockMvc, mail, admin, "Sales User", "omar", null);

        String rep1Widget = id(create(rep1, Json.map("name", "Rep1 widget")));
        create(rep2, Json.map("name", "Rep2 widget"));
        create(outsider, Json.map("name", "Outsider widget"));

        assertTotal(rep1, 1);
        assertTotal(manager, 2);   // TEAM includes sub-team members
        assertTotal(admin, 3);

        mockMvc.perform(get(WIDGETS + "/" + rep1Widget).header("Authorization", rep2.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("WIDGET_NOT_FOUND"));
        mockMvc.perform(patch(WIDGETS + "/" + rep1Widget).header("Authorization", outsider.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(Json.obj("name", "Hijack")))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch(WIDGETS + "/" + rep1Widget).header("Authorization", manager.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(Json.obj("name", "Managed")))
                .andExpect(status().isOk());

        // Reassigning ownership moves visibility with it.
        mockMvc.perform(patch(WIDGETS + "/" + rep1Widget).header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(Json.obj("ownerId", outsider.userId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.labels.ownerId").value("Omar Tester"));
        assertTotal(rep1, 0);
        assertTotal(outsider, 2);
    }

    @Test
    void softDeleteRestoreAndSearchIndexFollowTheRecord() throws Exception {
        Session admin = TestAuth.register(mockMvc);
        String id = id(create(admin, Json.map("name", "Flux Capacitor", "phone", "+1 (555) 010-9999")));

        List<Map<String, Object>> indexed = TenantContext.callAs(admin.tenantId(), () -> jdbc.queryForList(
                "select title, content from search_index where record_id = ?::uuid", id));
        assertThat(indexed).hasSize(1);
        assertThat(indexed.get(0).get("title")).isEqualTo("Flux Capacitor");
        assertThat((String) indexed.get(0).get("content")).contains("15550109999").contains("wid-1");

        mockMvc.perform(delete(WIDGETS + "/" + id).header("Authorization", admin.bearer())).andExpect(status().isOk());
        mockMvc.perform(get(WIDGETS + "/" + id).header("Authorization", admin.bearer())).andExpect(status().isNotFound());
        assertTotal(admin, 0);
        Integer remaining = TenantContext.callAs(admin.tenantId(), () -> jdbc.queryForObject(
                "select count(*) from search_index where record_id = ?::uuid", Integer.class, id));
        assertThat(remaining).isZero();

        mockMvc.perform(post(WIDGETS + "/" + id + "/restore").header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.values.name").value("Flux Capacitor"));
        assertTotal(admin, 1);
    }

    @Test
    void bulkOperationsReportPerRecordOutcomes() throws Exception {
        Session admin = TestAuth.register(mockMvc);
        Session rep = TestUsers.inviteAndAccept(mockMvc, mail, admin, "Sales User", "bulky", null);
        String a = id(create(admin, Json.map("name", "A")));
        String b = id(create(admin, Json.map("name", "B")));
        String missing = java.util.UUID.randomUUID().toString();

        mockMvc.perform(post(WIDGETS + "/bulk-update").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("ids", List.of(a, b, missing),
                                "values", Map.of("ownerId", rep.userId().toString())))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requested").value(3))
                .andExpect(jsonPath("$.data.succeeded").value(2))
                .andExpect(jsonPath("$.data.failures[0].id").value(missing))
                .andExpect(jsonPath("$.data.failures[0].code").value("WIDGET_NOT_FOUND"));
        assertTotal(rep, 2);

        mockMvc.perform(post(WIDGETS + "/bulk-delete").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(Json.write(Map.of("ids", List.of(a, b)))))
                .andExpect(jsonPath("$.data.succeeded").value(2));
        assertTotal(admin, 0);
    }

    @Test
    void referencesMustPointAtVisibleRecordsAndActiveUsers() throws Exception {
        Session admin = TestAuth.register(mockMvc);
        Session other = TestAuth.register(mockMvc);
        String foreign = id(create(other, Json.map("name", "Other tenant widget")));

        create(admin, Json.map("name", "Child", "parentId", foreign))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].code").value("INVALID_REFERENCE"));
        create(admin, Json.map("name", "Owned by stranger", "ownerId", other.userId().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].code").value("INVALID_USER"));
        mockMvc.perform(get(WIDGETS + "/" + foreign).header("Authorization", admin.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deactivatedPicklistValuesStayOnOldRecordsButCannotBeChosen() throws Exception {
        Session admin = TestAuth.register(mockMvc);
        String id = id(create(admin, Json.map("name", "Legacy", "status", "active")));
        MvcResult picklist = mockMvc.perform(get("/api/v1/picklists/widget_status").header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andReturn();
        List<String> activeIds = Json.read(picklist, "$.data.values[?(@.key == 'active')].id");

        mockMvc.perform(patch("/api/v1/picklists/widget_status/values/" + activeIds.get(0))
                        .header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(Json.obj("active", false, "label", "Live")))
                .andExpect(status().isOk());

        mockMvc.perform(patch(WIDGETS + "/" + id).header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(Json.obj("quantity", 3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.labels.status").value("Live"));
        create(admin, Json.map("name", "New", "status", "active"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].code").value("INVALID_OPTION"));

        mockMvc.perform(post("/api/v1/picklists/widget_status/values").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(Json.obj("label", "On Hold")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.key").value("on_hold"));
        create(admin, Json.map("name", "Paused", "status", "on_hold")).andExpect(status().isCreated());

        List<String> retiredIds = Json.read(picklist, "$.data.values[?(@.key == 'retired')].id");
        mockMvc.perform(patch("/api/v1/picklists/widget_status/values/" + retiredIds.get(0))
                        .header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(Json.obj("active", false)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PICKLIST_VALUE_SYSTEM"));
    }

    @Test
    void metadataDescribesFieldsOptionsAndOperators() throws Exception {
        Session admin = TestAuth.register(mockMvc);
        mockMvc.perform(get("/api/v1/metadata/modules").header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.key == 'widgets')].permissions.CREATE").value("ALL"));
        mockMvc.perform(get("/api/v1/metadata/modules/widgets").header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recordPrefix").value("WID"))
                .andExpect(jsonPath("$.data.fields[?(@.key == 'status')].options[0].key").value("draft"))
                .andExpect(jsonPath("$.data.fields[?(@.key == 'price')].type").value("CURRENCY"))
                .andExpect(jsonPath("$.data.fields[?(@.key == 'colors')].operators[*]", hasItem("contains_all")))
                .andExpect(jsonPath("$.data.fields[?(@.key == 'ownerId')].required").value(true));
    }

    @Test
    void auditLogIsSearchableAndAppendOnly() throws Exception {
        Session admin = TestAuth.register(mockMvc);
        String id = id(create(admin, Json.map("name", "Audited")));

        mockMvc.perform(get("/api/v1/audit-logs").header("Authorization", admin.bearer())
                        .param("module", "widgets").param("recordId", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].actorName").value("Asha Owner"));
        mockMvc.perform(get("/api/v1/audit-logs").header("Authorization", admin.bearer()).param("action", "LOGIN"))
                .andExpect(status().isOk());

        assertThatThrownBy(() -> TenantContext.runAs(admin.tenantId(),
                () -> jdbc.update("update audit_logs set action = 'TAMPERED' where record_id = ?::uuid", id)))
                .hasStackTraceContaining("permission denied for table audit_logs");
        assertThatThrownBy(() -> TenantContext.runAs(admin.tenantId(),
                () -> jdbc.update("delete from audit_logs where record_id = ?::uuid", id)))
                .hasStackTraceContaining("permission denied for table audit_logs");
    }

    // ------------------------------------------------------------------ helpers

    private ResultActions create(Session session, Map<String, Object> values) throws Exception {
        return mockMvc.perform(post(WIDGETS).header("Authorization", session.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(Json.write(values)));
    }

    private static String id(ResultActions actions) throws Exception {
        return Json.read(actions.andExpect(status().isCreated()).andReturn(), "$.data.id");
    }

    private ResultActions search(Session session, Map<String, Object> body) throws Exception {
        return mockMvc.perform(post(WIDGETS + "/search").header("Authorization", session.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(Json.write(body)));
    }

    private void assertCount(Session session, Map<String, Object> filter, int expected) throws Exception {
        MvcResult result = search(session, Map.of("filter", filter)).andExpect(status().isOk()).andReturn();
        Integer total = Json.read(result, "$.data.totalElements");
        assertThat(total).as("filter %s", filter).isEqualTo(expected);
    }

    private void assertTotal(Session session, int expected) throws Exception {
        mockMvc.perform(get(WIDGETS).header("Authorization", session.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(expected));
    }
}
