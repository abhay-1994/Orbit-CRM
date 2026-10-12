package com.orbitcrm.notification.controller;

import java.util.Map;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.orbitcrm.notification.dto.NotificationView;
import com.orbitcrm.notification.service.impl.NotificationServiceImpl;
import com.orbitcrm.notification.service.impl.NotificationStreams;
import com.orbitcrm.shared.dto.ApiResponse;
import com.orbitcrm.shared.dto.PageParams;
import com.orbitcrm.shared.dto.PageResponse;
import com.orbitcrm.shared.security.AccessControl;

/**
 * REST controller for the signed-in user's in-app notifications at {@code /api/v1/notifications}.
 *
 * <p>Purpose: power the notification bell (PRD section 31).
 * <ul>
 *   <li>{@code GET /} - the user's notifications, newest first, optionally unread only.</li>
 *   <li>{@code GET /unread-count} - the badge number; poll it as a fallback to the stream.</li>
 *   <li>{@code POST /{id}/read} and {@code POST /read-all} - mark notifications read.</li>
 *   <li>{@code GET /stream} - a server-sent event stream that delivers {@code notification} events as they happen
 *   (after a {@code ready} event). Browsers should use a fetch-based EventSource so the {@code Authorization} header
 *   can be sent.</li>
 * </ul>
 * Users only ever see their own notifications.
 *
 * @author Abhay
 */
@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications", description = "In-app notifications and a live server-sent event stream")
public class NotificationController {

    private final NotificationServiceImpl notifications;
    private final NotificationStreams streams;

    NotificationController(NotificationServiceImpl notifications, NotificationStreams streams) {
        this.notifications = notifications;
        this.streams = streams;
    }

    @GetMapping
    @Operation(summary = "Your notifications, newest first")
    ApiResponse<PageResponse<NotificationView>> list(@RequestParam(defaultValue = "false") boolean unreadOnly,
                                                     @RequestParam(required = false) Integer page,
                                                     @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(notifications.list(unreadOnly, PageParams.of(page, size)));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Number of unread notifications (poll this as a fallback to the stream)")
    ApiResponse<Map<String, Long>> unreadCount() {
        return ApiResponse.ok(Map.of("unread", notifications.unreadCount()));
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Mark one notification read")
    ApiResponse<Void> markRead(@PathVariable UUID id) {
        notifications.markRead(id);
        return ApiResponse.message("Marked as read");
    }

    @PostMapping("/read-all")
    @Operation(summary = "Mark all notifications read")
    ApiResponse<Map<String, Integer>> markAllRead() {
        return ApiResponse.ok(Map.of("updated", notifications.markAllRead()));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Server-sent events: 'notification' events as they happen",
            description = "Use a fetch-based EventSource so the Authorization header can be sent")
    SseEmitter stream() {
        return streams.open(AccessControl.requireUser().userId());
    }
}
