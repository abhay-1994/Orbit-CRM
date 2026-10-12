package com.orbitcrm.chat.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * Report from a browser tab on whether its user is idle (no keyboard or mouse input for a while).
 *
 * @author Abhay
 * @param connectionId the tab's live stream, from its {@code ready} event
 * @param idle         true when idle, false when the user is back
 */
public record ActivityRequest(@NotNull UUID connectionId, boolean idle) {
}
