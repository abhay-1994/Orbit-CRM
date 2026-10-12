package com.orbitcrm.lead.dto;

import java.util.UUID;

/**
 * Optional request body of {@code POST /api/v1/leads/{id}/convert}.
 *
 * <p>Purpose: tell the conversion what to do with each target. Omitted targets use defaults: create a contact,
 * create a company only if the lead has a company name, and create no deal. At least a contact or a company must be
 * created or linked.
 *
 * @author Abhay
 * @param company what to do with the company (create, link existing, none)
 * @param contact what to do with the contact (create, link existing, none)
 * @param deal    whether to create a deal (create or none; linking an existing deal is not allowed)
 * @param ownerId owner of created records; defaults to the lead's owner
 */
public record ConversionRequest(ConversionTarget company, ConversionTarget contact, ConversionTarget deal, UUID ownerId) {
}
