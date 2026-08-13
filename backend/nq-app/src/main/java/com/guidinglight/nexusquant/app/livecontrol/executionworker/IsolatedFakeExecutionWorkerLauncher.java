package com.guidinglight.nexusquant.app.livecontrol.executionworker;

import com.guidinglight.nexusquant.livecontrol.deployment.KillSwitchPropagationEnvelope;
import com.guidinglight.nexusquant.livecontrol.deployment.KillSwitchPropagationPolicy;
import com.guidinglight.nexusquant.livecontrol.deployment.WorkerOperationSafetyGate;
import com.guidinglight.nexusquant.livecontrol.execution.application.ExecutionIntentService;
import com.guidinglight.nexusquant.livecontrol.execution.application.port.ExecutionAttemptLifecycle;
import com.guidinglight.nexusquant.livecontrol.execution.application.port.FakeExchangeResult;
import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionIntent;
import com.guidinglight.nexusquant.livecontrol.execution.infra.fake.LoopbackFakeExchangeHttpClient;
import com.guidinglight.nexusquant.livecontrol.execution.infra.jdbc.JdbcExecutionIntentRepository;
import com.guidinglight.nexusquant.risk.infra.jdbc.JdbcKillSwitchStateRepository;
import com.guidinglight.nexusquant.risk.service.KillSwitchService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.net.URI;
import java.nio.file.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Properties;
import java.util.UUID;

/**
 * One-shot isolated fake worker。手工装配 execution/kill/JDBC，不启动 Spring、adapter、scheduler 或 HTTP。
 */
public final class IsolatedFakeExecutionWorkerLauncher {

    private IsolatedFakeExecutionWorkerLauncher() {
    }

    public static void main(String[] arguments) throws Exception {
        Arguments args = Arguments.parse(arguments);
        Instant startedAt = Instant.now();
        DisposableWorkerReleaseVerifier.VerifiedRelease release =
                new DisposableWorkerReleaseVerifier().verify(
                        releaseManifest(args.releaseManifest()), actualWorkerArtifact(),
                        args.releaseId(), args.workerIdentity(),
                        args.expectedManifestDigest(), startedAt);
        DataSource dataSource = dataSource(args);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Clock clock = Clock.systemUTC();
        KillSwitchService killService = new KillSwitchService(new JdbcKillSwitchStateRepository(jdbc), clock);
        WorkerOperationSafetyGate killGate = new WorkerOperationSafetyGate(
                killService, new KillSwitchPropagationPolicy(Duration.ofSeconds(30)), clock);
        KillSwitchPropagationEnvelope envelope = KillSwitchPropagationEnvelope.fromSnapshot(killService.snapshot());

        JdbcExecutionIntentRepository repository = new JdbcExecutionIntentRepository(
                jdbc, new DataSourceTransactionManager(dataSource));
        ExecutionIntentService service = new ExecutionIntentService(repository,
                new LoopbackFakeExchangeHttpClient(args.fakeVenue(), Duration.ofSeconds(2), Duration.ofSeconds(8)));
        HealthWriter health = new HealthWriter(args.health(), args, release, startedAt);
        health.write("STARTING", 0, null, "NONE");
        try {
            ExecutionIntent result;
            if (args.operation() == Operation.RECONCILE) {
                result = service.reconcileUnknown(args.intentId(), observationTime(clock));
            } else {
                requireAllowed(killGate.authorize(
                        WorkerOperationSafetyGate.Phase.ENVELOPE_ACCEPTANCE, envelope));
                WorkerLifecycle lifecycle = new WorkerLifecycle(killGate, envelope, args.crashPoint(), health);
                result = service.claimAndExecute(args.intentId(), args.workerIdentity(), args.claimToken(),
                        args.lease(), observationTime(clock), lifecycle);
            }
            health.write("STOPPED_CLEAN", args.operation() == Operation.EXECUTE ? 1 : 0,
                    result, "NONE");
            System.out.printf("ISOLATED_FAKE_WORKER_COMPLETE intent=%s state=%s release=%s tradingAuthorization=false%n",
                    result.intentId(), result.state(), release.releaseId());
        } catch (RuntimeException exception) {
            health.write("FAILED", 0, repository.find(args.intentId()).orElse(null),
                    sanitize(exception.getMessage()));
            throw exception;
        }
    }

    private static DataSource dataSource(Arguments args) {
        DriverManagerDataSource value = new DriverManagerDataSource();
        value.setDriverClassName("org.postgresql.Driver");
        value.setUrl(args.jdbcUrl());
        value.setUsername("fake_worker");
        value.setPassword("fake-worker-disposable-only");
        Properties connectionProperties = new Properties();
        connectionProperties.setProperty("connectTimeout", "5");
        connectionProperties.setProperty("socketTimeout", "15");
        connectionProperties.setProperty("ApplicationName", "nq-isolated-fake-worker");
        value.setConnectionProperties(connectionProperties);
        return value;
    }

    private static Path actualWorkerArtifact() throws Exception {
        Path repo = repositoryRoot();
        var source = IsolatedFakeExecutionWorkerLauncher.class.getProtectionDomain().getCodeSource();
        if (source == null) {
            throw new IllegalStateException("DISPOSABLE_RELEASE_DENIED:ACTUAL_ARTIFACT_NOT_IDENTIFIED");
        }
        Path artifact = Path.of(source.getLocation().toURI()).toAbsolutePath().normalize();
        Path target = repo.resolve("backend/nq-app/target").toRealPath();
        if (!artifact.getFileName().toString().matches("nq-app-[A-Za-z0-9._-]+\\.jar")) {
            throw new IllegalStateException("DISPOSABLE_RELEASE_DENIED:ACTUAL_ARTIFACT_MUST_BE_NQ_APP_JAR");
        }
        if (!artifact.getParent().toRealPath().equals(target)) {
            throw new IllegalStateException("DISPOSABLE_RELEASE_DENIED:ACTUAL_ARTIFACT_OUTSIDE_TRUSTED_TARGET");
        }
        return artifact;
    }

    private static Path releaseManifest(Path supplied) throws Exception {
        Path artifacts = repositoryRoot().resolve("artifacts").toAbsolutePath().normalize();
        Path value = supplied.toAbsolutePath().normalize();
        Path parent = value.getParent();
        if (parent == null || !value.getFileName().toString().matches("release-[ab]\\.properties")
                || !parent.getFileName().toString().matches("gatey5-worker-tmp-[0-9a-f]{32}")
                || !parent.getParent().equals(artifacts)
                || Files.isSymbolicLink(parent) || Files.isSymbolicLink(value)) {
            throw new IllegalStateException("DISPOSABLE_RELEASE_DENIED:MANIFEST_OUTSIDE_DISPOSABLE_ROOT");
        }
        return value;
    }

    private static Path repositoryRoot() throws Exception {
        return Path.of(System.getProperty("nq.fake-worker.repo-root", "."))
                .toAbsolutePath().normalize().toRealPath();
    }

    private static void requireAllowed(WorkerOperationSafetyGate.Decision value) {
        if (value.status() != WorkerOperationSafetyGate.Status.ALLOWED) {
            throw new IllegalStateException("FAKE_WORKER_KILL_DENIED:" + value.phase() + ":" + value.reason());
        }
    }

    private static Instant observationTime(Clock clock) {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private static final class WorkerLifecycle implements ExecutionAttemptLifecycle {
        private final WorkerOperationSafetyGate gate;
        private final KillSwitchPropagationEnvelope envelope;
        private final CrashPoint crashPoint;
        private final HealthWriter health;

        private WorkerLifecycle(
                WorkerOperationSafetyGate gate,
                KillSwitchPropagationEnvelope envelope,
                CrashPoint crashPoint,
                HealthWriter health
        ) {
            this.gate = gate;
            this.envelope = envelope;
            this.crashPoint = crashPoint;
            this.health = health;
        }

        @Override
        public void beforeClaim() {
            requireAllowed(gate.authorize(WorkerOperationSafetyGate.Phase.CLAIM, envelope));
        }

        @Override
        public void afterClaim(ExecutionIntent intent) {
            health.writeUnchecked("CLAIMED", 1, intent, "NONE");
            crash(CrashPoint.AFTER_CLAIM);
            pause(CrashPoint.WAIT_AFTER_CLAIM);
        }

        @Override
        public void afterSendStarted(ExecutionIntent intent) {
            health.writeUnchecked("SEND_STARTED", 1, intent, "NONE");
            crash(CrashPoint.AFTER_SEND_STARTED);
            pause(CrashPoint.WAIT_AFTER_SEND_STARTED);
        }

        @Override
        public void beforeFakeMutation(ExecutionIntent intent) {
            requireAllowed(gate.authorize(WorkerOperationSafetyGate.Phase.SEND, envelope));
        }

        @Override
        public void afterFakeMutation(ExecutionIntent intent, FakeExchangeResult result) {
            health.writeUnchecked("MUTATION_OBSERVED", 1, intent, result.outcome().name());
            if (crashPoint == CrashPoint.RECEIPT_FAILURE) {
                throw new IllegalStateException("CONTROLLED_RECEIPT_FAILURE");
            }
            crash(CrashPoint.AFTER_MUTATION);
        }

        private void crash(CrashPoint point) {
            if (crashPoint == point) {
                Runtime.getRuntime().halt(70 + point.ordinal());
            }
        }

        private void pause(CrashPoint point) {
            if (crashPoint == point) {
                try {
                    Thread.sleep(Duration.ofSeconds(4));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("controlled worker pause interrupted", exception);
                }
            }
        }
    }

    private static final class HealthWriter {
        private final Path path;
        private final Arguments args;
        private final DisposableWorkerReleaseVerifier.VerifiedRelease release;
        private final Instant startedAt;

        private HealthWriter(Path path, Arguments args,
                             DisposableWorkerReleaseVerifier.VerifiedRelease release, Instant startedAt) {
            this.path = path;
            this.args = args;
            this.release = release;
            this.startedAt = startedAt;
        }

        void write(String health, long claimCount, ExecutionIntent intent, String detail) throws Exception {
            Path target = requireHealthPath(path);
            Properties values = new Properties();
            values.setProperty("workerInstanceId", args.workerIdentity());
            values.setProperty("releaseIdentity", release.releaseId());
            values.setProperty("releaseDigest", release.manifestDigest());
            values.setProperty("startedAt", startedAt.toString());
            values.setProperty("lastObservedAt", Instant.now().toString());
            values.setProperty("health", health);
            values.setProperty("claimCount", Long.toString(claimCount));
            values.setProperty("currentIntent", intent == null ? "-" : intent.intentId().toString());
            values.setProperty("intentState", intent == null ? "-" : intent.state().name());
            values.setProperty("detail", sanitize(detail));
            values.setProperty("tradingAuthorization", "false");
            Path temporary = target.resolveSibling(target.getFileName() + ".new");
            if (Files.exists(temporary, LinkOption.NOFOLLOW_LINKS)) Files.delete(temporary);
            try (var output = Files.newOutputStream(temporary, StandardOpenOption.CREATE_NEW)) {
                values.store(output, "Disposable fake worker health");
            }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }

        void writeUnchecked(String health, long claimCount, ExecutionIntent intent, String detail) {
            try {
                write(health, claimCount, intent, detail);
            } catch (Exception exception) {
                throw new IllegalStateException("worker health update failed", exception);
            }
        }
    }

    private static Path requireHealthPath(Path supplied) throws Exception {
        Path repo = repositoryRoot();
        Path artifacts = repo.resolve("artifacts").normalize();
        Path value = supplied.toAbsolutePath().normalize();
        if (!value.startsWith(artifacts)
                || !value.getFileName().toString().matches("fake-worker-health-tmp-[a-z0-9-]+\\.properties")) {
            throw new IllegalArgumentException("health must be an exact disposable project artifact path");
        }
        Files.createDirectories(artifacts);
        if (Files.isSymbolicLink(artifacts) || Files.isSymbolicLink(value)) {
            throw new IllegalArgumentException("health path must not be a link");
        }
        return value;
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) return "-";
        return value.replaceAll("[^A-Za-z0-9_.:-]", "_").substring(0, Math.min(128, value.length()));
    }

    private record Arguments(
            String jdbcUrl,
            URI fakeVenue,
            UUID intentId,
            Operation operation,
            String workerIdentity,
            UUID claimToken,
            Duration lease,
            Path releaseManifest,
            String releaseId,
            String expectedManifestDigest,
            Path health,
            CrashPoint crashPoint
    ) {
        static Arguments parse(String[] values) {
            if (!Boolean.getBoolean("nq.fake-worker.confirm-disposable") || values.length != 12) {
                throw new IllegalArgumentException("exact disposable fake-worker arguments are required");
            }
            String jdbc = values[0];
            if (!jdbc.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{4,5}/nq_fake_worker_[a-z0-9_]+")) {
                throw new IllegalArgumentException("JDBC must target an explicit loopback disposable database");
            }
            URI fake = URI.create(values[1]);
            String worker = bounded(values[4], "workerIdentity");
            Duration lease = Duration.ofSeconds(Long.parseLong(values[6]));
            if (lease.isZero() || lease.isNegative() || lease.compareTo(Duration.ofMinutes(5)) > 0) {
                throw new IllegalArgumentException("lease must be bounded");
            }
            return new Arguments(jdbc, fake, UUID.fromString(values[2]), Operation.valueOf(values[3]), worker,
                    UUID.fromString(values[5]), lease, Path.of(values[7]), bounded(values[8], "releaseId"),
                    digest(values[9], "expectedManifestDigest"), Path.of(values[10]), CrashPoint.valueOf(values[11]));
        }

        private static String bounded(String value, String name) {
            if (value == null || !value.matches("[A-Za-z0-9._:-]{1,128}")) {
                throw new IllegalArgumentException(name + " is invalid");
            }
            return value;
        }

        private static String digest(String value, String name) {
            if (value == null || !value.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException(name + " is invalid");
            }
            return value;
        }
    }

    private enum Operation {EXECUTE, RECONCILE}

    private enum CrashPoint {
        NONE,
        AFTER_CLAIM,
        AFTER_SEND_STARTED,
        AFTER_MUTATION,
        RECEIPT_FAILURE,
        WAIT_AFTER_CLAIM,
        WAIT_AFTER_SEND_STARTED
    }
}
