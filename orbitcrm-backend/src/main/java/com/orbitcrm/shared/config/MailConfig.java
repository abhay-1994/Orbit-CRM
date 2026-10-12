package com.orbitcrm.shared.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import com.orbitcrm.shared.service.MailService;

/**
 * Spring configuration that provides the application's {@link com.orbitcrm.shared.service.MailService}.
 *
 * <p>Purpose: give every module one simple way to send email without knowing how or whether email is delivered.
 * <ul>
 *   <li>When {@code spring.mail.host} is configured, messages are sent through Spring's {@code JavaMailSender}
 *   (SMTP; Mailpit in local development).</li>
 *   <li>Sending happens asynchronously on a virtual-thread executor, so SMTP latency never slows a request and
 *   response times cannot reveal whether an address belongs to an account (important for password reset).</li>
 *   <li>Delivery failures are logged, never thrown: a broken mail server must not fail a business operation
 *   such as inviting a user.</li>
 *   <li>Without SMTP configuration, emails are only logged; the body is logged only when
 *   {@code orbitcrm.mail.log-body=true} (local profile), because bodies contain sensitive links.</li>
 *   <li>Recipient addresses are masked in logs.</li>
 * </ul>
 * The bean is {@code @ConditionalOnMissingBean}, so tests replace it with a recording implementation.
 *
 * @author Abhay
 */
@Configuration(proxyBeanMethods = false)
public class MailConfig {

    private static final Logger log = LoggerFactory.getLogger(MailConfig.class);

    @Bean(destroyMethod = "close")
    ExecutorService mailExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Sends asynchronously: SMTP latency must neither slow requests down nor reveal (through timing)
     * whether an address belongs to an account.
     */
    @Bean
    @ConditionalOnMissingBean(MailService.class)
    MailService mailService(ObjectProvider<JavaMailSender> senderProvider, OrbitProperties properties,
                            ExecutorService mailExecutor) {
        JavaMailSender sender = senderProvider.getIfAvailable();
        String from = properties.mail().from();
        boolean logBody = properties.mail().logBody();
        if (sender == null) {
            log.warn("spring.mail.host is not configured; outgoing email will only be logged");
            return (to, subject, body) -> log.info("Email (not sent) to={} subject='{}'{}", mask(to), subject,
                    logBody ? "\n" + body : "");
        }
        return (to, subject, body) -> mailExecutor.submit(() -> {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            try {
                sender.send(message);
                log.info("Email sent to={} subject='{}'", mask(to), subject);
            } catch (MailException ex) {
                log.error("Failed to send email to={} subject='{}': {}", mask(to), subject, ex.getMessage());
            }
        });
    }

    public static String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
