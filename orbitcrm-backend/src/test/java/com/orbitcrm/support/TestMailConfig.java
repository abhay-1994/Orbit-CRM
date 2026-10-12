package com.orbitcrm.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Test configuration that replaces the real mail service.
 *
 * <p>Purpose: register a {@link RecordingMailService} as the primary {@code MailService} bean, so no email is sent
 * during tests and tests can inspect what would have been sent. Imported by {@link IntegrationTest}.
 *
 * @author Abhay
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestMailConfig {

    @Bean
    @Primary
    RecordingMailService recordingMailService() {
        return new RecordingMailService();
    }
}
