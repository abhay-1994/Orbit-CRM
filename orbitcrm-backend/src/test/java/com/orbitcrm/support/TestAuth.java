package com.orbitcrm.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import jakarta.servlet.http.Cookie;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Test helper that obtains real tokens through the public signup and login endpoints.
 *
 * <p>Purpose: create authenticated sessions the same way clients do, instead of forging tokens. It registers new
 * workspaces with unique slugs, logs users in, and returns a {@code Session} record with the access token, refresh
 * token (from the cookie), tenant and user ids, plus helpers for the {@code Authorization} header and the refresh
 * cookie.
 *
 * @author Abhay
 */
public final class TestAuth {

    public static final String PASSWORD = "Str0ng-Passw0rd!";
    public static final String REFRESH_COOKIE = "orbitcrm_refresh";

    private TestAuth() {
    }

    /**
     * An authenticated test session obtained through the real signup or login endpoints.
     *
     * <p>Purpose: give tests everything needed to call the API as a specific user: {@link #bearer()} builds the
     * {@code Authorization} header value and {@link #refreshCookie()} the refresh cookie.
     *
     * @param accessToken  JWT access token
     * @param refreshToken refresh token from the cookie
     * @param tenantSlug   workspace slug
     * @param tenantId     tenant id
     * @param userId       user id
     * @param email        user's email address
     */
    public record Session(String accessToken, String refreshToken, String tenantSlug, UUID tenantId, UUID userId,
                          String email) {

        public String bearer() {
            return "Bearer " + accessToken;
        }

        public Cookie refreshCookie() {
            return new Cookie(REFRESH_COOKIE, refreshToken);
        }
    }

    public static String uniqueSlug(String prefix) {
        return prefix + "-" + Long.toString(ThreadLocalRandom.current().nextLong(1L << 40), 36);
    }

    public static Session register(MockMvc mvc) throws Exception {
        String slug = uniqueSlug("acme");
        return register(mvc, slug, "owner@" + slug + ".test");
    }

    public static Session register(MockMvc mvc, String slug, String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.obj(
                                "companyName", "Acme " + slug,
                                "tenantSlug", slug,
                                "firstName", "Asha",
                                "lastName", "Owner",
                                "email", email,
                                "password", PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn();
        return session(result, slug, email);
    }

    public static Session login(MockMvc mvc, String slug, String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Json.obj("tenant", slug, "email", email, "password", password)))
                .andExpect(status().isOk())
                .andReturn();
        return session(result, slug, email);
    }

    public static Session session(MvcResult result, String slug, String email) {
        String accessToken = Json.read(result, "$.data.accessToken");
        String tenantId = Json.read(result, "$.data.user.tenant.id");
        String userId = Json.read(result, "$.data.user.id");
        Cookie cookie = result.getResponse().getCookie(REFRESH_COOKIE);
        return new Session(accessToken, cookie == null ? null : cookie.getValue(), slug, UUID.fromString(tenantId),
                UUID.fromString(userId), email);
    }
}
