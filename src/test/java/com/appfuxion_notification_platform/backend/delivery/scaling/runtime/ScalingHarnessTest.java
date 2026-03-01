package com.appfuxion_notification_platform.backend.delivery.scaling.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.appfuxion_notification_platform.backend.delivery.domain.NotificationJobStatus;
import com.appfuxion_notification_platform.backend.delivery.persistence.NotificationJob;
import com.appfuxion_notification_platform.backend.delivery.scaling.NotificationJobExecutor;
import com.appfuxion_notification_platform.backend.delivery.scaling.PartitionAwareJobFetcher;
import com.appfuxion_notification_platform.backend.delivery.scaling.PartitionLeaseService;
import com.appfuxion_notification_platform.backend.delivery.scaling.PartitionedWorkerCoordinator;
import com.appfuxion_notification_platform.backend.delivery.scaling.domain.LeaseAcquireResult;
import com.appfuxion_notification_platform.backend.delivery.scaling.domain.PartitionLease;
import com.appfuxion_notification_platform.backend.delivery.scaling.domain.WorkerIdentity;
import com.appfuxion_notification_platform.backend.domain.shared.NotificationChannel;

@EnabledIfSystemProperty(named = "run.phase10.benchmark", matches = "true")
class ScalingHarnessTest {

    @Test
    void benchmark_shouldShowHigherThroughputWithFourWorkersThanOneWorker() throws Exception {
        int totalJobs = Integer.getInteger("phase10.jobs", 4000);
        int totalPartitions = Integer.getInteger("phase10.partitions", 128);
        int pollBatchSize = Integer.getInteger("phase10.batch", 100);
        long perJobWorkMicros = Long.getLong("phase10.workMicros", 1000L);

        BenchmarkResult oneWorker = runScenario(totalJobs, totalPartitions, 1, pollBatchSize, perJobWorkMicros);
        BenchmarkResult fourWorkers = runScenario(totalJobs, totalPartitions, 4, pollBatchSize, perJobWorkMicros);

        System.out.printf(
                "Phase10 benchmark -> jobs=%d partitions=%d batch=%d workMicros=%d | oneWorker=%.2f jobs/s (%d ms), fourWorkers=%.2f jobs/s (%d ms)%n",
                totalJobs,
                totalPartitions,
                pollBatchSize,
                perJobWorkMicros,
                oneWorker.jobsPerSecond(),
                oneWorker.durationMillis(),
                fourWorkers.jobsPerSecond(),
                fourWorkers.durationMillis());

        assertEquals(totalJobs, oneWorker.processedJobs());
        assertEquals(totalJobs, fourWorkers.processedJobs());
        assertTrue(fourWorkers.jobsPerSecond() > oneWorker.jobsPerSecond());
    }

    private BenchmarkResult runScenario(
            int totalJobs,
            int totalPartitions,
            int activeWorkers,
            int pollBatchSize,
            long perJobWorkMicros) throws Exception {
        InMemoryPartitionLeaseService leaseService = new InMemoryPartitionLeaseService();
        HashPartitionPlanner partitionPlanner = new HashPartitionPlanner();
        InMemoryPartitionAwareJobFetcher jobFetcher = new InMemoryPartitionAwareJobFetcher(seedJobs(totalJobs, totalPartitions));
        SimulatedNotificationJobExecutor jobExecutor = new SimulatedNotificationJobExecutor(perJobWorkMicros);
        NoopScalingMetricsRecorder metricsRecorder = new NoopScalingMetricsRecorder();

        List<PartitionedWorkerCoordinator> coordinators = new ArrayList<>();
        List<WorkerIdentity> workers = new ArrayList<>();
        for (int i = 0; i < activeWorkers; i++) {
            coordinators.add(new DefaultPartitionedWorkerCoordinator(
                    leaseService,
                    partitionPlanner,
                    jobFetcher,
                    jobExecutor,
                    metricsRecorder,
                    totalPartitions,
                    activeWorkers,
                    pollBatchSize,
                    Duration.ofSeconds(30)));
            workers.add(new WorkerIdentity("bench-worker-" + i));
        }

        ExecutorService pool = Executors.newFixedThreadPool(activeWorkers);
        CountDownLatch start = new CountDownLatch(1);
        Instant begin = Instant.now();
        long startedAtNanos = System.nanoTime();

        for (int i = 0; i < activeWorkers; i++) {
            PartitionedWorkerCoordinator coordinator = coordinators.get(i);
            WorkerIdentity worker = workers.get(i);
            pool.submit(() -> {
                try {
                    start.await();
                    while (jobExecutor.processedCount() < totalJobs) {
                        Instant now = Instant.now();
                        leaseService.reclaimExpiredLeases(now);
                        coordinator.rebalanceAndRunOnce(worker, now);
                        if (jobFetcher.remainingJobs() == 0) {
                            break;
                        }
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        start.countDown();
        pool.shutdown();
        boolean completed = pool.awaitTermination(120, TimeUnit.SECONDS);
        long durationNanos = System.nanoTime() - startedAtNanos;

        assertTrue(completed, "benchmark workers did not terminate within timeout");
        assertEquals(totalJobs, jobExecutor.processedCount(), "not all jobs were processed");
        assertEquals(0, jobFetcher.remainingJobs(), "all jobs should have been drained");

        return new BenchmarkResult(totalJobs, Duration.ofNanos(durationNanos), begin, Instant.now());
    }

    private List<NotificationJob> seedJobs(int totalJobs, int totalPartitions) {
        List<NotificationJob> jobs = new ArrayList<>(totalJobs);
        Instant now = Instant.now();
        for (int i = 0; i < totalJobs; i++) {
            NotificationJob job = new NotificationJob();
            job.setChannel(NotificationChannel.SMS);
            job.setStatus(NotificationJobStatus.PENDING);
            job.setPartitionKey(Math.floorMod(i, totalPartitions));
            job.setAttemptCount(0);
            job.setMaxRetries(0);
            job.setNextAttemptAt(now);
            jobs.add(job);
        }
        return jobs;
    }

    private record BenchmarkResult(
            int processedJobs,
            Duration duration,
            Instant startedAt,
            Instant finishedAt) {
        double jobsPerSecond() {
            double millis = duration.toMillis();
            if (millis <= 0) {
                return processedJobs;
            }
            return processedJobs / (millis / 1000.0d);
        }

        long durationMillis() {
            return duration.toMillis();
        }
    }

    private static final class InMemoryPartitionAwareJobFetcher implements PartitionAwareJobFetcher {
        private final List<NotificationJob> jobs;

        private InMemoryPartitionAwareJobFetcher(List<NotificationJob> jobs) {
            this.jobs = jobs;
        }

        @Override
        public synchronized List<NotificationJob> fetchDueJobs(Set<Integer> partitions, Instant now, int batchSize) {
            if (partitions.isEmpty()) {
                return List.of();
            }
            List<NotificationJob> selected = new ArrayList<>();
            for (NotificationJob job : jobs) {
                if (selected.size() >= batchSize) {
                    break;
                }
                if (job.getStatus() != NotificationJobStatus.PENDING
                        && job.getStatus() != NotificationJobStatus.RETRY_SCHEDULED
                        && job.getStatus() != NotificationJobStatus.DELAYED) {
                    continue;
                }
                if (!partitions.contains(job.getPartitionKey())) {
                    continue;
                }
                if (job.getNextAttemptAt() != null && job.getNextAttemptAt().isAfter(now)) {
                    continue;
                }
                job.setStatus(NotificationJobStatus.PROCESSING);
                job.setLastAttemptAt(now);
                selected.add(job);
            }
            return selected;
        }

        private synchronized int remainingJobs() {
            int remaining = 0;
            for (NotificationJob job : jobs) {
                if (job.getStatus() == NotificationJobStatus.PENDING
                        || job.getStatus() == NotificationJobStatus.RETRY_SCHEDULED
                        || job.getStatus() == NotificationJobStatus.DELAYED
                        || job.getStatus() == NotificationJobStatus.PROCESSING) {
                    remaining++;
                }
            }
            return remaining;
        }
    }

    private static final class SimulatedNotificationJobExecutor implements NotificationJobExecutor {
        private final long perJobWorkMicros;
        private final Set<NotificationJob> processed = ConcurrentHashMap.newKeySet();
        private final AtomicInteger processedCount = new AtomicInteger(0);

        private SimulatedNotificationJobExecutor(long perJobWorkMicros) {
            this.perJobWorkMicros = Math.max(0L, perJobWorkMicros);
        }

        @Override
        public void execute(NotificationJob job, WorkerIdentity worker, Instant now) {
            if (processed.add(job)) {
                if (perJobWorkMicros > 0) {
                    LockSupport.parkNanos(perJobWorkMicros * 1_000L);
                }
                job.setStatus(NotificationJobStatus.SENT);
                job.setCompletedAt(now);
                processedCount.incrementAndGet();
            }
        }

        private int processedCount() {
            return processedCount.get();
        }
    }

    private static final class InMemoryPartitionLeaseService implements PartitionLeaseService {
        private final ConcurrentMap<Integer, PartitionLease> leases = new ConcurrentHashMap<>();

        @Override
        public synchronized LeaseAcquireResult tryAcquire(int partitionId, WorkerIdentity worker, Duration leaseTtl) {
            Instant now = Instant.now();
            PartitionLease current = leases.get(partitionId);
            if (current == null
                    || current.workerId().equals(worker.workerId())
                    || !current.leaseExpiresAt().isAfter(now)) {
                PartitionLease updated = new PartitionLease(
                        partitionId,
                        worker.workerId(),
                        now.plus(leaseTtl),
                        now,
                        current == null ? 0L : current.version() + 1);
                leases.put(partitionId, updated);
                return new LeaseAcquireResult(true, updated);
            }
            return new LeaseAcquireResult(false, current);
        }

        @Override
        public synchronized boolean heartbeat(int partitionId, WorkerIdentity worker, Duration leaseTtl) {
            PartitionLease current = leases.get(partitionId);
            if (current == null || !current.workerId().equals(worker.workerId())) {
                return false;
            }
            Instant now = Instant.now();
            leases.put(partitionId, new PartitionLease(
                    partitionId,
                    worker.workerId(),
                    now.plus(leaseTtl),
                    now,
                    current.version() + 1));
            return true;
        }

        @Override
        public synchronized void release(int partitionId, WorkerIdentity worker) {
            PartitionLease current = leases.get(partitionId);
            if (current != null && current.workerId().equals(worker.workerId())) {
                leases.remove(partitionId);
            }
        }

        @Override
        public synchronized Set<Integer> listOwnedPartitions(WorkerIdentity worker) {
            Instant now = Instant.now();
            return leases.values().stream()
                    .filter(lease -> lease.workerId().equals(worker.workerId()))
                    .filter(lease -> lease.leaseExpiresAt().isAfter(now))
                    .map(PartitionLease::partitionId)
                    .collect(java.util.stream.Collectors.toSet());
        }

        @Override
        public synchronized int reclaimExpiredLeases(Instant now) {
            int before = leases.size();
            leases.entrySet().removeIf(entry -> !entry.getValue().leaseExpiresAt().isAfter(now));
            return before - leases.size();
        }
    }
}
