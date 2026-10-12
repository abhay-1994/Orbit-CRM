package com.orbitcrm.activity.controller;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.orbitcrm.activity.config.ActivityModule;
import com.orbitcrm.record.dto.RecordView;
import com.orbitcrm.record.dto.SearchRequest;
import com.orbitcrm.record.service.RecordService;
import com.orbitcrm.shared.dto.ApiResponse;
import com.orbitcrm.shared.exception.ApiException;

/**
 * REST controller for the calendar feed at {@code /api/v1/calendar} (PRD section 16).
 *
 * <p>Purpose: return the activities to show in a calendar. {@code GET /?from=...&to=...} returns activities that
 * overlap the half-open range {@code [from, to)} (at most 100 days) and are owned by, or involve as participant, the
 * given users ({@code userId}, repeatable; default: the current user). Activities without an end time count as
 * starting within the range. Up to 200 activities are returned, ordered by start time; the client renders day, week
 * and month views from them. Visibility follows the normal activity permissions.
 *
 * @author Abhay
 */
@RestController
@RequestMapping("/api/v1/calendar")
@Tag(name = "Calendar", description = "Activities overlapping a time range, for day/week/month views")
public class CalendarController {

    private static final Duration MAX_RANGE = Duration.ofDays(100);

    private final RecordService records;

    CalendarController(RecordService records) {
        this.records = records;
    }

    @GetMapping
    @Operation(summary = "Activities that overlap [from, to) owned by or involving the given users (default: you)")
    ApiResponse<List<RecordView>> calendar(@RequestParam Instant from, @RequestParam Instant to,
                                           @RequestParam(required = false) List<UUID> userId) {
        if (!to.isAfter(from) || Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
            throw ApiException.validation("to", "INVALID_RANGE", "'to' must be after 'from' and within 100 days");
        }
        List<Object> users = new ArrayList<>();
        if (userId == null || userId.isEmpty()) {
            users.add("$me");
        } else {
            userId.forEach(id -> users.add(id.toString()));
        }
        Map<String, Object> filter = Map.of("and", List.of(
                Map.of("field", "startAt", "op", "lt", "value", to.toString()),
                Map.of("or", List.of(
                        Map.of("field", "endAt", "op", "gte", "value", from.toString()),
                        Map.of("and", List.of(
                                Map.of("field", "endAt", "op", "is_empty"),
                                Map.of("field", "startAt", "op", "gte", "value", from.toString()))))),
                Map.of("or", List.of(
                        Map.of("field", "ownerId", "op", "in", "value", users),
                        Map.of("field", "participantIds", "op", "contains", "value", users)))));
        return ApiResponse.ok(records.search(ActivityModule.KEY, new SearchRequest(filter, null,
                List.of(new SearchRequest.Sort("startAt", "asc")), 0, 200)).content());
    }
}
