package com.orbitcrm.chat.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request to choose a presence status; {@code AVAILABLE} returns to automatic presence.
 *
 * @author Abhay
 * @param status the chosen status
 */
public record SetPresenceRequest(@NotNull PresenceStatus status) {
}
