package com.orbitcrm.identity.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.orbitcrm.identity.entity.Role;

/**
 * Spring Data repository for {@link com.orbitcrm.identity.entity.Role} (tenant-filtered by Hibernate).
 *
 * <p>Purpose: role lookups for administration and signup: case-insensitive lookup by name (to enforce unique names),
 * all roles ordered by name, and the seeded Administrator role that the first user of a new tenant receives.
 *
 * @author Abhay
 */
public interface RoleRepository extends JpaRepository<Role, UUID> {

    @Query("select r from Role r where lower(r.name) = lower(:name)")
    Optional<Role> findByNameIgnoreCase(String name);

    List<Role> findAllByOrderByNameAsc();

    Optional<Role> findFirstByAdministratorTrueAndSystemTrue();
}
