package com.appfuxion_notification_platform.backend.delivery.rules.runtime;

import java.util.Objects;
import java.util.UUID;

import com.appfuxion_notification_platform.backend.campaign.persistence.Campaign;
import com.appfuxion_notification_platform.backend.campaign.persistence.CampaignRecipient;
import com.appfuxion_notification_platform.backend.campaign.persistence.CampaignRecipientRepository;
import com.appfuxion_notification_platform.backend.campaign.persistence.CampaignRepository;
import com.appfuxion_notification_platform.backend.delivery.persistence.NotificationJob;
import com.appfuxion_notification_platform.backend.delivery.rules.NotificationRuleContext;
import com.appfuxion_notification_platform.backend.delivery.rules.NotificationRuleContextLoader;
import com.appfuxion_notification_platform.backend.tenant.persistence.Tenant;
import com.appfuxion_notification_platform.backend.tenant.persistence.TenantRepository;

public class RepositoryNotificationRuleContextLoader implements NotificationRuleContextLoader {

    private final CampaignRepository campaignRepository;
    private final CampaignRecipientRepository campaignRecipientRepository;
    private final TenantRepository tenantRepository;

    public RepositoryNotificationRuleContextLoader(
            CampaignRepository campaignRepository,
            CampaignRecipientRepository campaignRecipientRepository,
            TenantRepository tenantRepository) {
        this.campaignRepository = Objects.requireNonNull(campaignRepository);
        this.campaignRecipientRepository = Objects.requireNonNull(campaignRecipientRepository);
        this.tenantRepository = Objects.requireNonNull(tenantRepository);
    }

    @Override
    public NotificationRuleContext loadFor(NotificationJob job) {
        Objects.requireNonNull(job, "job");

        Campaign campaign = campaignRepository.findById(job.getCampaignId())
                .orElseThrow(() -> new IllegalStateException("Campaign not found: " + job.getCampaignId()));
        CampaignRecipient recipient = campaignRecipientRepository.findById(job.getCampaignRecipientId())
                .orElseThrow(() -> new IllegalStateException("Campaign recipient not found: " + job.getCampaignRecipientId()));
        Tenant tenant = tenantRepository.findById(job.getTenantId())
                .orElseThrow(() -> new IllegalStateException("Tenant not found: " + job.getTenantId()));

        UUID tenantId = job.getTenantId();
        verifyTenantConsistency("campaign", campaign.getTenantId(), tenantId);
        verifyTenantConsistency("campaignRecipient", recipient.getTenantId(), tenantId);

        return new NotificationRuleContext(job, campaign, recipient, tenant);
    }

    private void verifyTenantConsistency(String source, UUID sourceTenantId, UUID expectedTenantId) {
        if (!Objects.equals(sourceTenantId, expectedTenantId)) {
            throw new IllegalStateException(source + " tenant mismatch; expected " + expectedTenantId
                    + " but found " + sourceTenantId);
        }
    }
}
