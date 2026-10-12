/**
 * Public API of the record module: domain events published after record writes.
 * Other modules may depend on this package; everything else in the module is internal (verified by ModularityTests).
 */
@NamedInterface("event")
package com.orbitcrm.record.event;

import org.springframework.modulith.NamedInterface;
