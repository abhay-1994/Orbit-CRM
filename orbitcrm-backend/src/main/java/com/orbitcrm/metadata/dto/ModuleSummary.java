package com.orbitcrm.metadata.dto;

import java.util.Map;

import com.orbitcrm.shared.security.AccessScope;
import com.orbitcrm.shared.security.Action;

/**
 * Short description of a record module, returned by {@code GET /api/v1/metadata/modules}.
 *
 * <p>Purpose: build the navigation menu and permission-aware actions for the modules the user can read.
 *
 * @author Abhay
 * @param key           module key
 * @param singularLabel label for one record
 * @param pluralLabel   label for many records
 * @param icon          icon name
 * @param recordPrefix  prefix of record numbers
 * @param permissions   the current user's scope for every action on this module
 */
public record ModuleSummary(String key, String singularLabel, String pluralLabel, String icon, String recordPrefix,
                     Map<Action, AccessScope> permissions) {
}
