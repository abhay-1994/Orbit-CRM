package com.orbitcrm.identity.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;

import com.orbitcrm.shared.config.OrbitProperties;
import com.orbitcrm.shared.service.MailService;
import com.orbitcrm.shared.util.AfterCommit;

/**
 * Composes and sends account emails: invitations and password reset links.
 *
 * <p>Purpose: keep email texts and link building in one place. Links point to the frontend
 * ({@code orbitcrm.web.frontend-base-url}) at {@code /accept-invite?token=...} and
 * {@code /reset-password?token=...}. Emails are sent only after the surrounding transaction commits, so a link
 * always points at a persisted token.
 *
 * @author Abhay
 */
@Component
public class IdentityMailer {

    private final MailService mail;
    private final String frontendBaseUrl;

    IdentityMailer(MailService mail, OrbitProperties properties) {
        this.mail = mail;
        this.frontendBaseUrl = properties.web().frontendBaseUrl();
    }

    public void sendInvitation(String email, String inviteeName, String inviterName, String workspaceName,
                               String rawToken) {
        String link = frontendBaseUrl + "/accept-invite?token=" + encode(rawToken);
        String body = """
                Hi %s,

                %s has invited you to join %s on OrbitCRM.

                Accept the invitation and set your password:
                %s

                This link expires in 7 days. If you were not expecting this invitation, you can ignore this email.
                """.formatted(inviteeName, inviterName, workspaceName, link);
        AfterCommit.run(() -> mail.send(email, "You're invited to " + workspaceName + " on OrbitCRM", body));
    }

    public void sendPasswordReset(String email, String name, String rawToken) {
        String link = frontendBaseUrl + "/reset-password?token=" + encode(rawToken);
        String body = """
                Hi %s,

                We received a request to reset your OrbitCRM password. Choose a new password here:
                %s

                This link expires in 1 hour and can be used once. If you did not request a reset, ignore this
                email; your password will not change.
                """.formatted(name, link);
        AfterCommit.run(() -> mail.send(email, "Reset your OrbitCRM password", body));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
