package com.appfuxion_notification_platform.backend.api.campaign;

import java.util.List;
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
import com.appfuxion_notification_platform.backend.api.campaign.dto.CampaignSummaryResponse;
import com.appfuxion_notification_platform.backend.api.campaign.dto.CreateCampaignMetadataRequest;
import com.appfuxion_notification_platform.backend.api.campaign.dto.CampaignCreateAcceptedResponse;
import com.appfuxion_notification_platform.backend.api.campaign.dto.CampaignDetailResponse;
import com.appfuxion_notification_platform.backend.api.campaign.dto.RetryFailuresAcceptedResponse;
import com.appfuxion_notification_platform.backend.api.common.ApiBadRequestException;
import com.appfuxion_notification_platform.backend.api.common.FeatureNotImplementedException;
import com.appfuxion_notification_platform.backend.api.tenant.TenantContextHolder;
import com.appfuxion_notification_platform.backend.campaign.domain.CampaignStatus;
import com.appfuxion_notification_platform.backend.domain.shared.NotificationChannel;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@Validated
@RequestMapping(path = "/campaigns", produces = MediaType.APPLICATION_JSON_VALUE)
public class CampaignController {

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CampaignCreateAcceptedResponse> createCampaign(
            @Valid @RequestPart("metadata") CreateCampaignMetadataRequest metadata,
            @RequestPart("file") MultipartFile file) {
        TenantContextHolder.requireTenantKey();
        requireNonEmptyCsv(file);

        throw new FeatureNotImplementedException(
                "POST /campaigns is not implemented yet. Phase 2 provides API contracts, validation, and tenant context only.");
    }

    @GetMapping
    public ResponseEntity<CampaignListResponse> listCampaigns(
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page must be >= 0") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size must be >= 1") @Max(value = 200, message = "size must be <= 200") int size,
            @RequestParam(required = false) CampaignStatus status,
            @RequestParam(required = false) NotificationChannel channel) {
        TenantContextHolder.requireTenantKey();

        CampaignListResponse placeholderResponse = new CampaignListResponse(
                page,
                size,
                0,
                0,
                List.<CampaignSummaryResponse>of());
        return ResponseEntity.ok(placeholderResponse);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CampaignDetailResponse> getCampaign(@PathVariable UUID id) {
        TenantContextHolder.requireTenantKey();

        throw new FeatureNotImplementedException(
                "GET /campaigns/{id} is not implemented yet. Phase 2 provides the endpoint contract only.");
    }

    @PostMapping("/{id}/retry-failures")
    public ResponseEntity<RetryFailuresAcceptedResponse> retryFailures(@PathVariable UUID id) {
        TenantContextHolder.requireTenantKey();

        throw new FeatureNotImplementedException(
                "POST /campaigns/{id}/retry-failures is not implemented yet. Phase 2 provides the endpoint contract only.");
    }

    private void requireNonEmptyCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiBadRequestException("CSV file part 'file' must not be empty");
        }
    }
}
