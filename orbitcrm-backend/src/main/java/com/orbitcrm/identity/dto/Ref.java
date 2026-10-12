package com.orbitcrm.identity.dto;

import java.util.UUID;

/**
 * A lightweight reference to another object: its id and display name.
 *
 * <p>Purpose: embed related objects (role, team) in responses without their full representation.
 *
 * @author Abhay
 * @param id   id of the referenced object
 * @param name its display name
 */
public record Ref(UUID id, String name) {
}
