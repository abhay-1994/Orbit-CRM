package com.orbitcrm.dataio.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jakarta.annotation.PreDestroy;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import com.orbitcrm.audit.dto.AuditEntry;
import com.orbitcrm.audit.model.AuditAction;
import com.orbitcrm.audit.service.AuditService;
import com.orbitcrm.dataio.dto.DuplicateStrategy;
import com.orbitcrm.dataio.dto.ImportJob;
import com.orbitcrm.dataio.dto.ImportMappingRequest;
import com.orbitcrm.dataio.dto.ImportPreview;
import com.orbitcrm.dataio.dto.ImportPreviewRow;
import com.orbitcrm.dataio.dto.ImportRowError;
import com.orbitcrm.dataio.mapper.CsvValueMapper;
import com.orbitcrm.metadata.model.FieldDefinition;
import com.orbitcrm.metadata.model.ModuleDefinition;
import com.orbitcrm.metadata.service.ModuleRegistry;
import com.orbitcrm.record.dto.RecordView;
import com.orbitcrm.record.dto.SearchRequest;
import com.orbitcrm.record.model.ChangeOrigin;
import com.orbitcrm.record.model.FilterNode;
import com.orbitcrm.record.model.WriteOptions;
import com.orbitcrm.record.service.RecordService;
import com.orbitcrm.shared.dto.FieldErrorDetail;
import com.orbitcrm.shared.exception.ApiException;
import com.orbitcrm.shared.security.AccessControl;
import com.orbitcrm.shared.security.Action;
import com.orbitcrm.shared.security.CurrentUser;
import com.orbitcrm.shared.service.StorageService;
import com.orbitcrm.shared.tenant.TenantContext;
import com.orbitcrm.shared.util.AfterCommit;
import com.orbitcrm.shared.util.UuidV7;

/**
 * Business logic of CSV import (PRD section 38).
 *
 * <p>Purpose: import spreadsheets safely and transparently:
 * <ul>
 *   <li><b>Upload</b> requires the IMPORT and CREATE permissions on the module; accepts {@code .csv} files up to
 *   50,000 rows, UTF-8 with or without BOM, with the delimiter (comma, semicolon or tab) detected from the header; the
 *   file is kept in object storage under a tenant-prefixed key.</li>
 *   <li><b>Mapping</b> validates columns and fields and previews the first 10 rows with a dry run of the record
 *   engine's validation.</li>
 *   <li><b>Execution</b> runs in the background on a virtual thread after the start transaction commits, with the
 *   uploader's tenant and security context. Every row goes through the record engine (validation, permissions, audit,
 *   search index) in its own transaction, so one bad row never blocks the rest. Duplicates are skipped, updated or
 *   created according to the chosen strategy. Progress is saved every 50 rows; errors (up to 500) and totals are
 *   stored when the import finishes, and an {@code IMPORT} audit entry is written.</li>
 *   <li>Imported changes carry {@code ChangeOrigin.IMPORT}, so they do not trigger assignment notifications.</li>
 * </ul>
 * Jobs are visible to their creator and to administrators only.
 *
 * @author Abhay
 */
@Service
public class ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);
    public static final int MAX_ROWS = 50_000;
    private static final int MAX_STORED_ERRORS = 500;
    private static final int PREVIEW_ROWS = 10;
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() { };
    private static final TypeReference<Map<String, String>> MAPPING = new TypeReference<>() { };
    private static final TypeReference<Map<String, Object>> OBJECT = new TypeReference<>() { };
    private static final TypeReference<List<ImportRowError>> ERRORS = new TypeReference<>() { };
    private static final String COLUMNS = "id, module_key, file_name, storage_key, status, columns, total_rows, "
            + "processed_rows, created_count, updated_count, skipped_count, failed_count, mapping, options, errors, "
            + "created_by, created_at, started_at, finished_at";

    private final NamedParameterJdbcTemplate jdbc;
    private final StorageService storage;
    private final RecordService records;
    private final ModuleRegistry registry;
    private final CsvValueMapper values;
    private final AuditService audit;
    private final JsonMapper json;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    ImportService(NamedParameterJdbcTemplate jdbc, StorageService storage, RecordService records,
                  ModuleRegistry registry, CsvValueMapper values, AuditService audit, JsonMapper json) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.records = records;
        this.registry = registry;
        this.values = values;
        this.audit = audit;
        this.json = json;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }

    // ------------------------------------------------------------------ 1. upload

    @Transactional
    public ImportJob upload(String moduleKey, MultipartFile file) {
        ModuleDefinition module = registry.require(moduleKey);
        requireImportPermission(moduleKey);
        CurrentUser user = AccessControl.requireUser();
        if (file == null || file.isEmpty()) {
            throw ApiException.validation("file", "REQUIRED", "Choose a CSV file");
        }
        String fileName = file.getOriginalFilename() == null ? "import.csv" : file.getOriginalFilename();
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE", "Upload a .csv file");
        }
        List<String> headers;
        int rows;
        try (CSVParser parser = parse(file.getInputStream())) {
            headers = parser.getHeaderNames();
            rows = (int) parser.stream().count();
        } catch (IOException | UncheckedIOException | IllegalArgumentException ex) {
            throw ApiException.validation("file", "INVALID_CSV", "The file could not be read as CSV: " + ex.getMessage());
        }
        if (headers.isEmpty()) {
            throw ApiException.validation("file", "INVALID_CSV", "The first row must contain column names");
        }
        if (rows > MAX_ROWS) {
            throw ApiException.validation("file", "TOO_MANY_ROWS", "Imports are limited to " + MAX_ROWS + " rows");
        }
        UUID id = UuidV7.generate();
        String key = "tenants/" + user.tenantId() + "/imports/" + id + ".csv";
        try (InputStream in = file.getInputStream()) {
            storage.put(key, in, file.getSize(), "text/csv");
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Map<String, String> suggested = CsvValueMapper.suggestMapping(headers, module);
        jdbc.update("""
                INSERT INTO import_jobs (id, tenant_id, module_key, file_name, storage_key, status, columns, total_rows,
                                         mapping, created_by, created_at, updated_at, version)
                VALUES (:id, :tenantId, :module, :fileName, :key, 'UPLOADED', CAST(:columns AS jsonb), :rows,
                        CAST(:mapping AS jsonb), :user, :now, :now, 0)
                """, new MapSqlParameterSource()
                .addValue("id", id).addValue("tenantId", user.tenantId()).addValue("module", moduleKey)
                .addValue("fileName", fileName).addValue("key", key).addValue("columns", json.writeValueAsString(headers))
                .addValue("rows", rows).addValue("mapping", json.writeValueAsString(suggested))
                .addValue("user", user.userId()).addValue("now", now));
        return get(id);
    }

    // ------------------------------------------------------------------ 2. map + preview

    @Transactional
    public ImportPreview configure(UUID id, ImportMappingRequest request) {
        ImportJob job = requireOwnJob(id);
        if (!job.status().equals("UPLOADED") && !job.status().equals("MAPPED")) {
            throw ApiException.conflict("IMPORT_ALREADY_STARTED", "This import has already been started");
        }
        ModuleDefinition module = registry.require(job.module());
        Map<String, String> mapping = new LinkedHashMap<>();
        request.mapping().forEach((column, field) -> {
            if (!job.columns().contains(column)) {
                throw ApiException.validation("mapping." + column, "UNKNOWN_COLUMN", "No column named '" + column + "'");
            }
            if (field != null && !field.isBlank()) {
                FieldDefinition definition = module.field(field).orElseThrow(() -> ApiException.validation(
                        "mapping." + column, "UNKNOWN_FIELD", "Unknown field '" + field + "'"));
                if (definition.readOnly()) {
                    throw ApiException.validation("mapping." + column, "READ_ONLY", definition.label() + " cannot be imported");
                }
                mapping.put(column, field);
            }
        });
        if (mapping.isEmpty()) {
            throw ApiException.validation("mapping", "REQUIRED", "Map at least one column to a field");
        }
        DuplicateStrategy strategy = request.duplicateStrategy() == null ? DuplicateStrategy.SKIP : request.duplicateStrategy();
        if (request.duplicateField() != null && module.field(request.duplicateField()).isEmpty()) {
            throw ApiException.validation("duplicateField", "UNKNOWN_FIELD", "Unknown field '" + request.duplicateField() + "'");
        }
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("duplicateField", request.duplicateField());
        options.put("duplicateStrategy", strategy.name());
        options.put("defaults", request.defaults() == null ? Map.of() : request.defaults());
        jdbc.update("UPDATE import_jobs SET mapping = CAST(:mapping AS jsonb), options = CAST(:options AS jsonb), "
                        + "status = 'MAPPED', updated_at = :now, version = version + 1 WHERE id = :id AND tenant_id = :tenantId",
                new MapSqlParameterSource("id", id).addValue("tenantId", TenantContext.requireTenantId())
                        .addValue("mapping", json.writeValueAsString(mapping))
                        .addValue("options", json.writeValueAsString(options))
                        .addValue("now", OffsetDateTime.now(ZoneOffset.UTC)));

        List<ImportPreviewRow> rows = new ArrayList<>();
        try (InputStream in = storage.get(job.storageKey()); CSVParser parser = parse(in)) {
            int rowNumber = 1;
            for (CSVRecord record : parser) {
                if (rowNumber > PREVIEW_ROWS) {
                    break;
                }
                rowNumber++;
                List<String> problems = new ArrayList<>();
                Map<String, Object> input = toValues(module, record, mapping, options, problems);
                records.validate(module.key(), input).forEach(e -> problems.add(describe(module, e)));
                rows.add(new ImportPreviewRow((int) record.getRecordNumber() + 1, input, problems));
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return new ImportPreview(get(id), rows);
    }

    // ------------------------------------------------------------------ 3. run

    @Transactional
    public ImportJob start(UUID id) {
        ImportJob job = requireOwnJob(id);
        if (!job.status().equals("MAPPED")) {
            throw ApiException.conflict(job.status().equals("UPLOADED") ? "IMPORT_NOT_MAPPED" : "IMPORT_ALREADY_STARTED",
                    job.status().equals("UPLOADED") ? "Map the columns before starting" : "This import has already started");
        }
        jdbc.update("UPDATE import_jobs SET status = 'QUEUED', updated_at = :now WHERE id = :id AND tenant_id = :tenantId",
                new MapSqlParameterSource("id", id).addValue("tenantId", TenantContext.requireTenantId())
                        .addValue("now", OffsetDateTime.now(ZoneOffset.UTC)));
        UUID tenantId = TenantContext.requireTenantId();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        AfterCommit.run(() -> executor.submit(() -> runInBackground(tenantId, authentication, id)));
        return get(id);
    }

    private void runInBackground(UUID tenantId, Authentication authentication, UUID id) {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            TenantContext.runAs(tenantId, () -> execute(id));
        } catch (RuntimeException ex) {
            log.error("Import {} failed", id, ex);
            TenantContext.runAs(tenantId, () -> finish(id, "FAILED", new Counters(), List.of(
                    new ImportRowError(0, List.of("Import failed: " + ex.getMessage())))));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void execute(UUID id) {
        ImportJob job = get(id);
        ModuleDefinition module = registry.require(job.module());
        Map<String, String> mapping = job.mapping();
        Map<String, Object> options = job.options();
        String duplicateField = (String) options.get("duplicateField");
        DuplicateStrategy strategy = DuplicateStrategy.valueOf((String) options.get("duplicateStrategy"));
        jdbc.update("UPDATE import_jobs SET status = 'RUNNING', started_at = :now, updated_at = :now "
                        + "WHERE id = :id AND tenant_id = :tenantId",
                new MapSqlParameterSource("id", id).addValue("tenantId", TenantContext.requireTenantId())
                        .addValue("now", OffsetDateTime.now(ZoneOffset.UTC)));

        Counters counters = new Counters();
        List<ImportRowError> errors = new ArrayList<>();
        WriteOptions writeOptions = WriteOptions.of(ChangeOrigin.IMPORT);
        try (InputStream in = storage.get(job.storageKey()); CSVParser parser = parse(in)) {
            for (CSVRecord record : parser) {
                int rowNumber = (int) record.getRecordNumber() + 1;
                List<String> problems = new ArrayList<>();
                try {
                    Map<String, Object> input = toValues(module, record, mapping, options, problems);
                    if (!problems.isEmpty()) {
                        throw ApiException.validation("row", "INVALID_VALUE", String.join("; ", problems));
                    }
                    RecordView duplicate = duplicateField == null ? null : findDuplicate(module, duplicateField,
                            input.get(duplicateField));
                    if (duplicate != null && strategy == DuplicateStrategy.SKIP) {
                        counters.skipped++;
                    } else if (duplicate != null && strategy == DuplicateStrategy.UPDATE) {
                        records.update(module.key(), duplicate.id(), input, null, writeOptions);
                        counters.updated++;
                    } else {
                        records.create(module.key(), input, writeOptions);
                        counters.created++;
                    }
                } catch (ApiException ex) {
                    counters.failed++;
                    if (errors.size() < MAX_STORED_ERRORS) {
                        List<String> messages = ex.details().isEmpty() ? List.of(ex.getMessage())
                                : ex.details().stream().map(d -> describe(module, d)).toList();
                        errors.add(new ImportRowError(rowNumber, messages));
                    }
                }
                counters.processed++;
                if (counters.processed % 50 == 0) {
                    progress(id, counters);
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        finish(id, "COMPLETED", counters, errors);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("importId", id.toString());
        metadata.put("file", job.fileName());
        metadata.put("created", counters.created);
        metadata.put("updated", counters.updated);
        metadata.put("skipped", counters.skipped);
        metadata.put("failed", counters.failed);
        audit.record(new AuditEntry(AuditAction.IMPORT, module.key(), null, null, null, metadata, null, null));
    }

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public ImportJob get(UUID id) {
        List<ImportJob> jobs = jdbc.query("SELECT " + COLUMNS + " FROM import_jobs WHERE id = :id AND tenant_id = :tenantId",
                new MapSqlParameterSource("id", id).addValue("tenantId", TenantContext.requireTenantId()), mapper());
        if (jobs.isEmpty()) {
            throw ApiException.notFound("IMPORT_NOT_FOUND", "Import does not exist");
        }
        return jobs.get(0);
    }

    @Transactional(readOnly = true)
    public List<ImportJob> list(String moduleKey) {
        CurrentUser user = AccessControl.requireUser();
        MapSqlParameterSource params = new MapSqlParameterSource("tenantId", user.tenantId()).addValue("me", user.userId());
        String moduleFilter = "";
        if (moduleKey != null) {
            moduleFilter = " AND module_key = :module";
            params.addValue("module", moduleKey);
        }
        String ownerFilter = user.administrator() ? "" : " AND created_by = :me";
        return jdbc.query("SELECT " + COLUMNS + " FROM import_jobs WHERE tenant_id = :tenantId" + moduleFilter
                + ownerFilter + " ORDER BY created_at DESC LIMIT 50", params, mapper());
    }

    @Transactional(readOnly = true)
    public ImportJob requireOwnJob(UUID id) {
        ImportJob job = get(id);
        CurrentUser user = AccessControl.requireUser();
        if (!user.administrator() && !user.userId().equals(job.createdBy())) {
            throw ApiException.notFound("IMPORT_NOT_FOUND", "Import does not exist");
        }
        return job;
    }

    // ------------------------------------------------------------------ helpers

    private Map<String, Object> toValues(ModuleDefinition module, CSVRecord record, Map<String, String> mapping,
                                         Map<String, Object> options, List<String> problems) {
        Map<String, Object> input = new LinkedHashMap<>();
        @SuppressWarnings("unchecked")
        Map<String, Object> defaults = (Map<String, Object>) options.getOrDefault("defaults", Map.of());
        input.putAll(defaults);
        mapping.forEach((column, fieldKey) -> {
            FieldDefinition field = module.requireField(fieldKey);
            String cell = record.isMapped(column) ? record.get(column) : null;
            try {
                Object value = values.map(field, cell);
                if (value != null) {
                    input.put(fieldKey, value);
                }
            } catch (IllegalArgumentException ex) {
                problems.add(field.label() + ": " + ex.getMessage());
            }
        });
        return input;
    }

    private RecordView findDuplicate(ModuleDefinition module, String field, Object value) {
        if (value == null) {
            return null;
        }
        List<RecordView> matches = records.search(module.key(), new SearchRequest(FilterNode.eq(field, value), null,
                null, 0, 1)).content();
        return matches.isEmpty() ? null : matches.get(0);
    }

    private void progress(UUID id, Counters c) {
        jdbc.update("UPDATE import_jobs SET processed_rows = :p, created_count = :c, updated_count = :u, "
                        + "skipped_count = :s, failed_count = :f, updated_at = :now WHERE id = :id AND tenant_id = :tenantId",
                counters(id, c));
    }

    private void finish(UUID id, String status, Counters c, List<ImportRowError> errors) {
        jdbc.update("UPDATE import_jobs SET status = :status, processed_rows = :p, created_count = :c, "
                        + "updated_count = :u, skipped_count = :s, failed_count = :f, errors = CAST(:errors AS jsonb), "
                        + "finished_at = :now, updated_at = :now WHERE id = :id AND tenant_id = :tenantId",
                counters(id, c).addValue("status", status).addValue("errors", json.writeValueAsString(errors)));
    }

    private static MapSqlParameterSource counters(UUID id, Counters c) {
        return new MapSqlParameterSource("id", id).addValue("tenantId", TenantContext.requireTenantId())
                .addValue("p", c.processed).addValue("c", c.created).addValue("u", c.updated)
                .addValue("s", c.skipped).addValue("f", c.failed).addValue("now", OffsetDateTime.now(ZoneOffset.UTC));
    }

    private static String describe(ModuleDefinition module, FieldErrorDetail error) {
        if (error.field() == null) {
            return error.message();
        }
        return module.field(error.field()).map(f -> error.message().startsWith(f.label()) ? error.message()
                : f.label() + ": " + error.message()).orElse(error.message());
    }

    private static void requireImportPermission(String moduleKey) {
        AccessControl.require(moduleKey, Action.IMPORT);
        AccessControl.require(moduleKey, Action.CREATE);
    }

    /** UTF-8 with optional BOM; delimiter auto-detected from the header line (comma, semicolon or tab). */
    public static CSVParser parse(InputStream in) throws IOException {
        PushbackReader reader = new PushbackReader(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)), 1);
        int first = reader.read();
        if (first != -1 && first != '﻿') {
            reader.unread(first);
        }
        BufferedReader buffered = new BufferedReader(reader);
        buffered.mark(64 * 1024);
        String headerLine = buffered.readLine();
        buffered.reset();
        char delimiter = detectDelimiter(headerLine == null ? "" : headerLine);
        return CSVFormat.DEFAULT.builder()
                .setDelimiter(delimiter)
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .setAllowMissingColumnNames(false)
                .get()
                .parse(buffered);
    }

    private static char detectDelimiter(String headerLine) {
        long commas = headerLine.chars().filter(c -> c == ',').count();
        long semicolons = headerLine.chars().filter(c -> c == ';').count();
        long tabs = headerLine.chars().filter(c -> c == '\t').count();
        if (tabs > commas && tabs > semicolons) {
            return '\t';
        }
        return semicolons > commas ? ';' : ',';
    }

    private RowMapper<ImportJob> mapper() {
        return (rs, n) -> {
            OffsetDateTime started = rs.getObject("started_at", OffsetDateTime.class);
            OffsetDateTime finished = rs.getObject("finished_at", OffsetDateTime.class);
            String mapping = rs.getString("mapping");
            String options = rs.getString("options");
            String errors = rs.getString("errors");
            return new ImportJob(rs.getObject("id", UUID.class), rs.getString("module_key"), rs.getString("file_name"),
                    rs.getString("storage_key"), rs.getString("status"), json.readValue(rs.getString("columns"), STRINGS),
                    rs.getInt("total_rows"), rs.getInt("processed_rows"), rs.getInt("created_count"),
                    rs.getInt("updated_count"), rs.getInt("skipped_count"), rs.getInt("failed_count"),
                    mapping == null ? Map.of() : json.readValue(mapping, MAPPING),
                    options == null ? Map.of() : json.readValue(options, OBJECT),
                    errors == null ? List.of() : json.readValue(errors, ERRORS),
                    rs.getObject("created_by", UUID.class), rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                    started == null ? null : started.toInstant(), finished == null ? null : finished.toInstant());
        };
    }

    /**
     * Mutable running totals of an import while it executes.
     *
     * <p>Purpose: count processed, created, updated, skipped and failed rows; the totals are saved as progress every 50
     * rows and stored with the final result.
     */
    private static final class Counters {
        int processed;
        int created;
        int updated;
        int skipped;
        int failed;
    }
}
