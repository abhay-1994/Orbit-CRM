/**
 * Public API of the tenant module: events published when tenants are created.
 * Other modules may depend on this package; everything else in the module is internal (verified by ModularityTests).
 */
@NamedInterface("event")
package com.orbitcrm.tenant.event;

import org.springframework.modulith.NamedInterface;
