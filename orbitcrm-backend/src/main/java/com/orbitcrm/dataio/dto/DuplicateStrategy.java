package com.orbitcrm.dataio.dto;

/**
 * What a CSV import does when a row matches an existing record on the chosen duplicate field.
 *
 * <p>Purpose: let users decide how to handle data that is already in the CRM: {@code SKIP} the row (default),
 * {@code UPDATE} the existing record with the row's values, or {@code CREATE} a new record anyway.
 *
 * @author Abhay
 */
public enum DuplicateStrategy { SKIP, UPDATE, CREATE }
