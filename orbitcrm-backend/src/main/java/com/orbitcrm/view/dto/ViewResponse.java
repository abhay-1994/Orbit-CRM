package com.orbitcrm.view.dto;

import java.util.UUID;

/**
 * A saved list view as returned by the view endpoints.
 *
 * <p>Purpose: let the frontend list the available views and apply one (filter, sort, columns) to a list.
 *
 * @author Abhay
 * @param id          view id
 * @param module      module key
 * @param name        view name
 * @param ownerUserId creator, or {@code null} for built-in views
 * @param visibility  {@code PRIVATE} or {@code SHARED}
 * @param system      whether it is a built-in view seeded for the tenant (cannot be deleted)
 * @param filter      filter in the filter language
 * @param sort        sort definition
 * @param columns     column configuration
 * @param position    display order
 */
public record ViewResponse(UUID id, String module, String name, UUID ownerUserId, String visibility, boolean system,
                    Object filter, Object sort, Object columns, int position) {
}
