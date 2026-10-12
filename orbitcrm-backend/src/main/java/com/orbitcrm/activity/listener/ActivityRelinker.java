package com.orbitcrm.activity.listener;

import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import com.orbitcrm.record.event.RecordsRelinked;

/**
 * Moves activities to another record when a {@code RecordsRelinked} event is published.
 *
 * <p>Purpose: keep activities attached to the right customer record when records are transformed - for example, the
 * calls and meetings of a converted lead move to the new contact (and, later, a merged duplicate's activities move to
 * the surviving record). Runs inside the publishing transaction.
 *
 * @author Abhay
 */
@Component
public class ActivityRelinker {

    private final NamedParameterJdbcTemplate jdbc;

    ActivityRelinker(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener
    void onRelinked(RecordsRelinked event) {
        jdbc.update("UPDATE activities SET related_module = :toModule, related_id = :toId WHERE tenant_id = :tenantId "
                        + "AND related_module = :fromModule AND related_id = :fromId",
                new MapSqlParameterSource("tenantId", event.tenantId()).addValue("toModule", event.toModule())
                        .addValue("toId", event.toId()).addValue("fromModule", event.fromModule())
                        .addValue("fromId", event.fromId()));
    }
}
