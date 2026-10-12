package com.orbitcrm.chat.dto;

import java.util.UUID;

/**
 * The effective presence of one person.
 *
 * <p>Purpose: returned by the presence endpoints and broadcast to the workspace as a {@code presence} stream event
 * whenever it may have changed. Only the effective status is shared, never the reason (for example, whether someone
 * chose "Appear offline").
 *
 * @author Abhay
 * @param userId user id
 * @param status effective status
 */
public record PresenceView(UUID userId, PresenceStatus status) {
}
