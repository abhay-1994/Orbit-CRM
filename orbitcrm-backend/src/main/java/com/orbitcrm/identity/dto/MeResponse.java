package com.orbitcrm.identity.dto;

import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.orbitcrm.shared.security.AccessScope;
import com.orbitcrm.shared.security.Action;

/**
 * The "who am I" payload returned by {@code GET /api/v1/auth/me} and with every issued token.
 *
 * <p>Purpose: give the frontend everything it needs to render navigation and permission-aware UI without further
 * calls: the user's profile, effective time zone and locale (the user's own, falling back to the tenant's), the
 * tenant's regional settings, role, team and the full permission map. For administrators every catalog
 * permission is spelled out with {@code ALL} scope, so clients need no special case.
 *
 * @author Abhay
 * @param id            user id
 * @param email         email address
 * @param firstName     first name
 * @param lastName      last name
 * @param fullName      first and last name
 * @param jobTitle      job title
 * @param phone         phone number
 * @param timezone      effective IANA time zone (user preference or tenant default)
 * @param locale        effective locale (user preference or tenant default)
 * @param mfaEnabled    whether two-factor authentication is on
 * @param administrator whether the user has the Administrator role
 * @param tenant        workspace summary and regional settings
 * @param role          the user's role (id and name)
 * @param team          the user's team, or {@code null}
 * @param permissions   resource key to action to granted scope
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MeResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String fullName,
        String jobTitle,
        String phone,
        String timezone,
        String locale,
        boolean mfaEnabled,
        boolean administrator,
        TenantSummary tenant,
        Ref role,
        Ref team,
        Map<String, Map<Action, AccessScope>> permissions) {
}
