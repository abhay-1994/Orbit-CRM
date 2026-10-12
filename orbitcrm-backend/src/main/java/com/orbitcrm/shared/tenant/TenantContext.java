package com.orbitcrm.shared.tenant;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Holds the tenant (customer workspace) the current thread is working for.
 *
 * <p>Purpose: the single source of truth for tenant isolation at runtime. The value is pushed into PostgreSQL
 * ({@code app.tenant_id}) whenever a connection is checked out and into Hibernate's tenant discriminator, so both
 * the ORM and row-level security filter by it.
 * <ul>
 *   <li>For API requests the security layer binds it from the authenticated user and clears it afterwards.</li>
 *   <li>Background work and pre-authentication flows (signup, login, token links) use {@link #callAs} /
 *   {@link #runAs}, which restore the previous value.</li>
 *   <li>{@link #callAsSystem} bypasses row-level security for platform jobs that scan all tenants.</li>
 * </ul>
 * The context must be set before a transaction starts: switching tenant inside an open transaction would leave
 * the connection bound to the previous tenant, so {@code callAs} rejects it.
 *
 * @author Abhay
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> TENANT = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> SYSTEM = new ThreadLocal<>();

    private TenantContext() {
    }

    public static Optional<UUID> currentTenantId() {
        return Optional.ofNullable(TENANT.get());
    }

    public static UUID requireTenantId() {
        UUID tenantId = TENANT.get();
        if (tenantId == null) {
            throw new IllegalStateException("No tenant bound to the current thread");
        }
        return tenantId;
    }

    /** True while running cross-tenant platform work that is allowed to bypass row-level security. */
    public static boolean isSystem() {
        return Boolean.TRUE.equals(SYSTEM.get());
    }

    public static <T> T callAs(UUID tenantId, Supplier<T> work) {
        Objects.requireNonNull(tenantId, "tenantId");
        UUID previousTenant = TENANT.get();
        Boolean previousSystem = SYSTEM.get();
        guardTransactionSwitch(previousTenant, tenantId);
        TENANT.set(tenantId);
        SYSTEM.remove();
        try {
            return work.get();
        } finally {
            restore(previousTenant, previousSystem);
        }
    }

    public static void runAs(UUID tenantId, Runnable work) {
        callAs(tenantId, () -> {
            work.run();
            return null;
        });
    }

    /**
     * Runs platform-level work (schedulers scanning all tenants) with row-level security bypassed.
     * Code inside must switch to a specific tenant with {@link #callAs} before touching tenant data
     * through the ORM.
     */
    public static <T> T callAsSystem(Supplier<T> work) {
        UUID previousTenant = TENANT.get();
        Boolean previousSystem = SYSTEM.get();
        if (TransactionSynchronizationManager.isActualTransactionActive() && !isSystem()) {
            throw new IllegalStateException("Cannot enter system context inside an active transaction");
        }
        TENANT.remove();
        SYSTEM.set(Boolean.TRUE);
        try {
            return work.get();
        } finally {
            restore(previousTenant, previousSystem);
        }
    }

    /** Binds the tenant for the remainder of a request. Prefer {@link #callAs} elsewhere. */
    public static void bind(UUID tenantId) {
        TENANT.set(tenantId);
        SYSTEM.remove();
    }

    public static void clear() {
        TENANT.remove();
        SYSTEM.remove();
    }

    private static void guardTransactionSwitch(UUID current, UUID next) {
        if (TransactionSynchronizationManager.isActualTransactionActive() && !next.equals(current)) {
            throw new IllegalStateException(
                    "Cannot switch tenant inside an active transaction; start the transaction inside callAs()");
        }
    }

    private static void restore(UUID tenant, Boolean system) {
        if (tenant == null) {
            TENANT.remove();
        } else {
            TENANT.set(tenant);
        }
        if (system == null) {
            SYSTEM.remove();
        } else {
            SYSTEM.set(system);
        }
    }
}
