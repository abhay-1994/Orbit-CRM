package com.orbitcrm.metadata.dto;

import java.util.List;
import java.util.Map;

import com.orbitcrm.shared.security.AccessScope;
import com.orbitcrm.shared.security.Action;

/**
 * Full metadata of one record module, returned by {@code GET /api/v1/metadata/modules/{key}}.
 *
 * <p>Purpose: drive the generic list, detail, form and filter screens of a module.
 *
 * @author Abhay
 * @param key           module key, e.g. {@code leads}; also the permission resource and URL segment
 * @param singularLabel label for one record, e.g. "Lead"
 * @param pluralLabel   label for many records, e.g. "Leads"
 * @param icon          icon name for navigation
 * @param recordPrefix  prefix of human-readable record numbers, e.g. {@code LEAD}
 * @param titleFields   fields that form the record's display title
 * @param defaultSort   default list sort, e.g. {@code createdAt,desc}
 * @param permissions   the current user's scope for every action on this module
 * @param fields        metadata of every field, including the standard system fields
 */
public record ModuleDetail(String key, String singularLabel, String pluralLabel, String icon, String recordPrefix,
                    List<String> titleFields, String defaultSort, Map<Action, AccessScope> permissions,
                    List<FieldMeta> fields) {
}
