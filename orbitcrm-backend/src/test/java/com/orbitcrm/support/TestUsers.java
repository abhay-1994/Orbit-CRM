package com.orbitcrm.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.orbitcrm.support.TestAuth.Session;

/**
 * Test helper that creates additional users, roles and teams through the public API.
 *
 * <p>Purpose: set up realistic multi-user scenarios (sales users, managers, teams) for permission and scope tests. It
 * invites a user with a given role and team, accepts the invitation via the captured email token and returns the new
 * user's session; it also looks up role ids by name and creates teams.
 *
 * @author Abhay
 */
public final class TestUsers {

    private TestUsers() {
    }

    public static Session inviteAndAccept(MockMvc mvc, RecordingMailService mail, Session admin, String roleName,
                                          String localPart, String teamId) throws Exception {
        String email = localPart + "@" + admin.tenantSlug() + ".test";
        mvc.perform(post("/api/v1/users").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.obj("email", email, "firstName", capitalize(localPart), "lastName", "Tester",
                                "roleId", roleId(mvc, admin, roleName), "teamId", teamId)))
                .andExpect(status().isCreated());
        MvcResult accepted = mvc.perform(post("/api/v1/auth/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.obj("token", mail.lastTokenFor(email), "password", TestAuth.PASSWORD)))
                .andExpect(status().isOk()).andReturn();
        return TestAuth.session(accepted, admin.tenantSlug(), email);
    }

    public static String roleId(MockMvc mvc, Session admin, String roleName) throws Exception {
        MvcResult roles = mvc.perform(get("/api/v1/roles").header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andReturn();
        List<String> ids = Json.read(roles, "$.data[?(@.name == '" + roleName + "')].id");
        if (ids.isEmpty()) {
            throw new AssertionError("No role named " + roleName);
        }
        return ids.get(0);
    }

    public static String createTeam(MockMvc mvc, Session admin, String name, String parentId) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/teams").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(Json.obj("name", name, "parentId", parentId)))
                .andExpect(status().isCreated()).andReturn();
        return Json.read(result, "$.data.id");
    }

    private static String capitalize(String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
