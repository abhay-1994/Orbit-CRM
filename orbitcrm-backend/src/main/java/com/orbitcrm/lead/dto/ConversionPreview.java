package com.orbitcrm.lead.dto;

import java.util.List;
import java.util.Map;

/**
 * Response body of {@code GET /api/v1/leads/{id}/conversion-preview}.
 *
 * <p>Purpose: pre-fill the conversion dialog. It shows what would be created from the lead's data and suggests
 * existing records to link to, helping users avoid duplicates.
 *
 * @author Abhay
 * @param company           company field values mapped from the lead
 * @param contact           contact field values mapped from the lead
 * @param deal              deal field values mapped from the lead
 * @param matchingCompanies existing companies whose name contains the lead's company name (up to 5)
 * @param matchingContacts  existing contacts with the lead's email address (up to 5)
 */
public record ConversionPreview(Map<String, Object> company, Map<String, Object> contact, Map<String, Object> deal,
                         List<ConversionSuggestion> matchingCompanies, List<ConversionSuggestion> matchingContacts) {
}
