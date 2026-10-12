package com.orbitcrm.identity.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * Unit tests of {@link com.orbitcrm.identity.util.Totp} and {@link com.orbitcrm.identity.util.Base32}.
 *
 * <p>Purpose: guarantee compatibility with authenticator apps:
 * <ul>
 *   <li>generated codes match the RFC 6238 appendix B test vectors (HMAC-SHA1);</li>
 *   <li>verification accepts codes within the tolerance window, returns the matched time step (used for replay
 *   protection) and rejects codes outside the window, malformed codes and {@code null};</li>
 *   <li>base32 encoding round-trips and matches the RFC 4648 test vector.</li>
 * </ul>
 *
 * @author Abhay
 */
class TotpTest {

    /** RFC 6238 appendix B test secret for HMAC-SHA1. */
    private static final byte[] RFC_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Test
    void matchesRfc6238TestVectors() {
        // RFC values are 8 digits; a 6-digit TOTP is the same number modulo 10^6.
        assertThat(Totp.generate(RFC_SECRET, 59 / 30)).isEqualTo("287082");
        assertThat(Totp.generate(RFC_SECRET, 1111111109L / 30)).isEqualTo("081804");
        assertThat(Totp.generate(RFC_SECRET, 1234567890L / 30)).isEqualTo("005924");
        assertThat(Totp.generate(RFC_SECRET, 2000000000L / 30)).isEqualTo("279037");
    }

    @Test
    void verifiesWithinWindowAndReturnsMatchedStep() {
        String secret = Base32.encode(RFC_SECRET);
        long now = 1234567890L;
        long step = now / 30;
        assertThat(Totp.verify(secret, Totp.generate(RFC_SECRET, step - 1), now, 1)).hasValue(step - 1);
        assertThat(Totp.verify(secret, Totp.generate(RFC_SECRET, step + 2), now, 1)).isEmpty();
        assertThat(Totp.verify(secret, "12ab56", now, 1)).isEmpty();
        assertThat(Totp.verify(secret, null, now, 1)).isEmpty();
    }

    @Test
    void base32RoundTrips() {
        byte[] data = {0, 1, 2, (byte) 250, (byte) 255, 42, 17, 99, 100, 7};
        assertThat(Base32.decode(Base32.encode(data))).isEqualTo(data);
        assertThat(Base32.encode("foobar".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MZXW6YTBOI");
    }
}
