package com.orbitcrm.shared.security;

import java.util.List;

/**
 * SPI through which modules contribute the resources they protect.
 *
 * <p>Purpose: keep the permission catalog open for extension. The metadata module's {@code ModuleRegistry}
 * provides one resource per record module and the identity module's {@code IdentityResources} provides the
 * administrative resources; the identity module's {@code PermissionCatalog} collects all providers to build the
 * role editor catalog and to seed default roles for new tenants.
 *
 * @author Abhay
 */
public interface SecuredResourceProvider {

    List<SecuredResource> securedResources();
}
