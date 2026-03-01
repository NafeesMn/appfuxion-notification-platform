package com.appfuxion_notification_platform.backend.delivery.rules.runtime;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.appfuxion_notification_platform.backend.delivery.rules.NotificationRule;
import com.appfuxion_notification_platform.backend.delivery.rules.NotificationRuleContext;
import com.appfuxion_notification_platform.backend.delivery.rules.NotificationRuleDecision;
import com.appfuxion_notification_platform.backend.domain.shared.NotificationChannel;
import com.appfuxion_notification_platform.backend.tenant.domain.SuppressionChannelScope;
import com.appfuxion_notification_platform.backend.tenant.persistence.SuppressionEntry;
import com.appfuxion_notification_platform.backend.tenant.persistence.SuppressionEntryRepository;

public class SuppressionRule implements NotificationRule {

    private static final String DEFAULT_REASON = "SUPPRESSED";

    private final SuppressionEntryRepository suppressionEntryRepository;

    public SuppressionRule(SuppressionEntryRepository suppressionEntryRepository) {
        this.suppressionEntryRepository = Objects.requireNonNull(suppressionEntryRepository);
    }

    @Override
    public NotificationRuleDecision evaluate(NotificationRuleContext context, Instant now) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(now, "now");

        String suppressionKey = context.recipient().getNormalizedRecipientKey();
        if (suppressionKey == null || suppressionKey.isBlank()) {
            return NotificationRuleDecision.allow();
        }

        SuppressionChannelScope scopedChannel = toScopedChannel(context.job().getChannel());
        List<SuppressionEntry> matches = suppressionEntryRepository.findByActiveTrueAndSuppressionKeyAndChannelScopeIn(
                suppressionKey,
                List.of(SuppressionChannelScope.ANY, scopedChannel));

        if (matches.isEmpty()) {
            return NotificationRuleDecision.allow();
        }

        String reasonCode = matches.stream()
                .map(SuppressionEntry::getReasonCode)
                .filter(reason -> reason != null && !reason.isBlank())
                .findFirst()
                .orElse(DEFAULT_REASON);

        return NotificationRuleDecision.skip(reasonCode);
    }

    private SuppressionChannelScope toScopedChannel(NotificationChannel channel) {
        return switch (channel) {
            case EMAIL -> SuppressionChannelScope.EMAIL;
            case SMS -> SuppressionChannelScope.SMS;
            case PUSH -> SuppressionChannelScope.PUSH;
        };
    }
}
