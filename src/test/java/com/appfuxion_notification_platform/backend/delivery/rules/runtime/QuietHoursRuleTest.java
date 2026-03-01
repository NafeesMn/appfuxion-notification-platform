package com.appfuxion_notification_platform.backend.delivery.rules.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.appfuxion_notification_platform.backend.campaign.persistence.Campaign;
import com.appfuxion_notification_platform.backend.campaign.persistence.CampaignRecipient;
import com.appfuxion_notification_platform.backend.delivery.persistence.NotificationJob;
import com.appfuxion_notification_platform.backend.delivery.rules.NotificationRuleAction;
import com.appfuxion_notification_platform.backend.delivery.rules.NotificationRuleContext;
import com.appfuxion_notification_platform.backend.delivery.rules.NotificationRuleDecision;
import com.appfuxion_notification_platform.backend.domain.shared.CampaignType;
import com.appfuxion_notification_platform.backend.domain.shared.NotificationChannel;
import com.appfuxion_notification_platform.backend.tenant.persistence.Tenant;

class QuietHoursRuleTest {

    private final QuietHoursRule rule = new QuietHoursRule();

    @Test
    void evaluate_shouldDelayMarketingSmsDuringQuietHours() {
        Instant now = Instant.parse("2026-03-01T15:00:00Z"); // 23:00 in Asia/Kuala_Lumpur
        NotificationRuleContext context = context(
                NotificationChannel.SMS,
                CampaignType.MARKETING,
                "Asia/Kuala_Lumpur",
                "Asia/Kuala_Lumpur");

        NotificationRuleDecision decision = rule.evaluate(context, now);

        assertEquals(NotificationRuleAction.DELAY, decision.action());
        assertEquals("QUIET_HOURS_ACTIVE", decision.reasonCode());
        assertEquals(Instant.parse("2026-03-02T00:00:00Z"), decision.nextEligibleAt());
    }

    @Test
    void evaluate_shouldAllowTransactionalSmsDuringQuietHours() {
        Instant now = Instant.parse("2026-03-01T15:00:00Z"); // 23:00 in Asia/Kuala_Lumpur
        NotificationRuleContext context = context(
                NotificationChannel.SMS,
                CampaignType.TRANSACTIONAL,
                "Asia/Kuala_Lumpur",
                "Asia/Kuala_Lumpur");

        NotificationRuleDecision decision = rule.evaluate(context, now);

        assertEquals(NotificationRuleAction.ALLOW, decision.action());
        assertEquals("ALLOWED", decision.reasonCode());
    }

    @Test
    void evaluate_shouldAllowEmailEvenDuringQuietHours() {
        Instant now = Instant.parse("2026-03-01T15:00:00Z");
        NotificationRuleContext context = context(
                NotificationChannel.EMAIL,
                CampaignType.MARKETING,
                "Asia/Kuala_Lumpur",
                "Asia/Kuala_Lumpur");

        NotificationRuleDecision decision = rule.evaluate(context, now);

        assertEquals(NotificationRuleAction.ALLOW, decision.action());
    }

    @Test
    void evaluate_shouldFallbackToTenantTimezoneWhenRecipientTimezoneMissing() {
        Instant now = Instant.parse("2026-03-01T15:00:00Z"); // 23:00 in Asia/Kuala_Lumpur
        NotificationRuleContext context = context(
                NotificationChannel.PUSH,
                CampaignType.MARKETING,
                "",
                "Asia/Kuala_Lumpur");

        NotificationRuleDecision decision = rule.evaluate(context, now);

        assertEquals(NotificationRuleAction.DELAY, decision.action());
        assertEquals(Instant.parse("2026-03-02T00:00:00Z"), decision.nextEligibleAt());
    }

    private NotificationRuleContext context(
            NotificationChannel channel,
            CampaignType campaignType,
            String recipientTimezone,
            String tenantTimezone) {
        NotificationJob job = new NotificationJob();
        job.setCampaignId(UUID.randomUUID());
        job.setCampaignRecipientId(UUID.randomUUID());
        job.setTenantId(UUID.randomUUID());
        job.setChannel(channel);

        Campaign campaign = Mockito.mock(Campaign.class);
        Mockito.when(campaign.getCampaignType()).thenReturn(campaignType);

        CampaignRecipient recipient = Mockito.mock(CampaignRecipient.class);
        Mockito.when(recipient.getTimezone()).thenReturn(recipientTimezone);

        Tenant tenant = Mockito.mock(Tenant.class);
        Mockito.when(tenant.getDefaultTimezone()).thenReturn(tenantTimezone);

        return new NotificationRuleContext(job, campaign, recipient, tenant);
    }
}
