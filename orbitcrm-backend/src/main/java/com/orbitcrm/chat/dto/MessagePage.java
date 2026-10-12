package com.orbitcrm.chat.dto;

import java.util.List;

/**
 * One page of a conversation's history.
 *
 * <p>Purpose: keyset pagination that stays correct while new messages arrive. Messages are in chronological order;
 * to load older ones, pass the id of the first message as {@code before}.
 *
 * @author Abhay
 * @param messages messages, oldest first
 * @param hasMore  true if older messages exist
 */
public record MessagePage(List<MessageView> messages, boolean hasMore) {
}
