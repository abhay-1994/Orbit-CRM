package com.orbitcrm.chat.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * Request to start (or reopen) the direct chat with a colleague.
 *
 * @author Abhay
 * @param userId the colleague
 */
public record StartConversationRequest(@NotNull UUID userId) {
}
