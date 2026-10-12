package com.orbitcrm.shared.security;

/**
 * Permission actions that roles grant per resource (PRD section 36).
 *
 * <p>Purpose: the vocabulary of the permission matrix: {@code CREATE}, {@code READ}, {@code UPDATE}, {@code DELETE},
 * {@code EXPORT} and {@code IMPORT}. Read, update, delete and export carry an {@link AccessScope}; create and import
 * are simply granted or not. {@code SHARE} and {@code APPROVE} are reserved for record sharing and the approval
 * engine planned for V3.
 *
 * @author Abhay
 */
public enum Action {
    CREATE,
    READ,
    UPDATE,
    DELETE,
    EXPORT,
    IMPORT
}
