package com.orbitcrm.timeline.dto;

import java.time.Instant;
import java.util.List;

/**
 * Response body of the record timeline endpoint.
 *
 * <p>Purpose: one page of timeline items plus the cursor for the next (older) page.
 *
 * @author Abhay
 * @param items      timeline items, newest first
 * @param nextBefore pass as {@code before} to load older items; {@code null} when there are no more
 */
public record Timeline(List<TimelineItem> items, Instant nextBefore) {
}
