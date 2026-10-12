package com.orbitcrm.audit.model;

/**
 * The actions recorded in the audit log.
 *
 * <p>Purpose: a fixed vocabulary for filtering and displaying audit history.
 * <ul>
 *   <li>Record actions (carry field-level changes): {@code CREATE}, {@code UPDATE}, {@code DELETE}, {@code RESTORE},
 *   {@code CONVERT}, {@code MERGE}, {@code IMPORT}, {@code EXPORT}.</li>
 *   <li>Security actions (carry metadata): {@code LOGIN}, {@code LOGIN_FAILED}, {@code LOGOUT},
 *   {@code PASSWORD_CHANGED}, {@code PASSWORD_RESET}, {@code MFA_ENABLED}, {@code MFA_DISABLED}.</li>
 *   <li>Administration actions: {@code USER_INVITED}, {@code USER_UPDATED}, {@code ROLE_CREATED},
 *   {@code ROLE_UPDATED}, {@code ROLE_DELETED}, {@code SETTINGS_UPDATED}.</li>
 * </ul>
 *
 * @author Abhay
 */
public enum AuditAction {
    CREATE,
    UPDATE,
    DELETE,
    RESTORE,
    CONVERT,
    MERGE,
    IMPORT,
    EXPORT,
    LOGIN,
    LOGIN_FAILED,
    LOGOUT,
    PASSWORD_CHANGED,
    PASSWORD_RESET,
    MFA_ENABLED,
    MFA_DISABLED,
    USER_INVITED,
    USER_UPDATED,
    ROLE_CREATED,
    ROLE_UPDATED,
    ROLE_DELETED,
    SETTINGS_UPDATED
}
