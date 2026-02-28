package com.appfuxion_notification_platform.backend.campaign.api;

import java.io.IOException;
import java.util.UUID;

import org.springframework.web.multipart.MultipartFile;

import com.appfuxion_notification_platform.backend.api.campaign.dto.CampaignCreateAcceptedResponse;
import com.appfuxion_notification_platform.backend.api.campaign.dto.CreateCampaignMetadataRequest;
import com.appfuxion_notification_platform.backend.campaign.application.CreateCampaignCommand;
import com.appfuxion_notification_platform.backend.campaign.application.CreateCampaignResult;

public interface CampaignApiMapper {

    CreateCampaignCommand toCreateCommand(
            String tenantKey,
            UUID correlationId,
            CreateCampaignMetadataRequest metadata,
            MultipartFile file) throws IOException;

    CampaignCreateAcceptedResponse toCreateAcceptedResponse(CreateCampaignResult result);
}
