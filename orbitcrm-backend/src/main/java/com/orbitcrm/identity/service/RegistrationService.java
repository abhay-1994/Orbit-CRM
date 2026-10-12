package com.orbitcrm.identity.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.orbitcrm.identity.dto.AuthTokens;
import com.orbitcrm.identity.dto.ClientInfo;
import com.orbitcrm.identity.dto.IssuedSession;
import com.orbitcrm.identity.dto.RegisterRequest;
import com.orbitcrm.identity.entity.Role;
import com.orbitcrm.identity.entity.User;
import com.orbitcrm.identity.mapper.MeAssembler;
import com.orbitcrm.identity.repository.RoleRepository;
import com.orbitcrm.identity.repository.UserRepository;
import com.orbitcrm.identity.security.JwtService;
import com.orbitcrm.identity.security.LoginThrottle;
import com.orbitcrm.identity.security.PrincipalService;
import com.orbitcrm.identity.util.PasswordPolicy;
import com.orbitcrm.shared.tenant.TenantContext;
import com.orbitcrm.shared.util.UuidV7;
import com.orbitcrm.tenant.dto.NewTenant;
import com.orbitcrm.tenant.service.TenantService;

/**
 * Self-service signup: creates a new workspace and its first administrator.
 *
 * <p>Purpose: onboard a new customer in a single request. Within one transaction, bound to the new tenant id, it:
 * <ol>
 *   <li>validates the password policy and applies the IP throttle;</li>
 *   <li>creates the tenant, which publishes {@code TenantCreatedEvent} so every module seeds its defaults (roles,
 *   picklists, pipeline, views);</li>
 *   <li>creates the owner as an active user with the seeded Administrator role;</li>
 *   <li>opens a login session.</li>
 * </ol>
 * Any failure rolls everything back, so no half-created workspace remains. The caller receives tokens and is
 * signed in immediately.
 *
 * @author Abhay
 */
@Service
public class RegistrationService {

    private final TenantService tenants;
    private final RoleRepository roles;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessions;
    private final JwtService jwt;
    private final PrincipalService principals;
    private final MeAssembler meAssembler;
    private final LoginThrottle throttle;
    private final TransactionTemplate tx;

    RegistrationService(TenantService tenants, RoleRepository roles, UserRepository users,
                        PasswordEncoder passwordEncoder, SessionService sessions, JwtService jwt,
                        PrincipalService principals, MeAssembler meAssembler, LoginThrottle throttle,
                        PlatformTransactionManager txManager) {
        this.tenants = tenants;
        this.roles = roles;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.sessions = sessions;
        this.jwt = jwt;
        this.principals = principals;
        this.meAssembler = meAssembler;
        this.throttle = throttle;
        this.tx = new TransactionTemplate(txManager);
    }

    public AuthTokens register(RegisterRequest request, ClientInfo client) {
        throttle.check(client.ipAddress());
        PasswordPolicy.validate(request.password(), request.email(), "password");
        UUID tenantId = UuidV7.generate();

        IssuedSession session = TenantContext.callAs(tenantId, () -> tx.execute(status -> {
            tenants.create(new NewTenant(tenantId, request.tenantSlug(), request.companyName(),
                    request.timezone(), request.currency(), request.locale()));
            Role administrator = roles.findFirstByAdministratorTrueAndSystemTrue()
                    .orElseThrow(() -> new IllegalStateException("Administrator role was not seeded"));
            User owner = new User(request.email(), request.firstName(), request.lastName(),
                    administrator.getId(), null, User.Status.ACTIVE);
            Instant now = Instant.now();
            owner.setPassword(passwordEncoder.encode(request.password()), now);
            owner.recordSuccessfulLogin(now);
            users.save(owner);
            return sessions.create(tenantId, owner.getId(), client);
        }));

        var principal = principals.load(tenantId, session.userId(), session.sessionId());
        var me = TenantContext.callAs(tenantId, () -> meAssembler.assemble(principal));
        return new AuthTokens(jwt.issueAccessToken(tenantId, session.userId(), session.sessionId()), session, me);
    }
}
