package com.appfuxion_notification_platform.backend.api.campaign;

import java.io.IOException;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.appfuxion_notification_platform.backend.api.campaign.dto.CampaignListResponse;
import com.appfuxion_notification_platform.backend.api.campaign.dto.CreateCampaignMetadataRequest;
import com.appfuxion_notification_platform.backend.api.campaign.dto.CampaignCreateAcceptedResponse;
import com.appfuxion_notification_platform.backend.api.campaign.dto.CampaignDetailResponse;
import com.appfuxion_notification_platform.backend.api.campaign.dto.RetryFailuresAcceptedResponse;
import com.appfuxion_notification_platform.backend.api.common.ApiBadRequestException;
import com.appfuxion_notification_platform.backend.api.tenant.TenantContextHolder;
import com.appfuxion_notification_platform.backend.campaign.api.CampaignApiMapper;
import com.appfuxion_notification_platform.backend.campaign.application.CampaignCreateService;
import com.appfuxion_notification_platform.backend.campaign.application.CampaignQueryService;
import com.appfuxion_notification_platform.backend.campaign.application.CampaignRetryFailuresService;
import com.appfuxion_notification_platform.backend.campaign.application.CreateCampaignCommand;
import com.appfuxion_notification_platform.backend.campaign.application.CreateCampaignResult;
import com.appfuxion_notification_platform.backend.campaign.domain.CampaignStatus;
import com.appfuxion_notification_platform.backend.domain.shared.NotificationChannel;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@Validated
@RequestMapping(path = "/campaigns", produces = MediaType.APPLICATION_JSON_VALUE)
public class CampaignController {

    private final CampaignCreateService campaignCreateService;
    private final CampaignApiMapper campaignApiMapper;
    private final CampaignQueryService campaignQueryService;
    private final CampaignRetryFailuresService campaignRetryFailuresService;

    public CampaignController(
            CampaignCreateService campaignCreateService,
            CampaignApiMapper campaignApiMapper,
            CampaignQueryService campaignQueryService,
            CampaignRetryFailuresService campaignRetryFailuresService) {
        this.campaignCreateService = campaignCreateService;
        this.campaignApiMapper = campaignApiMapper;
        this.campaignQueryService = campaignQueryService;
        this.campaignRetryFailuresService = campaignRetryFailuresService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CampaignCreateAcceptedResponse> createCampaign(
            @Valid @RequestPart("metadata") CreateCampaignMetadataRequest metadata,
            @RequestPart("file") MultipartFile file) {
        String tenantKey = TenantContextHolder.requireTenantKey();
        requireNonEmptyCsv(file);

        try {
            CreateCampaignCommand command = campaignApiMapper.toCreateCommand(
                    tenantKey,
                    UUID.randomUUID(),
                    metadata,
                    file);
            CreateCampaignResult result = campaignCreateService.createCampaign(command);
            return ResponseEntity.accepted().body(campaignApiMapper.toCreateAcceptedResponse(result));
        } catch (IOException ex) {
            throw new ApiBadRequestException("Failed to read uploaded CSV file");
        }
    }

    @GetMapping
    public ResponseEntity<CampaignListResponse> listCampaigns(
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page must be >= 0") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size must be >= 1") @Max(value = 200, message = "size must be <= 200") int size,
            @RequestParam(required = false) CampaignStatus status,
            @RequestParam(required = false) NotificationChannel channel) {
        String tenantKey = TenantContextHolder.requireTenantKey();
        CampaignListResponse response = campaignQueryService.listCampaigns(tenantKey, page, size, status, channel);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CampaignDetailResponse> getCampaign(@PathVariable UUID id) {
        String tenantKey = TenantContextHolder.requireTenantKey();
        CampaignDetailResponse response = campaignQueryService.getCampaign(tenantKey, id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/retry-failures")
    public ResponseEntity<RetryFailuresAcceptedResponse> retryFailures(@PathVariable UUID id) {
        String tenantKey = TenantContextHolder.requireTenantKey();
        RetryFailuresAcceptedResponse response = campaignRetryFailuresService.retryFailures(tenantKey, id);
        return ResponseEntity.accepted().body(response);
    }

    private void requireNonEmptyCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiBadRequestException("CSV file part 'file' must not be empty");
        }
    }
}
