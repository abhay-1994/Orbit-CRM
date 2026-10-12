package com.orbitcrm.chat.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * A direct conversation from the signed-in user's point of view.
 *
 * <p>Purpose: one row of the chat list and the header of an open chat. {@code theirLastReadAt} drives the "Seen"
 * receipt: every message of mine created at or before it has been read by the other person.
 *
 * @author Abhay
 * @param id              conversation id
 * @param with            the other participant
 * @param lastMessage     newest message, or {@code null} for a chat without messages yet
 * @param unreadCount     messages from the other person I have not read
 * @param lastReadAt      how far I have read, or {@code null}
 * @param theirLastReadAt how far the other person has read, or {@code null}
 * @param createdAt       when the conversation was started
 */
public record ConversationView(UUID id, ChatPerson with, MessageView lastMessage, long unreadCount,
                               Instant lastReadAt, Instant theirLastReadAt, Instant createdAt) {
}
