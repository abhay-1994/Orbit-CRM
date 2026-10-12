package com.orbitcrm.identity.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import com.orbitcrm.shared.security.AccessControl;
import com.orbitcrm.shared.tenant.TenantContext;

/**
 * Servlet filter that binds the authenticated user's tenant to the request thread.
 *
 * <p>Purpose: after bearer-token authentication, put the user's tenant into
 * {@link com.orbitcrm.shared.tenant.TenantContext} (so Hibernate and PostgreSQL row-level security filter by it)
 * and the tenant and user ids into the logging MDC. It always clears both afterwards, so a pooled thread never
 * carries a previous request's tenant. Registered only inside the security filter chain.
 *
 * @author Abhay
 */
public class TenantContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        AccessControl.currentUser().ifPresent(user -> {
            TenantContext.bind(user.tenantId());
            MDC.put("tenantId", user.tenantId().toString());
            MDC.put("userId", user.userId().toString());
        });
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            MDC.remove("tenantId");
            MDC.remove("userId");
        }
    }
}
