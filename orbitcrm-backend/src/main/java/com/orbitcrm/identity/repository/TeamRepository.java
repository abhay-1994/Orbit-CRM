package com.orbitcrm.identity.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.orbitcrm.identity.entity.Team;

/**
 * Spring Data repository for {@link com.orbitcrm.identity.entity.Team} (tenant-filtered by Hibernate).
 *
 * <p>Purpose: team lookups for administration: ordered listing, case-insensitive name uniqueness checks and
 * detection of sub-teams before deletion.
 *
 * @author Abhay
 */
public interface TeamRepository extends JpaRepository<Team, UUID> {

    List<Team> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    boolean existsByParentId(UUID parentId);
}
