package com.appfuxion_notification_platform.backend.delivery.rules.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.appfuxion_notification_platform.backend.campaign.persistence.Campaign;
import com.appfuxion_notification_platform.backend.campaign.persistence.CampaignRecipient;
import com.appfuxion_notification_platform.backend.delivery.persistence.NotificationJob;
import com.appfuxion_notification_platform.backend.delivery.rules.NotificationRuleAction;
import com.appfuxion_notification_platform.backend.delivery.rules.NotificationRuleContext;
import com.appfuxion_notification_platform.backend.delivery.rules.NotificationRuleDecision;
import com.appfuxion_notification_platform.backend.domain.shared.NotificationChannel;
import com.appfuxion_notification_platform.backend.tenant.persistence.SuppressionEntry;
import com.appfuxion_notification_platform.backend.tenant.persistence.SuppressionEntryRepository;
import com.appfuxion_notification_platform.backend.tenant.persistence.Tenant;

class SuppressionRuleTest {

    @Test
    void evaluate_shouldSkipWhenSuppressionEntryExists() {
        SuppressionEntryRepository suppressionEntryRepository = Mockito.mock(SuppressionEntryRepository.class);
        SuppressionEntry entry = Mockito.mock(SuppressionEntry.class);
        Mockito.when(entry.getReasonCode()).thenReturn("GLOBAL_SUPPRESSION");
        Mockito.when(suppressionEntryRepository.findByActiveTrueAndSuppressionKeyAndChannelScopeIn(
                eq("user@example.com"), anyCollection()))
                .thenReturn(List.of(entry));

        SuppressionRule rule = new SuppressionRule(suppressionEntryRepository);
        NotificationRuleDecision decision = rule.evaluate(context(NotificationChannel.EMAIL, "user@example.com"), Instant.now());

        assertEquals(NotificationRuleAction.SKIP, decision.action());
        assertEquals("GLOBAL_SUPPRESSION", decision.reasonCode());
    }

    @Test
    void evaluate_shouldAllowWhenNoSuppressionEntryExists() {
        SuppressionEntryRepository suppressionEntryRepository = Mockito.mock(SuppressionEntryRepository.class);
        Mockito.when(suppressionEntryRepository.findByActiveTrueAndSuppressionKeyAndChannelScopeIn(
                eq("user@example.com"), anyCollection()))
                .thenReturn(List.of());

        SuppressionRule rule = new SuppressionRule(suppressionEntryRepository);
        NotificationRuleDecision decision = rule.evaluate(context(NotificationChannel.EMAIL, "user@example.com"), Instant.now());

        assertEquals(NotificationRuleAction.ALLOW, decision.action());
        assertEquals("ALLOWED", decision.reasonCode());
    }

    private NotificationRuleContext context(NotificationChannel channel, String suppressionKey) {
        NotificationJob job = new NotificationJob();
        job.setCampaignId(UUID.randomUUID());
        job.setCampaignRecipientId(UUID.randomUUID());
        job.setTenantId(UUID.randomUUID());
        job.setChannel(channel);

        Campaign campaign = Mockito.mock(Campaign.class);

        CampaignRecipient recipient = Mockito.mock(CampaignRecipient.class);
        Mockito.when(recipient.getNormalizedRecipientKey()).thenReturn(suppressionKey);

        Tenant tenant = Mockito.mock(Tenant.class);

        return new NotificationRuleContext(job, campaign, recipient, tenant);
    }
}
