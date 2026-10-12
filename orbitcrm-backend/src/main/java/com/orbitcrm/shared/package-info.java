/**
 * Shared kernel: API envelope, error model, tenant context, security primitives and utilities.
 * Every other module may depend on it; it depends on no other module.
 */
@ApplicationModule(type = ApplicationModule.Type.OPEN, displayName = "Shared Kernel")
package com.orbitcrm.shared;

import org.springframework.modulith.ApplicationModule;
