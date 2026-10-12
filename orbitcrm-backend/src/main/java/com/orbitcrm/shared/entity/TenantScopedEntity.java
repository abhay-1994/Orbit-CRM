package com.orbitcrm.shared.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;

import org.hibernate.annotations.TenantId;

import com.orbitcrm.shared.tenant.TenantContext;

/**
 * Base class of every JPA entity that belongs to a tenant (users, roles, teams, pipelines, picklist values...).
 *
 * <p>Purpose: enforce tenant ownership automatically. The {@code tenant_id} column carries Hibernate's
 * {@code @TenantId}: Hibernate adds the tenant to every query and verifies it on insert, while PostgreSQL row-level
 * security enforces the same rule underneath. The tenant is assigned from
 * {@link com.orbitcrm.shared.tenant.TenantContext} when the entity is persisted, so {@link #getTenantId()} is
 * readable immediately after {@code save} (Hibernate would otherwise fill it only at flush time).
 *
 * @author Abhay
 */
@MappedSuperclass
public abstract class TenantScopedEntity extends BaseEntity {

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    /** Hibernate only fills the discriminator at flush; set it at persist so it is readable right away. */
    @PrePersist
    void assignTenant() {
        if (tenantId == null) {
            tenantId = TenantContext.requireTenantId();
        }
    }

    public UUID getTenantId() {
        return tenantId;
    }
}
