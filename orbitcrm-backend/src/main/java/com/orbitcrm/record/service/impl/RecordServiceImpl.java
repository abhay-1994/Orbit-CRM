package com.orbitcrm.record.service.impl;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.orbitcrm.audit.dto.AuditEntry;
import com.orbitcrm.audit.model.AuditAction;
import com.orbitcrm.audit.service.AuditService;
import com.orbitcrm.identity.service.UserDirectory;
import com.orbitcrm.metadata.model.FieldDefinition;
import com.orbitcrm.metadata.model.FieldType;
import com.orbitcrm.metadata.model.ModuleDefinition;
import com.orbitcrm.metadata.service.ModuleRegistry;
import com.orbitcrm.metadata.service.PicklistService;
import com.orbitcrm.metadata.service.ReferenceResolvers;
import com.orbitcrm.record.dto.AggregateRequest;
import com.orbitcrm.record.dto.AggregateRow;
import com.orbitcrm.record.dto.BulkFailure;
import com.orbitcrm.record.dto.BulkResult;
import com.orbitcrm.record.dto.Metric;
import com.orbitcrm.record.dto.RecordChange;
import com.orbitcrm.record.dto.RecordView;
import com.orbitcrm.record.dto.SearchRequest;
import com.orbitcrm.record.event.RecordCreated;
import com.orbitcrm.record.event.RecordDeleted;
import com.orbitcrm.record.event.RecordUpdated;
import com.orbitcrm.record.hook.RecordHooks;
import com.orbitcrm.record.hook.RecordHooks.HookContext;
import com.orbitcrm.record.mapper.RecordPresenter;
import com.orbitcrm.record.model.FilterNode;
import com.orbitcrm.record.model.WriteOptions;
import com.orbitcrm.record.query.ScopeSql;
import com.orbitcrm.record.query.SqlFilterCompiler;
import com.orbitcrm.record.repository.RecordRepository;
import com.orbitcrm.record.service.RecordService;
import com.orbitcrm.search.service.SearchIndexer;
import com.orbitcrm.shared.dto.FieldErrorDetail;
import com.orbitcrm.shared.dto.PageParams;
import com.orbitcrm.shared.dto.PageResponse;
import com.orbitcrm.shared.exception.ApiException;
import com.orbitcrm.shared.security.AccessControl;
import com.orbitcrm.shared.security.AccessScope;
import com.orbitcrm.shared.security.Action;
import com.orbitcrm.shared.security.CurrentUser;
import com.orbitcrm.tenant.service.TenantService;

/**
 * Implementation of {@link com.orbitcrm.record.service.RecordService}, the heart of the generic record engine.
 *
 * <p>Purpose: orchestrate every record operation for every module:
 * <ul>
 *   <li><b>Create</b>: check CREATE permission, convert input, apply defaults (owner = current user, default picklist
 *   values), run {@code beforeCreate} hooks, validate required fields and references, insert with the next record
 *   number, then write the audit entry (masking sensitive fields), update the search index, run {@code afterCreate}
 *   hooks and publish {@code RecordCreated}.</li>
 *   <li><b>Read</b>: every query combines tenant, "not deleted", the scope predicate from {@code ScopeSql}, the
 *   compiled filter and quick search (title, searchable text, exact record number, phone digits); sorting is
 *   validated against sortable fields with a stable tie-breaker.</li>
 *   <li><b>Update</b>: load the record with visibility and action scope checks, convert only the changed fields, run
 *   {@code beforeUpdate} hooks, drop no-op changes, apply optimistic locking, then diff, audit, re-index, run
 *   {@code afterUpdate} hooks and publish {@code RecordUpdated}.</li>
 *   <li><b>Delete / restore</b>: soft delete into the recycle bin and back, with audit and index maintenance.</li>
 *   <li><b>Bulk</b>: up to 200 records, each in its own transaction so one failure never rolls back the others.</li>
 *   <li><b>Aggregate</b>: validated group-by (up to two fields, date buckets in the tenant time zone) and metrics
 *   (up to six of count, sum, avg, min, max) with group labels resolved in batch.</li>
 *   <li><b>Stream</b>: pages through large result sets in batches of 500 for exports.</li>
 * </ul>
 *
 * @author Abhay
 */
@Service
public class RecordServiceImpl implements RecordService {

    private static final int MAX_BULK = 200;
    private static final Pattern RECORD_NUMBER = Pattern.compile("^([A-Za-z]+)-(\\d{1,18})$");

    private final ModuleRegistry registry;
    private final RecordRepository repository;
    private final FieldValueConverter converter;
    private final RecordPresenter presenter;
    private final PicklistService picklists;
    private final UserDirectory users;
    private final AuditService audit;
    private final SearchIndexer searchIndexer;
    private final TenantService tenants;
    private final ApplicationEventPublisher events;
    private final ReferenceResolvers references;
    private final Map<String, List<RecordHooks>> hooks;
    private final TransactionTemplate perRecordTx;

    RecordServiceImpl(ModuleRegistry registry, RecordRepository repository, FieldValueConverter converter,
                      RecordPresenter presenter, PicklistService picklists, UserDirectory users, AuditService audit,
                      SearchIndexer searchIndexer, TenantService tenants, ApplicationEventPublisher events,
                      ReferenceResolvers references, ObjectProvider<RecordHooks> hookProvider,
                      PlatformTransactionManager txManager) {
        this.registry = registry;
        this.repository = repository;
        this.converter = converter;
        this.presenter = presenter;
        this.picklists = picklists;
        this.users = users;
        this.audit = audit;
        this.searchIndexer = searchIndexer;
        this.tenants = tenants;
        this.events = events;
        this.references = references;
        Map<String, List<RecordHooks>> byModule = new HashMap<>();
        hookProvider.orderedStream().forEach(h -> byModule.computeIfAbsent(h.moduleKey(), k -> new ArrayList<>()).add(h));
        this.hooks = Map.copyOf(byModule);
        this.perRecordTx = new TransactionTemplate(txManager);
        this.perRecordTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // ================================================================== create

    @Override
    public RecordView create(String module, Map<String, Object> input) {
        return create(module, input, WriteOptions.API);
    }

    @Override
    @Transactional
    public RecordView create(String moduleKey, Map<String, Object> input, WriteOptions options) {
        ModuleDefinition module = registry.require(moduleKey);
        CurrentUser user = AccessControl.requireUser();
        AccessControl.require(moduleKey, Action.CREATE);

        List<FieldErrorDetail> errors = new ArrayList<>();
        Map<String, Object> values = converter.convert(module, input, null, options.allowReadOnly(), errors);
        applyDefaults(module, values, user);
        HookContext context = new HookContext(moduleKey, user, options);
        hooksFor(moduleKey).forEach(h -> h.beforeCreate(context, values));
        validateRequired(module, values, errors);
        validateReferences(module, values, Map.of(), errors);
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }

        Instant now = Instant.now();
        values.put(ModuleDefinition.CREATED_BY, user.userId());
        values.put(ModuleDefinition.UPDATED_BY, user.userId());
        values.put(ModuleDefinition.CREATED_AT, now);
        values.put(ModuleDefinition.UPDATED_AT, now);
        UUID id = com.orbitcrm.shared.util.UuidV7.generate();
        repository.insert(module, id, repository.nextRecordNumber(moduleKey), values);

        RecordView created = load(module, id);
        audit.record(AuditEntry.record(AuditAction.CREATE, moduleKey, id, created.title(), createChanges(module, created))
                .withMetadata(originMetadata(options)));
        searchIndexer.upsert(presenter.indexDocument(module, created));
        hooksFor(moduleKey).forEach(h -> h.afterCreate(context, created));
        events.publishEvent(new RecordCreated(user.tenantId(), moduleKey, id, created.title(),
                created.uuid(ModuleDefinition.OWNER), user.userId(), options.origin()));
        return created;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FieldErrorDetail> validate(String moduleKey, Map<String, Object> input) {
        ModuleDefinition module = registry.require(moduleKey);
        CurrentUser user = AccessControl.requireUser();
        AccessControl.require(moduleKey, Action.CREATE);
        List<FieldErrorDetail> errors = new ArrayList<>();
        Map<String, Object> values = converter.convert(module, input, null, false, errors);
        applyDefaults(module, values, user);
        try {
            HookContext context = new HookContext(moduleKey, user, WriteOptions.of(com.orbitcrm.record.model.ChangeOrigin.IMPORT));
            hooksFor(moduleKey).forEach(h -> h.beforeCreate(context, values));
        } catch (ApiException ex) {
            errors.addAll(ex.details().isEmpty()
                    ? List.of(new FieldErrorDetail(null, ex.code(), ex.getMessage())) : ex.details());
        }
        validateRequired(module, values, errors);
        validateReferences(module, values, Map.of(), errors);
        return errors;
    }

    @Override
    @Transactional(readOnly = true)
    public long stream(String moduleKey, SearchRequest request, Action scopeAction, int maxRows,
                       java.util.function.Consumer<List<RecordView>> consumer) {
        ModuleDefinition module = registry.require(moduleKey);
        CurrentUser user = AccessControl.requireUser();
        AccessScope scope = AccessControl.require(moduleKey, scopeAction);
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = where(module, user, scope, FilterNode.parse(request.filter()), request.q(), params);
        String orderBy = orderBy(module, request.sort());
        int batch = 500;
        long streamed = 0;
        while (streamed < maxRows) {
            int size = (int) Math.min(batch, maxRows - streamed);
            List<RecordRepository.Row> rows = repository.search(module, where, params, orderBy, size, streamed);
            if (rows.isEmpty()) {
                break;
            }
            consumer.accept(presenter.present(module, rows));
            streamed += rows.size();
            if (rows.size() < size) {
                break;
            }
        }
        return streamed;
    }

    // ================================================================== read

    @Override
    @Transactional(readOnly = true)
    public RecordView get(String moduleKey, UUID id) {
        return find(moduleKey, id).orElseThrow(() -> notFound(registry.require(moduleKey)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RecordView> find(String moduleKey, UUID id) {
        ModuleDefinition module = registry.require(moduleKey);
        CurrentUser user = AccessControl.requireUser();
        AccessScope scope = AccessControl.require(moduleKey, Action.READ);
        MapSqlParameterSource params = new MapSqlParameterSource();
        String scopeSql = ScopeSql.predicate(scope, user, params, "t", "scope", module);
        return repository.findById(module, id, scopeSql, params, false).map(row -> presenter.present(module, row));
    }

    @Override
    @Transactional(readOnly = true)
    public RecordView requireAccess(String moduleKey, UUID id, Action action) {
        ModuleDefinition module = registry.require(moduleKey);
        CurrentUser user = AccessControl.requireUser();
        return action == Action.READ ? get(moduleKey, id) : requireWritable(module, user, id, action);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RecordView> search(String moduleKey, SearchRequest request) {
        ModuleDefinition module = registry.require(moduleKey);
        CurrentUser user = AccessControl.requireUser();
        AccessScope scope = AccessControl.require(moduleKey, Action.READ);
        PageParams page = PageParams.of(request.page(), request.size());

        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = where(module, user, scope, FilterNode.parse(request.filter()), request.q(), params);
        String orderBy = orderBy(module, request.sort());
        long total = repository.count(module, where, params);
        List<RecordRepository.Row> rows = total == 0 ? List.of()
                : repository.search(module, where, params, orderBy, page.size(), page.offset());
        return PageResponse.of(presenter.present(module, rows), page.page(), page.size(), total);
    }

    @Override
    @Transactional(readOnly = true)
    public long count(String moduleKey, FilterNode filter) {
        ModuleDefinition module = registry.require(moduleKey);
        CurrentUser user = AccessControl.requireUser();
        AccessScope scope = AccessControl.require(moduleKey, Action.READ);
        MapSqlParameterSource params = new MapSqlParameterSource();
        return repository.count(module, where(module, user, scope, filter, null, params), params);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, String> titles(String moduleKey, Collection<UUID> ids) {
        return repository.titles(registry.require(moduleKey), ids);
    }

    // ================================================================== aggregate

    private static final Set<String> BUCKETS = Set.of("day", "week", "month", "quarter", "year");
    private static final Set<String> FUNCTIONS = Set.of("count", "sum", "avg", "min", "max");

    @Override
    @Transactional(readOnly = true)
    public List<AggregateRow> aggregate(String moduleKey, AggregateRequest request) {
        ModuleDefinition module = registry.require(moduleKey);
        CurrentUser user = AccessControl.requireUser();
        AccessScope scope = AccessControl.require(moduleKey, Action.READ);
        List<String> groupBy = request.groupBy() == null ? List.of() : request.groupBy();
        List<Metric> metrics = request.metrics() == null || request.metrics().isEmpty()
                ? List.of(Metric.count()) : request.metrics();
        if (groupBy.size() > 2 || metrics.size() > 6) {
            throw ApiException.validation("aggregate", "TOO_COMPLEX", "At most 2 group-by fields and 6 metrics");
        }
        int limit = request.limit() == null ? 100 : Math.min(Math.max(request.limit(), 1), 1000);

        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = where(module, user, scope, FilterNode.parse(request.filter()), null, params);
        List<String> selects = new ArrayList<>();
        List<String> groupAliases = new ArrayList<>();
        List<FieldDefinition> groupFields = new ArrayList<>();
        for (int i = 0; i < groupBy.size(); i++) {
            String[] spec = groupBy.get(i).split(":");
            FieldDefinition field = module.field(spec[0]).filter(f -> !f.type().isArray() && f.type() != FieldType.TEXTAREA)
                    .orElseThrow(() -> ApiException.validation("groupBy", "INVALID", "Cannot group by '" + spec[0] + "'"));
            String expression = "t." + field.column();
            if (spec.length > 1) {
                String bucket = spec[1].toLowerCase(Locale.ROOT);
                if (!field.type().isTemporal() || !BUCKETS.contains(bucket)) {
                    throw ApiException.validation("groupBy", "INVALID", "Invalid date bucket '" + groupBy.get(i) + "'");
                }
                params.addValue("aggTz", tenants.current().timezone());
                expression = field.type() == FieldType.DATETIME
                        ? "date_trunc('" + bucket + "', " + expression + " AT TIME ZONE :aggTz)::date"
                        : "date_trunc('" + bucket + "', " + expression + ")::date";
            }
            selects.add(expression + " AS g" + i);
            groupAliases.add("g" + i);
            groupFields.add(field);
        }
        for (int i = 0; i < metrics.size(); i++) {
            Metric metric = metrics.get(i);
            String function = metric.function() == null ? "" : metric.function().toLowerCase(Locale.ROOT);
            if (!FUNCTIONS.contains(function)) {
                throw ApiException.validation("metrics", "INVALID", "Unknown aggregate function: " + metric.function());
            }
            if (function.equals("count") && metric.field() == null) {
                selects.add("count(*) AS m" + i);
                continue;
            }
            FieldDefinition field = module.field(metric.field() == null ? "" : metric.field())
                    .filter(f -> function.equals("count") || f.type().isNumeric())
                    .orElseThrow(() -> ApiException.validation("metrics", "INVALID",
                            "Cannot " + function + " field '" + metric.field() + "'"));
            selects.add(function + "(t." + field.column() + ") AS m" + i);
        }
        String sql = "SELECT " + String.join(", ", selects) + " FROM " + module.table() + " t WHERE " + where
                + (groupAliases.isEmpty() ? "" : " GROUP BY " + String.join(", ", groupAliases)
                + " ORDER BY " + String.join(", ", groupAliases))
                + " LIMIT " + limit;
        List<Map<String, Object>> raw = repository.aggregate(sql, params);

        Map<Integer, Map<Object, String>> labels = new HashMap<>();
        for (int i = 0; i < groupFields.size(); i++) {
            int index = i;
            List<Object> values = raw.stream().map(r -> r.get("g" + index)).filter(Objects::nonNull).distinct().toList();
            labels.put(i, groupLabels(groupFields.get(i), values));
        }
        List<AggregateRow> rows = new ArrayList<>();
        for (Map<String, Object> r : raw) {
            Map<String, Object> groups = new LinkedHashMap<>();
            Map<String, String> groupLabels = new LinkedHashMap<>();
            for (int i = 0; i < groupFields.size(); i++) {
                Object value = r.get("g" + i);
                groups.put(groupBy.get(i), value);
                String label = value == null ? null : labels.get(i).get(value);
                if (label != null) {
                    groupLabels.put(groupBy.get(i), label);
                }
            }
            Map<String, Object> values = new LinkedHashMap<>();
            for (int i = 0; i < metrics.size(); i++) {
                values.put(metrics.get(i).name(), r.get("m" + i));
            }
            rows.add(new AggregateRow(groups, groupLabels, values));
        }
        return rows;
    }

    private Map<Object, String> groupLabels(FieldDefinition field, List<Object> values) {
        Map<Object, String> labels = new HashMap<>();
        if (values.isEmpty()) {
            return labels;
        }
        List<UUID> ids = values.stream().filter(UUID.class::isInstance).map(UUID.class::cast).toList();
        switch (field.type()) {
            case USER -> labels.putAll(users.displayNames(ids));
            case LOOKUP -> {
                if (field.lookupModule() != null) {
                    labels.putAll(repository.titles(registry.require(field.lookupModule()), ids));
                }
            }
            case REFERENCE -> labels.putAll(references.require(field.referenceType()).labels(ids));
            case PICKLIST -> values.forEach(v -> labels.put(v, field.picklist() != null
                    ? picklists.label(field.picklist(), (String) v)
                    : field.fixedOptions().stream().filter(o -> o.key().equals(v)).map(o -> o.label())
                    .findFirst().orElse((String) v)));
            default -> {
            }
        }
        return labels;
    }

    // ================================================================== update

    @Override
    public RecordView update(String module, UUID id, Map<String, Object> input, Long expectedVersion) {
        return update(module, id, input, expectedVersion, WriteOptions.API);
    }

    @Override
    @Transactional
    public RecordView update(String moduleKey, UUID id, Map<String, Object> input, Long expectedVersion,
                             WriteOptions options) {
        ModuleDefinition module = registry.require(moduleKey);
        CurrentUser user = AccessControl.requireUser();
        RecordView existing = requireWritable(module, user, id, Action.UPDATE);

        List<FieldErrorDetail> errors = new ArrayList<>();
        Map<String, Object> changes = converter.convert(module, input, existing.values(), options.allowReadOnly(), errors);
        HookContext context = new HookContext(moduleKey, user, options);
        if (errors.isEmpty()) {
            hooksFor(moduleKey).forEach(h -> h.beforeUpdate(context, existing, changes));
        }
        changes.entrySet().removeIf(e -> same(e.getValue(), existing.values().get(e.getKey())));
        if (changes.isEmpty() && errors.isEmpty()) {
            if (expectedVersion != null && expectedVersion != existing.version()) {
                throw concurrentModification(existing.version());
            }
            return existing;
        }
        for (Map.Entry<String, Object> change : changes.entrySet()) {
            FieldDefinition field = module.requireField(change.getKey());
            if (field.required() && change.getValue() == null) {
                errors.add(new FieldErrorDetail(field.key(), "REQUIRED", field.label() + " is required"));
            }
        }
        validateReferences(module, changes, existing.values(), errors);
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }

        changes.put(ModuleDefinition.UPDATED_BY, user.userId());
        changes.put(ModuleDefinition.UPDATED_AT, Instant.now());
        if (repository.update(module, id, changes, expectedVersion) == 0) {
            Optional<Long> current = repository.currentVersion(module, id);
            throw current.isPresent() ? concurrentModification(current.get()) : notFound(module);
        }

        RecordView updated = load(module, id);
        List<RecordChange> diff = diff(module, existing, updated);
        audit.record(AuditEntry.record(AuditAction.UPDATE, moduleKey, id, updated.title(), auditChanges(module, diff))
                .withMetadata(originMetadata(options)));
        searchIndexer.upsert(presenter.indexDocument(module, updated));
        hooksFor(moduleKey).forEach(h -> h.afterUpdate(context, existing, updated, diff));
        events.publishEvent(new RecordUpdated(user.tenantId(), moduleKey, id, updated.title(),
                updated.uuid(ModuleDefinition.OWNER), user.userId(), options.origin(), diff));
        return updated;
    }

    // ================================================================== delete / restore

    @Override
    @Transactional
    public void delete(String moduleKey, UUID id) {
        ModuleDefinition module = registry.require(moduleKey);
        CurrentUser user = AccessControl.requireUser();
        RecordView existing = requireWritable(module, user, id, Action.DELETE);
        HookContext context = new HookContext(moduleKey, user, WriteOptions.API);
        hooksFor(moduleKey).forEach(h -> h.beforeDelete(context, existing));
        if (repository.setDeleted(module, id, Instant.now(), user.userId()) == 0) {
            throw notFound(module);
        }
        audit.record(AuditEntry.record(AuditAction.DELETE, moduleKey, id, existing.title(), null));
        searchIndexer.remove(moduleKey, id);
        events.publishEvent(new RecordDeleted(user.tenantId(), moduleKey, id, existing.title(),
                user.userId()));
    }

    @Override
    @Transactional
    public RecordView restore(String moduleKey, UUID id) {
        ModuleDefinition module = registry.require(moduleKey);
        CurrentUser user = AccessControl.requireUser();
        AccessScope scope = AccessControl.require(moduleKey, Action.DELETE);
        MapSqlParameterSource params = new MapSqlParameterSource();
        String scopeSql = ScopeSql.predicate(scope, user, params, "t", "scope", module);
        repository.findById(module, id, scopeSql, params, true).orElseThrow(() -> notFound(module));
        repository.setDeleted(module, id, null, user.userId());
        RecordView restored = load(module, id);
        audit.record(AuditEntry.record(AuditAction.RESTORE, moduleKey, id, restored.title(), null));
        searchIndexer.upsert(presenter.indexDocument(module, restored));
        return restored;
    }

    // ================================================================== bulk

    @Override
    public BulkResult bulkUpdate(String moduleKey, List<UUID> ids, Map<String, Object> values) {
        registry.require(moduleKey);
        AccessControl.require(moduleKey, Action.UPDATE);
        return bulk(ids, id -> update(moduleKey, id, values, null, WriteOptions.of(com.orbitcrm.record.model.ChangeOrigin.BULK)));
    }

    @Override
    public BulkResult bulkDelete(String moduleKey, List<UUID> ids) {
        registry.require(moduleKey);
        AccessControl.require(moduleKey, Action.DELETE);
        return bulk(ids, id -> delete(moduleKey, id));
    }

    /** Each record in its own transaction: one failure never rolls back the others. */
    private BulkResult bulk(List<UUID> ids, java.util.function.Consumer<UUID> action) {
        if (ids == null || ids.isEmpty()) {
            throw ApiException.validation("ids", "REQUIRED", "Provide at least one id");
        }
        List<UUID> distinct = ids.stream().distinct().toList();
        if (distinct.size() > MAX_BULK) {
            throw ApiException.validation("ids", "TOO_MANY", "At most " + MAX_BULK + " records per request");
        }
        List<BulkFailure> failures = new ArrayList<>();
        int succeeded = 0;
        for (UUID id : distinct) {
            try {
                perRecordTx.executeWithoutResult(status -> action.accept(id));
                succeeded++;
            } catch (ApiException ex) {
                failures.add(new BulkFailure(id, ex.code(), ex.getMessage()));
            }
        }
        return new BulkResult(distinct.size(), succeeded, failures);
    }

    // ================================================================== helpers: access

    /**
     * Loads a record the user can see, then checks the action's (possibly narrower) scope. Invisible
     * records are 404 so their existence is not revealed; visible but not modifiable ones are 403.
     */
    private RecordView requireWritable(ModuleDefinition module, CurrentUser user, UUID id, Action action) {
        AccessScope actionScope = AccessControl.require(module.key(), action);
        RecordView existing = find(module.key(), id).orElseThrow(() -> notFound(module));
        if (actionScope != AccessScope.ALL) {
            MapSqlParameterSource params = new MapSqlParameterSource();
            String scopeSql = ScopeSql.predicate(actionScope, user, params, "t", "act", module);
            if (!repository.matches(module, id, scopeSql, params)) {
                throw ApiException.forbidden("ACCESS_DENIED",
                        "You do not have permission to " + action.name().toLowerCase(Locale.ROOT) + " this record");
            }
        }
        return existing;
    }

    private RecordView load(ModuleDefinition module, UUID id) {
        return repository.findById(module, id, null, null, false)
                .map(row -> presenter.present(module, row))
                .orElseThrow(() -> notFound(module));
    }

    // ================================================================== helpers: query

    private String where(ModuleDefinition module, CurrentUser user, AccessScope scope, FilterNode filter, String q,
                         MapSqlParameterSource params) {
        params.addValue("tenantId", user.tenantId());
        List<String> parts = new ArrayList<>(List.of("t.tenant_id = :tenantId", "t.deleted_at IS NULL"));
        String scopeSql = ScopeSql.predicate(scope, user, params, "t", "scope", module);
        if (scopeSql != null) {
            parts.add("(" + scopeSql + ")");
        }
        if (filter != null) {
            ZoneId zone = ZoneId.of(tenants.current().timezone());
            String filterSql = new SqlFilterCompiler(module, user, zone, params, "f").compile(filter);
            if (filterSql != null) {
                parts.add("(" + filterSql + ")");
            }
        }
        String quick = quickSearch(module, q, params);
        if (quick != null) {
            parts.add("(" + quick + ")");
        }
        return String.join(" AND ", parts);
    }

    private static String quickSearch(ModuleDefinition module, String q, MapSqlParameterSource params) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String term = q.trim();
        if (term.length() > 200) {
            throw ApiException.validation("q", "INVALID_LENGTH", "Search text is too long");
        }
        Matcher number = RECORD_NUMBER.matcher(term);
        if (number.matches() && number.group(1).equalsIgnoreCase(module.recordPrefix())) {
            params.addValue("qNumber", Long.parseLong(number.group(2)));
            return "t.record_number = :qNumber";
        }
        params.addValue("q", "%" + SqlFilterCompiler.escapeLike(term) + "%");
        List<String> ors = new ArrayList<>();
        ors.add(module.titleSql("t") + " ILIKE :q ESCAPE '\\'");
        for (FieldDefinition field : module.searchableFields()) {
            if (field.type().isTextual()) {
                ors.add("t." + field.column() + " ILIKE :q ESCAPE '\\'");
            }
        }
        String digits = term.replaceAll("\\D", "");
        if (digits.length() >= 4 && !term.matches(".*\\p{L}.*")) {
            params.addValue("qDigits", "%" + digits + "%");
            module.searchableFields().stream().filter(f -> f.type() == FieldType.PHONE)
                    .forEach(f -> ors.add("regexp_replace(t." + f.column() + ", '\\D', '', 'g') LIKE :qDigits"));
        }
        return String.join(" OR ", ors);
    }

    private static String orderBy(ModuleDefinition module, List<SearchRequest.Sort> sorts) {
        List<SearchRequest.Sort> effective = sorts == null || sorts.isEmpty()
                ? List.of(SearchRequest.Sort.parse(module.defaultSort()))
                : sorts;
        if (effective.size() > 3) {
            throw ApiException.validation("sort", "TOO_MANY", "At most 3 sort fields");
        }
        List<String> parts = new ArrayList<>();
        for (SearchRequest.Sort sort : effective) {
            FieldDefinition field = module.field(sort.field())
                    .filter(FieldDefinition::sortable)
                    .orElseThrow(() -> ApiException.validation("sort", "INVALID",
                            "Cannot sort by '" + sort.field() + "'"));
            parts.add("t." + field.column() + (sort.descending() ? " DESC" : " ASC") + " NULLS LAST");
        }
        parts.add("t.id DESC");
        return String.join(", ", parts);
    }

    // ================================================================== helpers: validation

    private void applyDefaults(ModuleDefinition module, Map<String, Object> values, CurrentUser user) {
        values.putIfAbsent(ModuleDefinition.OWNER, user.userId());
        if (values.get(ModuleDefinition.OWNER) == null) {
            values.put(ModuleDefinition.OWNER, user.userId());
        }
        for (FieldDefinition field : module.fields()) {
            if (field.type() == FieldType.PICKLIST && !values.containsKey(field.key()) && !field.readOnly()) {
                String defaultValue = field.picklist() != null ? picklists.defaultValue(field.picklist())
                        : field.fixedOptions().stream().filter(o -> o.isDefault()).map(o -> o.key()).findFirst()
                        .orElse(null);
                if (defaultValue != null) {
                    values.put(field.key(), defaultValue);
                }
            }
        }
    }

    private static void validateRequired(ModuleDefinition module, Map<String, Object> values,
                                         List<FieldErrorDetail> errors) {
        for (FieldDefinition field : module.fields()) {
            if (field.required() && values.get(field.key()) == null
                    && errors.stream().noneMatch(e -> e.field().equals(field.key()))) {
                errors.add(new FieldErrorDetail(field.key(), "REQUIRED", field.label() + " is required"));
            }
        }
    }

    /** Owners must be active users; lookups must point at records the user can see. */
    private void validateReferences(ModuleDefinition module, Map<String, Object> changes, Map<String, Object> existing,
                                    List<FieldErrorDetail> errors) {
        Map<String, Object> merged = new LinkedHashMap<>(existing);
        merged.putAll(changes);
        for (Map.Entry<String, Object> change : changes.entrySet()) {
            FieldDefinition field = module.field(change.getKey()).orElse(null);
            Object value = change.getValue();
            if (field == null || value == null || same(value, existing.get(field.key()))) {
                continue;
            }
            switch (field.type()) {
                case USER -> {
                    if (!users.isActive((UUID) value)) {
                        errors.add(new FieldErrorDetail(field.key(), "INVALID_USER",
                                field.label() + " must be an active user"));
                    }
                }
                case MULTI_USER -> ((List<?>) value).stream().map(UUID.class::cast)
                        .filter(id -> !users.isActive(id)).findFirst()
                        .ifPresent(id -> errors.add(new FieldErrorDetail(field.key(), "INVALID_USER",
                                field.label() + " contains a user who is not active")));
                case LOOKUP -> validateLookup(field, (UUID) value, merged, errors);
                case REFERENCE -> {
                    if (!references.require(field.referenceType()).exists((UUID) value)) {
                        errors.add(new FieldErrorDetail(field.key(), "INVALID_REFERENCE",
                                field.label() + " does not exist"));
                    }
                }
                default -> {
                }
            }
        }
        // Polymorphic pairs must be complete: a target module needs an id and vice versa.
        for (FieldDefinition field : module.fields()) {
            if (field.isPolymorphicLookup()) {
                boolean hasId = merged.get(field.key()) != null;
                boolean hasModule = merged.get(field.lookupModuleField()) != null;
                if (hasId != hasModule && (changes.containsKey(field.key()) || changes.containsKey(field.lookupModuleField()))) {
                    errors.add(new FieldErrorDetail(field.key(), "INCOMPLETE_REFERENCE",
                            field.label() + " needs both a module and a record"));
                }
            }
        }
    }

    private void validateLookup(FieldDefinition field, UUID id, Map<String, Object> merged,
                                List<FieldErrorDetail> errors) {
        String target = RecordPresenter.targetModule(field, merged);
        Optional<ModuleDefinition> targetModule = target == null ? Optional.empty() : registry.find(target);
        if (targetModule.isEmpty()) {
            errors.add(new FieldErrorDetail(field.key(), "INVALID_REFERENCE", field.label() + " has no valid target module"));
            return;
        }
        CurrentUser user = AccessControl.requireUser();
        AccessScope scope = user.scope(target, Action.READ);
        if (!scope.allows()) {
            errors.add(new FieldErrorDetail(field.key(), "INVALID_REFERENCE",
                    "You do not have access to " + targetModule.get().pluralLabel()));
            return;
        }
        MapSqlParameterSource params = new MapSqlParameterSource();
        String scopeSql = ScopeSql.predicate(scope, user, params, "t", "ref", targetModule.get());
        if (!repository.matches(targetModule.get(), id, scopeSql, params)) {
            errors.add(new FieldErrorDetail(field.key(), "INVALID_REFERENCE",
                    field.label() + " points to a record that does not exist"));
        }
    }

    // ================================================================== helpers: audit & diff

    private static List<RecordChange> diff(ModuleDefinition module, RecordView before, RecordView after) {
        List<RecordChange> changes = new ArrayList<>();
        for (FieldDefinition field : module.fields()) {
            if (field.key().equals(ModuleDefinition.UPDATED_AT) || field.key().equals(ModuleDefinition.UPDATED_BY)) {
                continue;
            }
            Object oldValue = before.values().get(field.key());
            Object newValue = after.values().get(field.key());
            if (!same(oldValue, newValue)) {
                changes.add(new RecordChange(field.key(), oldValue, newValue));
            }
        }
        return changes;
    }

    private static List<AuditEntry.Change> createChanges(ModuleDefinition module, RecordView created) {
        List<AuditEntry.Change> changes = new ArrayList<>();
        for (FieldDefinition field : module.fields()) {
            Object value = created.values().get(field.key());
            if (value == null || "system".equals(field.group()) && !field.key().equals(ModuleDefinition.OWNER)) {
                continue;
            }
            changes.add(new AuditEntry.Change(field.key(), field.label(), null, mask(field, value)));
        }
        return changes;
    }

    private static List<AuditEntry.Change> auditChanges(ModuleDefinition module, List<RecordChange> diff) {
        return diff.stream().map(c -> {
            FieldDefinition field = module.requireField(c.field());
            return new AuditEntry.Change(c.field(), field.label(), mask(field, c.oldValue()), mask(field, c.newValue()));
        }).toList();
    }

    /** Equality that treats 100 and 100.00 as the same number. */
    public static boolean same(Object a, Object b) {
        if (a instanceof java.math.BigDecimal x && b instanceof java.math.BigDecimal y) {
            return x.compareTo(y) == 0;
        }
        return Objects.equals(a, b);
    }

    private static Object mask(FieldDefinition field, Object value) {
        return field.sensitive() && value != null ? "***" : value;
    }

    private static Map<String, Object> originMetadata(WriteOptions options) {
        return options.origin() == com.orbitcrm.record.model.ChangeOrigin.API ? null : Map.of("origin", options.origin().name());
    }

    private List<RecordHooks> hooksFor(String moduleKey) {
        return hooks.getOrDefault(moduleKey, List.of());
    }

    private static ApiException notFound(ModuleDefinition module) {
        String code = module.singularLabel().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_") + "_NOT_FOUND";
        return ApiException.notFound(code, module.singularLabel() + " does not exist");
    }

    private static ApiException concurrentModification(long currentVersion) {
        return new ApiException(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                "The record was changed by someone else (current version " + currentVersion + "). Reload and try again.");
    }
}
