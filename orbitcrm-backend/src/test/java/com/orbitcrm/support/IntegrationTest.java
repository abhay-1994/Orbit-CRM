package com.orbitcrm.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base class of all full-stack integration tests.
 *
 * <p>Purpose: give every integration test the same realistic environment: a complete Spring Boot context with the
 * {@code test} profile, a real PostgreSQL 18 database (shared Testcontainers instance, see
 * {@link PostgresTestContainer}), {@link org.springframework.test.web.servlet.MockMvc} for calling the API, and a
 * {@link RecordingMailService} that captures outgoing email. Because all subclasses use the same configuration, the
 * Spring context is created once and reused across test classes.
 *
 * @author Abhay
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestMailConfig.class)
public abstract class IntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected RecordingMailService mail;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        PostgresTestContainer.register(registry);
    }
}
