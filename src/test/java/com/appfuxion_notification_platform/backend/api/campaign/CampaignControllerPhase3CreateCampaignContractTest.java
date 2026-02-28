package com.appfuxion_notification_platform.backend.api.campaign;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.appfuxion_notification_platform.backend.api.common.GlobalApiExceptionHandler;
import com.appfuxion_notification_platform.backend.api.tenant.TenantContextInterceptor;
import com.fasterxml.jackson.databind.ObjectMapper;

class CampaignControllerPhase3CreateCampaignContractTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.standaloneSetup(new CampaignController())
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

        // Intentionally failing in Phase 3 tutor workflow:
        // expected behavior after implementation is 202 + accepted response payload.
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
                """
                phone_number,timezone
                +15551234567,UTC
                """.getBytes());

        // Intentionally failing until CSV schema validation is implemented in Phase 3.
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
