package com.orbitcrm.identity.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.orbitcrm.identity.entity.UserToken;

/**
 * Spring Data repository for {@link com.orbitcrm.identity.entity.UserToken}.
 *
 * <p>Purpose: find emailed tokens by hash (with a row lock when consuming them, so a link cannot be used twice
 * concurrently) and invalidate a user's outstanding tokens of a given purpose when a new one is issued.
 *
 * @author Abhay
 */
public interface UserTokenRepository extends JpaRepository<UserToken, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from UserToken t where t.tokenHash = :hash")
    Optional<UserToken> findByHashForUpdate(String hash);

    @Query("select t from UserToken t where t.tokenHash = :hash")
    Optional<UserToken> findByHash(String hash);

    @Modifying(flushAutomatically = true)
    @Query("""
            update UserToken t set t.usedAt = :now
            where t.tenantId = :tenantId and t.userId = :userId and t.purpose = :purpose and t.usedAt is null
            """)
    int invalidateOutstanding(UUID tenantId, UUID userId, UserToken.Purpose purpose, Instant now);
}
