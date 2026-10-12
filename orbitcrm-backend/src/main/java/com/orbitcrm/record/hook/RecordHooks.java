package com.orbitcrm.record.hook;

import java.util.List;
import java.util.Map;

import com.orbitcrm.record.dto.RecordChange;
import com.orbitcrm.record.dto.RecordView;
import com.orbitcrm.record.model.WriteOptions;
import com.orbitcrm.shared.security.CurrentUser;

/**
 * SPI for module-specific behaviour around the generic record writes.
 *
 * <p>Purpose: keep the engine generic while letting modules add their own rules: derived fields, cross-field
 * validation, side tables and read-only states. Examples: {@code DealHooks} derives probability, status and close date
 * from the stage and records stage history; {@code LeadHooks} makes converted leads read-only; {@code ActivityHooks}
 * validates start and end times, maintains the completion time and notifies invited participants. Hooks are Spring beans selected by {@link #moduleKey()} and run inside the write
 * transaction, so throwing aborts the write. Value maps hold already converted, typed values keyed by field key.
 * <ul>
 *   <li>{@code beforeCreate} may add or adjust values before insert;</li>
 *   <li>{@code beforeUpdate} may add or adjust the mutable map of changes;</li>
 *   <li>{@code afterCreate} / {@code afterUpdate} see the stored result (and the diff);</li>
 *   <li>{@code beforeDelete} may veto a deletion.</li>
 * </ul>
 * The nested {@code HookContext} carries the module, the acting user and the write options.
 *
 * @author Abhay
 */
public interface RecordHooks {

    String moduleKey();

    /** May add or adjust values before insert. */
    default void beforeCreate(HookContext context, Map<String, Object> values) {
    }

    default void afterCreate(HookContext context, RecordView created) {
    }

    /** May add or adjust {@code changes} (a mutable map of field -> new value). */
    default void beforeUpdate(HookContext context, RecordView existing, Map<String, Object> changes) {
    }

    default void afterUpdate(HookContext context, RecordView before, RecordView after, List<RecordChange> changes) {
    }

    default void beforeDelete(HookContext context, RecordView existing) {
    }

    /**
     * Context passed to every {@link RecordHooks} callback.
     *
     * <p>Purpose: tell a hook which module is written, who is writing and how, so hooks can apply different rules to
     * platform flows (for example lead conversion) than to ordinary API writes.
     *
     * @param moduleKey module being written
     * @param user      the acting user
     * @param options   write options, including the change origin
     */
    record HookContext(String moduleKey, CurrentUser user, WriteOptions options) {
    }
}
