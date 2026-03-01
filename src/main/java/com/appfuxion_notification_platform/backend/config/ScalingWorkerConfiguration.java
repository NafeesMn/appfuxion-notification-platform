package com.appfuxion_notification_platform.backend.config;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.appfuxion_notification_platform.backend.campaign.persistence.CampaignRecipientRepository;
import com.appfuxion_notification_platform.backend.campaign.persistence.CampaignRepository;
import com.appfuxion_notification_platform.backend.delivery.persistence.NotificationAttemptRepository;
import com.appfuxion_notification_platform.backend.delivery.persistence.NotificationJobRepository;
import com.appfuxion_notification_platform.backend.delivery.rules.NotificationRule;
import com.appfuxion_notification_platform.backend.delivery.rules.NotificationRuleContextLoader;
import com.appfuxion_notification_platform.backend.delivery.rules.NotificationRuleEngine;
import com.appfuxion_notification_platform.backend.delivery.rules.runtime.OrderedNotificationRuleEngine;
import com.appfuxion_notification_platform.backend.delivery.rules.runtime.QuietHoursRule;
import com.appfuxion_notification_platform.backend.delivery.rules.runtime.RepositoryNotificationRuleContextLoader;
import com.appfuxion_notification_platform.backend.delivery.rules.runtime.SuppressionRule;
import com.appfuxion_notification_platform.backend.delivery.scaling.GlobalChannelRateLimiter;
import com.appfuxion_notification_platform.backend.delivery.scaling.IdempotencyExecutionGuard;
import com.appfuxion_notification_platform.backend.delivery.scaling.NotificationJobExecutor;
import com.appfuxion_notification_platform.backend.delivery.scaling.NotificationProviderGateway;
import com.appfuxion_notification_platform.backend.delivery.scaling.PartitionAwareJobFetcher;
import com.appfuxion_notification_platform.backend.delivery.scaling.PartitionLeaseService;
import com.appfuxion_notification_platform.backend.delivery.scaling.PartitionPlanner;
import com.appfuxion_notification_platform.backend.delivery.scaling.PartitionedWorkerCoordinator;
import com.appfuxion_notification_platform.backend.delivery.scaling.RetryBackoffPolicy;
import com.appfuxion_notification_platform.backend.delivery.scaling.ScalingMetricsRecorder;
import com.appfuxion_notification_platform.backend.delivery.scaling.domain.WorkerIdentity;
import com.appfuxion_notification_platform.backend.delivery.scaling.runtime.DatabasePartitionLeaseService;
import com.appfuxion_notification_platform.backend.delivery.scaling.runtime.DefaultNotificationJobExecutor;
import com.appfuxion_notification_platform.backend.delivery.scaling.runtime.DefaultPartitionedWorkerCoordinator;
import com.appfuxion_notification_platform.backend.delivery.scaling.runtime.DeterministicProviderSimulatorGateway;
import com.appfuxion_notification_platform.backend.delivery.scaling.runtime.ExponentialRetryBackoffPolicy;
import com.appfuxion_notification_platform.backend.delivery.scaling.runtime.HashPartitionPlanner;
import com.appfuxion_notification_platform.backend.delivery.scaling.runtime.RepositoryIdempotencyExecutionGuard;
import com.appfuxion_notification_platform.backend.delivery.scaling.runtime.RepositoryPartitionAwareJobFetcher;
import com.appfuxion_notification_platform.backend.delivery.scaling.runtime.TokenBucketChannelRateLimiter;
import com.appfuxion_notification_platform.backend.operations.persistence.WorkerPartitionLeaseRepository;
import com.appfuxion_notification_platform.backend.tenant.persistence.SuppressionEntryRepository;
import com.appfuxion_notification_platform.backend.tenant.persistence.TenantRepository;

@Configuration
@EnableScheduling
public class ScalingWorkerConfiguration {

    @Bean
    public PartitionPlanner partitionPlanner() {
        return new HashPartitionPlanner();
    }

    @Bean
    public PartitionLeaseService partitionLeaseService(
            WorkerPartitionLeaseRepository workerPartitionLeaseRepository,
            Clock clock) {
        return new DatabasePartitionLeaseService(workerPartitionLeaseRepository, clock);
    }

    @Bean
    public PartitionAwareJobFetcher partitionAwareJobFetcher(NotificationJobRepository notificationJobRepository) {
        return new RepositoryPartitionAwareJobFetcher(notificationJobRepository);
    }

    @Bean
    public GlobalChannelRateLimiter globalChannelRateLimiter(
            @Value("${app.scaling.rate-limit-per-minute:100}") int requestsPerMinutePerChannel) {
        return new TokenBucketChannelRateLimiter(requestsPerMinutePerChannel);
    }

    @Bean
    public RetryBackoffPolicy retryBackoffPolicy(
            @Value("${app.scaling.retry.base-delay-seconds:10}") long baseDelaySeconds,
            @Value("${app.scaling.retry.multiplier:2}") int multiplier,
            @Value("${app.scaling.retry.max-delay-seconds:300}") long maxDelaySeconds) {
        return new ExponentialRetryBackoffPolicy(
                Duration.ofSeconds(baseDelaySeconds),
                multiplier,
                Duration.ofSeconds(maxDelaySeconds));
    }

    @Bean
    public IdempotencyExecutionGuard idempotencyExecutionGuard(NotificationAttemptRepository notificationAttemptRepository) {
        return new RepositoryIdempotencyExecutionGuard(notificationAttemptRepository);
    }

    @Bean
    public NotificationProviderGateway notificationProviderGateway() {
        return new DeterministicProviderSimulatorGateway();
    }

    @Bean
    public NotificationRuleContextLoader notificationRuleContextLoader(
            CampaignRepository campaignRepository,
            CampaignRecipientRepository campaignRecipientRepository,
            TenantRepository tenantRepository) {
        return new RepositoryNotificationRuleContextLoader(
                campaignRepository,
                campaignRecipientRepository,
                tenantRepository);
    }

    @Bean
    public NotificationRuleEngine notificationRuleEngine(SuppressionEntryRepository suppressionEntryRepository) {
        List<NotificationRule> rules = List.of(
                new SuppressionRule(suppressionEntryRepository),
                new QuietHoursRule());
        return new OrderedNotificationRuleEngine(rules);
    }

    @Bean
    public NotificationJobExecutor notificationJobExecutor(
            GlobalChannelRateLimiter globalChannelRateLimiter,
            ScalingMetricsRecorder scalingMetricsRecorder,
            NotificationJobRepository notificationJobRepository,
            NotificationAttemptRepository notificationAttemptRepository,
            NotificationProviderGateway notificationProviderGateway,
            RetryBackoffPolicy retryBackoffPolicy,
            IdempotencyExecutionGuard idempotencyExecutionGuard,
            NotificationRuleContextLoader notificationRuleContextLoader,
            NotificationRuleEngine notificationRuleEngine) {
        return new DefaultNotificationJobExecutor(
                globalChannelRateLimiter,
                scalingMetricsRecorder,
                notificationJobRepository,
                notificationAttemptRepository,
                notificationProviderGateway,
                retryBackoffPolicy,
                idempotencyExecutionGuard,
                notificationRuleContextLoader,
                notificationRuleEngine);
    }

    @Bean
    public PartitionedWorkerCoordinator partitionedWorkerCoordinator(
            PartitionLeaseService partitionLeaseService,
            PartitionPlanner partitionPlanner,
            PartitionAwareJobFetcher partitionAwareJobFetcher,
            NotificationJobExecutor notificationJobExecutor,
            ScalingMetricsRecorder scalingMetricsRecorder,
            @Value("${app.scaling.total-partitions:128}") int totalPartitions,
            @Value("${app.scaling.active-workers:1}") int activeWorkerCount,
            @Value("${app.scaling.poll-batch-size:200}") int pollBatchSize,
            @Value("${app.scaling.lease-ttl-seconds:30}") long leaseTtlSeconds) {
        return new DefaultPartitionedWorkerCoordinator(
                partitionLeaseService,
                partitionPlanner,
                partitionAwareJobFetcher,
                notificationJobExecutor,
                scalingMetricsRecorder,
                totalPartitions,
                activeWorkerCount,
                pollBatchSize,
                Duration.ofSeconds(leaseTtlSeconds));
    }

    @Bean
    public WorkerIdentity workerIdentity(@Value("${app.scaling.worker-id:worker-local}") String workerId) {
        return new WorkerIdentity(workerId);
    }
}
