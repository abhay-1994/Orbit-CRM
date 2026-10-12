package com.orbitcrm.deal.dto;

import java.util.List;
import java.util.UUID;

/**
 * The Kanban board of a pipeline, returned by the board endpoints.
 *
 * <p>Purpose: render the drag-and-drop pipeline view in one call.
 *
 * @author Abhay
 * @param pipelineId   the pipeline shown
 * @param pipelineName its name
 * @param columns      one column per active stage, in stage order
 */
public record PipelineBoard(UUID pipelineId, String pipelineName, List<BoardColumn> columns) {
}
