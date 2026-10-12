package com.orbitcrm.metadata.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.orbitcrm.metadata.entity.PicklistValue;

/**
 * Spring Data repository for {@link com.orbitcrm.metadata.entity.PicklistValue} (tenant-filtered by Hibernate).
 *
 * <p>Purpose: read a picklist's values in display order, list which picklists are already seeded for the tenant
 * (so only missing ones are added) and check whether a value key exists.
 *
 * @author Abhay
 */
public interface PicklistValueRepository extends JpaRepository<PicklistValue, UUID> {

    List<PicklistValue> findByPicklistKeyOrderByPositionAsc(String picklistKey);

    List<PicklistValue> findAllByOrderByPicklistKeyAscPositionAsc();

    @Query("select distinct p.picklistKey from PicklistValue p")
    List<String> findSeededKeys();

    boolean existsByPicklistKeyAndValueKey(String picklistKey, String valueKey);
}
