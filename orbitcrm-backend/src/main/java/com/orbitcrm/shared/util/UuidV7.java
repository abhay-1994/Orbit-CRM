package com.orbitcrm.shared.util;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Generator of time-ordered UUID version 7 values (RFC 9562).
 *
 * <p>Purpose: produce every primary key in the system. UUIDv7 values start with a millisecond timestamp, which
 * keeps B-tree indexes compact (inserts append at the end), while the random part makes ids impossible to
 * enumerate across tenants, unlike sequential numbers.
 *
 * @author Abhay
 */
public final class UuidV7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7() {
    }

    public static UUID generate() {
        long millis = System.currentTimeMillis();
        byte[] rand = new byte[10];
        RANDOM.nextBytes(rand);

        long msb = (millis & 0xFFFFFFFFFFFFL) << 16;
        msb |= 0x7000L;                                   // version 7
        msb |= ((rand[0] & 0x0FL) << 8) | (rand[1] & 0xFFL); // 12 bits rand_a

        long lsb = 0x8000000000000000L;                    // IETF variant (10xx)
        lsb |= (rand[2] & 0x3FL) << 56;
        for (int i = 3; i < 10; i++) {
            lsb |= (rand[i] & 0xFFL) << (8 * (9 - i));
        }
        return new UUID(msb, lsb);
    }
}
