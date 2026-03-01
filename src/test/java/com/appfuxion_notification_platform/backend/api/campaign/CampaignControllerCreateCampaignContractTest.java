package com.appfuxion_notification_platform.backend.api.campaign;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.appfuxion_notification_platform.backend.api.common.GlobalApiExceptionHandler;
import com.appfuxion_notification_platform.backend.api.tenant.TenantContextInterceptor;
import com.appfuxion_notification_platform.backend.api.campaign.dto.CreateCampaignMetadataRequest;
import com.appfuxion_notification_platform.backend.campaign.api.CampaignApiMapper;
import com.appfuxion_notification_platform.backend.campaign.application.CampaignCreateService;
import com.appfuxion_notification_platform.backend.campaign.application.CampaignQueryService;
import com.appfuxion_notification_platform.backend.campaign.application.CampaignRetryFailuresService;
import com.appfuxion_notification_platform.backend.campaign.application.CreateCampaignCommand;
import com.appfuxion_notification_platform.backend.campaign.application.CreateCampaignResult;
import com.appfuxion_notification_platform.backend.campaign.domain.CampaignStatus;
import com.appfuxion_notification_platform.backend.domain.shared.CampaignType;
import com.appfuxion_notification_platform.backend.domain.shared.NotificationChannel;
import com.appfuxion_notification_platform.backend.api.campaign.dto.CampaignCreateAcceptedResponse;
import com.appfuxion_notification_platform.backend.api.campaign.dto.CampaignImportSummaryResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

class CampaignControllerCreateCampaignContractTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private CampaignCreateService campaignCreateService;
    private CampaignApiMapper campaignApiMapper;

    @BeforeEach
    void setUp() {
        campaignCreateService = Mockito.mock(CampaignCreateService.class);
        campaignApiMapper = Mockito.mock(CampaignApiMapper.class);
        CampaignQueryService campaignQueryService = Mockito.mock(CampaignQueryService.class);
        CampaignRetryFailuresService campaignRetryFailuresService = Mockito.mock(CampaignRetryFailuresService.class);

        this.mockMvc = MockMvcBuilders.standaloneSetup(new CampaignController(
                        campaignCreateService,
                        campaignApiMapper,
                        campaignQueryService,
                        campaignRetryFailuresService))
                .setControllerAdvice(new GlobalApiExceptionHandler())
                .addInterceptors(new TenantContextInterceptor("X-Tenant-Key"))
                .build();
    }

    @Test
    void createCampaign_shouldReturn202Accepted_forValidEmailCsv() throws Exception {
        MockMultipartFile metadata = new MockMultipartFile(
                "metadata",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(new TestMetadata(
                        "EMAIL",
                        "MARKETING",
                        "Hello {{name}}",
                        "phase3-test")));
        MockMultipartFile csvFile = new MockMultipartFile(
                "file",
                "recipients.csv",
                "text/csv",
                """
                email,timezone
                alice@example.com,America/New_York
                """.getBytes());

        UUID campaignId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        CreateCampaignCommand command = new CreateCampaignCommand(
                "tenant-acme",
                correlationId,
                NotificationChannel.EMAIL,
                CampaignType.MARKETING,
                "Hello {{name}}",
                "phase3-test",
                "recipients.csv",
                new ByteArrayInputStream("email\nalice@example.com".getBytes()));
        CreateCampaignResult result = new CreateCampaignResult(
                campaignId,
                correlationId,
                CampaignStatus.READY_FOR_DISPATCH,
                Instant.parse("2026-03-01T00:00:00Z"),
                1,
                1,
                0,
                true);
        CampaignCreateAcceptedResponse acceptedResponse = new CampaignCreateAcceptedResponse(
                campaignId,
                correlationId,
                "READY_FOR_DISPATCH",
                Instant.parse("2026-03-01T00:00:00Z"),
                new CampaignImportSummaryResponse(1, 1, 0));

        when(campaignApiMapper.toCreateCommand(eq("tenant-acme"), any(UUID.class), any(CreateCampaignMetadataRequest.class), any()))
                .thenReturn(command);
        when(campaignCreateService.createCampaign(command)).thenReturn(result);
        when(campaignApiMapper.toCreateAcceptedResponse(result)).thenReturn(acceptedResponse);

        mockMvc.perform(multipart("/campaigns")
                        .file(metadata)
                        .file(csvFile)
                .header("X-Tenant-Key", "tenant-acme"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.campaignId").isNotEmpty())
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void createCampaign_shouldReturn400_whenEmailCampaignCsvIsMissingEmailHeader() throws Exception {
        MockMultipartFile metadata = new MockMultipartFile(
                "metadata",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(new TestMetadata(
                        "EMAIL",
                        "MARKETING",
                        "Hello",
                        "phase3-test")));
        MockMultipartFile csvFile = new MockMultipartFile(
                "file",
                "recipients.csv",
                "text/csv",
                new byte[0]);

        mockMvc.perform(multipart("/campaigns")
                        .file(metadata)
                        .file(csvFile)
                        .header("X-Tenant-Key", "tenant-acme"))
                .andExpect(status().isBadRequest());
    }

    private record TestMetadata(
            String channel,
            String campaignType,
            String messageTemplate,
            String createdBy) {
    }
}
