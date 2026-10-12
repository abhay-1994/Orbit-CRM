package com.orbitcrm.chat.repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.orbitcrm.chat.dto.PresenceStatus;
import com.orbitcrm.shared.tenant.TenantContext;

/**
 * JDBC access to live connections ({@code chat_connections}) and chosen statuses ({@code user_presence}).
 *
 * <p>Purpose: provide the facts presence is computed from. A connection row exists per open live stream and its
 * {@code last_heartbeat} is refreshed by the server that holds the stream; a person is online while at least one of
 * their rows is younger than {@link #CONNECTION_TTL_SECONDS}. Rows of a crashed server simply stop being refreshed,
 * so the person goes offline everywhere without any coordination. Most methods work in the current tenant;
 * {@link #touch}, {@link #deleteStale} and {@link #tenantsOnline} are cross-tenant and must run in system context.
 *
 * @author Abhay
 */
@Repository
public class PresenceRepository {

    /** A connection without a heartbeat for this long is dead (servers refresh every 25 seconds). */
    public static final int CONNECTION_TTL_SECONDS = 75;

    private static final int BATCH = 1000;

    private final NamedParameterJdbcTemplate jdbc;

    PresenceRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insertConnection(UUID id, UUID userId, String instanceId) {
        jdbc.update("""
                INSERT INTO chat_connections (id, tenant_id, user_id, instance_id, connected_at, last_heartbeat)
                VALUES (:id, :tenantId, :userId, :instanceId, now(), now())
                """, tenant().addValue("id", id).addValue("userId", userId).addValue("instanceId", instanceId));
    }

    public void deleteConnection(UUID id) {
        jdbc.update("DELETE FROM chat_connections WHERE tenant_id = :tenantId AND id = :id", tenant().addValue("id", id));
    }

    /** Deletes one of the user's own connections; false if it does not exist or belongs to someone else. */
    public boolean deleteConnection(UUID id, UUID userId) {
        return jdbc.update("DELETE FROM chat_connections WHERE tenant_id = :tenantId AND id = :id AND user_id = :userId",
                tenant().addValue("id", id).addValue("userId", userId)) == 1;
    }

    /** Marks one of the user's connections idle or active; false if it no longer exists. */
    public boolean setIdle(UUID id, UUID userId, boolean idle) {
        return jdbc.update("UPDATE chat_connections SET idle = :idle WHERE tenant_id = :tenantId AND id = :id "
                + "AND user_id = :userId", tenant().addValue("id", id).addValue("userId", userId)
                .addValue("idle", idle)) == 1;
    }

    /** Online users among {@code userIds}, mapped to whether all of their connections are idle. */
    public Map<UUID, Boolean> onlineIdle(Collection<UUID> userIds) {
        Map<UUID, Boolean> online = new HashMap<>();
        jdbc.query("""
                SELECT user_id, bool_and(idle) AS idle FROM chat_connections
                WHERE tenant_id = :tenantId AND user_id IN (:userIds)
                  AND last_heartbeat > now() - make_interval(secs => :ttl)
                GROUP BY user_id
                """, tenant().addValue("userIds", userIds).addValue("ttl", CONNECTION_TTL_SECONDS),
                rs -> {
                    online.put(rs.getObject("user_id", UUID.class), rs.getBoolean("idle"));
                });
        return online;
    }

    /** Statuses chosen by the given users; users on automatic presence are omitted. */
    public Map<UUID, PresenceStatus> chosenStatuses(Collection<UUID> userIds) {
        Map<UUID, PresenceStatus> chosen = new HashMap<>();
        jdbc.query("SELECT user_id, manual_status FROM user_presence WHERE tenant_id = :tenantId "
                + "AND user_id IN (:userIds) AND manual_status IS NOT NULL", tenant().addValue("userIds", userIds),
                rs -> {
                    chosen.put(rs.getObject("user_id", UUID.class), PresenceStatus.valueOf(rs.getString("manual_status")));
                });
        return chosen;
    }

    /** Saves the user's chosen status; {@code null} returns them to automatic presence. */
    public void saveChosenStatus(UUID userId, PresenceStatus status) {
        jdbc.update("""
                INSERT INTO user_presence (tenant_id, user_id, manual_status, updated_at)
                VALUES (:tenantId, :userId, :status, now())
                ON CONFLICT (tenant_id, user_id) DO UPDATE SET manual_status = EXCLUDED.manual_status, updated_at = now()
                """, tenant().addValue("userId", userId).addValue("status", status == null ? null : status.name()));
    }

    // ---- cross-tenant (system context) ---------------------------------------------------------------------------

    /** Refreshes the heartbeat of the given connections; returns the ids that still exist. */
    public Set<UUID> touch(Collection<UUID> connectionIds) {
        List<UUID> ids = List.copyOf(connectionIds);
        Set<UUID> alive = new HashSet<>();
        for (int from = 0; from < ids.size(); from += BATCH) {
            alive.addAll(jdbc.queryForList("UPDATE chat_connections SET last_heartbeat = now() WHERE id IN (:ids) "
                    + "RETURNING id", new MapSqlParameterSource("ids",
                    ids.subList(from, Math.min(from + BATCH, ids.size()))), UUID.class));
        }
        return alive;
    }

    /** Deletes connections whose server stopped refreshing them; returns their tenant and user. */
    public List<TenantUser> deleteStale() {
        return jdbc.query("DELETE FROM chat_connections WHERE last_heartbeat <= now() - make_interval(secs => :ttl) "
                + "RETURNING tenant_id, user_id", new MapSqlParameterSource("ttl", CONNECTION_TTL_SECONDS),
                (rs, n) -> new TenantUser(rs.getObject("tenant_id", UUID.class), rs.getObject("user_id", UUID.class)));
    }

    /** Tenants with at least one live connection. */
    public List<UUID> tenantsOnline() {
        return jdbc.queryForList("SELECT DISTINCT tenant_id FROM chat_connections", Map.of(), UUID.class);
    }

    private static MapSqlParameterSource tenant() {
        return new MapSqlParameterSource("tenantId", TenantContext.requireTenantId());
    }

    /**
     * A user together with their tenant, as found by a cross-tenant scan.
     *
     * @param tenantId tenant id
     * @param userId   user id
     */
    public record TenantUser(UUID tenantId, UUID userId) {
    }
}
