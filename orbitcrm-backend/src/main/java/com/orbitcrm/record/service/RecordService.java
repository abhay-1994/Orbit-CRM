package com.orbitcrm.record.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.orbitcrm.record.dto.AggregateRequest;
import com.orbitcrm.record.dto.AggregateRow;
import com.orbitcrm.record.dto.BulkResult;
import com.orbitcrm.record.dto.RecordView;
import com.orbitcrm.record.dto.SearchRequest;
import com.orbitcrm.record.model.FilterNode;
import com.orbitcrm.record.model.WriteOptions;
import com.orbitcrm.shared.dto.PageResponse;

/**
 * The generic record engine: the public API for working with records of every module described by a
 * {@code ModuleDefinition}.
 *
 * <p>Purpose: provide all record operations once, for all modules - create, read, update, soft delete and restore,
 * search, count, bulk update and delete, validation dry runs, streaming for exports, titles for labels and grouped
 * aggregates. Each call:
 * <ul>
 *   <li>enforces the caller's permission and OWN / TEAM / ALL scope (invisible records are 404, visible but not
 *   permitted ones are 403);</li>
 *   <li>validates and converts input against field metadata (types, lengths, ranges, picklists, required fields,
 *   active users, visible lookup targets);</li>
 *   <li>runs the module's {@code RecordHooks};</li>
 *   <li>writes the audit log and the search index in the same transaction;</li>
 *   <li>publishes the domain events in {@code com.orbitcrm.record.event}.</li>
 * </ul>
 * Used by the module controllers, lead conversion, import/export, dashboards, the pipeline board, notes and
 * attachments (via {@link #requireAccess}).
 *
 * @author Abhay
 */
public interface RecordService {

    RecordView create(String module, Map<String, Object> input);

    RecordView create(String module, Map<String, Object> input, WriteOptions options);

    RecordView get(String module, UUID id);

    Optional<RecordView> find(String module, UUID id);

    /**
     * Returns the record if the caller may perform {@code action} on it (404 if invisible, 403 if visible
     * but not permitted). Used to guard sub-resources such as notes and attachments.
     */
    RecordView requireAccess(String module, UUID id, com.orbitcrm.shared.security.Action action);

    /**
     * Partial update. {@code expectedVersion} enables optimistic locking (409 on mismatch); pass
     * {@code null} for last-write-wins.
     */
    RecordView update(String module, UUID id, Map<String, Object> input, Long expectedVersion);

    RecordView update(String module, UUID id, Map<String, Object> input, Long expectedVersion, WriteOptions options);

    void delete(String module, UUID id);

    RecordView restore(String module, UUID id);

    PageResponse<RecordView> search(String module, SearchRequest request);

    /** Counts records visible to the caller that match {@code filter} (may be null). */
    long count(String module, FilterNode filter);

    BulkResult bulkUpdate(String module, List<UUID> ids, Map<String, Object> values);

    BulkResult bulkDelete(String module, List<UUID> ids);

    /**
     * Dry run of {@link #create}: converts and validates input exactly as a create would (defaults, hooks,
     * required fields, references) and returns the problems without writing anything.
     */
    List<com.orbitcrm.shared.dto.FieldErrorDetail> validate(String module, Map<String, Object> input);

    /**
     * Pages through all records matching {@code request} using the scope of {@code scopeAction} (e.g. EXPORT),
     * handing each batch to {@code consumer}; stops after {@code maxRows}. Returns the number of rows streamed.
     */
    long stream(String module, SearchRequest request, com.orbitcrm.shared.security.Action scopeAction, int maxRows,
                java.util.function.Consumer<List<RecordView>> consumer);

    /** Display titles of records by id (for labels); ids that do not exist are omitted. */
    Map<UUID, String> titles(String module, Collection<UUID> ids);

    /**
     * Grouped aggregates over records visible to the caller (the reporting primitive behind the pipeline
     * board, dashboards and, later, the report builder).
     */
    List<AggregateRow> aggregate(String module, AggregateRequest request);
}
