package com.orbitcrm.tenant.service;

import java.util.Optional;
import java.util.UUID;

import com.orbitcrm.tenant.dto.NewTenant;
import com.orbitcrm.tenant.dto.TenantInfo;
import com.orbitcrm.tenant.dto.TenantSettingsUpdate;
import com.orbitcrm.tenant.event.TenantCreatedEvent;

/**
 * Public API of the tenant module.
 *
 * <p>Purpose: the only way other modules create, read or change tenants: provisioning during signup
 * ({@link #create}, which must run inside {@code TenantContext.callAs} for the new tenant), lookup by slug or id,
 * the current tenant's settings, settings updates, slug availability and the list of active tenant ids for
 * cross-tenant platform jobs.
 *
 * @author Abhay
 */
public interface TenantService {

    /**
     * Creates the tenant and publishes {@link TenantCreatedEvent}. Must run inside
     * {@code TenantContext.callAs(newTenant.id(), ...)} and a transaction.
     */
    TenantInfo create(NewTenant newTenant);

    Optional<TenantInfo> findBySlug(String slug);

    TenantInfo get(UUID tenantId);

    /** The tenant bound to the current thread. */
    TenantInfo current();

    TenantInfo updateSettings(TenantSettingsUpdate update);

    boolean isSlugAvailable(String slug);

    /** Ids of all active tenants, for platform jobs that iterate tenants. */
    java.util.List<UUID> allTenantIds();
}
