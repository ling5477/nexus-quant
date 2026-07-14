package com.guidinglight.nexusquant.trading.application.orderpreview;

import com.guidinglight.nexusquant.marketdata.domain.instrument.InstrumentCatalogItem;
import com.guidinglight.nexusquant.marketdata.domain.instrument.OkxVenueRuleContract;
import com.guidinglight.nexusquant.marketdata.domain.instrument.VenueRuleChecksumCalculator;
import com.guidinglight.nexusquant.marketdata.domain.instrument.VenueRuleFreshnessEvaluator;
import com.guidinglight.nexusquant.marketdata.domain.instrument.port.InstrumentCatalogReadPort;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

import static com.guidinglight.nexusquant.trading.application.orderpreview.DryRunOrderPreviewRequest.OrderType.LIMIT;
import static com.guidinglight.nexusquant.trading.application.orderpreview.DryRunOrderPreviewRequest.Side.BUY;
import static com.guidinglight.nexusquant.trading.application.orderpreview.DryRunOrderPreviewRequest.Side.SELL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DryRunOrderPreviewServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-14T03:00:00Z");
    private static final BigDecimal PRICE = new BigDecimal("50000.0");
    private static final BigDecimal QUANTITY = new BigDecimal("0.0100");

    @Test
    void alignedLimitBuyShouldPassStructuralAndVenueChecks() {
        DryRunOrderPreviewResult result = service(validItem()).preview(request(BUY, LIMIT, PRICE, QUANTITY));

        assertEquals(OrderPreviewStatus.PASS, result.structuralStatus());
        assertEquals(OrderPreviewStatus.PASS, result.venueFactStatus());
        assertEquals(new BigDecimal("500.00000"), result.grossNotional());
    }

    @Test
    void alignedLimitSellShouldPassStructuralAndVenueChecks() {
        DryRunOrderPreviewResult result = service(validItem()).preview(request(SELL, LIMIT, PRICE, QUANTITY));

        assertEquals(OrderPreviewStatus.PASS, result.structuralStatus());
        assertEquals(OrderPreviewStatus.PASS, result.venueFactStatus());
    }

    @Test
    void marketShouldBeRejectedBeforeCatalogRead() {
        CountingReadPort port = new CountingReadPort(validItem());

        DryRunOrderPreviewResult result = service(port).preview(request(
                BUY,
                DryRunOrderPreviewRequest.OrderType.MARKET,
                PRICE,
                QUANTITY
        ));

        assertBlocked(result, OrderPreviewFindingCode.ORDER_TYPE_NOT_SUPPORTED);
        assertEquals(0, port.readCount);
    }

    @Test
    void nonOkxExchangeShouldBeRejected() {
        DryRunOrderPreviewRequest request = new DryRunOrderPreviewRequest(
                "BINANCE", "BTC-USDT", BUY, LIMIT, QUANTITY, PRICE, NOW, "trace-1"
        );

        assertBlocked(service(validItem()).preview(request), OrderPreviewFindingCode.EXCHANGE_NOT_SUPPORTED);
    }

    @Test
    void nonSpotInstrumentShouldBeRejected() {
        assertVenueBlocked(
                service(item("LIVE", "SWAP", NOW.minusSeconds(30), completeFacts()))
                        .preview(request(BUY, LIMIT, PRICE, QUANTITY)),
                OrderPreviewFindingCode.INSTRUMENT_TYPE_NOT_SUPPORTED
        );
    }

    @Test
    void missingInstrumentShouldBeRejected() {
        assertVenueBlocked(
                service(new CountingReadPort()).preview(request(BUY, LIMIT, PRICE, QUANTITY)),
                OrderPreviewFindingCode.INSTRUMENT_NOT_FOUND
        );
    }

    @Test
    void nonLiveInstrumentShouldBeRejected() {
        assertVenueBlocked(
                service(item("SUSPEND", "SPOT", NOW.minusSeconds(30), completeFacts()))
                        .preview(request(BUY, LIMIT, PRICE, QUANTITY)),
                OrderPreviewFindingCode.INSTRUMENT_NOT_LIVE
        );
    }

    @Test
    void incompleteFactsShouldFailClosed() {
        InstrumentCatalogItem incomplete = item("LIVE", "SPOT", NOW.minusSeconds(30), incompleteFacts());

        DryRunOrderPreviewResult result = service(incomplete).preview(request(BUY, LIMIT, PRICE, QUANTITY));

        assertVenueBlocked(result, OrderPreviewFindingCode.VENUE_RULE_FACTS_MISSING);
        assertTrue(result.unknowns().contains(OrderPreviewFindingCode.MAX_LIMIT_QUANTITY_UNKNOWN));
        assertTrue(result.unknowns().contains(OrderPreviewFindingCode.MAX_LIMIT_NOTIONAL_UNKNOWN));
    }

    @Test
    void staleFactsShouldFailClosed() {
        InstrumentCatalogItem stale = item("LIVE", "SPOT", NOW.minusSeconds(601), completeFacts());

        assertVenueBlocked(
                service(stale).preview(request(BUY, LIMIT, PRICE, QUANTITY)),
                OrderPreviewFindingCode.VENUE_RULE_FACTS_STALE
        );
    }

    @Test
    void missingSchemaShouldFailClosed() {
        InstrumentCatalogItem item = completeItem(null, null);

        assertVenueBlocked(
                service(item).preview(request(BUY, LIMIT, PRICE, QUANTITY)),
                OrderPreviewFindingCode.VENUE_RULE_SCHEMA_UNSUPPORTED
        );
    }

    @Test
    void unsupportedSchemaShouldFailClosed() {
        InstrumentCatalogItem item = completeItem("NQ_OKX_VENUE_RULE_FACTS_V2", null);

        assertVenueBlocked(
                service(item).preview(request(BUY, LIMIT, PRICE, QUANTITY)),
                OrderPreviewFindingCode.VENUE_RULE_SCHEMA_UNSUPPORTED
        );
    }

    @Test
    void missingChecksumShouldFailClosed() {
        InstrumentCatalogItem item = completeItem(OkxVenueRuleContract.SOURCE_SCHEMA_VERSION, null);

        assertVenueBlocked(
                service(item).preview(request(BUY, LIMIT, PRICE, QUANTITY)),
                OrderPreviewFindingCode.VENUE_RULE_CHECKSUM_INVALID
        );
    }

    @Test
    void checksumMismatchShouldFailClosed() {
        InstrumentCatalogItem item = completeItem(OkxVenueRuleContract.SOURCE_SCHEMA_VERSION, "0".repeat(64));

        assertVenueBlocked(
                service(item).preview(request(BUY, LIMIT, PRICE, QUANTITY)),
                OrderPreviewFindingCode.VENUE_RULE_CHECKSUM_INVALID
        );
    }

    @Test
    void futureObservedAtShouldFailClosed() {
        InstrumentCatalogItem future = item("LIVE", "SPOT", NOW.plusSeconds(1), completeFacts());

        assertVenueBlocked(
                service(future).preview(request(BUY, LIMIT, PRICE, QUANTITY)),
                OrderPreviewFindingCode.VENUE_RULE_FACTS_STALE
        );
    }

    @Test
    void zeroOrNegativePriceShouldBeInvalid() {
        assertBlocked(
                service(validItem()).preview(request(BUY, LIMIT, BigDecimal.ZERO, QUANTITY)),
                OrderPreviewFindingCode.INVALID_PRICE
        );
        assertBlocked(
                service(validItem()).preview(request(BUY, LIMIT, new BigDecimal("-1"), QUANTITY)),
                OrderPreviewFindingCode.INVALID_PRICE
        );
    }

    @Test
    void zeroOrNegativeQuantityShouldBeInvalid() {
        assertBlocked(
                service(validItem()).preview(request(BUY, LIMIT, PRICE, BigDecimal.ZERO)),
                OrderPreviewFindingCode.INVALID_QUANTITY
        );
        assertBlocked(
                service(validItem()).preview(request(BUY, LIMIT, PRICE, new BigDecimal("-1"))),
                OrderPreviewFindingCode.INVALID_QUANTITY
        );
    }

    @Test
    void tickMismatchShouldBeRejectedWithoutRounding() {
        DryRunOrderPreviewResult result = service(validItem()).preview(
                request(BUY, LIMIT, new BigDecimal("50000.05"), QUANTITY)
        );

        assertVenueBlocked(result, OrderPreviewFindingCode.INVALID_TICK_ALIGNMENT);
        assertEquals(new BigDecimal("500.000500"), result.grossNotional());
    }

    @Test
    void stepMismatchShouldBeRejectedWithoutRounding() {
        assertVenueBlocked(
                service(validItem()).preview(request(BUY, LIMIT, PRICE, new BigDecimal("0.00105"))),
                OrderPreviewFindingCode.INVALID_STEP_ALIGNMENT
        );
    }

    @Test
    void belowMinimumQuantityShouldBeRejected() {
        assertVenueBlocked(
                service(validItem()).preview(request(BUY, LIMIT, PRICE, new BigDecimal("0.0005"))),
                OrderPreviewFindingCode.BELOW_MIN_QUANTITY
        );
    }

    @Test
    void aboveMaximumLimitQuantityShouldBeRejected() {
        assertVenueBlocked(
                service(validItem()).preview(request(BUY, LIMIT, new BigDecimal("1.0"), new BigDecimal("100.0001"))),
                OrderPreviewFindingCode.ABOVE_MAX_LIMIT_QUANTITY
        );
    }

    @Test
    void aboveMaximumLimitNotionalShouldBeRejected() {
        assertVenueBlocked(
                service(validItem()).preview(request(BUY, LIMIT, new BigDecimal("100000.0"), new BigDecimal("20.0000"))),
                OrderPreviewFindingCode.ABOVE_MAX_LIMIT_NOTIONAL
        );
    }

    @Test
    void nullOptionalMaximumFactsShouldRemainExplicit() {
        DryRunOrderPreviewResult result = service(
                item("LIVE", "SPOT", NOW.minusSeconds(30), incompleteFacts())
        ).preview(request(BUY, LIMIT, PRICE, QUANTITY));

        assertTrue(result.unknowns().contains(OrderPreviewFindingCode.MAX_LIMIT_QUANTITY_UNKNOWN));
        assertTrue(result.unknowns().contains(OrderPreviewFindingCode.MAX_LIMIT_NOTIONAL_UNKNOWN));
        assertNotEquals(OrderPreviewStatus.PASS, result.venueFactStatus());
    }

    @Test
    void minimumNotionalShouldRemainUnknown() {
        assertTrue(service(validItem()).preview(request(BUY, LIMIT, PRICE, QUANTITY)).unknowns()
                .contains(OrderPreviewFindingCode.MIN_NOTIONAL_UNKNOWN));
    }

    @Test
    void feeShouldRemainUnknown() {
        assertTrue(service(validItem()).preview(request(BUY, LIMIT, PRICE, QUANTITY)).unknowns()
                .contains(OrderPreviewFindingCode.FEE_UNKNOWN));
    }

    @Test
    void accountPermissionShouldRemainUnknown() {
        DryRunOrderPreviewResult result = service(validItem()).preview(request(BUY, LIMIT, PRICE, QUANTITY));

        assertEquals(OrderPreviewStatus.UNKNOWN, result.accountStatus());
        assertTrue(result.unknowns().contains(OrderPreviewFindingCode.ACCOUNT_PERMISSION_UNKNOWN));
    }

    @Test
    void balanceShouldRemainNotEvaluated() {
        assertTrue(service(validItem()).preview(request(BUY, LIMIT, PRICE, QUANTITY)).notEvaluated()
                .contains(OrderPreviewFindingCode.BALANCE_NOT_EVALUATED));
    }

    @Test
    void statefulRiskShouldRemainNotEvaluated() {
        DryRunOrderPreviewResult result = service(validItem()).preview(request(BUY, LIMIT, PRICE, QUANTITY));

        assertEquals(OrderPreviewStatus.NOT_EVALUATED, result.riskStatus());
        assertTrue(result.notEvaluated().contains(OrderPreviewFindingCode.RISK_PIPELINE_NOT_EVALUATED));
    }

    @Test
    void executionReadinessShouldAlwaysBeBlocked() {
        DryRunOrderPreviewResult result = service(validItem()).preview(request(BUY, LIMIT, PRICE, QUANTITY));

        assertEquals(OrderPreviewStatus.BLOCKED, result.executionReadiness());
        assertTrue(result.blockers().contains(OrderPreviewFindingCode.EXECUTION_NOT_AUTHORIZED));
    }

    @Test
    void safetyFlagsShouldProveNoOrderSideEffect() {
        CountingReadPort port = new CountingReadPort(validItem());

        DryRunOrderPreviewResult result = service(port).preview(request(BUY, LIMIT, PRICE, QUANTITY));

        assertTrue(result.diagnosticOnly());
        assertTrue(result.noSideEffect());
        assertFalse(result.orderSubmitted());
        assertEquals(1, port.readCount);
    }

    @Test
    void productionDependenciesShouldExcludeNetworkAndTradingAdapters() {
        List<String> dependencyTypes = Arrays.stream(DryRunOrderPreviewService.class.getDeclaredFields())
                .map(field -> field.getType().getName())
                .toList();

        assertTrue(dependencyTypes.contains(InstrumentCatalogReadPort.class.getName()));
        assertTrue(dependencyTypes.contains(VenueRuleFreshnessEvaluator.class.getName()));
        assertFalse(dependencyTypes.stream().anyMatch(name ->
                name.contains("TradingAdapter") || name.contains("Provider") || name.contains("Http")
        ));
    }

    @Test
    void productionDependenciesShouldExcludeCredentialAndAccountPorts() {
        assertFalse(Arrays.stream(DryRunOrderPreviewService.class.getDeclaredFields())
                .map(field -> field.getType().getName().toLowerCase())
                .anyMatch(name -> name.contains("credential") || name.contains("account") || name.contains("balance")));
    }

    @Test
    void resultContractShouldNotExposeOrderId() {
        assertFalse(Arrays.stream(DryRunOrderPreviewResult.class.getRecordComponents())
                .map(RecordComponent::getName)
                .anyMatch(name -> name.equalsIgnoreCase("orderId")));
    }

    @Test
    void repeatedEvaluationShouldBeDeterministic() {
        DryRunOrderPreviewService service = service(validItem());
        DryRunOrderPreviewRequest request = request(BUY, LIMIT, PRICE, QUANTITY);

        assertEquals(service.preview(request), service.preview(request));
    }

    @Test
    void trailingZerosShouldNotChangeAlignmentOrComparison() {
        DryRunOrderPreviewResult canonical = service(validItem()).preview(
                request(BUY, LIMIT, new BigDecimal("50000"), new BigDecimal("0.01"))
        );
        DryRunOrderPreviewResult trailing = service(validItem()).preview(
                request(BUY, LIMIT, new BigDecimal("50000.000"), new BigDecimal("0.0100"))
        );

        assertEquals(OrderPreviewStatus.PASS, canonical.venueFactStatus());
        assertEquals(OrderPreviewStatus.PASS, trailing.venueFactStatus());
        assertEquals(0, canonical.grossNotional().compareTo(trailing.grossNotional()));
    }

    @Test
    void localReadFailureShouldFailClosedWithoutLeakingException() {
        InstrumentCatalogReadPort failingPort = (exchangeCode, exchangeSymbols) -> {
            throw new IllegalStateException("do-not-expose");
        };

        assertVenueBlocked(
                service(failingPort).preview(request(BUY, LIMIT, PRICE, QUANTITY)),
                OrderPreviewFindingCode.LOCAL_FACT_READ_FAILED
        );
    }

    @Test
    void resultSafetyInvariantsShouldRejectMisleadingReadiness() {
        assertThrows(IllegalArgumentException.class, () -> new DryRunOrderPreviewResult(
                OrderPreviewStatus.PASS,
                OrderPreviewStatus.PASS,
                OrderPreviewStatus.NOT_EVALUATED,
                OrderPreviewStatus.UNKNOWN,
                OrderPreviewStatus.PASS,
                true,
                true,
                false,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of()
        ));
    }

    @Test
    void nullInputShouldFailClosedWithoutCatalogRead() {
        CountingReadPort port = new CountingReadPort(validItem());

        DryRunOrderPreviewResult result = service(port).preview(null);

        assertBlocked(result, OrderPreviewFindingCode.INPUT_REQUIRED);
        assertEquals(0, port.readCount);
        assertNull(result.grossNotional());
    }

    private static DryRunOrderPreviewService service(InstrumentCatalogItem item) {
        return service(new CountingReadPort(item));
    }

    private static DryRunOrderPreviewService service(InstrumentCatalogReadPort port) {
        return new DryRunOrderPreviewService(
                port,
                new VenueRuleFreshnessEvaluator(Clock.fixed(NOW, ZoneOffset.UTC), 600L)
        );
    }

    private static DryRunOrderPreviewRequest request(
            DryRunOrderPreviewRequest.Side side,
            DryRunOrderPreviewRequest.OrderType orderType,
            BigDecimal price,
            BigDecimal quantity
    ) {
        return new DryRunOrderPreviewRequest("OKX", "BTC-USDT", side, orderType, quantity, price, NOW, "trace-1");
    }

    private static InstrumentCatalogItem validItem() {
        return item("LIVE", "SPOT", NOW.minusSeconds(30), completeFacts());
    }

    private static InstrumentCatalogItem completeItem(String schema, String checksum) {
        Facts facts = completeFacts();
        return new InstrumentCatalogItem(
                1L, "OKX", "SPOT", "BTC-USDT", "BTC-USDT", "BTC", "USDT", "LIVE",
                facts.tickSize, facts.stepSize, facts.minQuantity, facts.maxLimitQuantity, facts.maxMarketSize,
                facts.maxMarketSizeUnit, facts.maxLimitNotionalUsd, facts.maxMarketNotionalUsd,
                OkxVenueRuleContract.SOURCE, schema,
                NOW.minusSeconds(30), NOW.minusSeconds(29), null, checksum, NOW.minusSeconds(29), NOW.minusSeconds(29)
        );
    }

    private static InstrumentCatalogItem item(
            String status,
            String instrumentType,
            Instant observedAt,
            Facts facts
    ) {
        Instant syncedAt = observedAt.plusSeconds(1);
        InstrumentCatalogItem raw = new InstrumentCatalogItem(
                1L, "OKX", instrumentType, "BTC-USDT", "BTC-USDT", "BTC", "USDT", status,
                facts.tickSize, facts.stepSize, facts.minQuantity, facts.maxLimitQuantity, facts.maxMarketSize,
                facts.maxMarketSizeUnit, facts.maxLimitNotionalUsd, facts.maxMarketNotionalUsd,
                OkxVenueRuleContract.SOURCE, OkxVenueRuleContract.SOURCE_SCHEMA_VERSION,
                observedAt, syncedAt, null, null, syncedAt, syncedAt
        );
        return withChecksum(raw, new VenueRuleChecksumCalculator().calculate(raw));
    }

    private static InstrumentCatalogItem withChecksum(InstrumentCatalogItem item, String checksum) {
        return new InstrumentCatalogItem(
                item.instrumentId(), item.exchangeCode(), item.instrumentType(), item.exchangeSymbol(),
                item.internalSymbol(), item.baseAsset(), item.quoteAsset(), item.status(), item.tickSize(),
                item.stepSize(), item.minQuantity(), item.maxLimitQuantity(), item.maxMarketSize(),
                item.maxMarketSizeUnit(), item.maxLimitNotionalUsd(), item.maxMarketNotionalUsd(), item.source(),
                item.sourceSchemaVersion(), item.observedAt(), item.syncedAt(), item.nextRuleEffectiveAt(), checksum,
                item.createdAt(), item.updatedAt()
        );
    }

    private static Facts completeFacts() {
        return new Facts(
                new BigDecimal("0.1"), new BigDecimal("0.0001"), new BigDecimal("0.001"),
                new BigDecimal("100"), new BigDecimal("100000"), "USDT",
                new BigDecimal("1000000"), new BigDecimal("1000000")
        );
    }

    private static Facts incompleteFacts() {
        return new Facts(
                new BigDecimal("0.1"), new BigDecimal("0.0001"), new BigDecimal("0.001"),
                null, null, null, null, null
        );
    }

    private static void assertBlocked(DryRunOrderPreviewResult result, OrderPreviewFindingCode code) {
        assertEquals(OrderPreviewStatus.BLOCKED, result.structuralStatus());
        assertTrue(result.blockers().contains(code));
        assertEquals(OrderPreviewStatus.BLOCKED, result.executionReadiness());
    }

    private static void assertVenueBlocked(DryRunOrderPreviewResult result, OrderPreviewFindingCode code) {
        assertEquals(OrderPreviewStatus.BLOCKED, result.venueFactStatus());
        assertTrue(result.blockers().contains(code));
        assertEquals(OrderPreviewStatus.BLOCKED, result.executionReadiness());
    }

    private record Facts(
            BigDecimal tickSize,
            BigDecimal stepSize,
            BigDecimal minQuantity,
            BigDecimal maxLimitQuantity,
            BigDecimal maxMarketSize,
            String maxMarketSizeUnit,
            BigDecimal maxLimitNotionalUsd,
            BigDecimal maxMarketNotionalUsd
    ) {
    }

    private static final class CountingReadPort implements InstrumentCatalogReadPort {

        private final List<InstrumentCatalogItem> items;
        private int readCount;

        private CountingReadPort(InstrumentCatalogItem... items) {
            this.items = List.of(items);
        }

        @Override
        public List<InstrumentCatalogItem> findByExchangeAndSymbols(
                String exchangeCode,
                List<String> exchangeSymbols
        ) {
            readCount++;
            return items;
        }
    }
}
