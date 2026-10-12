package com.orbitcrm.shared.config;

import java.util.UUID;

import javax.sql.DataSource;

import org.hibernate.cfg.MultiTenancySettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.orbitcrm.shared.tenant.TenantAwareDataSource;
import com.orbitcrm.shared.tenant.TenantContext;

/**
 * Wires tenant isolation into the persistence layer.
 *
 * <p>Purpose: make both the ORM and the database aware of the current tenant, so tenant data can never leak
 * between customer workspaces.
 * <ul>
 *   <li>A {@code BeanPostProcessor} wraps the application {@code DataSource} in a
 *   {@link com.orbitcrm.shared.tenant.TenantAwareDataSource}, which writes the current tenant into the PostgreSQL
 *   session setting {@code app.tenant_id} on every connection checkout; row-level security policies filter on it.</li>
 *   <li>A Hibernate {@code CurrentTenantIdentifierResolver} reads {@link com.orbitcrm.shared.tenant.TenantContext},
 *   so entities with {@code @TenantId} are automatically filtered and stamped with the tenant.</li>
 *   <li>When no tenant is bound, the resolver returns a nil-UUID sentinel: queries match no rows and inserts fail
 *   the tenant foreign key, i.e. the system fails closed.</li>
 * </ul>
 *
 * @author Abhay
 */
@Configuration(proxyBeanMethods = false)
public class TenantPersistenceConfig {

    /** Sentinel used when no tenant is bound: matches no rows, and inserts fail the tenant foreign key. */
    public static final UUID NO_TENANT = new UUID(0L, 0L);

    @Bean
    static BeanPostProcessor tenantAwareDataSourcePostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof DataSource dataSource
                        && !(bean instanceof TenantAwareDataSource)
                        && "dataSource".equals(beanName)) {
                    return new TenantAwareDataSource(dataSource);
                }
                return bean;
            }
        };
    }

    @Bean
    HibernatePropertiesCustomizer tenantIdentifierResolverCustomizer() {
        CurrentTenantIdentifierResolver<UUID> resolver = new CurrentTenantIdentifierResolver<>() {
            @Override
            public UUID resolveCurrentTenantIdentifier() {
                return TenantContext.currentTenantId().orElse(NO_TENANT);
            }

            @Override
            public boolean validateExistingCurrentSessions() {
                return false;
            }
        };
        return properties -> properties.put(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER, resolver);
    }
}
