package com.orbitcrm.identity.util;

import com.orbitcrm.shared.exception.ApiException;

/**
 * Minimum password rules applied at signup, invitation acceptance, password reset and password change.
 *
 * <p>Purpose: reject weak passwords with a clear validation error ({@code WEAK_PASSWORD}). Length matters more than
 * composition, so a password must have 10 to 128 characters, contain at least one letter and one digit, and must
 * not contain the local part of the user's email address (when that part has four or more characters).
 *
 * @author Abhay
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 10;
    public static final int MAX_LENGTH = 128;

    private PasswordPolicy() {
    }

    public static void validate(String password, String email, String field) {
        if (password == null || password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            throw ApiException.validation(field, "WEAK_PASSWORD",
                    "Password must be between " + MIN_LENGTH + " and " + MAX_LENGTH + " characters");
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw ApiException.validation(field, "WEAK_PASSWORD", "Password must contain letters and digits");
        }
        if (email != null) {
            String local = email.split("@")[0].toLowerCase(java.util.Locale.ROOT);
            if (local.length() >= 4 && password.toLowerCase(java.util.Locale.ROOT).contains(local)) {
                throw ApiException.validation(field, "WEAK_PASSWORD", "Password must not contain your email name");
            }
        }
    }
}
