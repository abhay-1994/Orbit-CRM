package com.orbitcrm.view.controller;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.orbitcrm.record.dto.RecordView;
import com.orbitcrm.shared.dto.ApiResponse;
import com.orbitcrm.shared.dto.PageResponse;
import com.orbitcrm.view.dto.UpdateViewRequest;
import com.orbitcrm.view.dto.ViewRequest;
import com.orbitcrm.view.dto.ViewResponse;
import com.orbitcrm.view.service.SavedViewService;

/**
 * REST controller for saved list views at {@code /api/v1/views}.
 *
 * <p>Purpose: let users save and reuse list configurations such as "My Open Deals" or "Closing This Month".
 * <ul>
 *   <li>{@code GET /?module=...} - built-in, shared and the user's own views for a module;</li>
 *   <li>{@code POST /} - save a view (filter language, sort and columns); {@code SHARED} views need the settings
 *   permission;</li>
 *   <li>{@code PATCH /{id}} and {@code DELETE /{id}} - change or remove a view (owner, or a user with settings
 *   permission; built-in views cannot be deleted);</li>
 *   <li>{@code GET /{id}/records} - run the view and return the matching records, with optional quick search and
 *   paging.</li>
 * </ul>
 *
 * @author Abhay
 */
@RestController
@RequestMapping("/api/v1/views")
@Tag(name = "Saved views", description = "Named filters such as 'My Open Deals'")
public class SavedViewController {

    private final SavedViewService views;

    SavedViewController(SavedViewService views) {
        this.views = views;
    }

    @GetMapping
    @Operation(summary = "Views of a module: built-in, shared and your own")
    ApiResponse<List<ViewResponse>> list(@RequestParam String module) {
        return ApiResponse.ok(views.list(module));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Save a view (filter language + sort + columns); SHARED needs settings permission")
    ApiResponse<ViewResponse> create(@Valid @RequestBody ViewRequest request) {
        return ApiResponse.ok(views.create(request), "View saved");
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update a view")
    ApiResponse<ViewResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateViewRequest request) {
        return ApiResponse.ok(views.update(id, request), "View updated");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a view")
    ApiResponse<Void> delete(@PathVariable UUID id) {
        views.delete(id);
        return ApiResponse.message("View deleted");
    }

    @GetMapping("/{id}/records")
    @Operation(summary = "Run a view and return the matching records")
    ApiResponse<PageResponse<RecordView>> records(@PathVariable UUID id, @RequestParam(required = false) String q,
                                                  @RequestParam(required = false) Integer page,
                                                  @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(views.run(id, q, page, size));
    }
}
