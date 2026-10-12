package com.orbitcrm.tenant.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.orbitcrm.tenant.entity.Tenant;

/**
 * Spring Data repository for {@link com.orbitcrm.tenant.entity.Tenant}.
 *
 * <p>Purpose: look tenants up by slug at login and signup ({@code findBySlug}, {@code existsBySlug}) and list the
 * ids of active tenants for platform jobs that iterate all tenants ({@code findActiveIds}).
 *
 * @author Abhay
 */
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findBySlug(String slug);

    boolean existsBySlug(String slug);

    @Query("select t.id from Tenant t where t.status = com.orbitcrm.tenant.entity.Tenant.Status.ACTIVE order by t.id")
    List<UUID> findActiveIds();
}
