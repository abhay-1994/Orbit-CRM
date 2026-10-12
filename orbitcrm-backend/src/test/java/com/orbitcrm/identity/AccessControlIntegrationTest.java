package com.orbitcrm.identity;

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

import com.orbitcrm.support.IntegrationTest;
import com.orbitcrm.support.Json;
import com.orbitcrm.support.TestAuth;
import com.orbitcrm.support.TestAuth.Session;

/**
 * Integration tests of authorization: roles, permissions, teams and user administration (phase 1).
 *
 * <p>Purpose: verify that
 * <ul>
 *   <li>a sales user cannot administer users or roles but can use the user pickers;</li>
 *   <li>changes to a role's permissions apply immediately to signed-in users;</li>
 *   <li>custom roles are validated against the permission catalog;</li>
 *   <li>built-in roles and the last administrator are protected;</li>
 *   <li>disabling a user revokes their access immediately;</li>
 *   <li>teams form a hierarchy in which cycles are rejected.</li>
 * </ul>
 *
 * @author Abhay
 */
class AccessControlIntegrationTest extends IntegrationTest {

    @Test
    void salesUserCannotAdministerButCanUsePickers() throws Exception {
        Session admin = TestAuth.register(mockMvc);
        Session sales = inviteAndAccept(admin, "Sales User", "seller");

        mockMvc.perform(post("/api/v1/roles").header("Authorization", sales.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(Json.obj("name", "Sneaky")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/teams").header("Authorization", sales.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(Json.obj("name", "Sneaky")))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/tenant").header("Authorization", sales.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(Json.obj("name", "Renamed")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/users/lookup").header("Authorization", sales.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
        mockMvc.perform(get("/api/v1/teams").header("Authorization", sales.bearer())).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/tenant").header("Authorization", sales.bearer())).andExpect(status().isOk());
    }

    @Test
    void rolePermissionChangesApplyImmediately() throws Exception {
        Session admin = TestAuth.register(mockMvc);
        Session sales = inviteAndAccept(admin, "Sales User", "promoted");
        String salesRoleId = roleId(admin, "Sales User");

        mockMvc.perform(get("/api/v1/users").header("Authorization", sales.bearer())).andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/roles/" + salesRoleId).header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("name", "Sales User",
                                "permissions", Map.of("users", Map.of("READ", "ALL"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions.users.READ").value("ALL"));

        mockMvc.perform(get("/api/v1/users").header("Authorization", sales.bearer())).andExpect(status().isOk());
    }

    @Test
    void customRolesAreValidatedAgainstTheCatalog() throws Exception {
        Session admin = TestAuth.register(mockMvc);
        mockMvc.perform(post("/api/v1/roles").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("name", "Broken",
                                "permissions", Map.of("spaceships", Map.of("READ", "ALL"))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].code").value("UNKNOWN_RESOURCE"));

        mockMvc.perform(post("/api/v1/roles").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.write(Map.of("name", "Auditor",
                                "permissions", Map.of("audit", Map.of("READ", "OWN"))))))
                .andExpect(status().isCreated())
                // Unscoped resources are normalised to ALL.
                .andExpect(jsonPath("$.data.permissions.audit.READ").value("ALL"));
    }

    @Test
    void builtInRolesAndLastAdministratorAreProtected() throws Exception {
        Session admin = TestAuth.register(mockMvc);
        String adminRoleId = roleId(admin, "Administrator");
        String salesRoleId = roleId(admin, "Sales User");

        mockMvc.perform(put("/api/v1/roles/" + adminRoleId).header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(Json.obj("name", "Administrator")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ROLE_LOCKED"));
        mockMvc.perform(delete("/api/v1/roles/" + salesRoleId).header("Authorization", admin.bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ROLE_LOCKED"));

        mockMvc.perform(patch("/api/v1/users/" + admin.userId()).header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(Json.obj("status", "DISABLED")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CANNOT_DEMOTE_SELF"));
    }

    @Test
    void disablingAUserRevokesTheirAccessImmediately() throws Exception {
        Session admin = TestAuth.register(mockMvc);
        Session sales = inviteAndAccept(admin, "Sales User", "leaver");
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", sales.bearer())).andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/users/" + sales.userId()).header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(Json.obj("status", "DISABLED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", sales.bearer()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void teamsFormAHierarchyWithoutCycles() throws Exception {
        Session admin = TestAuth.register(mockMvc);
        String sales = createTeam(admin, "Sales", null);
        String north = createTeam(admin, "Sales North", sales);

        mockMvc.perform(put("/api/v1/teams/" + sales).header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(Json.obj("name", "Sales", "parentId", north)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].code").value("CYCLE"));
        mockMvc.perform(delete("/api/v1/teams/" + sales).header("Authorization", admin.bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("TEAM_HAS_CHILDREN"));
    }

    private String createTeam(Session admin, String name, String parentId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/teams").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(Json.obj("name", name, "parentId", parentId)))
                .andExpect(status().isCreated()).andReturn();
        return Json.read(result, "$.data.id");
    }

    private Session inviteAndAccept(Session admin, String roleName, String localPart) throws Exception {
        String email = localPart + "@" + admin.tenantSlug() + ".test";
        mockMvc.perform(post("/api/v1/users").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.obj("email", email, "firstName", "Test", "lastName", localPart,
                                "roleId", roleId(admin, roleName))))
                .andExpect(status().isCreated());
        MvcResult accepted = mockMvc.perform(post("/api/v1/auth/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.obj("token", mail.lastTokenFor(email), "password", TestAuth.PASSWORD)))
                .andExpect(status().isOk()).andReturn();
        return TestAuth.session(accepted, admin.tenantSlug(), email);
    }

    private String roleId(Session admin, String roleName) throws Exception {
        MvcResult roles = mockMvc.perform(get("/api/v1/roles").header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andReturn();
        List<String> ids = Json.read(roles, "$.data[?(@.name == '" + roleName + "')].id");
        return ids.get(0);
    }
}
