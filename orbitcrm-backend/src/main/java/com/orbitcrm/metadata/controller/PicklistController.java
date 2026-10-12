package com.orbitcrm.metadata.controller;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.orbitcrm.metadata.dto.NewPicklistValueRequest;
import com.orbitcrm.metadata.dto.PicklistResponse;
import com.orbitcrm.metadata.dto.PicklistValueView;
import com.orbitcrm.metadata.dto.ReorderPicklistRequest;
import com.orbitcrm.metadata.dto.UpdatePicklistValueRequest;
import com.orbitcrm.metadata.model.PicklistDefinition;
import com.orbitcrm.metadata.service.impl.PicklistServiceImpl;
import com.orbitcrm.shared.dto.ApiResponse;
import com.orbitcrm.shared.security.AccessControl;
import com.orbitcrm.shared.security.Action;
import com.orbitcrm.shared.security.SystemResources;

/**
 * REST controller for tenant-configurable dropdown values at {@code /api/v1/picklists}.
 *
 * <p>Purpose: let administrators tailor picklists such as lead source, lead status, industry or activity type to
 * their business (PRD: configurable dropdowns).
 * <ul>
 *   <li>{@code GET /} and {@code GET /{key}} - picklists with all values, including inactive ones; open to every
 *   signed-in user.</li>
 *   <li>{@code POST /{key}/values} - add a value.</li>
 *   <li>{@code PATCH /{key}/values/{valueId}} - rename, recolor, activate/deactivate or make default.</li>
 *   <li>{@code PUT /{key}/order} - reorder all values.</li>
 * </ul>
 * Changes require the {@code settings UPDATE} permission. Values are never deleted, only deactivated, so existing
 * records keep their data; records store the stable value key, so renaming a label never touches data.
 *
 * @author Abhay
 */
@RestController
@RequestMapping("/api/v1/picklists")
@Tag(name = "Picklists", description = "Tenant-configurable dropdown values (lead source, status, industry, ...)")
public class PicklistController {

    private final PicklistServiceImpl picklists;

    PicklistController(PicklistServiceImpl picklists) {
        this.picklists = picklists;
    }

    @GetMapping
    @Operation(summary = "List all picklists with their values")
    ApiResponse<List<PicklistResponse>> list() {
        return ApiResponse.ok(picklists.definitions().stream()
                .map(d -> new PicklistResponse(d.key(), d.label(), picklists.values(d.key())))
                .toList());
    }

    @GetMapping("/{key}")
    @Operation(summary = "Get one picklist")
    ApiResponse<PicklistResponse> get(@PathVariable String key) {
        PicklistDefinition definition = picklists.requireKnownPicklist(key);
        return ApiResponse.ok(new PicklistResponse(definition.key(), definition.label(), picklists.values(key)));
    }

    @PostMapping("/{key}/values")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a value")
    ApiResponse<PicklistValueView> add(@PathVariable String key, @Valid @RequestBody NewPicklistValueRequest request) {
        AccessControl.require(SystemResources.SETTINGS, Action.UPDATE);
        return ApiResponse.ok(picklists.addValue(key, request.label(), request.color(),
                Boolean.TRUE.equals(request.isDefault())), "Value added");
    }

    @PatchMapping("/{key}/values/{valueId}")
    @Operation(summary = "Rename, recolour, (de)activate or make default. Values are never deleted, so "
            + "existing records keep their data.")
    ApiResponse<PicklistValueView> update(@PathVariable String key, @PathVariable UUID valueId,
                                          @Valid @RequestBody UpdatePicklistValueRequest request) {
        AccessControl.require(SystemResources.SETTINGS, Action.UPDATE);
        return ApiResponse.ok(picklists.updateValue(key, valueId, request.label(), request.color(), request.active(),
                request.isDefault()), "Value updated");
    }

    @PutMapping("/{key}/order")
    @Operation(summary = "Reorder values")
    ApiResponse<List<PicklistValueView>> reorder(@PathVariable String key, @Valid @RequestBody ReorderPicklistRequest request) {
        AccessControl.require(SystemResources.SETTINGS, Action.UPDATE);
        return ApiResponse.ok(picklists.reorder(key, request.valueIds()));
    }
}
