package com.orbitcrm.identity.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.orbitcrm.identity.entity.AuthSession;

/**
 * Spring Data repository for {@link com.orbitcrm.identity.entity.AuthSession}.
 *
 * <p>Purpose: list a user's active sessions and revoke sessions in bulk: all sessions of a user (password reset,
 * user disabled) or all except the current one (password change). It also returns the ids of unrevoked sessions so
 * the principal cache can be evicted for each of them.
 *
 * @author Abhay
 */
public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

    @Query("""
            select s from AuthSession s
            where s.tenantId = :tenantId and s.userId = :userId
              and s.revokedAt is null and s.expiresAt > :now
            order by s.lastUsedAt desc
            """)
    List<AuthSession> findActive(UUID tenantId, UUID userId, Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update AuthSession s set s.revokedAt = :now, s.revokedReason = :reason
            where s.tenantId = :tenantId and s.userId = :userId and s.revokedAt is null
            """)
    int revokeAll(UUID tenantId, UUID userId, Instant now, String reason);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update AuthSession s set s.revokedAt = :now, s.revokedReason = :reason
            where s.tenantId = :tenantId and s.userId = :userId and s.revokedAt is null and s.id <> :keepId
            """)
    int revokeAllExcept(UUID tenantId, UUID userId, UUID keepId, Instant now, String reason);

    @Query("""
            select s.id from AuthSession s
            where s.tenantId = :tenantId and s.userId = :userId and s.revokedAt is null
            """)
    List<UUID> findUnrevokedIds(UUID tenantId, UUID userId);
}
