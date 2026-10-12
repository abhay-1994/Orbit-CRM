package com.orbitcrm.identity.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.orbitcrm.identity.entity.User;

/**
 * Spring Data repository for {@link com.orbitcrm.identity.entity.User} (tenant-filtered by Hibernate).
 *
 * <p>Purpose: user lookups for authentication and administration: by email (login, invitation uniqueness), by ids
 * (display names), counts per role and team (deletion guards and member counts), active administrators (the "last
 * administrator" guard) and dynamic search via JPA {@code Specification}s for the user list and pickers.
 *
 * @author Abhay
 */
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    long countByRoleId(UUID roleId);

    long countByTeamId(UUID teamId);

    List<User> findAllByIdIn(Collection<UUID> ids);

    long countByRoleIdInAndStatus(Collection<UUID> roleIds, User.Status status);

    Page<User> findByStatus(User.Status status, Pageable pageable);
}
