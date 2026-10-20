package com.guidinglight.nexusquant.scheduler.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.account.domain.fee.FrozenDirectionalFeeModel;
import com.guidinglight.nexusquant.account.domain.fee.SpotCommissionTerms;
import com.guidinglight.nexusquant.account.domain.fee.SpotCommissionTerms.Discount;
import com.guidinglight.nexusquant.account.domain.fee.SpotCommissionTerms.Rates;
import com.guidinglight.nexusquant.strategy.domain.SpotFeeModel.DiscountFunding;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** 只证明冻结支付选择的数学与恢复校验；合成摘要不是正式来源证明，PG/HTTP路径另行验证。 */
class FrozenSimFeeChargeTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Instant at = Instant.parse("2026-10-01T00:06:00Z");

    private static BigDecimal d(String text) {
        return new BigDecimal(text);
    }

    @Test
    void signedChoicesRemainIdenticalAfterJsonReread() {
        for (String rate : new String[] {"0.001", "0", "-0.001"}) {
            var terms = terms(rate);
            var funds = funding("0.001");
            var charge = terms.charge("BTC-USDT", "BUY", d("0.2"), d("100"), at, funds);
            var original =
                    new FrozenSimFeeCharge(
                            "BTC-USDT",
                            "BUY",
                            d("0.2"),
                            d("100"),
                            at,
                            charge,
                            funds,
                            at.minusSeconds(59),
                            "{}");
            var restored = FrozenSimFeeCharge.read(original.json(mapper), mapper);
            assertEquals(
                    charge,
                    restored.charge(terms, "BTC-USDT", "BUY", d("0.20"), d("100.00000000"), at));
            assertEquals(
                    charge.amount().max(BigDecimal.ZERO), restored.expected().positiveReserve());
        }
    }

    @Test
    void insufficientChoiceRemainsFallbackAfterSettlementBalanceChanges() {
        var terms = terms("0.001");
        var funds = funding("0");
        var charge = terms.charge("BTC-USDT", "BUY", d("0.2"), d("100"), at, funds);
        assertEquals("DISCOUNT_ASSET_INSUFFICIENT_FALLBACK", charge.paymentStatus());
        var saved =
                new FrozenSimFeeCharge(
                        "BTC-USDT",
                        "BUY",
                        d("0.2"),
                        d("100"),
                        at,
                        charge,
                        funds,
                        at.minusSeconds(59),
                        "{}");
        assertEquals(
                "BTC",
                FrozenSimFeeCharge.read(saved.json(mapper), mapper)
                        .charge(terms, "BTC-USDT", "BUY", d("0.2"), d("100"), at)
                        .currency());
    }

    @Test
    void tamperedAmountAndReserveCannotPassOriginalModel() throws Exception {
        var terms = terms("0.001");
        var funds = funding("0.001");
        var saved =
                new FrozenSimFeeCharge(
                        "BTC-USDT",
                        "BUY",
                        d("0.2"),
                        d("100"),
                        at,
                        terms.charge("BTC-USDT", "BUY", d("0.2"), d("100"), at, funds),
                        funds,
                        at.minusSeconds(59),
                        "{}");
        ObjectNode node = (ObjectNode) mapper.readTree(saved.json(mapper));
        node.put("amount", "0.00001");
        node.put("positiveReserve", "0.00001");
        var altered = FrozenSimFeeCharge.read(node.toString(), mapper);
        assertThrows(
                IllegalStateException.class,
                () -> altered.charge(terms, "BTC-USDT", "BUY", d("0.2"), d("100"), at));
        node.put("positiveReserve", "0");
        assertThrows(
                IllegalStateException.class,
                () -> FrozenSimFeeCharge.read(node.toString(), mapper));
    }

    @Test
    void originalOrderAndTimeAreMandatoryDuringReplay() {
        var terms = terms("0.001");
        var funds = funding("0.001");
        var saved =
                new FrozenSimFeeCharge(
                        "BTC-USDT",
                        "BUY",
                        d("0.2"),
                        d("100"),
                        at,
                        terms.charge("BTC-USDT", "BUY", d("0.2"), d("100"), at, funds),
                        funds,
                        at.minusSeconds(59),
                        "{}");
        assertThrows(
                IllegalStateException.class,
                () -> saved.charge(terms, "BTC-USDT", "SELL", d("0.2"), d("100"), at));
        assertThrows(
                IllegalStateException.class,
                () -> saved.charge(terms, "BTC-USDT", "BUY", d("0.3"), d("100"), at));
        assertThrows(
                IllegalStateException.class,
                () -> saved.charge(terms, "BTC-USDT", "BUY", d("0.2"), d("101"), at));
        assertThrows(
                IllegalStateException.class,
                () ->
                        saved.charge(
                                terms, "BTC-USDT", "BUY", d("0.2"), d("100"), at.plusSeconds(1)));
    }

    @Test
    void futureVisibilityAndNonmicrosecondIdentityAreRejected() {
        var funds = funding("0.001");
        var charge = terms("0.001").charge("BTC-USDT", "BUY", d("0.2"), d("100"), at, funds);
        assertThrows(
                IllegalStateException.class,
                () ->
                        new FrozenSimFeeCharge(
                                "BTC-USDT",
                                "BUY",
                                d("0.2"),
                                d("100"),
                                at,
                                charge,
                                funds,
                                at.plusSeconds(1),
                                "{}"));
        assertThrows(
                IllegalStateException.class,
                () ->
                        new FrozenSimFeeCharge(
                                "BTC-USDT",
                                "BUY",
                                d("0.2"),
                                d("100"),
                                at.plusNanos(1),
                                charge,
                                funds,
                                at,
                                "{}"));
    }

    /** PG原反例：合法lot数量携带18位尾零，被8位文本约束拒绝；只去表示尾零，不允许舍入真实第九位。 */
    @Test
    void canonicalRepresentationDoesNotDiscardRealPrecision() throws Exception {
        var funds = funding("0.001");
        var terms = terms("0.001");
        var quantity = d("0.900000040000000000");
        var price = d("100.000000000000000000");
        var saved =
                new FrozenSimFeeCharge(
                        "BTC-USDT",
                        "BUY",
                        quantity,
                        price,
                        at,
                        terms.charge("BTC-USDT", "BUY", quantity, price, at, funds),
                        funds,
                        at.minusSeconds(59),
                        "{}");
        assertEquals("0.90000004", mapper.readTree(saved.json(mapper)).path("quantity").asText());
        assertEquals("100.00000000", mapper.readTree(saved.json(mapper)).path("price").asText());
        assertThrows(
                IllegalStateException.class,
                () ->
                        new FrozenSimFeeCharge(
                                "BTC-USDT",
                                "BUY",
                                d("0.900000041"),
                                price,
                                at,
                                saved.expected(),
                                funds,
                                at.minusSeconds(59),
                                "{}"));
        assertThrows(
                IllegalStateException.class,
                () ->
                        new FrozenSimFeeCharge(
                                "BTC-USDT",
                                "BUY",
                                quantity,
                                d("100.000000001"),
                                at,
                                saved.expected(),
                                funds,
                                at.minusSeconds(59),
                                "{}"));
    }

    private DiscountFunding funding(String available) {
        return new DiscountFunding(
                "BNB",
                "USDT",
                d("600"),
                d(available),
                at.minusSeconds(60),
                Duration.ofMinutes(5),
                "a".repeat(64));
    }

    private StrategySimFeeTerms terms(String rate) {
        var terms =
                new SpotCommissionTerms(
                        "BTCUSDT",
                        new Rates(d("0"), d(rate), d("0"), d("0")),
                        new Rates(d("0"), d("0"), d("0"), d("0")),
                        new Rates(d("0"), d("0"), d("0"), d("0")),
                        new Discount(true, true, "BNB", d("0.75")),
                        "b".repeat(64));
        ObjectNode snapshot = mapper.createObjectNode();
        snapshot.put("schemaVersion", FrozenDirectionalFeeModel.VERSION);
        snapshot.put("liquidityRole", "TAKER");
        snapshot.put("paymentPolicy", "DISCOUNT_ASSET_IF_FUNDED");
        snapshot.put("symbol", "BTC-USDT");
        snapshot.put("baseFeeScale", "8");
        snapshot.put("quoteFeeScale", "8");
        snapshot.put("discountAssetFeeScale", "8");
        snapshot.set("commissionTerms", terms.snapshot(mapper));
        ObjectNode spec = mapper.createObjectNode();
        spec.put("costSource", "CURRENT_ACCOUNT_OBSERVATION");
        spec.put("feeAssumptionVersion", FrozenDirectionalFeeModel.VERSION);
        spec.put("feeExchangeCode", "BINANCE");
        spec.put("feePaymentPolicy", "DISCOUNT_ASSET_IF_FUNDED");
        spec.put("feeSymbol", "BTC-USDT");
        spec.put("feeSourceSnapshotJson", snapshot.toString());
        spec.put("feeSourceSnapshotSha256", FrozenDirectionalFeeModel.digest(snapshot));
        spec.put("discountPriceDatasetId", UUID.randomUUID().toString());
        spec.put("discountPriceSnapshotId", UUID.randomUUID().toString());
        spec.put("discountPriceSnapshotSha256", "c".repeat(64));
        spec.put("discountAssetBudgetFraction", "0.1");
        return StrategySimFeeTerms.from(null, spec.toString(), mapper);
    }
}
