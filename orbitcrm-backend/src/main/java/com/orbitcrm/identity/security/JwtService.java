package com.orbitcrm.identity.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import com.orbitcrm.identity.dto.IssuedToken;
import com.orbitcrm.identity.dto.MfaChallenge;
import com.orbitcrm.shared.config.OrbitProperties;
import com.orbitcrm.shared.exception.ApiException;

/**
 * Issues and validates the short-lived HS256 JWTs used by the API.
 *
 * <p>Purpose: the single place where tokens are signed and verified. Two token types exist:
 * <ul>
 *   <li>{@code typ=access} - API access token (15 minutes by default) with {@code sub}=user id,
 *   {@code tid}=tenant id and {@code sid}=session id;</li>
 *   <li>{@code typ=mfa} - proof that the password step of a login succeeded (5 minutes by default), exchanged
 *   together with a TOTP code for real tokens.</li>
 * </ul>
 * Each decoder checks signature, expiry, issuer and the token type, so an MFA token can never be used as an access
 * token. Permissions are deliberately not embedded in the token: they are resolved server-side on each request so
 * role changes apply immediately. The secret must be at least 256 bits, otherwise startup fails.
 *
 * @author Abhay
 */
@Component
public class JwtService {

    public static final String CLAIM_TENANT = "tid";
    public static final String CLAIM_SESSION = "sid";
    public static final String CLAIM_TYPE = "typ";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_MFA = "mfa";

    private final JwtEncoder encoder;
    private final JwtDecoder accessDecoder;
    private final JwtDecoder mfaDecoder;
    private final String issuer;
    private final Duration accessTtl;
    private final Duration mfaTtl;

    public JwtService(OrbitProperties properties) {
        OrbitProperties.Jwt jwt = properties.security().jwt();
        SecretKey key = signingKey(jwt.secret());
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        this.issuer = jwt.issuer();
        this.accessTtl = jwt.accessTokenTtl();
        this.mfaTtl = jwt.mfaTokenTtl();
        this.accessDecoder = decoder(key, issuer, TYPE_ACCESS);
        this.mfaDecoder = decoder(key, issuer, TYPE_MFA);
    }

    public JwtDecoder accessTokenDecoder() {
        return accessDecoder;
    }

    public IssuedToken issueAccessToken(UUID tenantId, UUID userId, UUID sessionId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plus(accessTtl))
                .id(UUID.randomUUID().toString())
                .claim(CLAIM_TENANT, tenantId.toString())
                .claim(CLAIM_SESSION, sessionId.toString())
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .build();
        return new IssuedToken(encode(claims), accessTtl.toSeconds());
    }

    public String issueMfaToken(UUID tenantId, UUID userId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plus(mfaTtl))
                .claim(CLAIM_TENANT, tenantId.toString())
                .claim(CLAIM_TYPE, TYPE_MFA)
                .build();
        return encode(claims);
    }

    /** Returns (tenantId, userId) from a valid MFA challenge token or throws 401. */
    public MfaChallenge verifyMfaToken(String token) {
        try {
            Jwt jwt = mfaDecoder.decode(token);
            return new MfaChallenge(UUID.fromString(jwt.getClaimAsString(CLAIM_TENANT)),
                    UUID.fromString(jwt.getSubject()));
        } catch (JwtException | IllegalArgumentException ex) {
            throw ApiException.unauthorized("MFA_TOKEN_INVALID", "The sign-in attempt expired. Please sign in again.");
        }
    }

    private String encode(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static SecretKey signingKey(String base64Secret) {
        byte[] bytes = Base64.getDecoder().decode(base64Secret);
        if (bytes.length < 32) {
            throw new IllegalStateException("orbitcrm.security.jwt.secret must be at least 256 bits (base64)");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    private static JwtDecoder decoder(SecretKey key, String issuer, String expectedType) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> typeValidator = jwt -> expectedType.equals(jwt.getClaimAsString(CLAIM_TYPE))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Wrong token type", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer), typeValidator));
        return decoder;
    }
}
