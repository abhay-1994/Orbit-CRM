package com.orbitcrm.timeline.controller;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.orbitcrm.attachment.service.AttachmentService;
import com.orbitcrm.audit.service.AuditService;
import com.orbitcrm.note.service.NoteService;
import com.orbitcrm.record.dto.RecordView;
import com.orbitcrm.record.dto.SearchRequest;
import com.orbitcrm.record.model.FilterNode;
import com.orbitcrm.record.service.RecordService;
import com.orbitcrm.shared.dto.ApiResponse;
import com.orbitcrm.shared.security.AccessControl;
import com.orbitcrm.shared.security.Action;
import com.orbitcrm.timeline.dto.Timeline;
import com.orbitcrm.timeline.dto.TimelineItem;

/**
 * REST controller for a record's unified activity feed at {@code GET /api/v1/{module}/{recordId}/timeline}.
 *
 * <p>Purpose: show everything that happened to a record in one place (PRD: record timeline). It merges, newest first:
 * <ul>
 *   <li>audit entries - creation, field changes (with a readable summary such as "Updated Stage, Amount"),
 *   deletion, restore, conversion;</li>
 *   <li>notes;</li>
 *   <li>uploaded files;</li>
 *   <li>activities related to the record (if the user can read activities).</li>
 * </ul>
 * Requires read access to the record. Pages backwards with {@code before=<nextBefore>}; {@code limit} is 1-100
 * (default 30).
 *
 * @author Abhay
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Timeline", description = "Unified activity feed of a record")
public class TimelineController {

    private static final String ACTIVITIES = "activities";
    private static final int MAX_LIMIT = 100;

    private final RecordService records;
    private final AuditService audit;
    private final NoteService notes;
    private final AttachmentService attachments;

    TimelineController(RecordService records, AuditService audit, NoteService notes, AttachmentService attachments) {
        this.records = records;
        this.audit = audit;
        this.notes = notes;
        this.attachments = attachments;
    }

    @GetMapping("/{module}/{recordId}/timeline")
    @Operation(summary = "Changes, notes, files and activities of a record, newest first")
    ApiResponse<Timeline> timeline(@PathVariable String module, @PathVariable UUID recordId,
                                   @RequestParam(required = false) Instant before,
                                   @RequestParam(defaultValue = "30") int limit) {
        records.requireAccess(module, recordId, Action.READ);
        int size = Math.min(Math.max(limit, 1), MAX_LIMIT);
        Instant cursor = before == null ? Instant.now().plusSeconds(1) : before;
        List<TimelineItem> items = new ArrayList<>();

        audit.recentForRecord(module, recordId, cursor, size).forEach(entry -> {
            Map<String, Object> detail = new LinkedHashMap<>();
            if (entry.changes() != null) {
                detail.put("changes", entry.changes());
            }
            if (entry.metadata() != null) {
                detail.put("metadata", entry.metadata());
            }
            items.add(new TimelineItem("audit", entry.action().toLowerCase(), entry.occurredAt(), entry.actorId(),
                    entry.actorName(), summary(entry.action(), entry.changes()), entry.id(), detail));
        });
        notes.recent(module, recordId, cursor, size).forEach(note -> items.add(new TimelineItem("note", "note",
                note.createdAt(), note.createdBy(), note.createdByName(), note.body(), note.id(),
                Map.of("pinned", note.pinned()))));
        attachments.recent(module, recordId, cursor, size).forEach(file -> items.add(new TimelineItem("attachment",
                "uploaded", file.createdAt(), file.uploadedBy(), file.uploadedByName(), file.fileName(), file.id(),
                Map.of("contentType", file.contentType(), "sizeBytes", file.sizeBytes()))));
        if (!module.equals(ACTIVITIES) && AccessControl.requireUser().can(ACTIVITIES, Action.READ)) {
            FilterNode related = FilterNode.and(FilterNode.eq("relatedModule", module),
                    FilterNode.eq("relatedId", recordId.toString()),
                    FilterNode.condition("createdAt", com.orbitcrm.metadata.model.FilterOperator.LT, cursor.toString()));
            for (RecordView activity : records.search(ACTIVITIES, new SearchRequest(related, null,
                    List.of(new SearchRequest.Sort("createdAt", "desc")), 0, size)).content()) {
                items.add(new TimelineItem("activity", activity.string("type"), (Instant) activity.value("createdAt"),
                        activity.uuid("ownerId"), activity.labels().get("ownerId"), activity.title(), activity.id(),
                        Map.of("status", activity.string("status"),
                                "startAt", activity.value("startAt") == null ? "" : activity.value("startAt").toString())));
            }
        }

        items.sort(Comparator.comparing(TimelineItem::at).reversed());
        List<TimelineItem> page = items.size() > size ? items.subList(0, size) : items;
        Instant nextBefore = page.size() == size ? page.get(page.size() - 1).at() : null;
        return ApiResponse.ok(new Timeline(List.copyOf(page), nextBefore));
    }

    private static String summary(String action, List<Map<String, Object>> changes) {
        return switch (action) {
            case "CREATE" -> "Created";
            case "UPDATE" -> changes == null || changes.isEmpty() ? "Updated"
                    : "Updated " + String.join(", ", changes.stream().map(c -> String.valueOf(c.get("label"))).toList());
            case "DELETE" -> "Deleted";
            case "RESTORE" -> "Restored";
            case "CONVERT" -> "Converted";
            default -> action.charAt(0) + action.substring(1).toLowerCase().replace('_', ' ');
        };
    }
}
