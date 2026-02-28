package com.appfuxion_notification_platform.backend.campaign.application.csv;

@FunctionalInterface
public interface CampaignCsvRowConsumer {

    void accept(CampaignCsvRow row);
}
