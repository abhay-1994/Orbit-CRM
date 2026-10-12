package com.orbitcrm.deal.entity;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.orbitcrm.deal.model.StageType;
import com.orbitcrm.shared.entity.TenantScopedEntity;

/**
 * JPA entity for the {@code pipeline_stages} table: one stage of a pipeline.
 *
 * <p>Purpose: define a step in the sales process with its order, default win probability and type. The type drives
 * deal status: moving a deal into a {@code WON} or {@code LOST} stage closes it, moving it back into an
 * {@code OPEN} stage reopens it. Stages used by deals cannot be deleted, only deactivated.
 *
 * @author Abhay
 */
@Entity
@Table(name = "pipeline_stages")
public class PipelineStage extends TenantScopedEntity {

    @Column(name = "pipeline_id", nullable = false, updatable = false)
    private UUID pipelineId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal probability;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage_type", nullable = false)
    private StageType stageType;

    @Column(nullable = false)
    private boolean active = true;

    protected PipelineStage() {
    }

    public PipelineStage(UUID pipelineId, String name, int position, BigDecimal probability, StageType stageType) {
        this.pipelineId = pipelineId;
        this.name = name;
        this.position = position;
        this.probability = probability;
        this.stageType = stageType;
    }

    public UUID getPipelineId() {
        return pipelineId;
    }

    public String getName() {
        return name;
    }

    public int getPosition() {
        return position;
    }

    public BigDecimal getProbability() {
        return probability;
    }

    public StageType getStageType() {
        return stageType;
    }

    public boolean isActive() {
        return active;
    }

    public void update(String name, int position, BigDecimal probability, StageType stageType, boolean active) {
        this.name = name;
        this.position = position;
        this.probability = probability;
        this.stageType = stageType;
        this.active = active;
    }
}
