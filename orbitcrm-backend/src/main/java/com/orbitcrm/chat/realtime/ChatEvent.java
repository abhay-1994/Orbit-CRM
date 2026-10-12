package com.orbitcrm.chat.realtime;

import java.util.List;
import java.util.UUID;

import tools.jackson.databind.JsonNode;

/**
 * A live chat event on its way from the server that caused it to every server holding a recipient's stream.
 *
 * <p>Purpose: the wire format of the {@code orbitcrm_chat} PostgreSQL NOTIFY channel. It stays small (NOTIFY payloads
 * are limited to 8000 bytes): message events carry only ids and each receiving server loads the message itself.
 * Event types, which are also the names of the server-sent events the browser receives:
 * <ul>
 *   <li>{@link #MESSAGE_CREATED} / {@link #MESSAGE_UPDATED} - {@code {conversationId, messageId}}, delivered as the
 *   full message (an update is an edit or a delete);</li>
 *   <li>{@link #TYPING} - {@code {conversationId, userId}}, sent to the other participants;</li>
 *   <li>{@link #READ} - {@code {conversationId, userId, lastReadAt}}, a read receipt for all participants;</li>
 *   <li>{@link #PRESENCE} - a {@code PresenceView}, sent to everyone in the workspace.</li>
 * </ul>
 *
 * @author Abhay
 * @param tenantId   workspace the event belongs to; never delivered outside it
 * @param recipients users to deliver to, or {@code null} for everyone in the workspace
 * @param type       event type
 * @param data       event data
 */
public record ChatEvent(UUID tenantId, List<UUID> recipients, String type, JsonNode data) {

    public static final String MESSAGE_CREATED = "message.created";
    public static final String MESSAGE_UPDATED = "message.updated";
    public static final String TYPING = "typing";
    public static final String READ = "read";
    public static final String PRESENCE = "presence";

    boolean isFor(UUID tenant, UUID user) {
        return tenantId.equals(tenant) && (recipients == null || recipients.contains(user));
    }

    boolean isMessage() {
        return MESSAGE_CREATED.equals(type) || MESSAGE_UPDATED.equals(type);
    }
}
