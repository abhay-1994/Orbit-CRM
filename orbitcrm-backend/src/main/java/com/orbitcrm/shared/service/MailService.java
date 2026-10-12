package com.orbitcrm.shared.service;

/**
 * Outbound email abstraction.
 *
 * <p>Purpose: let modules send plain-text email (invitations, password resets, later notifications) without
 * depending on SMTP details. The implementation is provided by {@code MailConfig}: asynchronous SMTP when
 * configured, logging otherwise. Tests replace it with a recording implementation.
 *
 * @author Abhay
 */
public interface MailService {

    void send(String to, String subject, String textBody);
}
