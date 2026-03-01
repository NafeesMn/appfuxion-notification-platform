package com.appfuxion_notification_platform.backend.observability.logging;

public final class PiiMasker {

    private PiiMasker() {
    }

    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "***";
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(atIndex);
    }

    public static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return "***";
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() <= 4) {
            return "***";
        }
        return "***" + digits.substring(digits.length() - 4);
    }

    public static String maskToken(String token) {
        if (token == null || token.isBlank()) {
            return "***";
        }
        if (token.length() <= 8) {
            return "***";
        }
        return token.substring(0, 4) + "***" + token.substring(token.length() - 4);
    }

    public static String maskMessageBody(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        return "[REDACTED len=%d]".formatted(body.length());
    }
}
