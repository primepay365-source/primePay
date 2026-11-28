package com.primepay.common;

import com.primepay.common.error.BusinessException;
import com.primepay.common.error.ErrorCode;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * One storage format for the two sign-in identifiers, so look-ups and uniqueness checks always match:
 * email lower-case and trimmed; Indian mobile as +91 followed by 10 digits starting 6-9.
 */
public final class ContactNormalizer {
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+'-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern MOBILE_10 = Pattern.compile("^[6-9][0-9]{9}$");

    private ContactNormalizer() {
    }

    public static boolean looksLikeEmail(String value) {
        return value != null && value.contains("@");
    }

    /** Lower-case, trimmed email; throws on an invalid address. */
    public static String email(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (value.length() > 160 || !EMAIL.matcher(value).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Enter a valid email address.");
        }
        return value;
    }

    /** +91XXXXXXXXXX from "98765 43210", "+91-98765-43210", "919876543210" or "09876543210"; throws otherwise. */
    public static String mobile(String raw) {
        String digits = raw == null ? "" : raw.replaceAll("[^0-9]", "");
        if (digits.length() == 12 && digits.startsWith("91")) {
            digits = digits.substring(2);
        } else if (digits.length() == 11 && digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        if (!MOBILE_10.matcher(digits).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Enter a valid 10-digit Indian mobile number.");
        }
        return "+91" + digits;
    }

    /** Null or blank stays null; anything else must be a valid email. */
    public static String emailOrNull(String raw) {
        return raw == null || raw.isBlank() ? null : email(raw);
    }

    /** Null or blank stays null; anything else must be a valid mobile number. */
    public static String mobileOrNull(String raw) {
        return raw == null || raw.isBlank() ? null : mobile(raw);
    }

    /** "a•••@example.com" */
    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "your email";
        int at = email.indexOf('@');
        return email.charAt(0) + "•••" + email.substring(at);
    }

    /** "+91 ••••••3210" */
    public static String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 4) return "your mobile";
        return "+91 ••••••" + mobile.substring(mobile.length() - 4);
    }
}
