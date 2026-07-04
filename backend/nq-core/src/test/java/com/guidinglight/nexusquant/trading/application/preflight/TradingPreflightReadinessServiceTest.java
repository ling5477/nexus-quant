package com.guidinglight.nexusquant.trading.application.preflight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialMaterial;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialSummary;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountCredentialRepository;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountRepository;
import com.guidinglight.nexusquant.marketdata.application.MarketdataQualityOverviewService;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityDataOriginSummary;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityDatasetCoverageSummary;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityIssue;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityMetric;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityOverview;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityOverviewQuery;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityOverviewScope;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityStatus;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataReadinessSourceHealth;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataReadinessStatus;
import com.guidinglight.nexusquant.marketdata.domain.port.MarketdataQualityOverviewRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class TradingPreflightReadinessServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-04T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void shouldReturnBlockedBaselineWhenAccountAndCredentialMissing() {
        TradingPreflightReadinessService service = service(
                List.of(),
                List.of(),
                quality(MarketdataQualityStatus.INCOMPLETE)
        );

        TradingPreflightReadiness readiness = service.readiness(new TradingPreflightReadinessQuery(
                1L,
                null,
                null,
                null,
                "btc-usdt",
                null
        ));

        assertEquals("OKX", readiness.exchangeCode());
        assertEquals("SPOT", readiness.marketType());
        assertEquals("BTC-USDT", readiness.symbol());
        assertFalse(readiness.accountConfigured());
        assertFalse(readiness.credentialConfigured());
        assertEquals("CREDENTIAL_UNCONFIGURED", readiness.credentialStatus());
        assertEquals("PERMISSION_PROBE_NOT_AVAILABLE", readiness.permissionProbeStatus());
        assertEquals("RISK_PREFLIGHT_BLOCKED", readiness.riskPreflightStatus());
        assertHasReason(readiness.blockers(), "LIVE_DISABLED");
        assertHasReason(readiness.blockers(), "REAL_PROVIDER_NOT_IMPLEMENTED");
        assertHasReason(readiness.blockers(), "PRIVATE_TRADING_NOT_IMPLEMENTED");
        assertHasReason(readiness.blockers(), "ACCOUNT_UNCONFIGURED");
        assertHasReason(readiness.blockers(), "CREDENTIAL_UNCONFIGURED");
        assertHasReason(readiness.blockers(), "PERMISSION_PROBE_NOT_IMPLEMENTED");
        assertHasReason(readiness.blockers(), "DATA_QUALITY_NOT_OK");
        assertEquals(Instant.parse("2026-07-04T12:00:00Z"), readiness.generatedAt());
    }

    @Test
    void shouldUseCredentialMetadataWithoutReadingCredentialMaterial() {
        ExchangeAccountSummary account = account(900001L, "OKX", "SIM", "ACTIVE", true);
        ExchangeAccountCredentialSummary credential = credential(
                10L,
                "OKX_API_V5",
                "ACTIVE",
                "VERIFIED",
                "SKIPPED",
                "READ_ONLY"
        );
        TradingPreflightReadinessService service = service(
                List.of(account),
                List.of(credential),
                quality(MarketdataQualityStatus.OK)
        );

        TradingPreflightReadiness readiness = service.readiness(new TradingPreflightReadinessQuery(
                1L,
                "okx",
                900001L,
                "spot",
                "ETH-USDT",
                "strategy-alpha"
        ));

        assertTrue(readiness.accountConfigured());
        assertTrue(readiness.credentialConfigured());
        assertEquals("ACTIVE", readiness.credentialStatus());
        assertEquals("PERMISSION_PROBE_SKIPPED", readiness.permissionProbeStatus());
        assertEquals(1, readiness.credentialTypeSummary().size());
        assertEquals("OKX_API_V5", readiness.credentialTypeSummary().getFirst().credentialType());
        assertEquals("SKIPPED", readiness.credentialTypeSummary().getFirst().permissionProbeStatus());
        assertHasReason(readiness.blockers(), "PERMISSION_PROBE_NOT_IMPLEMENTED");
        assertHasReason(readiness.blockers(), "LIVE_DISABLED");
        assertFalse(hasReason(readiness.blockers(), "CREDENTIAL_UNCONFIGURED"));
    }

    @Test
    void shouldKeepDataQualityDiagnosticFromAuthorizingTrading() {
        ExchangeAccountSummary account = account(900001L, "OKX", "SIM", "ACTIVE", true);
        ExchangeAccountCredentialSummary credential = credential(
                10L,
                "OKX_API_V5",
                "ACTIVE",
                "VERIFIED",
                "NOT_PROBED",
                null
        );
        TradingPreflightReadinessService service = service(
                List.of(account),
                List.of(credential),
                quality(MarketdataQualityStatus.OK)
        );

        TradingPreflightReadiness readiness = service.readiness(new TradingPreflightReadinessQuery(
                1L,
                "OKX",
                null,
                "SPOT",
                "BTC-USDT",
                null
        ));

        assertEquals("OK", readiness.dataQualityStatus());
        assertEquals("RISK_PREFLIGHT_BLOCKED", readiness.riskPreflightStatus());
        assertHasReason(readiness.warnings(), "DATA_QUALITY_DIAGNOSTIC_ONLY");
        assertHasReason(readiness.warnings(), "RISK_PREFLIGHT_READONLY");
        assertHasReason(readiness.blockers(), "REAL_PROVIDER_NOT_IMPLEMENTED");
        assertHasReason(readiness.blockers(), "PRIVATE_TRADING_NOT_IMPLEMENTED");
        assertHasReason(readiness.blockers(), "LIVE_DISABLED");
        assertFalse(hasReason(readiness.blockers(), "DATA_QUALITY_NOT_OK"));
    }

    private TradingPreflightReadinessService service(
            List<ExchangeAccountSummary> accounts,
            List<ExchangeAccountCredentialSummary> credentials,
            MarketdataQualityOverview overview
    ) {
        return new TradingPreflightReadinessService(
                new StubAccountRepository(accounts),
                new GuardedCredentialRepository(credentials),
                new StubMarketdataQualityOverviewService(overview),
                FIXED_CLOCK
        );
    }

    private ExchangeAccountSummary account(
            Long accountId,
            String exchangeCode,
            String tradeEnv,
            String status,
            boolean isDefault
    ) {
        return new ExchangeAccountSummary(
                accountId,
                accountId,
                1L,
                exchangeCode,
                tradeEnv,
                exchangeCode + " account",
                null,
                isDefault,
                status
        );
    }

    private ExchangeAccountCredentialSummary credential(
            Long credentialId,
            String credentialType,
            String credentialStatus,
            String verificationStatus,
            String permissionProbeStatus,
            String permissionScope
    ) {
        return new ExchangeAccountCredentialSummary(
                credentialId,
                900001L,
                credentialType,
                "not-returned",
                credentialStatus,
                verificationStatus,
                true,
                null,
                null,
                null,
                Instant.parse("2026-07-04T00:00:00Z"),
                null,
                Instant.parse("2026-07-04T00:01:00Z"),
                permissionProbeStatus,
                permissionScope,
                false,
                "SKIPPED".equals(permissionProbeStatus) ? "SKIPPED" : "NOT_CHECKED",
                0,
                "SKIPPED".equals(permissionProbeStatus) ? Instant.parse("2026-07-04T00:02:00Z") : null,
                null
        );
    }

    private MarketdataQualityOverview quality(MarketdataQualityStatus status) {
        return new MarketdataQualityOverview(
                new MarketdataQualityOverviewScope("OKX", "SPOT", "BTC-USDT", null, null, null, null, null, null),
                status == MarketdataQualityStatus.INCOMPLETE ? 0 : 1,
                status == MarketdataQualityStatus.INCOMPLETE ? null : 1L,
                status == MarketdataQualityStatus.GAP_DETECTED ? 1L : 0L,
                MarketdataQualityMetric.notAvailable("duplicate diagnostic unavailable in test"),
                MarketdataQualityMetric.notAvailable("out-of-order diagnostic unavailable in test"),
                MarketdataQualityMetric.unknown("stale diagnostic unavailable in test"),
                null,
                null,
                null,
                null,
                null,
                status == MarketdataQualityStatus.OK
                        ? MarketdataReadinessSourceHealth.HEALTHY
                        : MarketdataReadinessSourceHealth.UNKNOWN,
                status == MarketdataQualityStatus.OK
                        ? MarketdataReadinessStatus.FRESH
                        : MarketdataReadinessStatus.NO_DATA,
                status,
                new MarketdataQualityDataOriginSummary(null, "LOCAL_DB", 0, 0, 0, "LOCAL_DB_ONLY_READ_MODEL"),
                new MarketdataQualityDatasetCoverageSummary(0, null, null, null, null, null, null, null),
                status == MarketdataQualityStatus.OK
                        ? List.of()
                        : List.of(new MarketdataQualityIssue("NO_DATA", "WARNING", 1, "no local bars")),
                Instant.parse("2026-07-04T00:10:00Z")
        );
    }

    private void assertHasReason(List<TradingPreflightReason> reasons, String code) {
        assertTrue(hasReason(reasons, code), "expected reason code: " + code + ", actual=" + reasons);
    }

    private boolean hasReason(List<TradingPreflightReason> reasons, String code) {
        return reasons.stream().anyMatch(reason -> code.equals(reason.code()));
    }

    private record StubAccountRepository(List<ExchangeAccountSummary> accounts) implements ExchangeAccountRepository {
        @Override
        public List<ExchangeAccountSummary> listByOwnerUserId(Long ownerUserId) {
            return accounts.stream()
                    .filter(account -> account.ownerUserId().equals(ownerUserId))
                    .toList();
        }

        @Override
        public Optional<ExchangeAccountSummary> findById(Long exchangeAccountId) {
            return accounts.stream()
                    .filter(account -> account.exchangeAccountId().equals(exchangeAccountId))
                    .findFirst();
        }

        @Override
        public Optional<ExchangeAccountSummary> findByIdForOwner(Long ownerUserId, Long exchangeAccountId) {
            return accounts.stream()
                    .filter(account -> account.ownerUserId().equals(ownerUserId))
                    .filter(account -> account.exchangeAccountId().equals(exchangeAccountId))
                    .findFirst();
        }

        @Override
        public Optional<ExchangeAccountSummary> findDefaultByOwnerUserId(Long ownerUserId) {
            return accounts.stream()
                    .filter(account -> account.ownerUserId().equals(ownerUserId))
                    .filter(ExchangeAccountSummary::isDefault)
                    .findFirst();
        }

        @Override
        public ExchangeAccountSummary create(
                Long ownerUserId,
                String exchangeCode,
                String tradeEnv,
                String accountAlias,
                String externalAccountRef,
                Instant now
        ) {
            throw new AssertionError("preflight must not create exchange accounts");
        }

        @Override
        public boolean updateProfile(
                Long ownerUserId,
                Long exchangeAccountId,
                String accountAlias,
                String externalAccountRef,
                Instant now
        ) {
            throw new AssertionError("preflight must not update exchange accounts");
        }

        @Override
        public boolean enable(Long ownerUserId, Long exchangeAccountId, Instant now) {
            throw new AssertionError("preflight must not enable exchange accounts");
        }

        @Override
        public boolean disable(Long ownerUserId, Long exchangeAccountId, Instant now) {
            throw new AssertionError("preflight must not disable exchange accounts");
        }

        @Override
        public void clearDefaultByScope(Long ownerUserId, String exchangeCode, String tradeEnv, Instant now) {
            throw new AssertionError("preflight must not clear default account scope");
        }

        @Override
        public boolean markDefault(Long ownerUserId, Long exchangeAccountId, Instant now) {
            throw new AssertionError("preflight must not mark default account");
        }
    }

    private record GuardedCredentialRepository(
            List<ExchangeAccountCredentialSummary> credentials
    ) implements ExchangeAccountCredentialRepository {
        @Override
        public List<ExchangeAccountCredentialSummary> listActiveSummaries(Long ownerUserId, Long exchangeAccountId) {
            return credentials.stream()
                    .filter(credential -> credential.exchangeAccountId().equals(exchangeAccountId))
                    .filter(ExchangeAccountCredentialSummary::isActive)
                    .toList();
        }

        @Override
        public Optional<ExchangeAccountCredentialSummary> findActiveSummary(
                Long ownerUserId,
                Long exchangeAccountId,
                String credentialType
        ) {
            throw new AssertionError("preflight should use listActiveSummaries only");
        }

        @Override
        public Optional<ExchangeAccountCredentialSummary> findActiveByAccountAndType(
                Long exchangeAccountId,
                String credentialType
        ) {
            throw new AssertionError("preflight should not query credential by type without owner");
        }

        @Override
        public Optional<ExchangeAccountCredentialMaterial> findActiveMaterial(
                Long ownerUserId,
                Long exchangeAccountId,
                String credentialType
        ) {
            throw new AssertionError("preflight must not read credential material");
        }

        @Override
        public Optional<ExchangeAccountCredentialSummary> findByCredentialIdForOwner(
                Long ownerUserId,
                Long exchangeAccountId,
                Long credentialId
        ) {
            throw new AssertionError("preflight should not query individual credential");
        }

        @Override
        public Optional<ExchangeAccountCredentialSummary> findActiveByCredentialIdForOwnerForUpdate(
                Long ownerUserId,
                Long exchangeAccountId,
                Long credentialId
        ) {
            throw new AssertionError("preflight must not lock credential rows");
        }

        @Override
        public Optional<ExchangeAccountCredentialMaterial> findByCredentialIdForOwnerForUpdate(
                Long ownerUserId,
                Long exchangeAccountId,
                Long credentialId
        ) {
            throw new AssertionError("preflight must not read or lock credential material");
        }

        @Override
        public boolean existsOtherActiveCredential(
                Long exchangeAccountId,
                String credentialType,
                Long excludedCredentialId
        ) {
            throw new AssertionError("preflight must not check enable conflicts");
        }

        @Override
        public void deactivateActiveByAccountAndType(Long exchangeAccountId, String credentialType, Instant revokedAt) {
            throw new AssertionError("preflight must not mutate credentials");
        }

        @Override
        public ExchangeAccountCredentialSummary insertNewVersion(
                Long exchangeAccountId,
                String credentialType,
                String encryptedPayloadJson,
                int keyVersion,
                String cipherSuite,
                String maskedAccessKey,
                Long rotatedFromCredentialId,
                Instant now
        ) {
            throw new AssertionError("preflight must not insert credentials");
        }

        @Override
        public boolean markVerificationResult(
                Long credentialId,
                String verificationStatus,
                Instant verifiedAt,
                String lastVerificationError,
                Instant updatedAt
        ) {
            throw new AssertionError("preflight must not mark credential verification");
        }

        @Override
        public boolean markEnabled(
                Long credentialId,
                Long exchangeAccountId,
                String verificationStatus,
                Instant verifiedAt,
                Instant updatedAt
        ) {
            throw new AssertionError("preflight must not enable credentials");
        }

        @Override
        public boolean updateLifecycleStatus(
                Long credentialId,
                Long exchangeAccountId,
                String credentialStatus,
                boolean active,
                Instant revokedAt,
                String revokedBy,
                String revokeReason,
                Instant updatedAt
        ) {
            throw new AssertionError("preflight must not update credential lifecycle");
        }

        @Override
        public boolean markRotated(Long credentialId, Long exchangeAccountId, String rotatedBy, Instant rotatedAt) {
            throw new AssertionError("preflight must not rotate credentials");
        }

        @Override
        public void appendCredentialAuditLog(
                Long credentialId,
                Long exchangeAccountId,
                String eventType,
                String actor,
                String reason,
                String metadataJson,
                Instant createdAt
        ) {
            throw new AssertionError("preflight must not append credential audit logs");
        }
    }

    private static final class StubMarketdataQualityOverviewService extends MarketdataQualityOverviewService {
        private final MarketdataQualityOverview overview;

        private StubMarketdataQualityOverviewService(MarketdataQualityOverview overview) {
            super(new EmptyMarketdataQualityOverviewRepository());
            this.overview = overview;
        }

        @Override
        public MarketdataQualityOverview summarize(MarketdataQualityOverviewQuery query) {
            return overview;
        }
    }

    private static final class EmptyMarketdataQualityOverviewRepository implements MarketdataQualityOverviewRepository {
        @Override
        public List<com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityBarScopeFacts> loadBarScopeFacts(
                MarketdataQualityOverviewQuery query
        ) {
            return List.of();
        }

        @Override
        public com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityIngestionFacts loadIngestionFacts(
                MarketdataQualityOverviewQuery query
        ) {
            return com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityIngestionFacts.empty();
        }

        @Override
        public com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityDatasetCoverageFacts loadDatasetCoverageFacts(
                MarketdataQualityOverviewQuery query
        ) {
            return com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityDatasetCoverageFacts.empty();
        }
    }
}
