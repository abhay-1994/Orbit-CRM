package com.orbitcrm.chat.controller;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.orbitcrm.chat.dto.ActivityRequest;
import com.orbitcrm.chat.dto.ChatPerson;
import com.orbitcrm.chat.dto.ConversationView;
import com.orbitcrm.chat.dto.EditMessageRequest;
import com.orbitcrm.chat.dto.MarkReadRequest;
import com.orbitcrm.chat.dto.MessagePage;
import com.orbitcrm.chat.dto.MessageView;
import com.orbitcrm.chat.dto.PresenceView;
import com.orbitcrm.chat.dto.SendMessageRequest;
import com.orbitcrm.chat.dto.SetPresenceRequest;
import com.orbitcrm.chat.dto.StartConversationRequest;
import com.orbitcrm.chat.realtime.ChatHub;
import com.orbitcrm.chat.service.ChatService;
import com.orbitcrm.chat.service.PresenceService;
import com.orbitcrm.shared.dto.ApiResponse;
import com.orbitcrm.shared.exception.ApiException;
import com.orbitcrm.shared.security.AccessControl;

/**
 * REST controller for in-app chat and presence at {@code /api/v1/chat}.
 *
 * <p>Purpose: direct messages between colleagues with Teams-style presence, available to every signed-in user.
 * <ul>
 *   <li>{@code GET /stream} - the live server-sent event stream: {@code ready} (connection id and own presence),
 *   {@code message.created}, {@code message.updated}, {@code typing}, {@code read}, {@code presence} and
 *   {@code resync} (refetch, events may have been missed). Use a fetch-based EventSource so the
 *   {@code Authorization} header can be sent; reconnect when it ends and refetch to catch up.
 *   {@code DELETE /stream/{connectionId}} closes a stream when its tab goes away (sent with fetch keepalive).</li>
 *   <li>Conversations: {@code GET /conversations}, {@code POST /conversations} (start or reopen the chat with a
 *   colleague), {@code GET /conversations/{id}}.</li>
 *   <li>Messages: {@code GET /conversations/{id}/messages?before=} (keyset pages, oldest first),
 *   {@code POST /conversations/{id}/messages} (201, or 200 for an idempotent retry with the same
 *   {@code clientId}), {@code PATCH /messages/{id}} and {@code DELETE /messages/{id}} (own messages only).</li>
 *   <li>{@code POST /conversations/{id}/read} - read receipt; {@code POST /conversations/{id}/typing} - typing
 *   indicator (call at most every few seconds while typing); {@code GET /unread-count} - the badge number.</li>
 *   <li>Presence: {@code GET /presence?userIds=}, {@code PUT /presence} (choose a status; {@code AVAILABLE} resets),
 *   {@code POST /presence/activity} (a tab went idle or active), {@code GET /people?q=} (colleagues with
 *   presence).</li>
 * </ul>
 *
 * @author Abhay
 */
@RestController
@RequestMapping("/api/v1/chat")
@Tag(name = "Chat", description = "Direct messages, Teams-style presence and a live server-sent event stream")
public class ChatController {

    private static final int MAX_PRESENCE_USERS = 200;

    private final ChatService chat;
    private final PresenceService presence;
    private final ChatHub hub;

    ChatController(ChatService chat, PresenceService presence, ChatHub hub) {
        this.chat = chat;
        this.presence = presence;
        this.hub = hub;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Live chat events (server-sent events)",
            description = "Use a fetch-based EventSource so the Authorization header can be sent")
    SseEmitter stream() {
        return hub.open(AccessControl.requireUser());
    }

    @DeleteMapping("/stream/{connectionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Close one of your live streams (the tab is closing)",
            description = "Send with fetch keepalive on pagehide, so colleagues see you go offline right away")
    void closeStream(@PathVariable UUID connectionId) {
        hub.disconnect(AccessControl.requireUser(), connectionId);
    }

    @GetMapping("/conversations")
    @Operation(summary = "Your conversations, most recently active first")
    ApiResponse<List<ConversationView>> conversations() {
        return ApiResponse.ok(chat.conversations());
    }

    @PostMapping("/conversations")
    @Operation(summary = "Start or reopen the direct chat with a colleague")
    ApiResponse<ConversationView> start(@Valid @RequestBody StartConversationRequest request) {
        return ApiResponse.ok(chat.start(request.userId()));
    }

    @GetMapping("/conversations/{id}")
    @Operation(summary = "One conversation")
    ApiResponse<ConversationView> conversation(@PathVariable UUID id) {
        return ApiResponse.ok(chat.conversation(id));
    }

    @GetMapping("/conversations/{id}/messages")
    @Operation(summary = "Message history, oldest first; pass the oldest loaded message id as 'before' for more")
    ApiResponse<MessagePage> messages(@PathVariable UUID id, @RequestParam(required = false) UUID before,
                                      @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(chat.messages(id, before, limit));
    }

    @PostMapping("/conversations/{id}/messages")
    @Operation(summary = "Send a message (201; 200 when retried with the same clientId)")
    ResponseEntity<ApiResponse<MessageView>> send(@PathVariable UUID id,
                                                  @Valid @RequestBody SendMessageRequest request) {
        ChatService.Sent sent = chat.send(id, request);
        return ResponseEntity.status(sent.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ApiResponse.ok(sent.message()));
    }

    @PatchMapping("/messages/{id}")
    @Operation(summary = "Edit one of your messages")
    ApiResponse<MessageView> edit(@PathVariable UUID id, @Valid @RequestBody EditMessageRequest request) {
        return ApiResponse.ok(chat.edit(id, request.body()));
    }

    @DeleteMapping("/messages/{id}")
    @Operation(summary = "Delete one of your messages")
    ApiResponse<Void> delete(@PathVariable UUID id) {
        chat.delete(id);
        return ApiResponse.message("Message deleted");
    }

    @PostMapping("/conversations/{id}/read")
    @Operation(summary = "Mark the conversation read up to a message (sends a read receipt)")
    ApiResponse<Map<String, Instant>> markRead(@PathVariable UUID id, @Valid @RequestBody MarkReadRequest request) {
        return ApiResponse.ok(Map.of("lastReadAt", chat.markRead(id, request.messageId())));
    }

    @PostMapping("/conversations/{id}/typing")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Tell the other participant you are typing")
    void typing(@PathVariable UUID id) {
        chat.typing(id);
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Number of unread chat messages")
    ApiResponse<Map<String, Long>> unreadCount() {
        return ApiResponse.ok(Map.of("unread", chat.unreadCount()));
    }

    @GetMapping("/people")
    @Operation(summary = "Active colleagues by name or email, with presence")
    ApiResponse<List<ChatPerson>> people(@RequestParam(required = false) String q,
                                         @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(chat.people(q, limit));
    }

    @GetMapping("/presence")
    @Operation(summary = "Presence of up to 200 users")
    ApiResponse<List<PresenceView>> presence(@RequestParam List<UUID> userIds) {
        if (userIds.size() > MAX_PRESENCE_USERS) {
            throw ApiException.badRequest("TOO_MANY_USERS", "Ask for at most " + MAX_PRESENCE_USERS + " users");
        }
        return ApiResponse.ok(presence.presenceOf(userIds));
    }

    @PutMapping("/presence")
    @Operation(summary = "Choose your status; AVAILABLE returns to automatic presence")
    ApiResponse<PresenceView> choose(@Valid @RequestBody SetPresenceRequest request) {
        return ApiResponse.ok(presence.choose(request.status()));
    }

    @PostMapping("/presence/activity")
    @Operation(summary = "Report that a tab went idle or became active again")
    ApiResponse<PresenceView> activity(@Valid @RequestBody ActivityRequest request) {
        return ApiResponse.ok(presence.reportActivity(request.connectionId(), request.idle()));
    }
}
