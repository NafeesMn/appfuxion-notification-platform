package com.appfuxion_notification_platform.backend.delivery.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.appfuxion_notification_platform.backend.domain.shared.NotificationChannel;

@Component
public class Sha256IdempotencyKeyFactory implements IdempotencyKeyFactory {

    @Override
    public String build(
            UUID tenantId,
            UUID campaignId,
            UUID campaignRecipientId,
            NotificationChannel channel,
            String normalizedMessageHash) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(campaignId, "campaignId");
        Objects.requireNonNull(campaignRecipientId, "campaignRecipientId");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(normalizedMessageHash, "normalizedMessageHash");

        String raw = tenantId + "|" + campaignId + "|" + campaignRecipientId + "|" + channel.name() + "|" + normalizedMessageHash.strip();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
