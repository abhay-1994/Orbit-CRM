package com.orbitcrm.record.model;

/**
 * Where a record change came from.
 *
 * <p>Purpose: let consumers treat changes differently depending on their source. For example assignment
 * notifications are suppressed during imports, and the audit log records the origin of non-API changes. Values:
 * {@code API} (a user in the UI or an integration), {@code BULK} (bulk update), {@code IMPORT} (CSV import),
 * {@code CONVERSION} (lead conversion), {@code MERGE} (record merge, planned) and {@code SYSTEM} (platform jobs).
 *
 * @author Abhay
 */
public enum ChangeOrigin {
    API,
    BULK,
    IMPORT,
    CONVERSION,
    MERGE,
    SYSTEM
}
