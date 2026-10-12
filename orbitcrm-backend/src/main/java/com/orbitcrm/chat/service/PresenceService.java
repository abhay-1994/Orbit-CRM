package com.orbitcrm.chat.service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbitcrm.activity.service.MeetingSchedule;
import com.orbitcrm.chat.dto.PresenceStatus;
import com.orbitcrm.chat.dto.PresenceView;
import com.orbitcrm.chat.realtime.ChatEvent;
import com.orbitcrm.chat.realtime.ChatEventPublisher;
import com.orbitcrm.chat.repository.PresenceRepository;
import com.orbitcrm.chat.repository.PresenceRepository.TenantUser;
import com.orbitcrm.shared.exception.ApiException;
import com.orbitcrm.shared.security.AccessControl;

/**
 * Teams-style presence: computes who is Available, Busy, In a meeting, Away or Offline and broadcasts changes.
 *
 * <p>Purpose: combine three facts into one status (rules in {@link PresenceStatus}) - live connections and their
 * idle flags (any server instance), the status a person chose, and meetings in progress (activity module). Presence
 * is computed on demand rather than stored, so it can never go stale; every event that may change it (stream
 * opened or closed, status chosen, tab idle or active, meeting started or ended, connection expired) broadcasts the
 * recomputed status to the workspace through {@link ChatEventPublisher}, on commit.
 *
 * @author Abhay
 */
@Service
public class PresenceService {

    private final PresenceRepository repository;
    private final MeetingSchedule meetings;
    private final ChatEventPublisher events;

    PresenceService(PresenceRepository repository, MeetingSchedule meetings, ChatEventPublisher events) {
        this.repository = repository;
        this.meetings = meetings;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<PresenceView> presenceOf(Collection<UUID> userIds) {
        Set<UUID> ids = new LinkedHashSet<>(userIds);
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<UUID, Boolean> online = repository.onlineIdle(ids);
        Map<UUID, PresenceStatus> chosen = online.isEmpty() ? Map.of() : repository.chosenStatuses(online.keySet());
        Set<UUID> automatic = online.keySet().stream().filter(id -> !chosen.containsKey(id))
                .collect(Collectors.toSet());
        Set<UUID> inMeeting = automatic.isEmpty() ? Set.of() : meetings.inMeetingNow(automatic);
        return ids.stream().map(id -> new PresenceView(id, resolve(online.containsKey(id), chosen.get(id),
                inMeeting.contains(id), Boolean.TRUE.equals(online.get(id))))).toList();
    }

    /** Chooses a status for the signed-in user ({@code AVAILABLE} = automatic) and broadcasts it. */
    @Transactional
    public PresenceView choose(PresenceStatus status) {
        if (status == PresenceStatus.IN_A_MEETING) {
            throw ApiException.badRequest("PRESENCE_NOT_SELECTABLE", "'In a meeting' is set automatically from your calendar");
        }
        UUID me = AccessControl.requireUser().userId();
        repository.saveChosenStatus(me, status == PresenceStatus.AVAILABLE ? null : status);
        return publish(List.of(me)).get(0);
    }

    /** Records whether one of the signed-in user's tabs is idle and broadcasts the resulting presence. */
    @Transactional
    public PresenceView reportActivity(UUID connectionId, boolean idle) {
        UUID me = AccessControl.requireUser().userId();
        if (!repository.setIdle(connectionId, me, idle)) {
            throw ApiException.notFound("CHAT_CONNECTION_NOT_FOUND", "The live connection has closed; reconnect");
        }
        return publish(List.of(me)).get(0);
    }

    /** Recomputes the presence of the given users and broadcasts it to their workspace on commit. */
    @Transactional
    public List<PresenceView> publish(Collection<UUID> userIds) {
        List<PresenceView> views = presenceOf(userIds);
        views.forEach(view -> events.publish(null, ChatEvent.PRESENCE, view));
        return views;
    }

    // ---- connection lifecycle (called by the chat hub and the presence job) --------------------------------------

    /** Registers a newly opened stream and broadcasts that its user is online. */
    @Transactional
    public PresenceView connected(UUID connectionId, UUID userId, String instanceId) {
        repository.insertConnection(connectionId, userId, instanceId);
        return publish(List.of(userId)).get(0);
    }

    @Transactional
    public void disconnected(UUID connectionId) {
        repository.deleteConnection(connectionId);
    }

    /** Removes one of the signed-in user's connections held by another instance; false if it is not theirs. */
    @Transactional
    public boolean disconnectOwn(UUID connectionId, UUID userId) {
        return repository.deleteConnection(connectionId, userId);
    }

    /** Refreshes the heartbeat of streams held by this instance; returns the ids still registered. System context. */
    @Transactional
    public Set<UUID> heartbeat(Collection<UUID> connectionIds) {
        return repository.touch(connectionIds);
    }

    /** Removes connections whose server stopped refreshing them. System context. */
    @Transactional
    public List<TenantUser> removeStaleConnections() {
        return repository.deleteStale();
    }

    /** Tenants with at least one live connection. System context. */
    @Transactional(readOnly = true)
    public List<UUID> tenantsOnline() {
        return repository.tenantsOnline();
    }

    static PresenceStatus resolve(boolean online, PresenceStatus chosen, boolean inMeeting, boolean idle) {
        if (!online) {
            return PresenceStatus.OFFLINE;
        }
        if (chosen != null) {
            return chosen;
        }
        if (inMeeting) {
            return PresenceStatus.IN_A_MEETING;
        }
        return idle ? PresenceStatus.AWAY : PresenceStatus.AVAILABLE;
    }
}
