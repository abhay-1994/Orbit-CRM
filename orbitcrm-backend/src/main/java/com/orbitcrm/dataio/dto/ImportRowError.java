package com.orbitcrm.dataio.dto;

import java.util.List;

/**
 * The errors of one failed row of a CSV import.
 *
 * <p>Purpose: report why a row was not imported, in the job result and in the downloadable errors file.
 *
 * @author Abhay
 * @param row      row number in the file (the header is row 1; 0 for errors that stopped the whole import)
 * @param messages human-readable error messages, prefixed with the field label
 */
public record ImportRowError(int row, List<String> messages) {
}
