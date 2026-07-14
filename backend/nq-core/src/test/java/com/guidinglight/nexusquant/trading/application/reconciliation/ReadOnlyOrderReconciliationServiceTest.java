package com.guidinglight.nexusquant.trading.application.reconciliation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ReadOnlyOrderReconciliationServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-14T08:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void matchesCanonicalDecimalsAndKeepsExecutionBlockedDeterministically() {
        LocalOrderSnapshot local = local("1.0", "2.00", "0.50", "ACCEPTED", NOW.minusSeconds(30));
        RemoteOrderSnapshot remote = remote("1.00", "2", "0.500", "live", NOW.minusSeconds(20));
        ReadOnlyOrderReconciliationService service = service(List.of(local), batch(List.of(remote)));

        ReconciliationResult first = service.reconcile(request());
        ReconciliationResult second = service.reconcile(request());

        assertEquals(first, second);
        assertEquals(List.of(ReconciliationTaxonomy.MATCHED), taxonomies(first.matches()));
        assertEquals("SNAPSHOT_MATCHED_AT_EVALUATION_TIME", first.snapshotAssessment());
        assertTrue(first.diagnosticOnly());
        assertTrue(first.readOnly());
        assertTrue(first.noSideEffect());
        assertFalse(first.repairPerformed());
        assertFalse(first.orderSubmitted());
        assertEquals("BLOCKED", first.executionReadiness());
        assertEquals(List.of(ReconciliationTaxonomy.EXECUTION_NOT_AUTHORIZED), taxonomies(first.blockers()));
    }

    @Test
    void reportsLocalOnlyRemoteOnlyAndAllFieldMismatches() {
        LocalOrderSnapshot localOnly = localWithIds("local-only", "client-only", "exchange-only", "1", "2", "0", "FILLED", NOW);
        RemoteOrderSnapshot remoteOnly = remoteWithIds("client-remote", "exchange-remote", "1", "2", "0", "filled", NOW);
        LocalOrderSnapshot mismatch = local("1", "2", "0", "FILLED", NOW);
        RemoteOrderSnapshot remoteMismatch = remote("3", "4", "1", "canceled", NOW);

        ReconciliationResult result = service(
                List.of(localOnly, mismatch),
                batch(List.of(remoteOnly, remoteMismatch))
        ).reconcile(request());

        assertTrue(taxonomies(result.differences()).containsAll(List.of(
                ReconciliationTaxonomy.LOCAL_ONLY,
                ReconciliationTaxonomy.REMOTE_ONLY,
                ReconciliationTaxonomy.STATUS_MISMATCH,
                ReconciliationTaxonomy.PRICE_MISMATCH,
                ReconciliationTaxonomy.QUANTITY_MISMATCH,
                ReconciliationTaxonomy.FILLED_QUANTITY_MISMATCH
        )));
    }

    @Test
    void failsClosedForDuplicateAndMissingIdentityAndUnknownStatus() {
        LocalOrderSnapshot duplicateA = localWithIds("local-a", "dup-client", null, "1", "2", "0", "ACCEPTED", NOW);
        LocalOrderSnapshot duplicateB = localWithIds("local-b", "dup-client", null, "1", "2", "0", "ACCEPTED", NOW);
        LocalOrderSnapshot missingLocal = localWithIds("missing", null, null, "1", "2", "0", "ACCEPTED", NOW);
        RemoteOrderSnapshot duplicateRemoteA = remoteWithIds("remote-a", "dup-exchange", "1", "2", "0", "live", NOW);
        RemoteOrderSnapshot duplicateRemoteB = remoteWithIds("remote-b", "dup-exchange", "1", "2", "0", "live", NOW);
        RemoteOrderSnapshot missingRemote = remoteWithIds(null, null, "1", "2", "0", "live", NOW);
        RemoteOrderSnapshot unknown = remote("1", "2", "0", "provider_new_state", NOW);

        ReconciliationResult result = service(
                List.of(duplicateA, duplicateB, missingLocal, local("1", "2", "0", "ACCEPTED", NOW)),
                batch(List.of(duplicateRemoteA, duplicateRemoteB, missingRemote, unknown))
        ).reconcile(request());

        assertTrue(taxonomies(result.blockers()).contains(ReconciliationTaxonomy.DUPLICATE_LOCAL_ID));
        assertTrue(taxonomies(result.blockers()).contains(ReconciliationTaxonomy.DUPLICATE_REMOTE_ID));
        assertTrue(taxonomies(result.unknowns()).contains(ReconciliationTaxonomy.UNMATCHED_IDENTITY));
        assertTrue(taxonomies(result.unknowns()).contains(ReconciliationTaxonomy.UNMAPPABLE_REMOTE_STATUS));
    }

    @Test
    void neverFallsBackToClientIdWhenExchangeOrderIdsConflict() {
        LocalOrderSnapshot local = localWithIds(
                "local-1", "same-client", "local-exchange", "1", "2", "0", "ACCEPTED", NOW
        );
        RemoteOrderSnapshot remote = remoteWithIds(
                "same-client", "remote-exchange", "1", "2", "0", "live", NOW
        );

        ReconciliationResult result = service(List.of(local), batch(List.of(remote))).reconcile(request());

        assertTrue(taxonomies(result.differences()).contains(ReconciliationTaxonomy.LOCAL_ONLY));
        assertTrue(taxonomies(result.differences()).contains(ReconciliationTaxonomy.REMOTE_ONLY));
        assertFalse(taxonomies(result.matches()).contains(ReconciliationTaxonomy.MATCHED));
    }

    @Test
    void flagsStaleFuturePartialAndPermissionUnknownWithoutEvaluation() {
        ReconciliationResult stale = service(
                List.of(local("1", "2", "0", "ACCEPTED", NOW.minusSeconds(301))),
                new RemoteSnapshotBatch(
                        List.of(remote("1", "2", "0", "live", NOW.plusSeconds(1))),
                        true, false, 2, 301, NOW.plusSeconds(1)
                )
        ).reconcile(request());
        assertTrue(taxonomies(stale.warnings()).contains(ReconciliationTaxonomy.STALE_LOCAL_SNAPSHOT));
        assertTrue(taxonomies(stale.blockers()).contains(ReconciliationTaxonomy.STALE_REMOTE_SNAPSHOT));
        assertTrue(taxonomies(stale.blockers()).contains(ReconciliationTaxonomy.PARTIAL_REMOTE_SNAPSHOT));

        ReconciliationResult permissionUnknown = service(
                List.of(), new RemoteSnapshotBatch(List.of(), false, false, 0, 0, NOW)
        ).reconcile(request());
        assertEquals(List.of(ReconciliationTaxonomy.REMOTE_PERMISSION_UNKNOWN), taxonomies(permissionUnknown.unknowns()));
        assertEquals(List.of(ReconciliationTaxonomy.REMOTE_NOT_EVALUATED), taxonomies(permissionUnknown.notEvaluated()));
    }

    @Test
    void enforcesOkxSpotSymbolsPageRecordTimeAndEnvironmentBoundsBeforePortsRun() {
        assertThrows(IllegalArgumentException.class, () -> request("BINANCE", "SPOT", "SIM", List.of("BTC-USDT"), 1, 100, 24));
        assertThrows(IllegalArgumentException.class, () -> request("OKX", "SWAP", "SIM", List.of("BTC-USDT"), 1, 100, 24));
        assertThrows(IllegalArgumentException.class, () -> request("OKX", "SPOT", "PAPER", List.of("BTC-USDT"), 1, 100, 24));
        assertThrows(IllegalArgumentException.class, () -> request("OKX", "SPOT", "SIM",
                List.of("BTC-USDT", "ETH-USDT", "SOL-USDT", "DOGE-USDT"), 1, 100, 24));
        assertThrows(IllegalArgumentException.class, () -> request("OKX", "SPOT", "SIM", List.of("BTC-USDT"), 2, 100, 24));
        assertThrows(IllegalArgumentException.class, () -> request("OKX", "SPOT", "SIM", List.of("BTC-USDT"), 1, 101, 24));
        assertThrows(IllegalArgumentException.class, () -> request("OKX", "SPOT", "SIM", List.of("BTC-USDT"), 1, 100, 25));

        AtomicInteger reads = new AtomicInteger();
        ReadOnlyOrderReconciliationService service = new ReadOnlyOrderReconciliationService(
                ignored -> { reads.incrementAndGet(); return List.of(); },
                ignored -> { reads.incrementAndGet(); return batch(List.of()); }, CLOCK
        );
        ReconciliationRequest future = new ReconciliationRequest(
                1, 1, 1, "OKX", "SPOT", "SIM", List.of("BTC-USDT"), NOW, NOW.plusSeconds(1), 1, 100
        );
        assertThrows(IllegalArgumentException.class, () -> service.reconcile(future));
        assertEquals(0, reads.get());
    }

    private static ReadOnlyOrderReconciliationService service(
            List<LocalOrderSnapshot> local,
            RemoteSnapshotBatch remote
    ) {
        return new ReadOnlyOrderReconciliationService(ignored -> local, ignored -> remote, CLOCK);
    }

    private static ReconciliationRequest request() {
        return request("OKX", "SPOT", "SIM", List.of("BTC-USDT"), 1, 100, 1);
    }

    private static ReconciliationRequest request(
            String exchange,
            String instrumentType,
            String environment,
            List<String> symbols,
            int pages,
            int records,
            int hours
    ) {
        return new ReconciliationRequest(
                1, 1, 1, exchange, instrumentType, environment, symbols,
                NOW.minusSeconds(hours * 3600L), NOW, pages, records
        );
    }

    private static LocalOrderSnapshot local(String price, String quantity, String filled, String status, Instant updatedAt) {
        return localWithIds("local-1", "client-1", "exchange-1", price, quantity, filled, status, updatedAt);
    }

    private static LocalOrderSnapshot localWithIds(
            String localId, String clientId, String exchangeId, String price, String quantity,
            String filled, String status, Instant updatedAt
    ) {
        return new LocalOrderSnapshot(
                localId, clientId, exchangeId, "BTC-USDT", "BUY", "LIMIT",
                decimal(price), decimal(quantity), decimal(filled), status, updatedAt
        );
    }

    private static RemoteOrderSnapshot remote(
            String price, String quantity, String filled, String status, Instant observedAt
    ) {
        return remoteWithIds("client-1", "exchange-1", price, quantity, filled, status, observedAt);
    }

    private static RemoteOrderSnapshot remoteWithIds(
            String clientId, String exchangeId, String price, String quantity,
            String filled, String status, Instant observedAt
    ) {
        return new RemoteOrderSnapshot(
                exchangeId, clientId, "BTC-USDT", "buy", "limit",
                decimal(price), decimal(quantity), decimal(filled), status, observedAt,
                "OKX_SPOT_ORDER_HISTORY_READ"
        );
    }

    private static RemoteSnapshotBatch batch(List<RemoteOrderSnapshot> orders) {
        return new RemoteSnapshotBatch(orders, true, true, 1, orders.size(), NOW.minusSeconds(10));
    }

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private static List<ReconciliationTaxonomy> taxonomies(List<ReconciliationFinding> findings) {
        return findings.stream().map(ReconciliationFinding::taxonomy).toList();
    }
}
