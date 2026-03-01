package com.appfuxion_notification_platform.backend.campaign.application.csv;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.appfuxion_notification_platform.backend.domain.shared.NotificationChannel;

@Component
public class DefaultCampaignCsvParser implements CampaignCsvParser {

    @Override
    public CsvParseSummary parse(
            InputStream inputStream,
            NotificationChannel channel,
            CampaignCsvRowConsumer rowConsumer) throws IOException {
        Objects.requireNonNull(inputStream, "inputStream");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(rowConsumer, "rowConsumer");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) {
                return new CsvParseSummary(0);
            }

            List<String> headers = parseCsvLine(stripUtf8Bom(headerLine)).stream()
                    .map(this::normalizeHeader)
                    .toList();

            int emailIndex = indexOf(headers, "email");
            int phoneIndex = firstIndexOf(headers, "phone_number", "phone");
            int deviceTokenIndex = firstIndexOf(headers, "device_token", "device_token_id", "device");
            int timezoneIndex = firstIndexOf(headers, "timezone", "time_zone", "tz");
            int personalizationIndex = firstIndexOf(headers, "personalization_payload", "personalization", "payload");

            validateRequiredHeader(channel, emailIndex, phoneIndex, deviceTokenIndex);

            long rowCount = 0L;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                rowCount++;
                List<String> values = parseCsvLine(line);
                rowConsumer.accept(new CampaignCsvRow(
                        Math.toIntExact(rowCount),
                        valueAt(values, emailIndex),
                        valueAt(values, phoneIndex),
                        valueAt(values, deviceTokenIndex),
                        valueAt(values, timezoneIndex),
                        valueAt(values, personalizationIndex)));
            }

            return new CsvParseSummary(rowCount);
        }
    }

    private void validateRequiredHeader(
            NotificationChannel channel,
            int emailIndex,
            int phoneIndex,
            int deviceTokenIndex) throws IOException {
        switch (channel) {
            case EMAIL -> {
                if (emailIndex < 0) {
                    throw new IOException("Missing required CSV header: email");
                }
            }
            case SMS -> {
                if (phoneIndex < 0) {
                    throw new IOException("Missing required CSV header: phone_number");
                }
            }
            case PUSH -> {
                if (deviceTokenIndex < 0) {
                    throw new IOException("Missing required CSV header: device_token");
                }
            }
        }
    }

    private List<String> parseCsvLine(String line) throws IOException {
        List<String> columns = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    token.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
                continue;
            }

            if (ch == ',' && !inQuotes) {
                columns.add(token.toString().trim());
                token.setLength(0);
                continue;
            }
            token.append(ch);
        }

        if (inQuotes) {
            throw new IOException("Malformed CSV line: unmatched quote");
        }

        columns.add(token.toString().trim());
        return columns;
    }

    private String valueAt(List<String> values, int index) {
        if (index < 0 || index >= values.size()) {
            return null;
        }
        String value = values.get(index);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private int indexOf(List<String> headers, String header) {
        return headers.indexOf(header);
    }

    private int firstIndexOf(List<String> headers, String... candidates) {
        for (String candidate : candidates) {
            int idx = headers.indexOf(candidate);
            if (idx >= 0) {
                return idx;
            }
        }
        return -1;
    }

    private String normalizeHeader(String header) {
        return header == null ? "" : header.strip().toLowerCase(Locale.ROOT);
    }

    private String stripUtf8Bom(String value) {
        if (value != null && !value.isEmpty() && value.charAt(0) == '\uFEFF') {
            return value.substring(1);
        }
        return value;
    }
}
