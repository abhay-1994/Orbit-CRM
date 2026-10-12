package com.orbitcrm.timeline.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One entry of a record's timeline; {@code null} fields are omitted from the JSON.
 *
 * <p>Purpose: a common shape for the different kinds of events merged into the timeline.
 *
 * @author Abhay
 * @param kind      {@code audit}, {@code note}, {@code attachment} or {@code activity}
 * @param action    what happened, e.g. {@code update}, {@code note}, {@code uploaded}, or the activity type
 * @param at        when it happened
 * @param actorId   user who did it (for activities: the owner)
 * @param actorName that user's name
 * @param summary   one-line text: change summary, note text, file name or activity subject
 * @param refId     id of the underlying audit entry, note, attachment or activity
 * @param detail    kind-specific details, e.g. field changes, pinned flag, file type and size, activity status
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TimelineItem(String kind, String action, Instant at, UUID actorId, String actorName, String summary,
                    UUID refId, Map<String, Object> detail) {
}
