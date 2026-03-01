package com.appfuxion_notification_platform.backend.campaign.application.csv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.appfuxion_notification_platform.backend.domain.shared.NotificationChannel;

class DefaultCampaignCsvParserTest {

    private final DefaultCampaignCsvParser parser = new DefaultCampaignCsvParser();

    @Test
    void parse_shouldHandleUtf8BomAndQuotedValues() throws Exception {
        String csv = """
                \uFEFFemail,timezone,personalization_payload
                alice@example.com,Asia/Kuala_Lumpur,"{""name"":""Alice, A""}"

                bob@example.com,UTC,
                """;

        List<CampaignCsvRow> rows = new ArrayList<>();
        CsvParseSummary summary = parser.parse(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
                NotificationChannel.EMAIL,
                rows::add);

        assertEquals(2L, summary.totalRows());
        assertEquals(2, rows.size());
        assertEquals(1, rows.get(0).rowNumber());
        assertEquals("alice@example.com", rows.get(0).email());
        assertEquals("Asia/Kuala_Lumpur", rows.get(0).timezone());
        assertEquals("{\"name\":\"Alice, A\"}", rows.get(0).personalizationPayloadJson());
        assertEquals(2, rows.get(1).rowNumber());
        assertEquals("bob@example.com", rows.get(1).email());
        assertNull(rows.get(1).personalizationPayloadJson());
    }

    @Test
    void parse_shouldRequireEmailHeaderForEmailChannel() {
        String csv = """
                phone_number,timezone
                +60123456789,UTC
                """;

        IOException ex = assertThrows(
                IOException.class,
                () -> parser.parse(
                        new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
                        NotificationChannel.EMAIL,
                        row -> {
                        }));

        assertEquals("Missing required CSV header: email", ex.getMessage());
    }

    @Test
    void parse_shouldAcceptHeaderAliasesForPhoneColumn() throws Exception {
        String csv = """
                phone,timezone
                +60123456789,UTC
                """;

        List<CampaignCsvRow> rows = new ArrayList<>();
        CsvParseSummary summary = parser.parse(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
                NotificationChannel.SMS,
                rows::add);

        assertEquals(1L, summary.totalRows());
        assertEquals(1, rows.size());
        assertEquals("+60123456789", rows.get(0).phoneNumber());
        assertEquals("UTC", rows.get(0).timezone());
    }

    @Test
    void parse_shouldFailWhenRowContainsUnmatchedQuote() {
        String csv = """
                email
                "alice@example.com
                """;

        IOException ex = assertThrows(
                IOException.class,
                () -> parser.parse(
                        new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
                        NotificationChannel.EMAIL,
                        row -> {
                        }));

        assertEquals("Malformed CSV line: unmatched quote", ex.getMessage());
    }
}
