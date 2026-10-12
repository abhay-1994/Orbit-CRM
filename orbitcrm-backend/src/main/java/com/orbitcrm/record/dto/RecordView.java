package com.orbitcrm.record.dto;

import java.util.Map;
import java.util.UUID;

/**
 * A record of any module as returned by the API and passed between modules.
 *
 * <p>Purpose: the uniform representation of records produced by the generic engine. Values are keyed by field key
 * and include {@code null}s, so clients can render every field; display labels for users, lookups, references and
 * picklists are resolved in batch so lists need no extra calls. Helper methods read typed values for internal
 * callers.
 *
 * @author Abhay
 * @param id           record id
 * @param module       module key, e.g. {@code deals}
 * @param recordNumber human-readable number, e.g. {@code DEAL-1042}
 * @param version      optimistic locking version (also sent as the ETag)
 * @param title        display title built from the module's title fields
 * @param values       field key to value (JSON-friendly types, nulls included)
 * @param labels       display labels for reference and picklist fields (owner name, company name, status label)
 */
public record RecordView(
        UUID id,
        String module,
        String recordNumber,
        long version,
        String title,
        Map<String, Object> values,
        Map<String, String> labels) {

    public Object value(String field) {
        return values.get(field);
    }

    public UUID uuid(String field) {
        Object value = values.get(field);
        return value instanceof UUID uuid ? uuid : null;
    }

    public String string(String field) {
        Object value = values.get(field);
        return value == null ? null : value.toString();
    }
}
