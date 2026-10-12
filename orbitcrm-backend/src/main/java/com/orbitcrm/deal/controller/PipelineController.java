package com.orbitcrm.deal.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.orbitcrm.deal.dto.PipelineRequest;
import com.orbitcrm.deal.dto.PipelineView;
import com.orbitcrm.deal.service.impl.PipelineServiceImpl;
import com.orbitcrm.shared.dto.ApiResponse;
import com.orbitcrm.shared.security.AccessControl;
import com.orbitcrm.shared.security.Action;
import com.orbitcrm.shared.security.SystemResources;

/**
 * REST controller for sales pipelines and their stages at {@code /api/v1/pipelines}.
 *
 * <p>Purpose: let administrators model their sales process (PRD section 14).
 * <ul>
 *   <li>{@code GET /}, {@code GET /{id}} - pipelines with ordered stages; open to every signed-in user.</li>
 *   <li>{@code POST /} - create a pipeline; it needs at least one active open, one won and one lost stage.</li>
 *   <li>{@code PUT /{id}} - replace name, default and active flags and the ordered stages: stages with an id are
 *   updated, stages without an id are created, omitted stages are deleted (only if no deal uses them; deactivate them
 *   otherwise).</li>
 *   <li>{@code DELETE /{id}} - delete a pipeline that is not the default and has no deals.</li>
 * </ul>
 * Changes require the {@code settings UPDATE} permission.
 *
 * @author Abhay
 */
@RestController
@RequestMapping("/api/v1/pipelines")
@Tag(name = "Pipelines", description = "Sales pipelines and their stages (probability and open/won/lost type)")
public class PipelineController {

    private final PipelineServiceImpl pipelines;

    PipelineController(PipelineServiceImpl pipelines) {
        this.pipelines = pipelines;
    }

    @GetMapping
    @Operation(summary = "List pipelines with stages")
    ApiResponse<List<PipelineView>> list() {
        return ApiResponse.ok(pipelines.list());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a pipeline")
    ApiResponse<PipelineView> get(@PathVariable UUID id) {
        return ApiResponse.ok(pipelines.get(id));
    }

    @PostMapping
    @Operation(summary = "Create a pipeline (needs at least one open, one won and one lost stage)")
    ResponseEntity<ApiResponse<PipelineView>> create(@Valid @RequestBody PipelineRequest request) {
        AccessControl.require(SystemResources.SETTINGS, Action.UPDATE);
        PipelineView created = pipelines.create(request);
        return ResponseEntity.created(URI.create("/api/v1/pipelines/" + created.id()))
                .body(ApiResponse.ok(created, "Pipeline created"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace a pipeline's name, default flag and ordered stages",
            description = "Stages with an id are updated, without an id created; omitted stages are deleted "
                    + "(only if no deal uses them - deactivate instead)")
    ApiResponse<PipelineView> update(@PathVariable UUID id, @Valid @RequestBody PipelineRequest request) {
        AccessControl.require(SystemResources.SETTINGS, Action.UPDATE);
        return ApiResponse.ok(pipelines.update(id, request), "Pipeline updated");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an unused, non-default pipeline")
    ApiResponse<Void> delete(@PathVariable UUID id) {
        AccessControl.require(SystemResources.SETTINGS, Action.UPDATE);
        pipelines.delete(id);
        return ApiResponse.message("Pipeline deleted");
    }
}
