package com.orbitcrm.dataio.controller;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.orbitcrm.dataio.dto.ImportJob;
import com.orbitcrm.dataio.dto.ImportMappingRequest;
import com.orbitcrm.dataio.dto.ImportPreview;
import com.orbitcrm.dataio.dto.ImportRowError;
import com.orbitcrm.dataio.service.ImportService;
import com.orbitcrm.dataio.util.CsvUtils;
import com.orbitcrm.shared.dto.ApiResponse;

/**
 * REST controller for the CSV import wizard at {@code /api/v1/imports} (PRD section 38).
 *
 * <p>Purpose: guide users through importing spreadsheets step by step:
 * <ol>
 *   <li>{@code POST /} (multipart {@code file}, parameter {@code module}) - upload a CSV; returns the detected columns
 *   and a suggested column-to-field mapping;</li>
 *   <li>{@code PUT /{id}/mapping} - set the mapping, duplicate handling and default values; returns a validated
 *   preview of the first rows;</li>
 *   <li>{@code POST /{id}/start} - start the import in the background;</li>
 *   <li>{@code GET /{id}} - status, totals (created, updated, skipped, failed) and row errors;
 *   {@code GET /{id}/errors.csv} downloads the failed rows with their error messages.</li>
 * </ol>
 * {@code GET /} lists recent imports. Users see their own imports; administrators see all.
 *
 * @author Abhay
 */
@RestController
@RequestMapping("/api/v1/imports")
@Tag(name = "Import", description = "CSV import: upload -> map columns -> preview -> start -> results")
public class ImportController {

    private final ImportService imports;

    ImportController(ImportService imports) {
        this.imports = imports;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "1. Upload a CSV; returns detected columns and a suggested column-to-field mapping")
    ApiResponse<ImportJob> upload(@RequestParam String module, @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(imports.upload(module, file), "File uploaded");
    }

    @PutMapping("/{id}/mapping")
    @Operation(summary = "2. Set the mapping and duplicate handling; returns a validated preview of the first rows")
    ApiResponse<ImportPreview> configure(@PathVariable UUID id, @RequestBody ImportMappingRequest request) {
        return ApiResponse.ok(imports.configure(id, request));
    }

    @PostMapping("/{id}/start")
    @Operation(summary = "3. Start the import in the background")
    ApiResponse<ImportJob> start(@PathVariable UUID id) {
        return ApiResponse.ok(imports.start(id), "Import started");
    }

    @GetMapping("/{id}")
    @Operation(summary = "Status, totals (created/updated/skipped/failed) and row errors")
    ApiResponse<ImportJob> get(@PathVariable UUID id) {
        return ApiResponse.ok(imports.requireOwnJob(id));
    }

    @GetMapping
    @Operation(summary = "Recent imports")
    ApiResponse<List<ImportJob>> list(@RequestParam(required = false) String module) {
        return ApiResponse.ok(imports.list(module));
    }

    @GetMapping("/{id}/errors.csv")
    @Operation(summary = "Download failed rows and their error messages")
    void errors(@PathVariable UUID id, HttpServletResponse response) throws IOException {
        ImportJob job = imports.requireOwnJob(id);
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename("import-errors-" + id + ".csv").build().toString());
        Writer writer = new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8);
        try (CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder().setHeader("Row", "Errors").get())) {
            for (ImportRowError error : job.errors()) {
                printer.printRecord(error.row(), CsvUtils.safe(String.join("; ", error.messages())));
            }
        }
    }
}
