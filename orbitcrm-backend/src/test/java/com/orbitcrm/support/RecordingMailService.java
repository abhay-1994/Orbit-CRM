package com.orbitcrm.support;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.orbitcrm.shared.service.MailService;

/**
 * {@link com.orbitcrm.shared.service.MailService} for tests that captures outgoing email instead of sending it.
 *
 * <p>Purpose: let tests follow invitation and password reset flows. Messages are recorded synchronously (no
 * background thread), {@link #lastTo} returns the latest message to an address and {@link #lastTokenFor} extracts the
 * token from the link in that message.
 *
 * @author Abhay
 */
public class RecordingMailService implements MailService {

    /**
     * One captured email.
     *
     * <p>Purpose: let tests assert on recipients, subjects and bodies, and extract links from the body.
     *
     * @param to      recipient address
     * @param subject subject line
     * @param body    plain-text body
     */
    public record Message(String to, String subject, String body) {
    }

    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_%-]+)");

    private final List<Message> messages = new CopyOnWriteArrayList<>();

    @Override
    public void send(String to, String subject, String textBody) {
        messages.add(new Message(to, subject, textBody));
    }

    public Optional<Message> lastTo(String email) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).to().equalsIgnoreCase(email)) {
                return Optional.of(messages.get(i));
            }
        }
        return Optional.empty();
    }

    public String lastTokenFor(String email) {
        Message message = lastTo(email).orElseThrow(() -> new AssertionError("No email sent to " + email));
        Matcher matcher = TOKEN.matcher(message.body());
        if (!matcher.find()) {
            throw new AssertionError("No token link in email: " + message.body());
        }
        return java.net.URLDecoder.decode(matcher.group(1), java.nio.charset.StandardCharsets.UTF_8);
    }
}
