package com.guidinglight.nexusquant.trading.application.riskpreflight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.trading.application.orderpreview.DryRunOrderPreviewResult;
import com.guidinglight.nexusquant.trading.application.orderpreview.OrderPreviewFindingCode;
import com.guidinglight.nexusquant.trading.application.orderpreview.OrderPreviewStatus;
import com.guidinglight.nexusquant.trading.application.reconciliation.ReconciliationFinding;
import com.guidinglight.nexusquant.trading.application.reconciliation.ReconciliationResult;
import com.guidinglight.nexusquant.trading.application.reconciliation.ReconciliationTaxonomy;
import com.guidinglight.nexusquant.trading.application.riskpreflight.RiskPreflightFactBundle.CredentialMetadataSummary;
import com.guidinglight.nexusquant.trading.application.riskpreflight.RiskPreflightFactBundle.LocalAccountMetadataSnapshot;
import com.guidinglight.nexusquant.trading.application.riskpreflight.RiskPreflightFactBundle.MarketdataQualitySnapshot;
import com.guidinglight.nexusquant.trading.application.riskpreflight.RiskPreflightFactBundle.MarketdataQualitySnapshot.Quality;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

/** GateW-3 risk preflight 的 safety、taxonomy、determinism 与 zero-call 回归。 */
class GateW3RiskPreflightServiceTest {

    private static final Instant EVALUATION_TIME = Instant.parse("2026-07-14T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(EVALUATION_TIME, ZoneOffset.UTC);

    @Test
    void shouldCombineCleanPreviewAndReconciliationWithoutAuthorizingExecution() {
        GateW3RiskPreflightResult result = service().evaluate(request(
                preview(OrderPreviewStatus.PASS, OrderPreviewStatus.PASS),
                cleanReconciliation(),
                healthyFacts()
        ));

        assertEquals(GateW3RiskPreflightStatus.PASS, result.structuralStatus());
        assertEquals(GateW3RiskPreflightStatus.PASS, result.venueFactStatus());
        assertEquals(GateW3RiskPreflightStatus.PASS, result.reconciliationStatus());
        assertEquals(GateW3RiskPreflightStatus.PASS, result.localAccountStatus());
        assertEquals(GateW3RiskPreflightStatus.PASS, result.credentialMetadataStatus());
        assertEquals(GateW3RiskPreflightStatus.PASS, result.marketdataQualityStatus());
        assertEquals(GateW3RiskPreflightStatus.PASS, result.pureRiskStatus());
        assertEquals(GateW3RiskPreflightStatus.BLOCKED, result.executionReadiness());
        assertTrue(result.diagnosticOnly());
        assertTrue(result.readOnly());
        assertTrue(result.noSideEffect());
        assertFalse(result.orderSubmitted());
        assertFalse(result.tradingAuthorized());
        assertTrue(result.blockers().contains(GateW3RiskPreflightFindingCode.EXECUTION_NOT_AUTHORIZED));
    }

    @Test
    void shouldKeepMinimumNotionalFeeAndRemotePermissionUnknown() {
        GateW3RiskPreflightResult result = evaluateHealthy();

        assertTrue(result.unknowns().contains(GateW3RiskPreflightFindingCode.MIN_NOTIONAL_UNKNOWN));
        assertTrue(result.unknowns().contains(GateW3RiskPreflightFindingCode.FEE_UNKNOWN));
        assertTrue(result.unknowns().contains(GateW3RiskPreflightFindingCode.REMOTE_PERMISSION_UNKNOWN));
        assertEquals(GateW3RiskPreflightStatus.UNKNOWN, result.permissionStatus());
        assertEquals(GateW3RiskPreflightStatus.BLOCKED, result.executionReadiness());
    }

    @Test
    void shouldKeepAllStatefulRiskDimensionsNotEvaluated() {
        GateW3RiskPreflightResult result = evaluateHealthy();
        List<GateW3RiskPreflightFindingCode> expected = List.of(
                GateW3RiskPreflightFindingCode.BALANCE_NOT_EVALUATED,
                GateW3RiskPreflightFindingCode.POSITION_NOT_EVALUATED,
                GateW3RiskPreflightFindingCode.DAILY_LOSS_NOT_EVALUATED,
                GateW3RiskPreflightFindingCode.OPEN_ORDERS_RISK_NOT_EVALUATED,
                GateW3RiskPreflightFindingCode.KILL_SWITCH_NOT_EVALUATED,
                GateW3RiskPreflightFindingCode.DUPLICATE_REQUEST_NOT_EVALUATED,
                GateW3RiskPreflightFindingCode.RATE_LIMIT_NOT_EVALUATED,
                GateW3RiskPreflightFindingCode.STATEFUL_RISK_PIPELINE_NOT_EVALUATED
        );

        assertTrue(result.notEvaluated().containsAll(expected));
        assertEquals(GateW3RiskPreflightStatus.NOT_EVALUATED, result.statefulRiskStatus());
        assertEquals(GateW3RiskPreflightStatus.NOT_EVALUATED, result.balanceStatus());
    }

    @Test
    void shouldBlockWhenPreviewIsBlocked() {
        GateW3RiskPreflightResult result = service().evaluate(request(
                preview(OrderPreviewStatus.PASS, OrderPreviewStatus.BLOCKED),
                cleanReconciliation(),
                healthyFacts()
        ));

        assertEquals(GateW3RiskPreflightStatus.BLOCKED, result.venueFactStatus());
        assertEquals(GateW3RiskPreflightStatus.BLOCKED, result.pureRiskStatus());
        assertTrue(result.blockers().contains(GateW3RiskPreflightFindingCode.ORDER_PREVIEW_BLOCKED));
    }

    @Test
    void shouldMarkMissingPreviewNotEvaluated() {
        GateW3RiskPreflightResult result = service().evaluate(request(null, cleanReconciliation(), healthyFacts()));

        assertEquals(GateW3RiskPreflightStatus.NOT_EVALUATED, result.structuralStatus());
        assertEquals(GateW3RiskPreflightStatus.NOT_EVALUATED, result.venueFactStatus());
        assertEquals(GateW3RiskPreflightStatus.NOT_EVALUATED, result.pureRiskStatus());
        assertTrue(result.blockers().contains(GateW3RiskPreflightFindingCode.ORDER_PREVIEW_NOT_EVALUATED));
    }

    @Test
    void shouldPreservePreviewNotEvaluatedStatus() {
        GateW3RiskPreflightResult result = service().evaluate(request(
                preview(OrderPreviewStatus.PASS, OrderPreviewStatus.NOT_EVALUATED),
                cleanReconciliation(),
                healthyFacts()
        ));

        assertEquals(GateW3RiskPreflightStatus.NOT_EVALUATED, result.venueFactStatus());
        assertEquals(GateW3RiskPreflightStatus.NOT_EVALUATED, result.pureRiskStatus());
        assertTrue(result.blockers().contains(GateW3RiskPreflightFindingCode.ORDER_PREVIEW_NOT_EVALUATED));
    }

    @Test
    void shouldBlockReconciliationMismatch() {
        ReconciliationResult reconciliation = reconciliation(
                List.of(),
                List.of(finding(ReconciliationTaxonomy.STATUS_MISMATCH)),
                executionBlocker(),
                List.of(),
                List.of(),
                List.of(),
                "SNAPSHOT_DIFFERENCES_OR_UNKNOWNS_PRESENT",
                EVALUATION_TIME
        );

        GateW3RiskPreflightResult result = service().evaluate(request(
                preview(OrderPreviewStatus.PASS, OrderPreviewStatus.PASS),
                reconciliation,
                healthyFacts()
        ));

        assertEquals(GateW3RiskPreflightStatus.BLOCKED, result.reconciliationStatus());
        assertTrue(result.blockers().contains(GateW3RiskPreflightFindingCode.RECONCILIATION_BLOCKED));
        assertTrue(result.blockers().contains(GateW3RiskPreflightFindingCode.RECONCILIATION_MISMATCH));
    }

    @Test
    void shouldBlockStaleReconciliationWarning() {
        ReconciliationResult reconciliation = reconciliation(
                List.of(),
                List.of(),
                executionBlocker(),
                List.of(finding(ReconciliationTaxonomy.STALE_LOCAL_SNAPSHOT)),
                List.of(),
                List.of(),
                "SNAPSHOT_DIFFERENCES_OR_UNKNOWNS_PRESENT",
                EVALUATION_TIME
        );

        GateW3RiskPreflightResult result = service().evaluate(request(
                preview(OrderPreviewStatus.PASS, OrderPreviewStatus.PASS),
                reconciliation,
                healthyFacts()
        ));

        assertEquals(GateW3RiskPreflightStatus.BLOCKED, result.reconciliationStatus());
        assertTrue(result.blockers().contains(GateW3RiskPreflightFindingCode.RECONCILIATION_BLOCKED));
    }

    @Test
    void shouldBlockPartialReconciliation() {
        ReconciliationResult reconciliation = reconciliation(
                List.of(),
                List.of(),
                List.of(
                        finding(ReconciliationTaxonomy.EXECUTION_NOT_AUTHORIZED),
                        finding(ReconciliationTaxonomy.PARTIAL_REMOTE_SNAPSHOT)
                ),
                List.of(),
                List.of(),
                List.of(),
                "SNAPSHOT_DIFFERENCES_OR_UNKNOWNS_PRESENT",
                EVALUATION_TIME
        );

        GateW3RiskPreflightResult result = service().evaluate(request(
                preview(OrderPreviewStatus.PASS, OrderPreviewStatus.PASS),
                reconciliation,
                healthyFacts()
        ));

        assertEquals(GateW3RiskPreflightStatus.BLOCKED, result.reconciliationStatus());
    }

    @Test
    void shouldBlockFutureReconciliationResult() {
        ReconciliationResult future = reconciliation(
                List.of(), List.of(), executionBlocker(), List.of(), List.of(), List.of(),
                "SNAPSHOT_MATCHED_AT_EVALUATION_TIME", EVALUATION_TIME.plusSeconds(1)
        );

        GateW3RiskPreflightResult result = service().evaluate(request(
                preview(OrderPreviewStatus.PASS, OrderPreviewStatus.PASS),
                future,
                healthyFacts()
        ));

        assertEquals(GateW3RiskPreflightStatus.BLOCKED, result.reconciliationStatus());
    }

    @Test
    void shouldMarkMissingReconciliationNotEvaluated() {
        GateW3RiskPreflightResult result = service().evaluate(request(
                preview(OrderPreviewStatus.PASS, OrderPreviewStatus.PASS),
                null,
                healthyFacts()
        ));

        assertEquals(GateW3RiskPreflightStatus.NOT_EVALUATED, result.reconciliationStatus());
        assertTrue(result.notEvaluated().contains(GateW3RiskPreflightFindingCode.RECONCILIATION_NOT_EVALUATED));
    }

    @Test
    void shouldBlockMissingLocalAccount() {
        RiskPreflightFactBundle facts = facts(
                new LocalAccountMetadataSnapshot(false, null, null, null, null),
                credential(1, List.of("API_KEY")),
                Quality.OK
        );

        GateW3RiskPreflightResult result = service().evaluate(request(
                preview(OrderPreviewStatus.PASS, OrderPreviewStatus.PASS), cleanReconciliation(), facts
        ));

        assertEquals(GateW3RiskPreflightStatus.BLOCKED, result.localAccountStatus());
        assertTrue(result.blockers().contains(GateW3RiskPreflightFindingCode.LOCAL_ACCOUNT_UNCONFIGURED));
    }

    @Test
    void shouldBlockDisabledLocalAccount() {
        assertAccountBlock(
                new LocalAccountMetadataSnapshot(true, "OKX", "SPOT", "SIM", "DISABLED"),
                GateW3RiskPreflightFindingCode.LOCAL_ACCOUNT_DISABLED
        );
    }

    @Test
    void shouldBlockLocalAccountExchangeMismatch() {
        assertAccountBlock(
                new LocalAccountMetadataSnapshot(true, "OTHER", "SPOT", "SIM", "ACTIVE"),
                GateW3RiskPreflightFindingCode.LOCAL_ACCOUNT_SCOPE_MISMATCH
        );
    }

    @Test
    void shouldBlockLocalAccountMarketTypeMismatch() {
        assertAccountBlock(
                new LocalAccountMetadataSnapshot(true, "OKX", "SWAP", "SIM", "ACTIVE"),
                GateW3RiskPreflightFindingCode.LOCAL_ACCOUNT_SCOPE_MISMATCH
        );
    }

    @Test
    void shouldBlockLocalAccountEnvironmentMismatch() {
        assertAccountBlock(
                new LocalAccountMetadataSnapshot(true, "OKX", "SPOT", "LIVE", "ACTIVE"),
                GateW3RiskPreflightFindingCode.LOCAL_ACCOUNT_SCOPE_MISMATCH
        );
    }

    @Test
    void shouldBlockMissingCredentialMetadata() {
        RiskPreflightFactBundle facts = facts(
                activeAccount(),
                new CredentialMetadataSummary(false, 0, List.of(), List.of(), List.of()),
                Quality.OK
        );
        GateW3RiskPreflightResult result = service().evaluate(request(
                preview(OrderPreviewStatus.PASS, OrderPreviewStatus.PASS), cleanReconciliation(), facts
        ));

        assertEquals(GateW3RiskPreflightStatus.BLOCKED, result.credentialMetadataStatus());
        assertTrue(result.blockers().contains(GateW3RiskPreflightFindingCode.CREDENTIAL_METADATA_UNCONFIGURED));
    }

    @Test
    void shouldBlockDuplicateActiveCredentialMetadata() {
        RiskPreflightFactBundle facts = facts(
                activeAccount(),
                credential(2, List.of("API_KEY", "API_KEY")),
                Quality.OK
        );
        GateW3RiskPreflightResult result = service().evaluate(request(
                preview(OrderPreviewStatus.PASS, OrderPreviewStatus.PASS), cleanReconciliation(), facts
        ));

        assertEquals(GateW3RiskPreflightStatus.BLOCKED, result.credentialMetadataStatus());
        assertTrue(result.blockers().contains(GateW3RiskPreflightFindingCode.CREDENTIAL_METADATA_CONFLICT));
    }

    @Test
    void shouldAllowDistinctActiveCredentialTypesAsLocalMetadata() {
        RiskPreflightFactBundle facts = facts(
                activeAccount(),
                credential(2, List.of("API_KEY", "READ_ONLY_KEY")),
                Quality.OK
        );
        GateW3RiskPreflightResult result = service().evaluate(request(
                preview(OrderPreviewStatus.PASS, OrderPreviewStatus.PASS), cleanReconciliation(), facts
        ));

        assertEquals(GateW3RiskPreflightStatus.PASS, result.credentialMetadataStatus());
        assertFalse(result.blockers().contains(GateW3RiskPreflightFindingCode.CREDENTIAL_METADATA_CONFLICT));
    }

    @Test
    void shouldKeepMarketdataWarningDiagnosticOnly() {
        GateW3RiskPreflightResult result = evaluateWithMarketdata(Quality.WARNING);

        assertEquals(GateW3RiskPreflightStatus.UNKNOWN, result.marketdataQualityStatus());
        assertTrue(result.warnings().contains(GateW3RiskPreflightFindingCode.MARKETDATA_QUALITY_NOT_OK));
        assertEquals(GateW3RiskPreflightStatus.BLOCKED, result.executionReadiness());
    }

    @Test
    void shouldBlockMarketdataBlockedStatus() {
        GateW3RiskPreflightResult result = evaluateWithMarketdata(Quality.BLOCKED);

        assertEquals(GateW3RiskPreflightStatus.BLOCKED, result.marketdataQualityStatus());
        assertTrue(result.blockers().contains(GateW3RiskPreflightFindingCode.MARKETDATA_QUALITY_NOT_OK));
    }

    @Test
    void shouldKeepUnknownMarketdataOutOfPass() {
        GateW3RiskPreflightResult result = evaluateWithMarketdata(Quality.UNKNOWN);

        assertEquals(GateW3RiskPreflightStatus.UNKNOWN, result.marketdataQualityStatus());
        assertTrue(result.unknowns().contains(GateW3RiskPreflightFindingCode.MARKETDATA_QUALITY_NOT_OK));
        assertEquals(GateW3RiskPreflightStatus.BLOCKED, result.executionReadiness());
    }

    @Test
    void shouldReturnDeterministicResultForRepeatedEvaluation() {
        GateW3RiskPreflightRequest request = request(
                preview(OrderPreviewStatus.PASS, OrderPreviewStatus.PASS),
                cleanReconciliation(),
                healthyFacts()
        );

        assertEquals(service().evaluate(request), service().evaluate(request));
    }

    @Test
    void shouldRejectFutureEvaluationTimeUsingInjectedClock() {
        GateW3RiskPreflightRequest future = new GateW3RiskPreflightRequest(
                "trace-risk-preflight",
                EVALUATION_TIME.plusSeconds(1),
                "SIM",
                preview(OrderPreviewStatus.PASS, OrderPreviewStatus.PASS),
                cleanReconciliation(),
                healthyFacts()
        );

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service().evaluate(future));
        assertEquals("evaluationTime must not be in the future", error.getMessage());
    }

    @Test
    void shouldExposeImmutableResultCollections() {
        GateW3RiskPreflightResult result = evaluateHealthy();

        assertThrows(UnsupportedOperationException.class,
                () -> result.blockers().add(GateW3RiskPreflightFindingCode.ORDER_PREVIEW_BLOCKED));
        assertThrows(UnsupportedOperationException.class,
                () -> result.unknowns().add(GateW3RiskPreflightFindingCode.MARKETDATA_QUALITY_NOT_OK));
        assertThrows(UnsupportedOperationException.class,
                () -> result.notEvaluated().add(GateW3RiskPreflightFindingCode.RECONCILIATION_NOT_EVALUATED));
    }

    @Test
    void shouldCopyCredentialMetadataCollections() {
        java.util.ArrayList<String> types = new java.util.ArrayList<>(List.of("API_KEY"));
        CredentialMetadataSummary summary = credential(1, types);
        types.add("MUTATED");

        assertEquals(List.of("API_KEY"), summary.credentialTypes());
        assertThrows(UnsupportedOperationException.class, () -> summary.credentialTypes().add("MUTATED"));
    }

    @Test
    void shouldKeepFindingGroupsDisjoint() {
        GateW3RiskPreflightResult result = evaluateWithMarketdata(Quality.WARNING);
        Set<GateW3RiskPreflightFindingCode> all = new java.util.HashSet<>();

        assertTrue(result.blockers().stream().allMatch(all::add));
        assertTrue(result.warnings().stream().allMatch(all::add));
        assertTrue(result.unknowns().stream().allMatch(all::add));
        assertTrue(result.notEvaluated().stream().allMatch(all::add));
    }

    @Test
    void shouldHaveOnlyClockAsRuntimeDependency() {
        List<? extends Class<?>> fieldTypes = Arrays.stream(GateW3RiskPreflightService.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(field -> (Class<?>) field.getType())
                .toList();

        assertEquals(List.of(Clock.class), fieldTypes);
    }

    @Test
    void shouldNotReferenceForbiddenRiskOrOrderChainTypes() throws IOException {
        String classBytes = classBytes(GateW3RiskPreflightService.class);
        List<String> forbidden = List.of(
                "PlaceOrderCommand",
                "PreTradeRiskService",
                "RiskRuleRegistry",
                "DuplicateRequestRule",
                "RateLimitRule",
                "KillSwitchRiskRule",
                "OrderCommandService"
        );

        forbidden.forEach(type -> assertFalse(classBytes.contains(type), type));
    }

    @Test
    void shouldNotReferenceNetworkDatabaseOrWritePorts() throws IOException {
        String classBytes = classBytes(GateW3RiskPreflightService.class);
        List<String> forbidden = List.of(
                "java/net/",
                "Jdbc",
                "Repository",
                "HttpClient",
                "CredentialMaterial",
                "AuditLog",
                "Ledger",
                "EventPublisher"
        );

        forbidden.forEach(type -> assertFalse(classBytes.contains(type), type));
    }

    @Test
    void shouldNotExposeCredentialMaterialFields() {
        Set<String> componentNames = Arrays.stream(CredentialMetadataSummary.class.getRecordComponents())
                .map(component -> component.getName().toLowerCase())
                .collect(Collectors.toSet());

        List.of("apikey", "secret", "passphrase", "token", "privatekey", "payload", "header")
                .forEach(forbidden -> assertFalse(componentNames.contains(forbidden), forbidden));
    }

    private void assertAccountBlock(
            LocalAccountMetadataSnapshot account,
            GateW3RiskPreflightFindingCode expected
    ) {
        GateW3RiskPreflightResult result = service().evaluate(request(
                preview(OrderPreviewStatus.PASS, OrderPreviewStatus.PASS),
                cleanReconciliation(),
                facts(account, credential(1, List.of("API_KEY")), Quality.OK)
        ));

        assertEquals(GateW3RiskPreflightStatus.BLOCKED, result.localAccountStatus());
        assertTrue(result.blockers().contains(expected));
    }

    private GateW3RiskPreflightResult evaluateHealthy() {
        return service().evaluate(request(
                preview(OrderPreviewStatus.PASS, OrderPreviewStatus.PASS),
                cleanReconciliation(),
                healthyFacts()
        ));
    }

    private GateW3RiskPreflightResult evaluateWithMarketdata(Quality quality) {
        return service().evaluate(request(
                preview(OrderPreviewStatus.PASS, OrderPreviewStatus.PASS),
                cleanReconciliation(),
                facts(activeAccount(), credential(1, List.of("API_KEY")), quality)
        ));
    }

    private GateW3RiskPreflightService service() {
        return new GateW3RiskPreflightService(CLOCK);
    }

    private GateW3RiskPreflightRequest request(
            DryRunOrderPreviewResult preview,
            ReconciliationResult reconciliation,
            RiskPreflightFactBundle facts
    ) {
        return new GateW3RiskPreflightRequest(
                "trace-risk-preflight",
                EVALUATION_TIME,
                "SIM",
                preview,
                reconciliation,
                facts
        );
    }

    private RiskPreflightFactBundle healthyFacts() {
        return facts(activeAccount(), credential(1, List.of("API_KEY")), Quality.OK);
    }

    private RiskPreflightFactBundle facts(
            LocalAccountMetadataSnapshot account,
            CredentialMetadataSummary credential,
            Quality quality
    ) {
        return new RiskPreflightFactBundle(account, credential, new MarketdataQualitySnapshot(quality));
    }

    private LocalAccountMetadataSnapshot activeAccount() {
        return new LocalAccountMetadataSnapshot(true, "OKX", "SPOT", "SIM", "ACTIVE");
    }

    private CredentialMetadataSummary credential(int count, List<String> types) {
        return new CredentialMetadataSummary(
                count > 0,
                count,
                types,
                count > 0 ? List.of("STRUCTURALLY_VALID") : List.of(),
                count > 0 ? List.of("NOT_PROBED") : List.of()
        );
    }

    private DryRunOrderPreviewResult preview(
            OrderPreviewStatus structuralStatus,
            OrderPreviewStatus venueStatus
    ) {
        return new DryRunOrderPreviewResult(
                structuralStatus,
                venueStatus,
                OrderPreviewStatus.NOT_EVALUATED,
                OrderPreviewStatus.UNKNOWN,
                OrderPreviewStatus.BLOCKED,
                true,
                true,
                false,
                new java.math.BigDecimal("100"),
                List.of(OrderPreviewFindingCode.EXECUTION_NOT_AUTHORIZED),
                List.of(),
                List.of(OrderPreviewFindingCode.MIN_NOTIONAL_UNKNOWN, OrderPreviewFindingCode.FEE_UNKNOWN),
                List.of(
                        OrderPreviewFindingCode.BALANCE_NOT_EVALUATED,
                        OrderPreviewFindingCode.RISK_PIPELINE_NOT_EVALUATED
                )
        );
    }

    private ReconciliationResult cleanReconciliation() {
        return reconciliation(
                List.of(),
                List.of(),
                executionBlocker(),
                List.of(),
                List.of(),
                List.of(),
                "SNAPSHOT_MATCHED_AT_EVALUATION_TIME",
                EVALUATION_TIME
        );
    }

    private List<ReconciliationFinding> executionBlocker() {
        return List.of(finding(ReconciliationTaxonomy.EXECUTION_NOT_AUTHORIZED));
    }

    private ReconciliationResult reconciliation(
            List<ReconciliationFinding> matches,
            List<ReconciliationFinding> differences,
            List<ReconciliationFinding> blockers,
            List<ReconciliationFinding> warnings,
            List<ReconciliationFinding> unknowns,
            List<ReconciliationFinding> notEvaluated,
            String assessment,
            Instant evaluatedAt
    ) {
        return new ReconciliationResult(
                matches,
                differences,
                blockers,
                warnings,
                unknowns,
                notEvaluated,
                evaluatedAt,
                assessment
        );
    }

    private ReconciliationFinding finding(ReconciliationTaxonomy taxonomy) {
        return new ReconciliationFinding(taxonomy, null, null, null, taxonomy.name());
    }

    private String classBytes(Class<?> type) throws IOException {
        String resourceName = "/" + type.getName().replace('.', '/') + ".class";
        try (InputStream input = type.getResourceAsStream(resourceName)) {
            if (input == null) {
                throw new IOException("class resource not found");
            }
            return new String(input.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }
}
