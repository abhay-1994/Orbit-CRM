package com.orbitcrm.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.orbitcrm.identity.util.Base32;
import com.orbitcrm.identity.util.Totp;
import com.orbitcrm.support.IntegrationTest;
import com.orbitcrm.support.Json;
import com.orbitcrm.support.TestAuth;
import com.orbitcrm.support.TestAuth.Session;

/**
 * Integration tests of all authentication flows (phase 1).
 *
 * <p>Purpose: verify sign-up and sign-in security end to end through the real endpoints:
 * <ul>
 *   <li>registration creates the workspace, its administrator and a session, and rejects taken slugs and weak
 *   passwords;</li>
 *   <li>login failures are generic and the account locks after repeated failures;</li>
 *   <li>refresh rotates tokens, and reusing an old refresh token revokes the session;</li>
 *   <li>cookie-based refresh and logout require the CSRF header;</li>
 *   <li>logout revokes access immediately, and unauthenticated requests get the standard error envelope;</li>
 *   <li>changing the password signs out the other sessions; forgot / reset password works via the emailed link;</li>
 *   <li>invited users accept their invitation and are signed in;</li>
 *   <li>TOTP two-factor authentication can be enabled, used at login and disabled;</li>
 *   <li>users can list and revoke their own sessions;</li>
 *   <li>login returns the refresh token in the body, and body-based clients can refresh and log out without
 *   cookies.</li>
 * </ul>
 *
 * @author Abhay
 */
class AuthFlowIntegrationTest extends IntegrationTest {

    @Test
    void registrationCreatesWorkspaceAdministratorAndSession() throws Exception {
        String slug = TestAuth.uniqueSlug("reg");
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.obj("companyName", "Registration Co", "tenantSlug", slug, "firstName", "Asha",
                                "lastName", "Owner", "email", "Owner@Reg.test", "password", TestAuth.PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.user.email").value("owner@reg.test"))
                .andExpect(jsonPath("$.data.user.administrator").value(true))
                .andExpect(jsonPath("$.data.user.tenant.slug").value(slug))
                .andExpect(jsonPath("$.data.user.tenant.currency").value("INR"))
                .andExpect(jsonPath("$.data.user.permissions.users.CREATE").value("ALL"))
                .andExpect(cookie().httpOnly(TestAuth.REFRESH_COOKIE, true))
                .andReturn();
        Session session = TestAuth.session(result, slug, "owner@reg.test");

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", session.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role.name").value("Administrator"));

        mockMvc.perform(get("/api/v1/roles").header("Authorization", session.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(6));
    }

    @Test
    void registrationRejectsTakenSlugAndWeakPassword() throws Exception {
        Session existing = TestAuth.register(mockMvc);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.obj("companyName", "Dup", "tenantSlug", existing.tenantSlug(), "firstName", "A",
                                "lastName", "B", "email", "dup@dup.test", "password", TestAuth.PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("TENANT_SLUG_TAKEN"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.obj("companyName", "Weak", "tenantSlug", TestAuth.uniqueSlug("weak"),
                                "firstName", "A", "lastName", "B", "email", "weak@weak.test", "password", "short1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details[0].code").value("WEAK_PASSWORD"));
    }

    @Test
    void loginFailuresAreGenericAndAccountLocksAfterRepeatedFailures() throws Exception {
        Session owner = TestAuth.register(mockMvc);

        loginExpect(owner.tenantSlug(), owner.email(), "Wrong-passw0rd", 401, "INVALID_CREDENTIALS");
        loginExpect("no-such-workspace", owner.email(), TestAuth.PASSWORD, 401, "INVALID_CREDENTIALS");
        loginExpect(owner.tenantSlug(), "nobody@nowhere.test", TestAuth.PASSWORD, 401, "INVALID_CREDENTIALS");

        for (int i = 0; i < 4; i++) {
            loginExpect(owner.tenantSlug(), owner.email(), "Wrong-passw0rd", 401, "INVALID_CREDENTIALS");
        }
        // Five failures in total: the correct password is now refused while the account is locked.
        loginExpect(owner.tenantSlug(), owner.email(), TestAuth.PASSWORD, 401, "INVALID_CREDENTIALS");
    }

    @Test
    void refreshRotatesTokensAndReuseRevokesTheSession() throws Exception {
        Session session = TestAuth.register(mockMvc);
        Cookie original = session.refreshCookie();

        MvcResult rotated = mockMvc.perform(refresh(original))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();
        Cookie next = rotated.getResponse().getCookie(TestAuth.REFRESH_COOKIE);
        assertThat(next).isNotNull();
        assertThat(next.getValue()).isNotEqualTo(original.getValue());
        String newAccess = Json.read(rotated, "$.data.accessToken");

        // Replaying the old token is treated as theft: the whole session dies.
        mockMvc.perform(refresh(original))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("SESSION_EXPIRED"));
        mockMvc.perform(refresh(next)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + newAccess))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
    }

    @Test
    void cookieEndpointsRequireCsrfHeader() throws Exception {
        Session session = TestAuth.register(mockMvc);
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(session.refreshCookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CSRF_HEADER_MISSING"));
    }

    @Test
    void logoutRevokesAccessImmediately() throws Exception {
        Session session = TestAuth.register(mockMvc);
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", session.bearer())).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Authorization", session.bearer())
                        .cookie(session.refreshCookie()))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge(TestAuth.REFRESH_COOKIE, 0));

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", session.bearer()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(refresh(session.refreshCookie())).andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedRequestsGetStandardError() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
    }

    @Test
    void changingPasswordSignsOutOtherSessions() throws Exception {
        Session first = TestAuth.register(mockMvc);
        Session second = TestAuth.login(mockMvc, first.tenantSlug(), first.email(), TestAuth.PASSWORD);

        mockMvc.perform(post("/api/v1/auth/password/change")
                        .header("Authorization", first.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.obj("currentPassword", TestAuth.PASSWORD, "newPassword", "An0ther-Str0ng-Pass")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", first.bearer())).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", second.bearer()))
                .andExpect(status().isUnauthorized());
        TestAuth.login(mockMvc, first.tenantSlug(), first.email(), "An0ther-Str0ng-Pass");
    }

    @Test
    void forgotAndResetPasswordFlow() throws Exception {
        Session owner = TestAuth.register(mockMvc);

        mockMvc.perform(post("/api/v1/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.obj("tenant", owner.tenantSlug(), "email", owner.email())))
                .andExpect(status().isAccepted());
        // Unknown accounts get the same answer.
        mockMvc.perform(post("/api/v1/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.obj("tenant", owner.tenantSlug(), "email", "ghost@nowhere.test")))
                .andExpect(status().isAccepted());

        String token = mail.lastTokenFor(owner.email());
        String resetBody = Json.obj("token", token, "newPassword", "Reset-Passw0rd-123");
        mockMvc.perform(post("/api/v1/auth/password/reset").contentType(MediaType.APPLICATION_JSON).content(resetBody))
                .andExpect(status().isOk());
        // Single use.
        mockMvc.perform(post("/api/v1/auth/password/reset").contentType(MediaType.APPLICATION_JSON).content(resetBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("TOKEN_INVALID"));

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", owner.bearer()))
                .andExpect(status().isUnauthorized());
        TestAuth.login(mockMvc, owner.tenantSlug(), owner.email(), "Reset-Passw0rd-123");
    }

    @Test
    void invitedUserAcceptsInvitationAndSignsIn() throws Exception {
        Session admin = TestAuth.register(mockMvc);
        String salesRoleId = roleId(admin, "Sales User");
        String inviteeEmail = "rahul@" + admin.tenantSlug() + ".test";

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.obj("email", inviteeEmail, "firstName", "Rahul", "lastName", "Sharma",
                                "roleId", salesRoleId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("INVITED"))
                .andExpect(jsonPath("$.data.roleName").value("Sales User"));

        // Invited users cannot sign in before accepting.
        loginExpect(admin.tenantSlug(), inviteeEmail, TestAuth.PASSWORD, 401, "INVALID_CREDENTIALS");

        String token = mail.lastTokenFor(inviteeEmail);
        MvcResult accepted = mockMvc.perform(post("/api/v1/auth/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.obj("token", token, "password", "Welcome-Passw0rd-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.role.name").value("Sales User"))
                .andExpect(jsonPath("$.data.user.administrator").value(false))
                .andReturn();
        Session invitee = TestAuth.session(accepted, admin.tenantSlug(), inviteeEmail);

        mockMvc.perform(get("/api/v1/users").header("Authorization", invitee.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
        TestAuth.login(mockMvc, admin.tenantSlug(), inviteeEmail, "Welcome-Passw0rd-1");
    }

    @Test
    void totpTwoFactorAuthentication() throws Exception {
        Session owner = TestAuth.register(mockMvc);

        MvcResult setup = mockMvc.perform(post("/api/v1/auth/mfa/setup").header("Authorization", owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.otpauthUri").value(org.hamcrest.Matchers.startsWith("otpauth://totp/")))
                .andReturn();
        String secret = Json.read(setup, "$.data.secret");
        long step = Instant.now().getEpochSecond() / Totp.STEP_SECONDS;

        mockMvc.perform(post("/api/v1/auth/mfa/enable")
                        .header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.obj("code", Totp.generate(Base32.decode(secret), step))))
                .andExpect(status().isOk());

        MvcResult challenge = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.obj("tenant", owner.tenantSlug(), "email", owner.email(),
                                "password", TestAuth.PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mfaRequired").value(true))
                .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                .andReturn();
        String mfaToken = Json.read(challenge, "$.data.mfaToken");

        // The code used for enrolment cannot be replayed.
        mockMvc.perform(post("/api/v1/auth/login/mfa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.obj("mfaToken", mfaToken, "code", Totp.generate(Base32.decode(secret), step))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_MFA_CODE"));

        mockMvc.perform(post("/api/v1/auth/login/mfa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.obj("mfaToken", mfaToken,
                                "code", Totp.generate(Base32.decode(secret), step + 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.mfaEnabled").value(true));
    }

    @Test
    void userCanListAndRevokeOwnSessions() throws Exception {
        Session first = TestAuth.register(mockMvc);
        Session second = TestAuth.login(mockMvc, first.tenantSlug(), first.email(), TestAuth.PASSWORD);

        MvcResult list = mockMvc.perform(get("/api/v1/auth/sessions").header("Authorization", first.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andReturn();
        java.util.List<String> otherIds = JsonPathIds.nonCurrent(list);
        assertThat(otherIds).hasSize(1);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/auth/sessions/" + otherIds.get(0))
                        .header("Authorization", first.bearer()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", second.bearer()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginReturnsRefreshTokenInBodyAndBodyClientsCanRefreshAndLogout() throws Exception {
        Session owner = TestAuth.register(mockMvc);
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.obj("tenant", owner.tenantSlug(), "email", owner.email(),
                                "password", TestAuth.PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andReturn();
        String bodyToken = Json.read(login, "$.data.refreshToken");
        assertThat(bodyToken).isEqualTo(login.getResponse().getCookie(TestAuth.REFRESH_COOKIE).getValue());

        // A cookie-less client (mobile, Postman) refreshes with the body token; no CSRF header needed.
        MvcResult rotated = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON).content(Json.obj("refreshToken", bodyToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();
        String nextToken = Json.read(rotated, "$.data.refreshToken");
        assertThat(nextToken).isNotBlank().isNotEqualTo(bodyToken);

        // Rotation still applies: the old token is dead and replaying it revokes the session.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON).content(Json.obj("refreshToken", bodyToken)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON).content(Json.obj("refreshToken", nextToken)))
                .andExpect(status().isUnauthorized());

        // Logout with a body token revokes that session.
        MvcResult second = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.obj("tenant", owner.tenantSlug(), "email", owner.email(),
                                "password", TestAuth.PASSWORD)))
                .andExpect(status().isOk()).andReturn();
        String secondToken = Json.read(second, "$.data.refreshToken");
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON).content(Json.obj("refreshToken", secondToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON).content(Json.obj("refreshToken", secondToken)))
                .andExpect(status().isUnauthorized());
    }

    private void loginExpect(String tenant, String email, String password, int status, String code) throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.obj("tenant", tenant, "email", email, "password", password)))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.error.code").value(code));
    }

    private static MockHttpServletRequestBuilder refresh(Cookie cookie) {
        return post("/api/v1/auth/refresh").header("X-Requested-With", "XMLHttpRequest").cookie(cookie);
    }

    private String roleId(Session admin, String roleName) throws Exception {
        MvcResult roles = mockMvc.perform(get("/api/v1/roles").header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andReturn();
        java.util.List<String> ids = Json.read(roles, "$.data[?(@.name == '" + roleName + "')].id");
        return ids.get(0);
    }

    /**
     * JsonPath helpers used by the session management test.
     *
     * <p>Purpose: extract the ids of the sessions that are not the current one from a session list response.
     */
    private static final class JsonPathIds {
        static java.util.List<String> nonCurrent(MvcResult result) {
            return Json.read(result, "$.data[?(@.current == false)].id");
        }
    }
}
