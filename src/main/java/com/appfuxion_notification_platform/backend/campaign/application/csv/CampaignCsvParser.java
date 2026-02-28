package com.appfuxion_notification_platform.backend.campaign.application.csv;

import java.io.IOException;
import java.io.InputStream;

import com.appfuxion_notification_platform.backend.domain.shared.NotificationChannel;

public interface CampaignCsvParser {

    CsvParseSummary parse(
            InputStream inputStream,
            NotificationChannel channel,
            CampaignCsvRowConsumer rowConsumer) throws IOException;
}
