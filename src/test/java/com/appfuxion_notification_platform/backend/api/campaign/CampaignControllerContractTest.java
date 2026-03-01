package com.appfuxion_notification_platform.backend.api.campaign;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.appfuxion_notification_platform.backend.api.campaign.dto.CampaignDeliverySummaryResponse;
import com.appfuxion_notification_platform.backend.api.campaign.dto.CampaignDetailResponse;
import com.appfuxion_notification_platform.backend.api.campaign.dto.CampaignImportSummaryResponse;
import com.appfuxion_notification_platform.backend.api.campaign.dto.CampaignListResponse;
import com.appfuxion_notification_platform.backend.api.campaign.dto.CampaignSummaryResponse;
import com.appfuxion_notification_platform.backend.api.campaign.dto.RetryFailuresAcceptedResponse;
import com.appfuxion_notification_platform.backend.api.common.GlobalApiExceptionHandler;
import com.appfuxion_notification_platform.backend.api.tenant.TenantContextInterceptor;
import com.appfuxion_notification_platform.backend.campaign.api.CampaignApiMapper;
import com.appfuxion_notification_platform.backend.campaign.application.CampaignCreateService;
import com.appfuxion_notification_platform.backend.campaign.application.CampaignQueryService;
import com.appfuxion_notification_platform.backend.campaign.application.CampaignRetryFailuresService;
import com.appfuxion_notification_platform.backend.campaign.domain.CampaignStatus;
import com.appfuxion_notification_platform.backend.domain.shared.CampaignType;
import com.appfuxion_notification_platform.backend.domain.shared.NotificationChannel;

class CampaignControllerContractTest {

    private MockMvc mockMvc;
    private CampaignQueryService campaignQueryService;
    private CampaignRetryFailuresService campaignRetryFailuresService;

    @BeforeEach
    void setUp() {
        CampaignCreateService campaignCreateService = Mockito.mock(CampaignCreateService.class);
        CampaignApiMapper campaignApiMapper = Mockito.mock(CampaignApiMapper.class);
        campaignQueryService = Mockito.mock(CampaignQueryService.class);
        campaignRetryFailuresService = Mockito.mock(CampaignRetryFailuresService.class);

        CampaignController controller = new CampaignController(
                campaignCreateService,
                campaignApiMapper,
                campaignQueryService,
                campaignRetryFailuresService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalApiExceptionHandler())
                .addInterceptors(new TenantContextInterceptor("X-Tenant-Key"))
                .build();
    }

    @Test
    void listCampaigns_shouldReturn200WithCampaignPage() throws Exception {
        UUID campaignId = UUID.randomUUID();
        CampaignListResponse response = new CampaignListResponse(
                0,
                20,
                1,
                1,
                List.of(new CampaignSummaryResponse(
                        campaignId,
                        NotificationChannel.EMAIL,
                        CampaignType.MARKETING,
                        CampaignStatus.PROCESSING,
                        Instant.parse("2026-03-01T00:00:00Z"),
                        new CampaignDeliverySummaryResponse(10, 7, 1, 1, 0, 1))));

        when(campaignQueryService.listCampaigns("tenant-acme", 0, 20, null, null)).thenReturn(response);

        mockMvc.perform(get("/campaigns")
                        .header("X-Tenant-Key", "tenant-acme"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(campaignId.toString()))
                .andExpect(jsonPath("$.items[0].deliverySummary.pending").value(1));
    }

    @Test
    void getCampaign_shouldReturn200WithCampaignDetail() throws Exception {
        UUID campaignId = UUID.randomUUID();
        CampaignDetailResponse response = new CampaignDetailResponse(
                campaignId,
                UUID.randomUUID(),
                NotificationChannel.SMS,
                CampaignType.MARKETING,
                CampaignStatus.PROCESSING,
                "Hello",
                "qa-user",
                Instant.parse("2026-03-01T00:00:00Z"),
                Instant.parse("2026-03-01T00:10:00Z"),
                new CampaignImportSummaryResponse(100, 95, 5),
                new CampaignDeliverySummaryResponse(95, 70, 10, 5, 3, 7));

        when(campaignQueryService.getCampaign(eq("tenant-acme"), eq(campaignId))).thenReturn(response);

        mockMvc.perform(get("/campaigns/{id}", campaignId)
                        .header("X-Tenant-Key", "tenant-acme"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(campaignId.toString()))
                .andExpect(jsonPath("$.importSummary.invalidRows").value(5));
    }

    @Test
    void retryFailures_shouldReturn202Accepted() throws Exception {
        UUID campaignId = UUID.randomUUID();
        RetryFailuresAcceptedResponse response = new RetryFailuresAcceptedResponse(
                campaignId,
                "RETRY_ACCEPTED",
                Instant.parse("2026-03-01T01:00:00Z"));

        when(campaignRetryFailuresService.retryFailures("tenant-acme", campaignId)).thenReturn(response);

        mockMvc.perform(post("/campaigns/{id}/retry-failures", campaignId)
                        .header("X-Tenant-Key", "tenant-acme"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.campaignId").value(campaignId.toString()))
                .andExpect(jsonPath("$.status").value("RETRY_ACCEPTED"));
    }
}
