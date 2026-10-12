package com.orbitcrm.shared.security;

/**
 * How far a granted permission reaches (PRD section 36).
 *
 * <p>Purpose: express row-level visibility in roles:
 * <ul>
 *   <li>{@link #NONE} - not granted;</li>
 *   <li>{@link #OWN} - only records the user owns;</li>
 *   <li>{@link #TEAM} - records owned by anyone in the user's team or its sub-teams (a parent team plays the role
 *   of a department);</li>
 *   <li>{@link #ALL} - every record in the tenant.</li>
 * </ul>
 * The record engine converts the scope into a SQL predicate on the record owner for every read path.
 *
 * @author Abhay
 */
public enum AccessScope {
    NONE,
    OWN,
    TEAM,
    ALL;

    public boolean allows() {
        return this != NONE;
    }

    public AccessScope widest(AccessScope other) {
        return this.ordinal() >= other.ordinal() ? this : other;
    }
}
