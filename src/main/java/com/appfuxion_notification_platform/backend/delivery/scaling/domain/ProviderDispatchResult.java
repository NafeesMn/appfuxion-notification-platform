package com.appfuxion_notification_platform.backend.delivery.scaling.domain;

import java.util.Objects;

import com.appfuxion_notification_platform.backend.delivery.domain.NotificationAttemptOutcome;

public record ProviderDispatchResult(
        NotificationAttemptOutcome outcome,
        String errorCode,
        String errorMessage,
        String providerRequestId,
        String providerResponseCode,
        Integer latencyMs,
        String requestMetadata,
        String responseMetadata) {

    public ProviderDispatchResult {
        Objects.requireNonNull(outcome, "outcome");
    }

    public static ProviderDispatchResult success(
            String providerRequestId,
            String providerResponseCode,
            Integer latencyMs,
            String requestMetadata,
            String responseMetadata) {
        return new ProviderDispatchResult(
                NotificationAttemptOutcome.SUCCESS,
                null,
                null,
                providerRequestId,
                providerResponseCode,
                latencyMs,
                requestMetadata,
                responseMetadata);
    }

    public static ProviderDispatchResult retryableFailure(
            String errorCode,
            String errorMessage,
            String providerRequestId,
            String providerResponseCode,
            Integer latencyMs,
            String requestMetadata,
            String responseMetadata) {
        return new ProviderDispatchResult(
                NotificationAttemptOutcome.RETRYABLE_FAILURE,
                errorCode,
                errorMessage,
                providerRequestId,
                providerResponseCode,
                latencyMs,
                requestMetadata,
                responseMetadata);
    }

    public static ProviderDispatchResult terminalFailure(
            String errorCode,
            String errorMessage,
            String providerRequestId,
            String providerResponseCode,
            Integer latencyMs,
            String requestMetadata,
            String responseMetadata) {
        return new ProviderDispatchResult(
                NotificationAttemptOutcome.TERMINAL_FAILURE,
                errorCode,
                errorMessage,
                providerRequestId,
                providerResponseCode,
                latencyMs,
                requestMetadata,
                responseMetadata);
    }
}
