package com.appfuxion_notification_platform.backend.campaign.api;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.appfuxion_notification_platform.backend.api.campaign.dto.CampaignCreateAcceptedResponse;
import com.appfuxion_notification_platform.backend.api.campaign.dto.CampaignImportSummaryResponse;
import com.appfuxion_notification_platform.backend.api.campaign.dto.CreateCampaignMetadataRequest;
import com.appfuxion_notification_platform.backend.campaign.application.CreateCampaignCommand;
import com.appfuxion_notification_platform.backend.campaign.application.CreateCampaignResult;
import com.appfuxion_notification_platform.backend.campaign.domain.CampaignStatus;

@Component
public class DefaultCampaignApiMapper implements CampaignApiMapper {

    @Override
    public CreateCampaignCommand toCreateCommand(
            String tenantKey,
            UUID correlationId,
            CreateCampaignMetadataRequest metadata,
            MultipartFile file) throws IOException {
        Objects.requireNonNull(tenantKey, "tenantKey");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(file, "file");

        InputStream csvInputStream = file.getInputStream();
        String originalFilename = StringUtils.hasText(file.getOriginalFilename())
                ? StringUtils.cleanPath(file.getOriginalFilename())
                : "recipients.csv";
        return new CreateCampaignCommand(
                tenantKey,
                correlationId,
                metadata.channel(),
                metadata.campaignType(),
                metadata.messageTemplate(),
                metadata.createdBy(),
                originalFilename,
                csvInputStream);
    }

    @Override
    public CampaignCreateAcceptedResponse toCreateAcceptedResponse(CreateCampaignResult result) {
        Objects.requireNonNull(result, "result");

        String mapApiStatus = mapApiStatus(result.status());

        CampaignImportSummaryResponse campaignImportSummaryResponse = new CampaignImportSummaryResponse(
            result.totalRows(), result.acceptedRows(), result.invalidRows());

        return new CampaignCreateAcceptedResponse(
            result.campaignId(),
            result.correlationId(),
            mapApiStatus,
            result.acceptedAt(),
            campaignImportSummaryResponse
        );
    }

    private String mapApiStatus(CampaignStatus status) {
        return switch(status) {
            case INGESTING -> "INGESTING";
            case READY_FOR_DISPATCH -> "READY_FOR_DISPATCH";
            case PROCESSING -> "PROCESSING";
            case COMPLETED -> "COMPLETED";
            case COMPLETED_WITH_FAILURES -> "COMPLETED_WITH_FAILURES";
            case FAILED_IMPORT -> "FAILED_IMPORT";
        };
    }
}
