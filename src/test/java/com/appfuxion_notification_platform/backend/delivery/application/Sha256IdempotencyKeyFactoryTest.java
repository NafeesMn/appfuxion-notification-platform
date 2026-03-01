package com.appfuxion_notification_platform.backend.delivery.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.appfuxion_notification_platform.backend.domain.shared.NotificationChannel;

class Sha256IdempotencyKeyFactoryTest {

    private final Sha256IdempotencyKeyFactory factory = new Sha256IdempotencyKeyFactory();

    @Test
    void build_shouldBeDeterministicAndSensitiveToInput() {
        UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID campaignId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID recipientId = UUID.fromString("33333333-3333-3333-3333-333333333333");

        String hashA1 = factory.build(
                tenantId,
                campaignId,
                recipientId,
                NotificationChannel.SMS,
                "message-hash-v1");
        String hashA2 = factory.build(
                tenantId,
                campaignId,
                recipientId,
                NotificationChannel.SMS,
                "message-hash-v1");
        String hashB = factory.build(
                tenantId,
                campaignId,
                recipientId,
                NotificationChannel.SMS,
                "message-hash-v2");

        assertEquals(hashA1, hashA2);
        assertNotEquals(hashA1, hashB);
        assertEquals(64, hashA1.length());
        assertTrue(hashA1.matches("[0-9a-f]{64}"));
    }

    @Test
    void build_shouldRejectNullInputs() {
        UUID value = UUID.randomUUID();

        assertThrows(
                NullPointerException.class,
                () -> factory.build(
                        null,
                        value,
                        value,
                        NotificationChannel.EMAIL,
                        "hash"));
    }
}
