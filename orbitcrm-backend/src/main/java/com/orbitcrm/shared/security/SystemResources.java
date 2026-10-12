package com.orbitcrm.shared.security;

/**
 * Keys of the administrative (system) permission resources.
 *
 * <p>Purpose: avoid string typos in permission checks for administration: {@code users}, {@code roles},
 * {@code teams}, {@code settings} (tenant settings, picklists, pipelines, shared views) and {@code audit}.
 *
 * @author Abhay
 */
public final class SystemResources {

    public static final String USERS = "users";
    public static final String ROLES = "roles";
    public static final String TEAMS = "teams";
    public static final String SETTINGS = "settings";
    public static final String AUDIT = "audit";

    private SystemResources() {
    }
}
