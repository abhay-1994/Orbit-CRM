package com.orbitcrm.record.model;

/**
 * Options for internal record writes.
 *
 * <p>Purpose: let platform flows write records with a different origin or with permission to set read-only fields.
 * API calls use {@link #API}; bulk updates and imports use {@code of(origin)}; flows such as lead conversion use
 * {@code system(origin)} to set system-maintained fields like a deal's {@code leadId}.
 *
 * @author Abhay
 * @param origin        where the change comes from (recorded in events and the audit log)
 * @param allowReadOnly permit setting read-only fields (used by platform flows such as lead conversion)
 */
public record WriteOptions(ChangeOrigin origin, boolean allowReadOnly) {

    public static final WriteOptions API = new WriteOptions(ChangeOrigin.API, false);

    public static WriteOptions of(ChangeOrigin origin) {
        return new WriteOptions(origin, false);
    }

    public static WriteOptions system(ChangeOrigin origin) {
        return new WriteOptions(origin, true);
    }
}
