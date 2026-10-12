package com.orbitcrm.dataio.dto;

import java.util.List;
import java.util.Map;

/**
 * One previewed row of a CSV import.
 *
 * <p>Purpose: display the values a row would be imported with and the validation problems it has - using exactly the
 * same conversion and validation as the real import (dry run).
 *
 * @author Abhay
 * @param row    row number in the file (the header is row 1)
 * @param values the field values the row maps to
 * @param errors human-readable problems; empty if the row would import cleanly
 */
public record ImportPreviewRow(int row, Map<String, Object> values, List<String> errors) {
}
