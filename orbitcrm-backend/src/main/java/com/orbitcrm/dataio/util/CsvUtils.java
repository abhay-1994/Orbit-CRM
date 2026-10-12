package com.orbitcrm.dataio.util;

/**
 * CSV helpers shared by import and export.
 *
 * <p>Purpose: protect users who open exported files in spreadsheet programs. {@link #safe} prevents CSV / formula
 * injection: a cell that starts with {@code =}, {@code +}, {@code -}, {@code @}, a tab or a carriage return is
 * prefixed with a single quote so Excel, LibreOffice or Google Sheets treat it as text instead of executing it as a
 * formula.
 *
 * @author Abhay
 */
public final class CsvUtils {

    private CsvUtils() {
    }

    /** Prevents CSV/formula injection: cells starting with = + - @ tab or CR are prefixed with a quote. */
    public static String safe(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        char first = value.charAt(0);
        return first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r'
                ? "'" + value : value;
    }
}
