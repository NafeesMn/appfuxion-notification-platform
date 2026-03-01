package com.appfuxion_notification_platform.backend.delivery.scaling.domain;

import java.time.Instant;

public record BackPressureDecision(
        boolean allowedNow,
        Instant nextEligibleAt,
        String reason) {
}
