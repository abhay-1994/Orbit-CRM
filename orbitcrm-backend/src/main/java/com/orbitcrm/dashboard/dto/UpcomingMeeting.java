package com.orbitcrm.dashboard.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * A meeting shown in the dashboard's "upcoming meetings" list.
 *
 * <p>Purpose: enough to display the meeting and link to it.
 *
 * @author Abhay
 * @param id        activity id
 * @param subject   meeting subject
 * @param startAt   start time
 * @param relatedTo title of the related lead, contact, company or deal, if any
 */
public record UpcomingMeeting(UUID id, String subject, Instant startAt, String relatedTo) {
}
