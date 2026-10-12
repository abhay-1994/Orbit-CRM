package com.orbitcrm.deal.config;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.orbitcrm.deal.dto.PipelineView;
import com.orbitcrm.deal.entity.Pipeline;
import com.orbitcrm.deal.entity.PipelineStage;
import com.orbitcrm.deal.service.impl.PipelineServiceImpl;
import com.orbitcrm.metadata.dto.ReferenceOption;
import com.orbitcrm.metadata.service.ReferenceResolver;

/**
 * Provides the {@code ReferenceResolver}s for the deal fields {@code pipelineId} and {@code stageId}.
 *
 * <p>Purpose: let the generic record engine display, validate and filter by pipelines and stages without knowing the
 * deal module: it resolves ids to names (lists, exports, search, aggregates), checks that a pipeline or stage exists
 * in the current tenant, and lists options for filter dropdowns - stages grouped by their pipeline.
 *
 * @author Abhay
 */
@Configuration(proxyBeanMethods = false)
public class PipelineReferenceResolvers {

    @Bean
    ReferenceResolver pipelineReferenceResolver(PipelineServiceImpl pipelines) {
        return new ReferenceResolver() {
            @Override
            public String type() {
                return DealModule.REF_PIPELINE;
            }

            @Override
            public Map<UUID, String> labels(Collection<UUID> ids) {
                return pipelines.pipelinesById(new HashSet<>(ids)).stream()
                        .collect(Collectors.toMap(Pipeline::getId, Pipeline::getName));
            }

            @Override
            public boolean exists(UUID id) {
                return !pipelines.pipelinesById(java.util.Set.of(id)).isEmpty();
            }

            @Override
            public List<ReferenceOption> options() {
                return pipelines.list().stream().map(p -> new ReferenceOption(p.id(), p.name(), null)).toList();
            }
        };
    }

    @Bean
    ReferenceResolver stageReferenceResolver(PipelineServiceImpl pipelines) {
        return new ReferenceResolver() {
            @Override
            public String type() {
                return DealModule.REF_STAGE;
            }

            @Override
            public Map<UUID, String> labels(Collection<UUID> ids) {
                return pipelines.stagesById(new HashSet<>(ids)).stream()
                        .collect(Collectors.toMap(PipelineStage::getId, PipelineStage::getName));
            }

            @Override
            public boolean exists(UUID id) {
                return !pipelines.stagesById(java.util.Set.of(id)).isEmpty();
            }

            @Override
            public List<ReferenceOption> options() {
                return pipelines.list().stream()
                        .flatMap((PipelineView p) -> p.stages().stream()
                                .map(s -> new ReferenceOption(s.id(), s.name(), p.name())))
                        .toList();
            }
        };
    }
}
