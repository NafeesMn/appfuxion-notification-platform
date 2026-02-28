package com.appfuxion_notification_platform.backend.campaign.application;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

class DefaultCampaignCreateServicePhase3TodoTest {

    @Test
    void createCampaign_shouldPersistCampaignRecipientsJobsAndOutboxInOneTransactionalFlow() {
        fail("""
                TODO (Phase 3): write this test first using fakes/mocks.
                Expected behavior:
                - tenant is resolved from tenantKey
                - campaign is persisted in INGESTING/READY state with counters
                - CSV rows are streamed (not loaded fully)
                - invalid rows are stored in campaign_import_row_errors
                - accepted rows create campaign_recipients + notification_jobs
                - outbox event is inserted only when there is at least one accepted recipient
                - result returns 202-style accepted payload metadata (campaignId/correlationId/counts)
                """);
    }

    @Test
    void createCampaign_shouldNotEnqueueOutboxEvent_whenAllRowsAreInvalid() {
        fail("""
                TODO (Phase 3): assert no dispatch outbox event is persisted when acceptedRows == 0.
                Also assert campaign status/counters reflect import failure semantics clearly.
                """);
    }
}
