package com.orbitcrm.identity.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

import com.orbitcrm.shared.dto.ApiErrorResponse;
import com.orbitcrm.shared.web.RequestIdFilter;

/**
 * Writes 401 and 403 responses raised inside the Spring Security filter chain in the standard error envelope.
 *
 * <p>Purpose: make authentication and authorization failures look like every other API error. The entry point
 * answers {@code 401 INVALID_TOKEN} when a token was presented but is invalid, expired or revoked, and
 * {@code 401 UNAUTHENTICATED} when no token was sent (with a {@code WWW-Authenticate: Bearer} header). The access
 * denied handler answers {@code 403 ACCESS_DENIED}. Both include the request id.
 *
 * @author Abhay
 */
@Component
public class JsonSecurityHandlers {

    private final JsonMapper jsonMapper;

    JsonSecurityHandlers(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public AuthenticationEntryPoint entryPoint() {
        return (request, response, ex) -> {
            boolean tokenPresented = ex instanceof InvalidBearerTokenException
                    || ex.getClass().getSimpleName().contains("OAuth2");
            response.setHeader("WWW-Authenticate", "Bearer");
            write(response, HttpServletResponse.SC_UNAUTHORIZED,
                    tokenPresented ? "INVALID_TOKEN" : "UNAUTHENTICATED",
                    tokenPresented ? "The access token is invalid or expired" : "Authentication is required");
        };
    }

    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, ex) ->
                write(response, HttpServletResponse.SC_FORBIDDEN, "ACCESS_DENIED", "Access is denied");
    }

    private void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(),
                ApiErrorResponse.of(code, message, null, RequestIdFilter.currentRequestId()));
    }
}
