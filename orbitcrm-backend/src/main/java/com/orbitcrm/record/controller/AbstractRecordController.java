package com.orbitcrm.record.controller;

import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import com.orbitcrm.audit.dto.AuditLogView;
import com.orbitcrm.audit.service.AuditService;
import com.orbitcrm.record.dto.BulkDeleteRequest;
import com.orbitcrm.record.dto.BulkResult;
import com.orbitcrm.record.dto.BulkUpdateRequest;
import com.orbitcrm.record.dto.RecordView;
import com.orbitcrm.record.dto.SearchRequest;
import com.orbitcrm.record.service.RecordService;
import com.orbitcrm.shared.dto.ApiResponse;
import com.orbitcrm.shared.dto.PageParams;
import com.orbitcrm.shared.dto.PageResponse;
import com.orbitcrm.shared.exception.ApiException;

/**
 * Base class that gives every record module its standard REST API (PRD section 53).
 *
 * <p>Purpose: avoid writing the same CRUD controller for leads, contacts, companies, deals and activities. A module
 * controller extends this class, adds {@code @RestController @RequestMapping("/api/v1/<module>")}, passes its module
 * key to the constructor and optionally adds module-specific endpoints. All endpoints delegate to the generic
 * {@link com.orbitcrm.record.service.RecordService}, which enforces permissions and scope.
 * <ul>
 *   <li>{@code GET /} - list with quick search {@code q}, repeatable {@code sort=field,dir}, {@code page} and
 *   {@code size}.</li>
 *   <li>{@code POST /search} - list with the full filter language, sort and paging in the body.</li>
 *   <li>{@code POST /}, {@code GET /{id}}, {@code PATCH /{id}}, {@code DELETE /{id}} - CRUD. Responses carry an
 *   {@code ETag} with the record version; PATCH is a partial update that honours {@code If-Match} or a
 *   {@code version} field in the body for optimistic locking (409 on conflict). DELETE moves the record to the
 *   recycle bin (soft delete).</li>
 *   <li>{@code POST /{id}/restore} - restore a deleted record.</li>
 *   <li>{@code POST /bulk-update} and {@code POST /bulk-delete} - apply to up to 200 records, reporting per-record
 *   failures.</li>
 *   <li>{@code GET /{id}/history} - field-level change history from the audit log (requires read access to the
 *   record).</li>
 * </ul>
 *
 * @author Abhay
 */
public abstract class AbstractRecordController {

    protected final String moduleKey;
    protected final RecordService records;
    protected final AuditService audit;

    protected AbstractRecordController(String moduleKey, RecordService records, AuditService audit) {
        this.moduleKey = moduleKey;
        this.records = records;
        this.audit = audit;
    }

    @GetMapping
    @Operation(summary = "List records (quick search, sort, pagination)",
            description = "sort is repeatable: ?sort=amount,desc&sort=name,asc")
    public ApiResponse<PageResponse<RecordView>> list(@RequestParam(required = false) String q,
                                                      @RequestParam(required = false) Integer page,
                                                      @RequestParam(required = false) Integer size,
                                                      HttpServletRequest request) {
        // Read raw values: Spring would split a single "field,dir" value into two list items.
        String[] rawSorts = request.getParameterValues("sort");
        List<SearchRequest.Sort> sorts = rawSorts == null ? null
                : Arrays.stream(rawSorts).map(SearchRequest.Sort::parse).toList();
        return ApiResponse.ok(records.search(moduleKey, new SearchRequest(null, q, sorts, page, size)));
    }

    @PostMapping("/search")
    @Operation(summary = "Search records with the filter language",
            description = "Body: {filter:{and:[{field,op,value}]}, q, sort:[{field,direction}], page, size}")
    public ApiResponse<PageResponse<RecordView>> search(@RequestBody SearchRequest request) {
        return ApiResponse.ok(records.search(moduleKey, request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a record")
    public ResponseEntity<ApiResponse<RecordView>> get(@PathVariable UUID id) {
        RecordView record = records.get(moduleKey, id);
        return ResponseEntity.ok().eTag(etag(record)).body(ApiResponse.ok(record));
    }

    @PostMapping
    @Operation(summary = "Create a record", description = "Body: a JSON object of field values")
    public ResponseEntity<ApiResponse<RecordView>> create(@RequestBody Map<String, Object> values) {
        RecordView created = records.create(moduleKey, values);
        return ResponseEntity.created(URI.create("/api/v1/" + moduleKey + "/" + created.id()))
                .eTag(etag(created))
                .body(ApiResponse.ok(created, singular() + " created successfully"));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update some fields of a record")
    public ResponseEntity<ApiResponse<RecordView>> update(@PathVariable UUID id,
                                                          @RequestBody Map<String, Object> values,
                                                          @RequestHeader(value = HttpHeaders.IF_MATCH, required = false)
                                                          String ifMatch) {
        Map<String, Object> input = new LinkedHashMap<>(values);
        Long version = expectedVersion(ifMatch, input.remove("version"));
        RecordView updated = records.update(moduleKey, id, input, version);
        return ResponseEntity.ok().eTag(etag(updated)).body(ApiResponse.ok(updated, singular() + " updated successfully"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a record (moves it to the recycle bin)")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        records.delete(moduleKey, id);
        return ApiResponse.message(singular() + " deleted");
    }

    @PostMapping("/{id}/restore")
    @Operation(summary = "Restore a deleted record")
    public ApiResponse<RecordView> restore(@PathVariable UUID id) {
        return ApiResponse.ok(records.restore(moduleKey, id), singular() + " restored");
    }

    @PostMapping("/bulk-update")
    @Operation(summary = "Apply the same field values to many records (e.g. assign owner)")
    public ApiResponse<BulkResult> bulkUpdate(@Valid @RequestBody BulkUpdateRequest request) {
        return ApiResponse.ok(records.bulkUpdate(moduleKey, request.ids(), request.values()));
    }

    @PostMapping("/bulk-delete")
    @Operation(summary = "Delete many records")
    public ApiResponse<BulkResult> bulkDelete(@Valid @RequestBody BulkDeleteRequest request) {
        return ApiResponse.ok(records.bulkDelete(moduleKey, request.ids()));
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Field-level change history of a record")
    public ApiResponse<PageResponse<AuditLogView>> history(@PathVariable UUID id,
                                                           @RequestParam(required = false) Integer page,
                                                           @RequestParam(required = false) Integer size) {
        records.get(moduleKey, id); // enforces read access
        return ApiResponse.ok(audit.forRecord(moduleKey, id, PageParams.of(page, size)));
    }

    protected String singular() {
        return moduleKey;
    }

    private static String etag(RecordView record) {
        return "\"" + record.version() + "\"";
    }

    private static Long expectedVersion(String ifMatch, Object bodyVersion) {
        String raw = ifMatch != null ? ifMatch.replace("W/", "").replace("\"", "").trim()
                : bodyVersion == null ? null : bodyVersion.toString();
        if (raw == null || raw.isEmpty() || raw.equals("*")) {
            return null;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            throw ApiException.validation("version", "INVALID", "version must be a number");
        }
    }
}
