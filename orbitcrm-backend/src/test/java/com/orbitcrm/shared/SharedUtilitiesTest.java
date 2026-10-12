package com.orbitcrm.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.orbitcrm.shared.config.OrbitProperties;
import com.orbitcrm.shared.security.SecretCipher;
import com.orbitcrm.shared.security.Tokens;
import com.orbitcrm.shared.tenant.TenantContext;
import com.orbitcrm.shared.util.UuidV7;

/**
 * Unit tests of shared building blocks that need no Spring context.
 *
 * <p>Purpose: verify that
 * <ul>
 *   <li>{@code UuidV7} produces ids with the correct version and variant bits in time order;</li>
 *   <li>{@code SecretCipher} round-trips secrets and uses a fresh IV for every encryption;</li>
 *   <li>{@code Tokens} are random and hashed deterministically;</li>
 *   <li>{@code TenantContext} restores the previous tenant after {@code callAs} and rejects access when no tenant is
 *   bound.</li>
 * </ul>
 *
 * @author Abhay
 */
class SharedUtilitiesTest {

    @Test
    void uuidV7HasCorrectVersionVariantAndTimeOrder() throws InterruptedException {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            ids.add(UuidV7.generate());
            Thread.sleep(2);
        }
        ids.forEach(id -> {
            assertThat(id.version()).isEqualTo(7);
            assertThat(id.variant()).isEqualTo(2);
        });
        assertThat(ids).isSortedAccordingTo((a, b) -> Long.compareUnsigned(a.getMostSignificantBits(),
                b.getMostSignificantBits()));
    }

    @Test
    void secretCipherRoundTripsAndUsesFreshIvs() {
        OrbitProperties properties = new OrbitProperties(null,
                new OrbitProperties.Security(null, null, null, null,
                        "b3JiaXRjcm0tZGV2LWVuY3J5cHRpb24ta2V5LTMyYnk=", null),
                null, null, null);
        SecretCipher cipher = new SecretCipher(properties);
        String a = cipher.encrypt("JBSWY3DPEHPK3PXP");
        String b = cipher.encrypt("JBSWY3DPEHPK3PXP");
        assertThat(a).isNotEqualTo(b);
        assertThat(cipher.decrypt(a)).isEqualTo("JBSWY3DPEHPK3PXP");
    }

    @Test
    void tokensAreRandomAndHashedDeterministically() {
        String token = Tokens.newToken();
        assertThat(token).hasSize(43).isNotEqualTo(Tokens.newToken());
        assertThat(Tokens.sha256(token)).hasSize(64).isEqualTo(Tokens.sha256(token));
    }

    @Test
    void tenantContextRestoresPreviousValueAndRejectsMissingTenant() {
        UUID outer = UUID.randomUUID();
        UUID inner = UUID.randomUUID();
        TenantContext.runAs(outer, () -> {
            TenantContext.runAs(inner, () -> assertThat(TenantContext.requireTenantId()).isEqualTo(inner));
            assertThat(TenantContext.requireTenantId()).isEqualTo(outer);
        });
        assertThat(TenantContext.currentTenantId()).isEmpty();
        assertThatThrownBy(TenantContext::requireTenantId).isInstanceOf(IllegalStateException.class);
    }
}
