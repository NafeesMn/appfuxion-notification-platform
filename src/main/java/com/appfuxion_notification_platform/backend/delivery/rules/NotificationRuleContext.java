package com.appfuxion_notification_platform.backend.delivery.rules;

import java.util.Objects;

import com.appfuxion_notification_platform.backend.campaign.persistence.Campaign;
import com.appfuxion_notification_platform.backend.campaign.persistence.CampaignRecipient;
import com.appfuxion_notification_platform.backend.delivery.persistence.NotificationJob;
import com.appfuxion_notification_platform.backend.tenant.persistence.Tenant;

public record NotificationRuleContext(
        NotificationJob job,
        Campaign campaign,
        CampaignRecipient recipient,
        Tenant tenant) {

    public NotificationRuleContext {
        Objects.requireNonNull(job, "job");
        Objects.requireNonNull(campaign, "campaign");
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(tenant, "tenant");
    }
}
