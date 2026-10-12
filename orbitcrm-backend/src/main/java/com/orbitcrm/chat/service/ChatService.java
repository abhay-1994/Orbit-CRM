package com.orbitcrm.chat.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbitcrm.chat.dto.ChatPerson;
import com.orbitcrm.chat.dto.ConversationView;
import com.orbitcrm.chat.dto.MessagePage;
import com.orbitcrm.chat.dto.MessageView;
import com.orbitcrm.chat.dto.PresenceStatus;
import com.orbitcrm.chat.dto.PresenceView;
import com.orbitcrm.chat.dto.SendMessageRequest;
import com.orbitcrm.chat.realtime.ChatEvent;
import com.orbitcrm.chat.realtime.ChatEventPublisher;
import com.orbitcrm.chat.repository.ChatRepository;
import com.orbitcrm.chat.repository.ChatRepository.ConversationRow;
import com.orbitcrm.identity.dto.UserLookup;
import com.orbitcrm.identity.dto.UserSummary;
import com.orbitcrm.identity.service.UserDirectory;
import com.orbitcrm.identity.service.UserService;
import com.orbitcrm.shared.exception.ApiException;
import com.orbitcrm.shared.security.AccessControl;
import com.orbitcrm.shared.util.UuidV7;

/**
 * Direct messages between colleagues of one workspace.
 *
 * <p>Purpose: the business rules of chat, always from the signed-in user's point of view.
 * <ul>
 *   <li>Every signed-in user may chat with any active colleague; each pair has exactly one conversation.</li>
 *   <li>Only participants can see a conversation; to everyone else it does not exist (404).</li>
 *   <li>Sending is idempotent by {@code clientId}; a retry returns the original message ({@link Sent#created()}
 *   false). Messages cannot be sent to a colleague who has been disabled.</li>
 *   <li>Only the author can edit or delete a message; a deleted message keeps its place without its text.</li>
 *   <li>Read positions only move forward and are shared as read receipts.</li>
 *   <li>Every change is pushed live to the participants (all their tabs) after commit through
 *   {@link ChatEventPublisher}; typing indicators are pushed but never stored.</li>
 * </ul>
 *
 * @author Abhay
 */
@Service
public class ChatService {

    static final int MAX_CONVERSATIONS = 100;
    static final int MAX_PAGE = 100;

    private final ChatRepository chats;
    private final PresenceService presence;
    private final UserDirectory users;
    private final UserService userService;
    private final ChatEventPublisher events;

    ChatService(ChatRepository chats, PresenceService presence, UserDirectory users, UserService userService,
                ChatEventPublisher events) {
        this.chats = chats;
        this.presence = presence;
        this.users = users;
        this.userService = userService;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<ConversationView> conversations() {
        return views(chats.conversations(me(), null, MAX_CONVERSATIONS));
    }

    @Transactional(readOnly = true)
    public ConversationView conversation(UUID conversationId) {
        return views(chats.conversations(me(), conversationId, 1)).stream().findFirst()
                .orElseThrow(ChatService::conversationNotFound);
    }

    /** Returns the direct conversation with a colleague, starting it if needed. */
    @Transactional
    public ConversationView start(UUID userId) {
        UUID me = me();
        if (me.equals(userId)) {
            throw ApiException.badRequest("CHAT_WITH_SELF", "You cannot start a chat with yourself");
        }
        if (!users.isActive(userId)) {
            throw ApiException.notFound("USER_NOT_FOUND", "User does not exist");
        }
        UUID id = chats.findOrCreateDirect(directKey(me, userId), me, List.of(me, userId));
        return conversation(id);
    }

    /** A page of history, oldest first; {@code before} is the id of the oldest message already loaded. */
    @Transactional(readOnly = true)
    public MessagePage messages(UUID conversationId, UUID before, int limit) {
        requireParticipants(conversationId);
        int size = Math.clamp(limit, 1, MAX_PAGE);
        List<MessageView> newestFirst = chats.history(conversationId, before, size + 1);
        List<MessageView> page = new ArrayList<>(newestFirst.subList(0, Math.min(size, newestFirst.size())));
        Collections.reverse(page);
        return new MessagePage(page, newestFirst.size() > size);
    }

    @Transactional
    public Sent send(UUID conversationId, SendMessageRequest request) {
        UUID me = me();
        List<UUID> participants = requireParticipants(conversationId);
        Optional<MessageView> previous = chats.findByClientId(me, request.clientId());
        if (previous.isPresent()) {
            return replay(previous.get(), conversationId);
        }
        participants.stream().filter(id -> !id.equals(me) && !users.isActive(id)).findAny().ifPresent(id -> {
            throw ApiException.conflict("CHAT_RECIPIENT_INACTIVE", "This person can no longer receive messages");
        });
        Optional<MessageView> inserted = chats.insertMessage(UuidV7.generate(), conversationId, me, request.clientId(),
                request.body().strip());
        if (inserted.isEmpty()) {
            // A concurrent retry with the same clientId stored it first.
            return replay(chats.findByClientId(me, request.clientId()).orElseThrow(), conversationId);
        }
        MessageView message = inserted.get();
        chats.messageSent(conversationId, me, message.createdAt());
        events.publish(participants, ChatEvent.MESSAGE_CREATED, ids(message));
        return new Sent(message, true);
    }

    @Transactional
    public MessageView edit(UUID messageId, String body) {
        MessageView message = chats.edit(messageId, me(), body.strip()).orElseThrow(ChatService::messageNotFound);
        events.publish(chats.participants(message.conversationId()), ChatEvent.MESSAGE_UPDATED, ids(message));
        return message;
    }

    @Transactional
    public void delete(UUID messageId) {
        MessageView message = chats.delete(messageId, me()).orElseThrow(ChatService::messageNotFound);
        events.publish(chats.participants(message.conversationId()), ChatEvent.MESSAGE_UPDATED, ids(message));
    }

    /** Marks the conversation read up to a message and sends a read receipt; returns the new read position. */
    @Transactional
    public Instant markRead(UUID conversationId, UUID messageId) {
        UUID me = me();
        List<UUID> participants = requireParticipants(conversationId);
        Instant lastReadAt = chats.markRead(conversationId, me, messageId).orElseThrow(ChatService::messageNotFound);
        events.publish(participants, ChatEvent.READ,
                Map.of("conversationId", conversationId, "userId", me, "lastReadAt", lastReadAt));
        return lastReadAt;
    }

    /** Tells the other participants that the signed-in user is typing. */
    @Transactional
    public void typing(UUID conversationId) {
        UUID me = me();
        List<UUID> others = requireParticipants(conversationId).stream().filter(id -> !id.equals(me)).toList();
        events.publish(others, ChatEvent.TYPING, Map.of("conversationId", conversationId, "userId", me));
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return chats.unreadTotal(me());
    }

    /** Active colleagues matching {@code query} (name or email), with their presence. */
    @Transactional(readOnly = true)
    public List<ChatPerson> people(String query, int limit) {
        UUID me = me();
        int size = Math.clamp(limit, 1, 50);
        List<UserLookup> found = userService.lookup(query, size + 1).stream()
                .filter(u -> !u.id().equals(me)).limit(size).toList();
        Map<UUID, PresenceStatus> statuses = statuses(found.stream().map(UserLookup::id).toList());
        return found.stream().map(u -> new ChatPerson(u.id(), u.fullName(), u.email(), true, statuses.get(u.id())))
                .toList();
    }

    private List<ConversationView> views(List<ConversationRow> rows) {
        List<UUID> others = rows.stream().map(ConversationRow::otherUserId).distinct().toList();
        Map<UUID, UserSummary> people = users.summaries(others);
        Map<UUID, PresenceStatus> statuses = statuses(others);
        return rows.stream().map(row -> {
            UserSummary person = people.get(row.otherUserId());
            ChatPerson with = person == null
                    ? new ChatPerson(row.otherUserId(), "Unknown user", null, false, PresenceStatus.OFFLINE)
                    : new ChatPerson(person.id(), person.displayName(), person.email(),
                    "ACTIVE".equals(person.status()), statuses.get(person.id()));
            return new ConversationView(row.id(), with, row.lastMessage(), row.unreadCount(), row.lastReadAt(),
                    row.theirLastReadAt(), row.createdAt());
        }).toList();
    }

    private Map<UUID, PresenceStatus> statuses(List<UUID> userIds) {
        return presence.presenceOf(userIds).stream()
                .collect(Collectors.toMap(PresenceView::userId, PresenceView::status));
    }

    /** The conversation's participants; 404 unless the signed-in user is one of them. */
    private List<UUID> requireParticipants(UUID conversationId) {
        List<UUID> participants = chats.participants(conversationId);
        if (!participants.contains(me())) {
            throw conversationNotFound();
        }
        return participants;
    }

    private static Sent replay(MessageView message, UUID conversationId) {
        if (!message.conversationId().equals(conversationId)) {
            throw ApiException.conflict("CLIENT_ID_REUSED", "This clientId was already used for another message");
        }
        return new Sent(message, false);
    }

    private static Map<String, UUID> ids(MessageView message) {
        return Map.of("conversationId", message.conversationId(), "messageId", message.id());
    }

    /** Key of the direct conversation between two users: both ids in sorted order. */
    static String directKey(UUID a, UUID b) {
        return a.toString().compareTo(b.toString()) < 0 ? a + ":" + b : b + ":" + a;
    }

    private static UUID me() {
        return AccessControl.requireUser().userId();
    }

    private static ApiException conversationNotFound() {
        return ApiException.notFound("CONVERSATION_NOT_FOUND", "Conversation does not exist");
    }

    private static ApiException messageNotFound() {
        return ApiException.notFound("MESSAGE_NOT_FOUND", "Message does not exist or cannot be changed");
    }

    /**
     * Result of sending a message.
     *
     * @param message the stored message
     * @param created true if this call stored it, false if it was an idempotent retry
     */
    public record Sent(MessageView message, boolean created) {
    }
}
