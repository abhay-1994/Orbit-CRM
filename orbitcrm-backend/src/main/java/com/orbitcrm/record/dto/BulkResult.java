package com.orbitcrm.record.dto;

import java.util.List;

/**
 * Result of a bulk update or bulk delete.
 *
 * <p>Purpose: summarize a bulk operation in which each record succeeds or fails independently.
 *
 * @author Abhay
 * @param requested number of distinct ids requested
 * @param succeeded number of records processed successfully
 * @param failures  records that failed, with the reason
 */
public record BulkResult(int requested, int succeeded, List<BulkFailure> failures) {
}
