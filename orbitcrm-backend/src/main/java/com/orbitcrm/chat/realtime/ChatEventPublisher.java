package com.orbitcrm.chat.realtime;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

import com.orbitcrm.shared.tenant.TenantContext;

/**
 * Publishes live chat events to all server instances through PostgreSQL {@code NOTIFY}.
 *
 * <p>Purpose: fan events out across instances without extra infrastructure. {@code pg_notify} is transactional:
 * called inside a transaction, the event is delivered only when it commits and is dropped on rollback, so a
 * recipient can never be told about a message that was not stored. Every instance (including this one) receives it
 * through {@link ChatEventListener} and forwards it to the streams it holds - one delivery path for all cases.
 *
 * @author Abhay
 */
@Component
public class ChatEventPublisher {

    static final String CHANNEL = "orbitcrm_chat";

    private static final Logger log = LoggerFactory.getLogger(ChatEventPublisher.class);
    /** PostgreSQL rejects NOTIFY payloads of 8000 bytes or more. */
    private static final int MAX_PAYLOAD_BYTES = 7900;

    private final NamedParameterJdbcTemplate jdbc;
    private final JsonMapper json;

    ChatEventPublisher(NamedParameterJdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /**
     * Publishes an event in the current tenant, on commit of the current transaction (immediately without one).
     *
     * @param recipients users to deliver to, or {@code null} for everyone in the workspace
     * @param type       one of the {@link ChatEvent} types
     * @param data       event data, serialized as JSON
     */
    public void publish(Collection<UUID> recipients, String type, Object data) {
        ChatEvent event = new ChatEvent(TenantContext.requireTenantId(),
                recipients == null ? null : List.copyOf(recipients), type, json.valueToTree(data));
        String payload = json.writeValueAsString(event);
        if (payload.getBytes(StandardCharsets.UTF_8).length > MAX_PAYLOAD_BYTES) {
            log.warn("Dropping chat event {} for {} recipients: payload too large", type,
                    recipients == null ? "all" : recipients.size());
            return;
        }
        jdbc.query("SELECT pg_notify(:channel, :payload)", Map.of("channel", CHANNEL, "payload", payload),
                rs -> null);
    }
}
