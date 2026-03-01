package com.appfuxion_notification_platform.backend.observability.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PiiMaskerTest {

    @Test
    void maskEmail_shouldKeepOnlyPrefixAndDomain() {
        assertEquals("a***@example.com", PiiMasker.maskEmail("alice@example.com"));
    }

    @Test
    void maskPhone_shouldKeepOnlyLast4Digits() {
        assertEquals("***4567", PiiMasker.maskPhone("+1 (555) 123-4567"));
    }

    @Test
    void maskToken_shouldKeepPrefixAndSuffix() {
        assertEquals("abcd***wxyz", PiiMasker.maskToken("abcd1234wxyz"));
    }

    @Test
    void maskMessageBody_shouldRedactContent() {
        assertEquals("[REDACTED len=11]", PiiMasker.maskMessageBody("hello world"));
    }
}
