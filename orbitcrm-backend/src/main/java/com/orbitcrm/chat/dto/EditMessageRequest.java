package com.orbitcrm.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request to replace the text of one of my messages.
 *
 * @author Abhay
 * @param body new text, up to {@value SendMessageRequest#MAX_BODY} characters
 */
public record EditMessageRequest(@NotBlank @Size(max = SendMessageRequest.MAX_BODY) String body) {
}
