package com.orbitcrm.chat.dto;

import java.util.UUID;

/**
 * A colleague as shown in chat: in the people picker and as the other side of a conversation.
 *
 * @author Abhay
 * @param id       user id
 * @param name     display name
 * @param email    email address
 * @param active   false once the user has been disabled; their chat history stays readable but no new messages
 *                 can be sent to them
 * @param presence effective presence
 */
public record ChatPerson(UUID id, String name, String email, boolean active, PresenceStatus presence) {
}
