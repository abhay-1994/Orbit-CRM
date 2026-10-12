package com.orbitcrm.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import com.orbitcrm.shared.tenant.TenantContext;
import com.orbitcrm.support.IntegrationTest;
import com.orbitcrm.support.Json;
import com.orbitcrm.support.TestAuth;
import com.orbitcrm.support.TestAuth.Session;

/**
 * Integration tests of tenant isolation, the most important property of the platform.
 *
 * <p>Purpose: verify isolation at both layers:
 * <ul>
 *   <li>through the API, ids from another tenant behave as if they do not exist;</li>
 *   <li>directly in PostgreSQL, row-level security filters even hand-written SQL by the tenant stamped on the
 *   connection, and rejects writes into another tenant.</li>
 * </ul>
 *
 * @author Abhay
 */
class TenantIsolationIntegrationTest extends IntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void apiNeverExposesAnotherTenantsUsers() throws Exception {
        Session tenantA = TestAuth.register(mockMvc);
        Session tenantB = TestAuth.register(mockMvc);

        MvcResult list = mockMvc.perform(get("/api/v1/users").header("Authorization", tenantA.bearer()))
                .andExpect(status().isOk())
                .andReturn();
        List<String> emails = Json.read(list, "$.data.content[*].email");
        assertThat(emails).containsExactly(tenantA.email());

        mockMvc.perform(get("/api/v1/users/" + tenantB.userId()).header("Authorization", tenantA.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));

        mockMvc.perform(patch("/api/v1/users/" + tenantB.userId())
                        .header("Authorization", tenantA.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.obj("firstName", "Hijacked")))
                .andExpect(status().isNotFound());
    }

    @Test
    void rowLevelSecurityFiltersRawSqlByTenant() throws Exception {
        Session tenantA = TestAuth.register(mockMvc);
        Session tenantB = TestAuth.register(mockMvc);

        List<UUID> visibleToA = TenantContext.callAs(tenantA.tenantId(),
                () -> jdbc.queryForList("select tenant_id from users", UUID.class));
        assertThat(visibleToA).isNotEmpty().allMatch(tenantA.tenantId()::equals);

        List<UUID> visibleToB = TenantContext.callAs(tenantB.tenantId(),
                () -> jdbc.queryForList("select tenant_id from roles", UUID.class));
        assertThat(visibleToB).isNotEmpty().allMatch(tenantB.tenantId()::equals);

        // No tenant bound: fail closed.
        Integer anonymous = jdbc.queryForObject("select count(*) from users", Integer.class);
        assertThat(anonymous).isZero();

        // Explicit platform context may see everything.
        Integer all = TenantContext.callAsSystem(() -> jdbc.queryForObject(
                "select count(distinct tenant_id) from users where tenant_id in (?, ?)", Integer.class,
                tenantA.tenantId(), tenantB.tenantId()));
        assertThat(all).isEqualTo(2);
    }

    @Test
    void rowLevelSecurityRejectsWritesIntoAnotherTenant() throws Exception {
        Session tenantA = TestAuth.register(mockMvc);
        Session tenantB = TestAuth.register(mockMvc);

        Integer updated = TenantContext.callAs(tenantA.tenantId(), () -> jdbc.update(
                "update users set first_name = 'Hacked' where id = ?", tenantB.userId()));
        assertThat(updated).isZero();
    }
}
