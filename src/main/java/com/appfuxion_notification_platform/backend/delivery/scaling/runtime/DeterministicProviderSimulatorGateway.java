package com.appfuxion_notification_platform.backend.delivery.scaling.runtime;

import java.util.Objects;

import com.appfuxion_notification_platform.backend.delivery.persistence.NotificationJob;
import com.appfuxion_notification_platform.backend.delivery.scaling.NotificationProviderGateway;
import com.appfuxion_notification_platform.backend.delivery.scaling.domain.ProviderDispatchResult;
import com.appfuxion_notification_platform.backend.delivery.scaling.domain.WorkerIdentity;

public class DeterministicProviderSimulatorGateway implements NotificationProviderGateway {

    @Override
    public ProviderDispatchResult dispatch(NotificationJob job, WorkerIdentity worker) {
        Objects.requireNonNull(job, "job");
        Objects.requireNonNull(worker, "worker");

        int nextAttemptNumber = Math.max(1, job.getAttemptCount() + 1);
        String idempotencyKey = job.getIdempotencyKey() == null ? "" : job.getIdempotencyKey();
        int bucket = Math.floorMod(Objects.hash(idempotencyKey, nextAttemptNumber), 100);

        String providerRequestId = "%s-%s-a%d".formatted(worker.workerId(), job.getChannel().name(), nextAttemptNumber);
        String requestMetadata = "{\"idempotencyKey\":\"" + idempotencyKey + "\"}";
        int latencyMs = 20 + (bucket % 80);

        if (bucket < 70) {
            return ProviderDispatchResult.success(
                    providerRequestId,
                    "200_OK",
                    latencyMs,
                    requestMetadata,
                    "{\"result\":\"accepted\"}");
        }

        if (bucket < 90) {
            return ProviderDispatchResult.retryableFailure(
                    "PROVIDER_TEMPORARY_UNAVAILABLE",
                    "Temporary provider failure; safe to retry",
                    providerRequestId,
                    "503_UNAVAILABLE",
                    latencyMs,
                    requestMetadata,
                    "{\"result\":\"retryable_failure\"}");
        }

        return ProviderDispatchResult.terminalFailure(
                "PROVIDER_INVALID_RECIPIENT",
                "Recipient rejected by provider",
                providerRequestId,
                "422_INVALID",
                latencyMs,
                requestMetadata,
                "{\"result\":\"terminal_failure\"}");
    }
}
