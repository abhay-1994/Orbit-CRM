package com.orbitcrm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point of the OrbitCRM backend: a multi-tenant CRM platform built as a modular monolith.
 *
 * <p>Starting this class boots the Spring Boot application, which scans every module under
 * {@code com.orbitcrm} (identity, tenant, metadata, record engine, leads, contacts, companies, deals, activities,
 * notes, attachments, notifications, search, views, import/export, dashboard, audit) and registers all
 * {@code @ConfigurationProperties} records ({@link com.orbitcrm.shared.config.OrbitProperties},
 * {@link com.orbitcrm.shared.config.StorageProperties}) through {@code @ConfigurationPropertiesScan}.
 *
 * <p>Each top-level package is a Spring Modulith application module; its public API is limited to the packages
 * annotated with {@code @NamedInterface}, and {@code ModularityTests} verifies those boundaries on every build.
 * On startup Flyway migrates the database (as the schema owner) before the application connects as the
 * restricted, row-level-security-bound role.
 *
 * @author Abhay
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class OrbitCrmApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrbitCrmApplication.class, args);
    }
}
