package com.orbitcrm.dataio.controller;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.orbitcrm.audit.dto.AuditEntry;
import com.orbitcrm.audit.model.AuditAction;
import com.orbitcrm.audit.service.AuditService;
import com.orbitcrm.dataio.dto.ExportRequest;
import com.orbitcrm.dataio.util.CsvUtils;
import com.orbitcrm.metadata.model.FieldDefinition;
import com.orbitcrm.metadata.model.ModuleDefinition;
import com.orbitcrm.metadata.service.ModuleRegistry;
import com.orbitcrm.record.dto.RecordView;
import com.orbitcrm.record.dto.SearchRequest;
import com.orbitcrm.record.service.RecordService;
import com.orbitcrm.shared.exception.ApiException;
import com.orbitcrm.shared.security.AccessControl;
import com.orbitcrm.shared.security.Action;

/**
 * REST controller for CSV export at {@code POST /api/v1/{module}/export} (PRD section 38).
 *
 * <p>Purpose: let users take their data out, e.g. for spreadsheets or other tools. The request body can carry a
 * filter, quick-search text, sort and the fields to include (all business fields when omitted); at most 50,000 rows
 * are exported. Behaviour:
 * <ul>
 *   <li>requires the {@code EXPORT} permission and uses its scope, which may be narrower than READ;</li>
 *   <li>streams the CSV in batches (UTF-8 with a BOM so Excel detects the encoding) as a file download;</li>
 *   <li>writes human-readable values - owner names, picklist labels, related record titles, multi-values separated by
 *   "; " - which the importer accepts back;</li>
 *   <li>neutralizes spreadsheet formula injection in every cell ({@link com.orbitcrm.dataio.util.CsvUtils#safe});</li>
 *   <li>records the export (row count, fields, filter) in the audit log.</li>
 * </ul>
 *
 * @author Abhay
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Export", description = "CSV export of filtered records")
public class ExportController {

    static final int MAX_ROWS = 50_000;

    private final RecordService records;
    private final ModuleRegistry registry;
    private final AuditService audit;

    ExportController(RecordService records, ModuleRegistry registry, AuditService audit) {
        this.records = records;
        this.registry = registry;
        this.audit = audit;
    }

    @PostMapping("/{module}/export")
    @Operation(summary = "Export records matching a filter as CSV (max 50,000 rows)",
            description = "Body: {filter, q, sort, fields}. Omit fields for all business fields.")
    void export(@PathVariable String module, @RequestBody(required = false) ExportRequest request,
                HttpServletResponse response) throws IOException {
        ModuleDefinition definition = registry.require(module);
        AccessControl.require(module, Action.EXPORT);
        ExportRequest effective = request == null ? new ExportRequest(null, null, null, null) : request;
        List<FieldDefinition> fields = fields(definition, effective.fields());

        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(module + "-export.csv").build().toString());
        Writer writer = new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8);
        writer.write('﻿'); // Excel detects UTF-8 with a BOM
        List<String> header = new ArrayList<>();
        header.add("Record number");
        fields.forEach(f -> header.add(f.label()));
        long count;
        try (CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder()
                .setHeader(header.toArray(String[]::new)).get())) {
            count = records.stream(module, new SearchRequest(effective.filter(), effective.q(), effective.sort(), null, null),
                    Action.EXPORT, MAX_ROWS, batch -> write(printer, fields, batch));
        }
        Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        metadata.put("rows", count);
        metadata.put("fields", fields.stream().map(FieldDefinition::key).toList());
        if (effective.filter() != null) {
            metadata.put("filter", effective.filter());
        }
        audit.record(new AuditEntry(AuditAction.EXPORT, module, null, null, null, metadata, null, null));
    }

    private static void write(CSVPrinter printer, List<FieldDefinition> fields, List<RecordView> batch) {
        try {
            for (RecordView record : batch) {
                List<String> row = new ArrayList<>();
                row.add(record.recordNumber());
                for (FieldDefinition field : fields) {
                    row.add(CsvUtils.safe(format(record, field)));
                }
                printer.printRecord(row);
            }
            printer.flush();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static String format(RecordView record, FieldDefinition field) {
        Object value = record.values().get(field.key());
        if (value == null) {
            return "";
        }
        String label = record.labels().get(field.key());
        if (label != null) {
            return label;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).collect(Collectors.joining("; "));
        }
        return value.toString();
    }

    private static List<FieldDefinition> fields(ModuleDefinition module, List<String> requested) {
        if (requested == null || requested.isEmpty()) {
            return module.fields().stream()
                    .filter(f -> !f.key().equals(ModuleDefinition.UPDATED_BY) && !f.key().equals(ModuleDefinition.CREATED_BY))
                    .toList();
        }
        return requested.stream().map(key -> module.field(key).orElseThrow(() ->
                ApiException.validation("fields", "UNKNOWN_FIELD", "Unknown field '" + key + "'"))).toList();
    }
}
