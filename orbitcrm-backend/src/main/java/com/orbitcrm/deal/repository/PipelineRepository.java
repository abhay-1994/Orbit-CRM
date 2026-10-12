package com.orbitcrm.deal.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.orbitcrm.deal.entity.Pipeline;

/**
 * Spring Data repository for {@link com.orbitcrm.deal.entity.Pipeline} (tenant-filtered by Hibernate).
 *
 * <p>Purpose: list pipelines in display order, find the default pipeline and enforce unique pipeline names
 * (case-insensitive).
 *
 * @author Abhay
 */
public interface PipelineRepository extends JpaRepository<Pipeline, UUID> {

    List<Pipeline> findAllByOrderByPositionAscNameAsc();

    Optional<Pipeline> findFirstByIsDefaultTrue();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
}
