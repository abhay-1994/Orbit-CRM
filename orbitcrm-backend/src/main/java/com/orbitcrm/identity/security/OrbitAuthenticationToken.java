package com.orbitcrm.identity.security;

import java.util.List;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import com.orbitcrm.shared.security.CurrentUser;

/**
 * Spring Security {@code Authentication} whose principal is the resolved {@link com.orbitcrm.shared.security.CurrentUser}.
 *
 * <p>Purpose: carry the authenticated user through the security context for the duration of a request, so
 * {@code AccessControl} and all business code can read it. The original JWT is kept as the credentials; authorities
 * are {@code ROLE_USER} plus {@code ROLE_ADMIN} for administrators.
 *
 * @author Abhay
 */
public class OrbitAuthenticationToken extends AbstractAuthenticationToken {

    private final CurrentUser user;
    private final Jwt jwt;

    public OrbitAuthenticationToken(CurrentUser user, Jwt jwt) {
        super(user.administrator()
                ? List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN"))
                : List.of(new SimpleGrantedAuthority("ROLE_USER")));
        this.user = user;
        this.jwt = jwt;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return jwt;
    }

    @Override
    public Object getPrincipal() {
        return user;
    }

    @Override
    public String getName() {
        return user.userId().toString();
    }
}
