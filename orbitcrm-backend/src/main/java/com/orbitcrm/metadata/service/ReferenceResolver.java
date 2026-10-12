package com.orbitcrm.metadata.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.orbitcrm.metadata.dto.ReferenceOption;
import com.orbitcrm.metadata.model.FieldDefinition;
import com.orbitcrm.metadata.model.FieldType;

/**
 * SPI for {@link com.orbitcrm.metadata.model.FieldType#REFERENCE} fields.
 *
 * <p>Purpose: let the record engine treat configuration entities (pipelines and stages today; products or price books
 * later) as field values without knowing the modules that own them. An implementation resolves ids to labels
 * (display, export, search), validates that an id exists in the current tenant and lists options for dropdowns and
 * filters. Implementations are Spring beans, e.g. {@code PipelineReferenceResolvers} in the deal module.
 *
 * @author Abhay
 */
public interface ReferenceResolver {

    /** The reference type this resolver handles, matching {@link FieldDefinition#referenceType()}. */
    String type();

    Map<UUID, String> labels(Collection<UUID> ids);

    boolean exists(UUID id);

    List<ReferenceOption> options();
}
