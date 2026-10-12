package com.orbitcrm.identity.repository;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.orbitcrm.identity.entity.RefreshToken;

/**
 * Spring Data repository for {@link com.orbitcrm.identity.entity.RefreshToken}.
 *
 * <p>Purpose: find a refresh token by its hash with a pessimistic row lock ({@code SELECT ... FOR UPDATE}), which
 * serializes concurrent refreshes of the same token so that exactly one of them rotates it.
 *
 * @author Abhay
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /** Row lock serializes concurrent refreshes of the same token, so exactly one of them wins. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshToken t where t.tokenHash = :hash")
    Optional<RefreshToken> findByHashForUpdate(String hash);
}
