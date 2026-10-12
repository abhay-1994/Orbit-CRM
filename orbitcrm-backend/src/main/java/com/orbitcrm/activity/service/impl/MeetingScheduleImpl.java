package com.orbitcrm.activity.service.impl;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.orbitcrm.activity.config.ActivityModule;
import com.orbitcrm.activity.service.MeetingSchedule;
import com.orbitcrm.shared.tenant.TenantContext;

/**
 * {@link MeetingSchedule} answered with single queries on the {@code activities} table.
 *
 * <p>Purpose: find, in one round trip each, who is in a meeting now and whose meeting started or ended in a window.
 * The owner and every participant of a meeting count as attendees. "Now" is the database clock, so all server
 * instances agree.
 *
 * @author Abhay
 */
@Component
class MeetingScheduleImpl implements MeetingSchedule {

    /** Open, non-deleted meetings of the current tenant, one row per attendee ({@code person.id}). */
    private static final String ATTENDEES = """
            SELECT DISTINCT person.id
            FROM activities a
            CROSS JOIN LATERAL unnest(array_append(coalesce(a.participant_ids, '{}'::uuid[]), a.owner_id)) AS person(id)
            WHERE a.tenant_id = :tenantId AND a.deleted_at IS NULL AND a.type = 'meeting' AND a.status NOT IN (:closed)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    MeetingScheduleImpl(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> inMeetingNow(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Set.of();
        }
        return query(ATTENDEES + " AND a.start_at <= now() AND a.end_at > now() AND person.id IN (:userIds)",
                params().addValue("userIds", userIds));
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> meetingChangesBetween(Instant from, Instant to) {
        return query(ATTENDEES + " AND ((a.start_at >= :from AND a.start_at < :to) OR (a.end_at >= :from AND a.end_at < :to))",
                params().addValue("from", OffsetDateTime.ofInstant(from, ZoneOffset.UTC))
                        .addValue("to", OffsetDateTime.ofInstant(to, ZoneOffset.UTC)));
    }

    private Set<UUID> query(String sql, MapSqlParameterSource params) {
        return new HashSet<>(jdbc.queryForList(sql, params, UUID.class));
    }

    private static MapSqlParameterSource params() {
        return new MapSqlParameterSource("tenantId", TenantContext.requireTenantId())
                .addValue("closed", Set.of(ActivityModule.STATUS_COMPLETED, ActivityModule.STATUS_CANCELLED));
    }
}
