package com.orbitcrm.lead.dto;

import java.util.UUID;

import com.orbitcrm.record.dto.RecordView;

/**
 * Response body of a successful lead conversion.
 *
 * <p>Purpose: report which records the lead was converted into and which of them were newly created, so the client
 * can navigate to them.
 *
 * @author Abhay
 * @param leadId         the converted lead
 * @param company        the created or linked company, or {@code null}
 * @param companyCreated whether the company was created by this conversion
 * @param contact        the created or linked contact, or {@code null}
 * @param contactCreated whether the contact was created by this conversion
 * @param deal           the created deal, or {@code null}
 * @param dealCreated    whether a deal was created
 */
public record ConversionResult(UUID leadId, RecordView company, boolean companyCreated, RecordView contact,
                        boolean contactCreated, RecordView deal, boolean dealCreated) {
}
