package com.orbitcrm.chat.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * Request to mark a conversation read up to and including a message.
 *
 * @author Abhay
 * @param messageId newest message the user has seen
 */
public record MarkReadRequest(@NotNull UUID messageId) {
}
