package com.orbitcrm.chat.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.orbitcrm.chat.dto.MessageView;
import com.orbitcrm.shared.tenant.TenantContext;
import com.orbitcrm.shared.util.UuidV7;

/**
 * JDBC access to conversations, participants and messages.
 *
 * <p>Purpose: keep all chat SQL in one place. Every statement filters by the current tenant explicitly, and
 * row-level security enforces the same rule underneath. Design points:
 * <ul>
 *   <li>Message times come from the database clock ({@code clock_timestamp()}), so ordering is consistent across
 *   server instances; history is paged by the keyset {@code (created_at, id)}.</li>
 *   <li>Sending is idempotent: {@link #insertMessage} ignores a repeated {@code (sender, client_id)} and the caller
 *   then returns the stored original.</li>
 *   <li>A direct conversation is unique per pair of users ({@code direct_key}); concurrent starts resolve to the same
 *   row.</li>
 * </ul>
 *
 * @author Abhay
 */
@Repository
public class ChatRepository {

    private static final String MESSAGE = "m.id AS m_id, m.conversation_id AS m_conversation_id, "
            + "m.sender_id AS m_sender_id, m.body AS m_body, m.client_id AS m_client_id, m.created_at AS m_created_at, "
            + "m.edited_at AS m_edited_at, m.deleted_at AS m_deleted_at";

    private final NamedParameterJdbcTemplate jdbc;

    ChatRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---- conversations -------------------------------------------------------------------------------------------

    /**
     * Returns the id of the direct conversation with {@code directKey}, creating it with its participants if new.
     * When two people start the same chat at once, the second insert waits for the first and then finds its row.
     */
    public UUID findOrCreateDirect(String directKey, UUID createdBy, List<UUID> participants) {
        Optional<UUID> existing = findDirect(directKey);
        if (existing.isPresent()) {
            return existing.get();
        }
        List<UUID> created = jdbc.queryForList("""
                INSERT INTO chat_conversations (id, tenant_id, kind, direct_key, created_by, created_at)
                VALUES (:id, :tenantId, 'DIRECT', :key, :createdBy, clock_timestamp())
                ON CONFLICT (tenant_id, direct_key) DO NOTHING
                RETURNING id
                """, tenant().addValue("id", UuidV7.generate()).addValue("key", directKey)
                .addValue("createdBy", createdBy), UUID.class);
        if (created.isEmpty()) {
            return findDirect(directKey).orElseThrow();
        }
        UUID id = created.get(0);
        jdbc.batchUpdate("""
                INSERT INTO chat_participants (tenant_id, conversation_id, user_id, joined_at)
                VALUES (:tenantId, :conversationId, :userId, clock_timestamp())
                """, participants.stream().map(userId -> tenant().addValue("conversationId", id)
                .addValue("userId", userId)).toArray(MapSqlParameterSource[]::new));
        return id;
    }

    private Optional<UUID> findDirect(String directKey) {
        return jdbc.queryForList("SELECT id FROM chat_conversations WHERE tenant_id = :tenantId AND direct_key = :key",
                tenant().addValue("key", directKey), UUID.class).stream().findFirst();
    }

    public List<UUID> participants(UUID conversationId) {
        return jdbc.queryForList("SELECT user_id FROM chat_participants WHERE tenant_id = :tenantId "
                + "AND conversation_id = :conversationId", tenant().addValue("conversationId", conversationId),
                UUID.class);
    }

    /**
     * The user's conversations, most recently active first, with the other participant, the newest message, the
     * unread count and both read positions - in one query. {@code conversationId} restricts it to one conversation.
     */
    public List<ConversationRow> conversations(UUID userId, UUID conversationId, int limit) {
        String only = conversationId == null ? "" : " AND c.id = :conversationId";
        return jdbc.query("""
                SELECT c.id, c.created_at, me.last_read_at, other.user_id AS other_id,
                       other.last_read_at AS their_last_read_at,
                       (SELECT count(*) FROM chat_messages u
                         WHERE u.tenant_id = c.tenant_id AND u.conversation_id = c.id AND u.sender_id <> me.user_id
                           AND u.deleted_at IS NULL AND (me.last_read_at IS NULL OR u.created_at > me.last_read_at)
                       ) AS unread,
                       %s
                FROM chat_participants me
                JOIN chat_conversations c ON c.tenant_id = me.tenant_id AND c.id = me.conversation_id
                JOIN chat_participants other ON other.tenant_id = me.tenant_id
                     AND other.conversation_id = me.conversation_id AND other.user_id <> me.user_id
                LEFT JOIN LATERAL (
                    SELECT * FROM chat_messages lm
                    WHERE lm.tenant_id = c.tenant_id AND lm.conversation_id = c.id
                    ORDER BY lm.created_at DESC, lm.id DESC LIMIT 1
                ) m ON true
                WHERE me.tenant_id = :tenantId AND me.user_id = :userId%s
                ORDER BY coalesce(c.last_message_at, c.created_at) DESC, c.id DESC
                LIMIT :limit
                """.formatted(MESSAGE, only), tenant().addValue("userId", userId)
                .addValue("conversationId", conversationId).addValue("limit", limit), (rs, n) -> new ConversationRow(
                rs.getObject("id", UUID.class), rs.getObject("other_id", UUID.class), rs.getLong("unread"),
                instant(rs, "last_read_at"), instant(rs, "their_last_read_at"), instant(rs, "created_at"),
                rs.getObject("m_id") == null ? null : message(rs)));
    }

    // ---- messages ------------------------------------------------------------------------------------------------

    public Optional<MessageView> findMessage(UUID id) {
        return single("SELECT " + MESSAGE + " FROM chat_messages m WHERE m.tenant_id = :tenantId AND m.id = :id",
                tenant().addValue("id", id));
    }

    public Optional<MessageView> findByClientId(UUID senderId, String clientId) {
        return single("SELECT " + MESSAGE + " FROM chat_messages m WHERE m.tenant_id = :tenantId "
                + "AND m.sender_id = :senderId AND m.client_id = :clientId",
                tenant().addValue("senderId", senderId).addValue("clientId", clientId));
    }

    /** Stores a message; empty when the sender already sent one with this {@code clientId}. */
    public Optional<MessageView> insertMessage(UUID id, UUID conversationId, UUID senderId, String clientId,
                                               String body) {
        return single("""
                INSERT INTO chat_messages AS m (id, tenant_id, conversation_id, sender_id, client_id, body, created_at)
                VALUES (:id, :tenantId, :conversationId, :senderId, :clientId, :body, clock_timestamp())
                ON CONFLICT (tenant_id, sender_id, client_id) DO NOTHING
                RETURNING\s""" + MESSAGE, tenant().addValue("id", id).addValue("conversationId", conversationId)
                .addValue("senderId", senderId).addValue("clientId", clientId).addValue("body", body));
    }

    /** Moves the conversation up the list and marks the sender's own message as read for them. */
    public void messageSent(UUID conversationId, UUID senderId, Instant at) {
        MapSqlParameterSource params = tenant().addValue("conversationId", conversationId)
                .addValue("senderId", senderId).addValue("at", OffsetDateTime.ofInstant(at, ZoneOffset.UTC));
        jdbc.update("UPDATE chat_conversations SET last_message_at = greatest(last_message_at, :at) "
                + "WHERE tenant_id = :tenantId AND id = :conversationId", params);
        jdbc.update("UPDATE chat_participants SET last_read_at = greatest(last_read_at, :at) "
                + "WHERE tenant_id = :tenantId AND conversation_id = :conversationId AND user_id = :senderId", params);
    }

    /** Up to {@code limit} messages, newest first, older than message {@code before} when given. */
    public List<MessageView> history(UUID conversationId, UUID before, int limit) {
        String olderThan = before == null ? "" : " AND (m.created_at, m.id) < (SELECT b.created_at, b.id "
                + "FROM chat_messages b WHERE b.tenant_id = :tenantId AND b.conversation_id = :conversationId "
                + "AND b.id = :before)";
        return jdbc.query("SELECT " + MESSAGE + " FROM chat_messages m WHERE m.tenant_id = :tenantId "
                + "AND m.conversation_id = :conversationId" + olderThan
                + " ORDER BY m.created_at DESC, m.id DESC LIMIT :limit", tenant()
                .addValue("conversationId", conversationId).addValue("before", before).addValue("limit", limit),
                (rs, n) -> message(rs));
    }

    /** Replaces the text of the sender's own, not deleted message. */
    public Optional<MessageView> edit(UUID id, UUID senderId, String body) {
        return single("UPDATE chat_messages m SET body = :body, edited_at = clock_timestamp() "
                + "WHERE m.tenant_id = :tenantId AND m.id = :id AND m.sender_id = :senderId AND m.deleted_at IS NULL "
                + "RETURNING " + MESSAGE, tenant().addValue("id", id).addValue("senderId", senderId)
                .addValue("body", body));
    }

    /** Deletes the sender's own message: the row keeps its place, the text is erased. */
    public Optional<MessageView> delete(UUID id, UUID senderId) {
        return single("UPDATE chat_messages m SET body = '', deleted_at = clock_timestamp() "
                + "WHERE m.tenant_id = :tenantId AND m.id = :id AND m.sender_id = :senderId AND m.deleted_at IS NULL "
                + "RETURNING " + MESSAGE, tenant().addValue("id", id).addValue("senderId", senderId));
    }

    // ---- read state ----------------------------------------------------------------------------------------------

    /**
     * Moves the user's read position forward to the message's time (never backwards); empty when the message is not
     * in the conversation.
     */
    public Optional<Instant> markRead(UUID conversationId, UUID userId, UUID messageId) {
        return jdbc.query("""
                UPDATE chat_participants p SET last_read_at = greatest(p.last_read_at, m.created_at)
                FROM chat_messages m
                WHERE p.tenant_id = :tenantId AND p.conversation_id = :conversationId AND p.user_id = :userId
                  AND m.tenant_id = :tenantId AND m.conversation_id = :conversationId AND m.id = :messageId
                RETURNING p.last_read_at
                """, tenant().addValue("conversationId", conversationId).addValue("userId", userId)
                .addValue("messageId", messageId), (rs, n) -> instant(rs, "last_read_at")).stream().findFirst();
    }

    /** Messages from others the user has not read, across all their conversations. */
    public long unreadTotal(UUID userId) {
        Long count = jdbc.queryForObject("""
                SELECT count(*) FROM chat_participants p
                JOIN chat_messages m ON m.tenant_id = p.tenant_id AND m.conversation_id = p.conversation_id
                WHERE p.tenant_id = :tenantId AND p.user_id = :userId AND m.sender_id <> :userId
                  AND m.deleted_at IS NULL AND (p.last_read_at IS NULL OR m.created_at > p.last_read_at)
                """, tenant().addValue("userId", userId), Long.class);
        return count == null ? 0 : count;
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    private Optional<MessageView> single(String sql, MapSqlParameterSource params) {
        return jdbc.query(sql, params, (rs, n) -> message(rs)).stream().findFirst();
    }

    private static MapSqlParameterSource tenant() {
        return new MapSqlParameterSource("tenantId", TenantContext.requireTenantId());
    }

    private static MessageView message(ResultSet rs) throws SQLException {
        boolean deleted = rs.getObject("m_deleted_at") != null;
        return new MessageView(rs.getObject("m_id", UUID.class), rs.getObject("m_conversation_id", UUID.class),
                rs.getObject("m_sender_id", UUID.class), deleted ? null : rs.getString("m_body"),
                rs.getString("m_client_id"), instant(rs, "m_created_at"), instant(rs, "m_edited_at"), deleted);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    /**
     * One conversation as stored, from the point of view of one participant.
     *
     * <p>Purpose: the raw material of {@link com.orbitcrm.chat.dto.ConversationView}; the service adds the other
     * person's name and presence.
     *
     * @param id              conversation id
     * @param otherUserId     the other participant
     * @param unreadCount     messages from others the user has not read
     * @param lastReadAt      the user's read position, or {@code null}
     * @param theirLastReadAt the other participant's read position, or {@code null}
     * @param createdAt       when the conversation was started
     * @param lastMessage     newest message, or {@code null}
     */
    public record ConversationRow(UUID id, UUID otherUserId, long unreadCount, Instant lastReadAt,
                                  Instant theirLastReadAt, Instant createdAt, MessageView lastMessage) {
    }
}
