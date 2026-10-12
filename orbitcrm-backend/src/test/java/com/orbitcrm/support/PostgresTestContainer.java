package com.orbitcrm.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One PostgreSQL 18 container shared by the whole test run.
 *
 * <p>Purpose: run tests against the same database engine and security setup as production. The container is started
 * once (statically) and registered as Spring properties: Flyway migrates as the container's superuser (schema owner),
 * while the application connects as the restricted {@code orbitcrm_app} role created by the baseline migration -
 * exactly like production - so row-level security is exercised by every integration test. Accessors expose the admin
 * connection for tests that need to inspect the database directly.
 *
 * @author Abhay
 */
public final class PostgresTestContainer {

    public static final String APP_USER = "orbitcrm_app";
    public static final String APP_PASSWORD = "orbitcrm_app_test";

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine")
            .withDatabaseName("orbitcrm")
            .withUsername("orbitcrm")
            .withPassword("orbitcrm");

    static {
        POSTGRES.start();
    }

    private PostgresTestContainer() {
    }

    public static void register(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> APP_USER);
        registry.add("spring.datasource.password", () -> APP_PASSWORD);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    public static String jdbcUrl() {
        return POSTGRES.getJdbcUrl();
    }

    public static String adminUser() {
        return POSTGRES.getUsername();
    }

    public static String adminPassword() {
        return POSTGRES.getPassword();
    }
}
