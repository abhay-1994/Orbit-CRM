package com.orbitcrm.activity.service;

import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Public API of the activity module: who is in a meeting.
 *
 * <p>Purpose: let other modules react to people's calendars without reading activity tables themselves. The chat
 * module uses it for the Teams-style "In a meeting" presence. A person is in a meeting while they own, or are a
 * participant of, a {@code meeting} activity that has started, has not ended, and is neither completed, cancelled nor
 * deleted. Always scoped to the current tenant.
 * <ul>
 *   <li>{@link #inMeetingNow} - the subset of people in a meeting at this moment;</li>
 *   <li>{@link #meetingChangesBetween} - people whose meeting started or ended in a time window, so presence can be
 *   refreshed for them when it happens.</li>
 * </ul>
 *
 * @author Abhay
 */
public interface MeetingSchedule {

    Set<UUID> inMeetingNow(Collection<UUID> userIds);

    /** Owners and participants of open meetings that started or ended in {@code [from, to)}. */
    Set<UUID> meetingChangesBetween(Instant from, Instant to);
}
