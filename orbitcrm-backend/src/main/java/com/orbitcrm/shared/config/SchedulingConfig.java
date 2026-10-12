package com.orbitcrm.shared.config;

import javax.sql.DataSource;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables background jobs and makes each job run on exactly one server instance.
 *
 * <p>Purpose: allow {@code @Scheduled} jobs (currently the activity reminder job) while the application runs on
 * several instances. {@code @EnableSchedulerLock} (ShedLock) acquires a lock row in the {@code shedlock} table,
 * using the database clock, before a job runs; jobs additionally annotated with {@code @SchedulerLock} are
 * therefore never executed concurrently (a lock is held for at most ten minutes by default, so a crashed
 * instance cannot block a job forever). The whole configuration is switched off with
 * {@code orbitcrm.jobs.enabled=false}, which the test profile uses so tests can trigger jobs deterministically.
 *
 * @author Abhay
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
@ConditionalOnProperty(name = "orbitcrm.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {

    @Bean
    LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build());
    }
}
