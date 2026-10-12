package com.orbitcrm.metadata.dto;

import java.util.UUID;

/**
 * One selectable configuration entity offered by a {@code ReferenceResolver}, e.g. a pipeline or a stage.
 *
 * <p>Purpose: populate dropdowns and filter options of reference fields.
 *
 * @author Abhay
 * @param id    id of the entity
 * @param label display label
 * @param group optional group label, e.g. the pipeline a stage belongs to
 */
public record ReferenceOption(UUID id, String label, String group) {
}
