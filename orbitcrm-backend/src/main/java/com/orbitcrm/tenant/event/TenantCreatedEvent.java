package com.orbitcrm.tenant.event;

import java.util.UUID;

/**
 * Event published synchronously inside the tenant provisioning transaction.
 *
 * <p>Purpose: let each module seed its per-tenant defaults without the tenant module knowing about them:
 * default roles (identity), picklist values (metadata), the default sales pipeline (deals) and built-in saved
 * views (views). Because listeners run in the same transaction, a failing listener rolls the whole signup back.
 *
 * @author Abhay
 * @param tenantId id of the new tenant
 * @param slug     its workspace slug
 */
public record TenantCreatedEvent(UUID tenantId, String slug) {
}
