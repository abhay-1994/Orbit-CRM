package com.orbitcrm.deal.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.orbitcrm.deal.entity.PipelineStage;

/**
 * Spring Data repository for {@link com.orbitcrm.deal.entity.PipelineStage} (tenant-filtered by Hibernate).
 *
 * <p>Purpose: load the stages of one pipeline, of several pipelines or of all pipelines in stage order.
 *
 * @author Abhay
 */
public interface PipelineStageRepository extends JpaRepository<PipelineStage, UUID> {

    List<PipelineStage> findByPipelineIdOrderByPositionAsc(UUID pipelineId);

    List<PipelineStage> findByPipelineIdInOrderByPositionAsc(Collection<UUID> pipelineIds);

    List<PipelineStage> findAllByOrderByPositionAsc();
}
