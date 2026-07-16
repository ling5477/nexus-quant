package com.guidinglight.nexusquant.account.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.account.application.command.CredentialPermissionProbeCommand;
import com.guidinglight.nexusquant.account.domain.CredentialPermissionProbeSummary;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialMaterial;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialSummary;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary;
import com.guidinglight.nexusquant.account.domain.ExchangeCredentialPermissionProbeRequest;
import com.guidinglight.nexusquant.account.domain.ExchangeCredentialPermissionProbeResult;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountCredentialRepository;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountRepository;
import com.guidinglight.nexusquant.account.domain.port.ExchangeCredentialPermissionProbePort;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CredentialPermissionProbeServiceTest {

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-06-13T00:00:00Z"), ZoneOffset.UTC);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldWriteStartedAndSucceededAuditWithoutClearingFailedAuthCount() throws Exception {
        Fixture fixture = new Fixture("SIM");
        ExchangeAccountCredentialMaterial credential = fixture.seedCredential(credential("ACTIVE", true)
                .withFailedAuthCount(3)
                .withPermissionScope(null));
        RecordingProbePort port = RecordingProbePort.success("READ_ONLY");
        CredentialPermissionProbeService service = fixture.service(port);

        CredentialPermissionProbeSummary summary = service.probe(1L, fixture.account.exchangeAccountId(), credential.credentialId(),
                "admin", paperCommand(), "trace-success");

        assertEquals("SUCCEEDED", summary.permissionProbeStatus());
        assertEquals("READ_ONLY", summary.permissionScope());
        assertEquals(3, summary.failedAuthCount());
        assertEquals(List.of("PERMISSION_PROBE_STARTED", "PERMISSION_PROBE_SUCCEEDED"), fixture.repository.auditLogs.stream().map(AuditLog::eventType).toList());
        var successAuditMetadata = objectMapper.readTree(fixture.repository.auditLogs.get(1).metadataJson());
        assertEquals("READ_ONLY", successAuditMetadata.get("detectedScope").asText());
        assertEquals("PASSED", successAuditMetadata.get("ipAllowlistStatus").asText());
        assertEquals(1, port.calls);
        assertEquals(2, fixture.transactions.executions);
        assertFalse(fixture.transactions.active);
        assertFalse(fixture.repository.auditLogs.stream().anyMatch(log -> containsSensitive(log.metadataJson())));
    }

    @Test
    void executesPortBetweenTwoShortTransactions() {
        Fixture fixture = new Fixture("SIM");
        ExchangeAccountCredentialMaterial credential = fixture.seedCredential(credential("ACTIVE", true));
        boolean[] calledInsideTransaction = {true};
        ExchangeCredentialPermissionProbePort port = request -> {
            calledInsideTransaction[0] = fixture.transactions.active;
            return ExchangeCredentialPermissionProbeResult.succeeded(
                    "OKX", "OKX_API_V5", "READ_ONLY", "PASSED", "req-tx", request.traceId(),
                    fixedClock.instant(), fixedClock.instant()
            );
        };

        CredentialPermissionProbeSummary summary = fixture.service(port).probe(
                1L, fixture.account.exchangeAccountId(), credential.credentialId(),
                "admin", paperCommand(), "trace-tx"
        );

        assertEquals("SUCCEEDED", summary.permissionProbeStatus());
        assertFalse(calledInsideTransaction[0]);
        assertEquals(2, fixture.transactions.executions);
    }

    @Test
    void writebackConflictNeverReturnsSuccess() {
        Fixture fixture = new Fixture("SIM");
        ExchangeAccountCredentialMaterial credential = fixture.seedCredential(credential("ACTIVE", true));
        fixture.repository.finalWriteAllowed = false;

        IllegalStateException conflict = assertThrows(IllegalStateException.class, () -> fixture.service(
                RecordingProbePort.success("READ_ONLY")
        ).probe(1L, fixture.account.exchangeAccountId(), credential.credentialId(),
                "admin", paperCommand(), "trace-writeback-conflict"));

        assertEquals("VERSION_CONFLICT", conflict.getMessage());
        assertEquals(List.of("PERMISSION_PROBE_STARTED"),
                fixture.repository.auditLogs.stream().map(AuditLog::eventType).toList());
    }

    @Test
    void controlledReadonlyPortMayInspectLiveCredentialWithoutTradingAuthorization() {
        Fixture fixture = new Fixture("LIVE");
        ExchangeAccountCredentialMaterial credential = fixture.seedCredential(credential("ACTIVE", true));
        ExchangeCredentialPermissionProbePort port = new ExchangeCredentialPermissionProbePort() {
            @Override
            public boolean supportsControlledLiveReadOnlyProbe() {
                return true;
            }

            @Override
            public ExchangeCredentialPermissionProbeResult probe(ExchangeCredentialPermissionProbeRequest request) {
                assertEquals("LIVE", request.tradeEnv());
                assertEquals(1L, request.ownerUserId());
                return ExchangeCredentialPermissionProbeResult.succeeded(
                        "OKX", "OKX_API_V5", "READ_ONLY", "PASSED", "req-live-read", request.traceId(),
                        fixedClock.instant(), fixedClock.instant()
                );
            }
        };

        CredentialPermissionProbeSummary summary = fixture.service(port).probe(
                1L, fixture.account.exchangeAccountId(), credential.credentialId(),
                "admin", paperCommand(), "trace-live-read"
        );

        assertEquals("SUCCEEDED", summary.permissionProbeStatus());
        assertEquals("READ_ONLY", summary.permissionScope());
        assertFalse(summary.withdrawEnabled());
    }

    @Test
    void shouldWriteFailedAuditAndIncrementFailedAuthCountForAuthFailures() {
        Fixture fixture = new Fixture("SIM");
        ExchangeAccountCredentialMaterial credential = fixture.seedCredential(credential("ACTIVE", true));
        CredentialPermissionProbeService service = fixture.service(RecordingProbePort.failed("AUTH_FAILED", "FAILED"));

        CredentialPermissionProbeSummary summary = service.probe(1L, fixture.account.exchangeAccountId(), credential.credentialId(),
                "admin", paperCommand(), "trace-auth-failed");

        assertEquals("FAILED", summary.permissionProbeStatus());
        assertEquals("AUTH_FAILED", summary.sanitizedErrorCategory());
        assertEquals(1, summary.failedAuthCount());
        assertEquals(List.of("PERMISSION_PROBE_STARTED", "PERMISSION_PROBE_FAILED"), fixture.repository.auditLogs.stream().map(AuditLog::eventType).toList());
    }

    @Test
    void failureWithoutPermissionObservationPreservesLastKnownRiskFacts() {
        Fixture fixture = new Fixture("SIM");
        ExchangeAccountCredentialMaterial credential = fixture.seedCredential(credential("ACTIVE", true)
                .withPermissionScope("TRADE"));

        CredentialPermissionProbeSummary summary = fixture.service(
                RecordingProbePort.failed("AUTH_FAILED", "UNKNOWN")
        ).probe(1L, fixture.account.exchangeAccountId(), credential.credentialId(),
                "admin", paperCommand(), "trace-preserve-risk");

        assertEquals("FAILED", summary.permissionProbeStatus());
        assertEquals("TRADE", summary.permissionScope());
        assertFalse(summary.withdrawEnabled());
        assertEquals("NOT_CHECKED", summary.ipAllowlistProbeStatus());
    }

    @Test
    void auditFailureReturnsAtomicWritebackCategory() {
        Fixture fixture = new Fixture("SIM");
        ExchangeAccountCredentialMaterial credential = fixture.seedCredential(credential("ACTIVE", true));
        fixture.repository.auditWriteAllowed = false;

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> fixture.service(
                RecordingProbePort.success("READ_ONLY")
        ).probe(1L, fixture.account.exchangeAccountId(), credential.credentialId(),
                "admin", paperCommand(), "trace-atomic-writeback"));

        assertEquals("ATOMIC_WRITEBACK_FAILED", failure.getMessage());
    }

    @Test
    void shouldNotIncrementFailedAuthCountForTimeoutRateLimitOrRemote5xx() {
        assertNoFailedAuthIncrement("TIMEOUT");
        assertNoFailedAuthIncrement("RATE_LIMITED");
        assertNoFailedAuthIncrement("EXCHANGE_5XX");
    }

    @Test
    void shouldRejectLiveInactiveAndNonActiveCredentialWithoutCallingPort() {
        assertSkippedWithoutPort("LIVE", credential("ACTIVE", true), "LIVE_CREDENTIAL_BLOCKED");
        assertSkippedWithoutPort("SIM", credential("ACTIVE", false), "CREDENTIAL_NOT_ACTIVE");
        assertSkippedWithoutPort("SIM", credential("DISABLED", false), "CREDENTIAL_NOT_ACTIVE");
    }

    @Test
    void shouldSkipWhenPaperSafetyGateMissingOrWithdrawEnabledRisk() {
        Fixture missingPaperGate = new Fixture("SIM");
        ExchangeAccountCredentialMaterial active = missingPaperGate.seedCredential(credential("ACTIVE", true));
        RecordingProbePort missingGatePort = RecordingProbePort.success("READ_ONLY");
        CredentialPermissionProbeSummary missingGate = missingPaperGate.service(missingGatePort).probe(
                1L,
                missingPaperGate.account.exchangeAccountId(),
                active.credentialId(),
                "admin",
                new CredentialPermissionProbeCommand("operator probe", true, "PAPER", false),
                "trace-missing-gate"
        );
        assertEquals("SKIPPED", missingGate.permissionProbeStatus());
        assertEquals("PAPER_SAFETY_GATE_MISSING", missingGate.sanitizedErrorCategory());
        assertEquals(0, missingGatePort.calls);

        Fixture withdrawRisk = new Fixture("SIM");
        ExchangeAccountCredentialMaterial risky = withdrawRisk.seedCredential(credential("ACTIVE", true).withWithdrawEnabled(true));
        RecordingProbePort withdrawPort = RecordingProbePort.success("READ_ONLY");
        CredentialPermissionProbeSummary withdraw = withdrawRisk.service(withdrawPort).probe(
                1L,
                withdrawRisk.account.exchangeAccountId(),
                risky.credentialId(),
                "admin",
                paperCommand(),
                "trace-withdraw-risk"
        );
        assertEquals("SKIPPED", withdraw.permissionProbeStatus());
        assertEquals("WITHDRAW_ENABLED_RISK", withdraw.sanitizedErrorCategory());
        assertEquals(0, withdrawPort.calls);
    }

    @Test
    void shouldKeepPermissionScopeNullWhenProbeDoesNotConfirmScope() {
        Fixture fixture = new Fixture("SIM");
        ExchangeAccountCredentialMaterial credential = fixture.seedCredential(credential("ACTIVE", true).withPermissionScope(null));
        CredentialPermissionProbeService service = fixture.service(RecordingProbePort.failed("IP_ALLOWLIST_FAILED", "FAILED"));

        CredentialPermissionProbeSummary summary = service.probe(1L, fixture.account.exchangeAccountId(), credential.credentialId(),
                "admin", paperCommand(), "trace-null-scope");

        assertEquals("FAILED", summary.permissionProbeStatus());
        assertEquals("IP_ALLOWLIST_FAILED", summary.sanitizedErrorCategory());
        assertEquals(1, summary.failedAuthCount());
        assertEquals(null, summary.permissionScope());
        assertFalse("TRADE".equals(summary.permissionScope()));
    }

    @Test
    void shouldSanitizeLastPermissionProbeError() {
        Fixture fixture = new Fixture("SIM");
        ExchangeAccountCredentialMaterial credential = fixture.seedCredential(credential("ACTIVE", true));
        CredentialPermissionProbeService service = fixture.service(RecordingProbePort.failed("secret raw response", "UNKNOWN"));

        CredentialPermissionProbeSummary summary = service.probe(1L, fixture.account.exchangeAccountId(), credential.credentialId(),
                "admin", paperCommand(), "trace-sanitize");

        assertEquals("REDACTED_ERROR", summary.sanitizedErrorCategory());
        assertFalse(containsSensitive(summary.sanitizedErrorCategory()));
    }

    @Test
    void shouldRejectConcurrentInProgressProbe() {
        Fixture fixture = new Fixture("SIM");
        ExchangeAccountCredentialMaterial credential = fixture.seedCredential(credential("ACTIVE", true).withProbeStatus("IN_PROGRESS"));
        RecordingProbePort port = RecordingProbePort.success("READ_ONLY");
        CredentialPermissionProbeService service = fixture.service(port);

        IllegalStateException conflict = assertThrows(IllegalStateException.class, () -> service.probe(
                1L,
                fixture.account.exchangeAccountId(),
                credential.credentialId(),
                "admin",
                paperCommand(),
                "trace-concurrent"
        ));

        assertEquals("credential permission probe already in progress", conflict.getMessage());
        assertEquals(0, port.calls);
        assertTrue(fixture.repository.auditLogs.isEmpty());
    }

    @Test
    void latestShouldNotCallPortOrReadMaterialAgain() {
        Fixture fixture = new Fixture("SIM");
        ExchangeAccountCredentialMaterial credential = fixture.seedCredential(credential("ACTIVE", true)
                .withProbeStatus("SUCCEEDED")
                .withPermissionScope("READ_ONLY"));
        RecordingProbePort port = RecordingProbePort.success("TRADE");
        CredentialPermissionProbeService service = fixture.service(port);

        CredentialPermissionProbeSummary summary = service.latest(1L, fixture.account.exchangeAccountId(), credential.credentialId(),
                "req-latest", "trace-latest");

        assertEquals("SUCCEEDED", summary.permissionProbeStatus());
        assertEquals("READ_ONLY", summary.permissionScope());
        assertEquals(0, port.calls);
    }

    private void assertNoFailedAuthIncrement(String errorCategory) {
        Fixture fixture = new Fixture("SIM");
        ExchangeAccountCredentialMaterial credential = fixture.seedCredential(credential("ACTIVE", true));
        CredentialPermissionProbeService service = fixture.service(RecordingProbePort.failed(errorCategory, "UNKNOWN"));

        CredentialPermissionProbeSummary summary = service.probe(1L, fixture.account.exchangeAccountId(), credential.credentialId(),
                "admin", paperCommand(), "trace-" + errorCategory);

        assertEquals(0, summary.failedAuthCount());
    }

    private void assertSkippedWithoutPort(String tradeEnv, CredentialBuilder builder, String expectedCategory) {
        Fixture fixture = new Fixture(tradeEnv);
        ExchangeAccountCredentialMaterial credential = fixture.seedCredential(builder);
        RecordingProbePort port = RecordingProbePort.success("READ_ONLY");
        CredentialPermissionProbeSummary summary = fixture.service(port).probe(1L, fixture.account.exchangeAccountId(),
                credential.credentialId(), "admin", paperCommand(), "trace-skipped");

        assertEquals("SKIPPED", summary.permissionProbeStatus());
        assertEquals(expectedCategory, summary.sanitizedErrorCategory());
        assertEquals(0, port.calls);
        assertEquals(List.of("PERMISSION_PROBE_SKIPPED"), fixture.repository.auditLogs.stream().map(AuditLog::eventType).toList());
    }

    private CredentialPermissionProbeCommand paperCommand() {
        return new CredentialPermissionProbeCommand("operator probe", true, "PAPER", true);
    }

    private CredentialBuilder credential(String status, boolean active) {
        return new CredentialBuilder(status, active);
    }

    private static boolean containsSensitive(String text) {
        if (text == null) {
            return false;
        }
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("secret")
                || lower.contains("token")
                || lower.contains("signature")
                || lower.contains("headers")
                || lower.contains("raw response")
                || lower.contains("decrypted_payload")
                || lower.contains("encrypted_payload");
    }

    private final class Fixture {
        private final ExchangeAccountSummary account;
        private final InMemoryExchangeAccountRepository accountRepository;
        private final InMemoryExchangeAccountCredentialRepository repository = new InMemoryExchangeAccountCredentialRepository();
        private final RecordingTransactions transactions = new RecordingTransactions();

        private Fixture(String tradeEnv) {
            this.account = new ExchangeAccountSummary(900001L, 900001L, 1L, "OKX", tradeEnv, "demo", null, true, "ACTIVE");
            this.accountRepository = new InMemoryExchangeAccountRepository(account);
        }

        private ExchangeAccountCredentialMaterial seedCredential(CredentialBuilder builder) {
            return repository.seed(account.exchangeAccountId(), builder);
        }

        private CredentialPermissionProbeService service(ExchangeCredentialPermissionProbePort port) {
            return new CredentialPermissionProbeService(
                    accountRepository, repository, port, objectMapper, fixedClock, transactions
            );
        }
    }

    private static final class InMemoryExchangeAccountRepository implements ExchangeAccountRepository {
        private final ExchangeAccountSummary account;

        private InMemoryExchangeAccountRepository(ExchangeAccountSummary account) {
            this.account = account;
        }

        @Override
        public List<ExchangeAccountSummary> listByOwnerUserId(Long ownerUserId) {
            return List.of(account);
        }

        @Override
        public Optional<ExchangeAccountSummary> findById(Long exchangeAccountId) {
            return Optional.of(account).filter(item -> item.exchangeAccountId().equals(exchangeAccountId));
        }

        @Override
        public Optional<ExchangeAccountSummary> findByIdForOwner(Long ownerUserId, Long exchangeAccountId) {
            return Optional.of(account).filter(item -> item.ownerUserId().equals(ownerUserId) && item.exchangeAccountId().equals(exchangeAccountId));
        }

        @Override
        public Optional<ExchangeAccountSummary> findDefaultByOwnerUserId(Long ownerUserId) {
            return Optional.of(account);
        }

        @Override
        public ExchangeAccountSummary create(Long ownerUserId, String exchangeCode, String tradeEnv, String accountAlias, String externalAccountRef, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean updateProfile(Long ownerUserId, Long exchangeAccountId, String accountAlias, String externalAccountRef, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean enable(Long ownerUserId, Long exchangeAccountId, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean disable(Long ownerUserId, Long exchangeAccountId, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clearDefaultByScope(Long ownerUserId, String exchangeCode, String tradeEnv, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean markDefault(Long ownerUserId, Long exchangeAccountId, Instant now) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class InMemoryExchangeAccountCredentialRepository implements ExchangeAccountCredentialRepository {
        private final Map<Long, ExchangeAccountCredentialMaterial> storage = new LinkedHashMap<>();
        private final List<AuditLog> auditLogs = new ArrayList<>();
        private boolean finalWriteAllowed = true;
        private boolean auditWriteAllowed = true;
        private long nextId = 0L;

        private ExchangeAccountCredentialMaterial seed(Long exchangeAccountId, CredentialBuilder builder) {
            ExchangeAccountCredentialMaterial material = builder.build(++nextId, exchangeAccountId);
            storage.put(material.credentialId(), material);
            return material;
        }

        @Override
        public List<ExchangeAccountCredentialSummary> listActiveSummaries(Long ownerUserId, Long exchangeAccountId) {
            return storage.values().stream().filter(item -> item.exchangeAccountId().equals(exchangeAccountId) && item.isActive() && "ACTIVE".equals(item.credentialStatus())).map(ExchangeAccountCredentialMaterial::toSummary).toList();
        }

        @Override
        public Optional<ExchangeAccountCredentialSummary> findActiveSummary(Long ownerUserId, Long exchangeAccountId, String credentialType) {
            return listActiveSummaries(ownerUserId, exchangeAccountId).stream().filter(item -> item.credentialType().equals(credentialType)).findFirst();
        }

        @Override
        public Optional<ExchangeAccountCredentialSummary> findActiveByAccountAndType(Long exchangeAccountId, String credentialType) {
            return findActiveSummary(1L, exchangeAccountId, credentialType);
        }

        @Override
        public Optional<ExchangeAccountCredentialMaterial> findActiveMaterial(Long ownerUserId, Long exchangeAccountId, String credentialType) {
            return storage.values().stream().filter(item -> item.exchangeAccountId().equals(exchangeAccountId) && item.credentialType().equals(credentialType) && item.isActive() && "ACTIVE".equals(item.credentialStatus())).findFirst();
        }

        @Override
        public Optional<ExchangeAccountCredentialSummary> findByCredentialIdForOwner(Long ownerUserId, Long exchangeAccountId, Long credentialId) {
            return Optional.ofNullable(storage.get(credentialId)).filter(item -> item.exchangeAccountId().equals(exchangeAccountId)).map(ExchangeAccountCredentialMaterial::toSummary);
        }

        @Override
        public Optional<ExchangeAccountCredentialSummary> findActiveByCredentialIdForOwnerForUpdate(Long ownerUserId, Long exchangeAccountId, Long credentialId) {
            return findByCredentialIdForOwner(ownerUserId, exchangeAccountId, credentialId).filter(item -> item.isActive() && "ACTIVE".equals(item.credentialStatus()));
        }

        @Override
        public Optional<ExchangeAccountCredentialMaterial> findByCredentialIdForOwnerForUpdate(Long ownerUserId, Long exchangeAccountId, Long credentialId) {
            return Optional.ofNullable(storage.get(credentialId)).filter(item -> item.exchangeAccountId().equals(exchangeAccountId));
        }

        @Override
        public boolean existsOtherActiveCredential(Long exchangeAccountId, String credentialType, Long excludedCredentialId) {
            return false;
        }

        @Override
        public void deactivateActiveByAccountAndType(Long exchangeAccountId, String credentialType, Instant revokedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ExchangeAccountCredentialSummary insertNewVersion(Long exchangeAccountId, String credentialType, String encryptedPayloadJson, int keyVersion, String cipherSuite, String maskedAccessKey, Long rotatedFromCredentialId, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean markVerificationResult(Long credentialId, String verificationStatus, Instant verifiedAt, String lastVerificationError, Instant updatedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean markEnabled(Long credentialId, Long exchangeAccountId, String verificationStatus, Instant verifiedAt, Instant updatedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean updateLifecycleStatus(Long credentialId, Long exchangeAccountId, String credentialStatus, boolean active, Instant revokedAt, String revokedBy, String revokeReason, Instant updatedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean markRotated(Long credentialId, Long exchangeAccountId, String rotatedBy, Instant rotatedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void appendCredentialAuditLog(Long credentialId, Long exchangeAccountId, String eventType, String actor, String reason, String metadataJson, Instant createdAt) {
            if (!auditWriteAllowed && !"PERMISSION_PROBE_STARTED".equals(eventType)) {
                throw new IllegalStateException("synthetic audit failure");
            }
            auditLogs.add(new AuditLog(credentialId, exchangeAccountId, eventType, actor, reason, metadataJson, createdAt));
        }

        @Override
        public boolean markPermissionProbeInProgress(Long credentialId, Long exchangeAccountId, Instant updatedAt) {
            ExchangeAccountCredentialMaterial current = storage.get(credentialId);
            if (current == null || "IN_PROGRESS".equals(current.permissionProbeStatus())) {
                return false;
            }
            storage.put(credentialId, copy(current, "IN_PROGRESS", current.permissionScope(), current.withdrawEnabled(), current.ipAllowlistProbeStatus(), current.failedAuthCount(), current.lastPermissionProbeAt(), null, updatedAt));
            return true;
        }

        @Override
        public boolean markPermissionProbeResult(Long credentialId, Long exchangeAccountId, String permissionProbeStatus,
                                                 String permissionScope, boolean withdrawEnabled,
                                                 boolean ipAllowlistRequired, String ipAllowlistProbeStatus,
                                                 Instant lastPermissionProbeAt, String lastPermissionProbeError,
                                                 boolean incrementFailedAuthCount, Instant updatedAt) {
            ExchangeAccountCredentialMaterial current = storage.get(credentialId);
            if (!finalWriteAllowed || current == null || !"IN_PROGRESS".equals(current.permissionProbeStatus())) {
                return false;
            }
            storage.put(credentialId, copy(current, permissionProbeStatus, permissionScope, withdrawEnabled,
                    ipAllowlistProbeStatus, current.failedAuthCount() + (incrementFailedAuthCount ? 1 : 0),
                    lastPermissionProbeAt, lastPermissionProbeError, updatedAt));
            return true;
        }

        private ExchangeAccountCredentialMaterial copy(ExchangeAccountCredentialMaterial current, String probeStatus, String permissionScope, boolean withdrawEnabled, String ipStatus, int failedAuthCount, Instant lastProbeAt, String lastProbeError, Instant updatedAt) {
            return new ExchangeAccountCredentialMaterial(current.credentialId(), current.exchangeAccountId(), current.credentialType(), current.maskedAccessKey(), current.credentialStatus(), current.verificationStatus(), current.isActive(), current.revokedAt(), current.rotatedFromCredentialId(), current.rotatedAt(), current.lastVerifiedAt(), current.lastVerificationError(), updatedAt, current.decryptedPayloadJson(), probeStatus, permissionScope, withdrawEnabled, ipStatus, failedAuthCount, lastProbeAt, lastProbeError);
        }
    }

    private static final class RecordingProbePort implements ExchangeCredentialPermissionProbePort {
        private final ExchangeCredentialPermissionProbeResult result;
        private int calls;

        private RecordingProbePort(ExchangeCredentialPermissionProbeResult result) {
            this.result = result;
        }

        static RecordingProbePort success(String scope) {
            return new RecordingProbePort(ExchangeCredentialPermissionProbeResult.succeeded("OKX", "OKX_API_V5", scope, "PASSED", "req-success", "trace-port", Instant.parse("2026-06-13T00:00:00Z"), Instant.parse("2026-06-13T00:00:01Z")));
        }

        static RecordingProbePort failed(String errorCategory, String ipStatus) {
            return new RecordingProbePort(ExchangeCredentialPermissionProbeResult.failed("OKX", "OKX_API_V5", errorCategory, ipStatus, "req-failed", "trace-port", Instant.parse("2026-06-13T00:00:00Z"), Instant.parse("2026-06-13T00:00:01Z")));
        }

        @Override
        public ExchangeCredentialPermissionProbeResult probe(ExchangeCredentialPermissionProbeRequest request) {
            calls++;
            return result;
        }
    }

    private static final class RecordingTransactions implements TransactionOperations {
        private int executions;
        private boolean active;

        @Override
        public <T> T execute(TransactionCallback<T> action) {
            executions++;
            active = true;
            try {
                return action.doInTransaction(new SimpleTransactionStatus());
            } finally {
                active = false;
            }
        }
    }

    private static final class CredentialBuilder {
        private final String status;
        private final boolean active;
        private String probeStatus = "NOT_PROBED";
        private String permissionScope;
        private boolean withdrawEnabled;
        private int failedAuthCount;

        private CredentialBuilder(String status, boolean active) {
            this.status = status;
            this.active = active;
        }

        private CredentialBuilder withProbeStatus(String value) {
            this.probeStatus = value;
            return this;
        }

        private CredentialBuilder withPermissionScope(String value) {
            this.permissionScope = value;
            return this;
        }

        private CredentialBuilder withWithdrawEnabled(boolean value) {
            this.withdrawEnabled = value;
            return this;
        }

        private CredentialBuilder withFailedAuthCount(int value) {
            this.failedAuthCount = value;
            return this;
        }

        private ExchangeAccountCredentialMaterial build(Long credentialId, Long exchangeAccountId) {
            return new ExchangeAccountCredentialMaterial(
                    credentialId,
                    exchangeAccountId,
                    "OKX_API_V5",
                    "tes***ey",
                    status,
                    "VERIFIED",
                    active,
                    null,
                    null,
                    null,
                    Instant.parse("2026-06-12T00:00:00Z"),
                    null,
                    Instant.parse("2026-06-12T00:00:00Z"),
                    "{\"apiKey\":\"test\"}",
                    probeStatus,
                    permissionScope,
                    withdrawEnabled,
                    "NOT_CHECKED",
                    failedAuthCount,
                    null,
                    null
            );
        }
    }

    private record AuditLog(Long credentialId, Long exchangeAccountId, String eventType, String actor, String reason,
                            String metadataJson, Instant createdAt) {
    }
}
