package com.guidinglight.nexusquant.account.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.account.domain.fee.AccountFeeObservation;
import com.guidinglight.nexusquant.account.domain.fee.AccountFeeQuote;
import com.guidinglight.nexusquant.account.domain.fee.AccountFeeSourceIdentity;
import com.guidinglight.nexusquant.account.domain.fee.FrozenAccountFeeModel;
import com.guidinglight.nexusquant.account.domain.fee.FrozenDirectionalFeeModel;
import com.guidinglight.nexusquant.account.domain.fee.SpotCommissionTerms;
import com.guidinglight.nexusquant.account.domain.fee.SpotCommissionTerms.Discount;
import com.guidinglight.nexusquant.account.domain.fee.SpotCommissionTerms.Rates;
import com.guidinglight.nexusquant.strategy.domain.SpotFeeModel.Policy;
import com.guidinglight.nexusquant.strategy.domain.SpotFeeModel.Precision;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** 冻结的是完整来源及支付约束，重新排序不改变身份；变更分量、时间、精度或政策都会改变身份。 */
class FrozenDirectionalFeeModelTest {
    private static final Instant AT = Instant.parse("2026-10-14T00:00:00Z");
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void freezesTwelveComponentsAndNoInventedScalarOrProviderDate() {
        var model = frozen();
        var snapshot = model.snapshot(mapper);
        assertEquals(FrozenDirectionalFeeModel.VERSION, snapshot.path("schemaVersion").asText());
        assertEquals("BINANCE_PRIVATE_SPOT_COMMISSION", snapshot.path("source").asText());
        assertEquals("0.001200000000", snapshot.path("buyReceivedAssetRate").asText());
        assertEquals("0.001300000000", snapshot.path("sellReceivedAssetRate").asText());
        assertTrue(snapshot.path("providerAt").isNull());
        assertTrue(snapshot.path("tier").isNull());
        assertEquals("NOT_PROVIDED", snapshot.path("providerTimeBasis").asText());
        assertFalse(snapshot.has("selectedRate"));
        assertFalse(snapshot.has("feeCurrency"));
        assertEquals(
                model.observation().quote().commissionTerms().snapshot(mapper),
                snapshot.path("commissionTerms"));
        assertEquals("8", snapshot.path("baseFeeScale").asText());
        assertEquals("NONE", snapshot.path("fundingRequirement").asText());
        assertTrue(FrozenDirectionalFeeModel.digest(snapshot).matches("[a-f0-9]{64}"));
    }

    @Test
    void fieldOrderIsStableAndEveryBusinessTermAffectsDigest() {
        var snapshot = frozen().snapshot(mapper);
        String digest = FrozenDirectionalFeeModel.digest(snapshot);
        var reordered = mapper.createObjectNode();
        snapshot.properties().stream()
                .sorted((a, b) -> b.getKey().compareTo(a.getKey()))
                .forEach(entry -> reordered.set(entry.getKey(), entry.getValue()));
        assertEquals(digest, FrozenDirectionalFeeModel.digest(reordered));
        for (String field :
                new String[] {
                    "paymentPolicy", "baseFeeScale", "observedAt", "sourceSha256", "liquidityRole"
                }) {
            var changed = snapshot.deepCopy();
            changed.put(field, "changed");
            assertNotEquals(digest, FrozenDirectionalFeeModel.digest(changed), field);
        }
        var changed = snapshot.deepCopy();
        ((ObjectNode) changed.path("commissionTerms").path("tax")).put("seller", "0.000000000001");
        assertNotEquals(digest, FrozenDirectionalFeeModel.digest(changed));
        changed = snapshot.deepCopy();
        changed.put("tier", "VIP1");
        assertNotEquals(digest, FrozenDirectionalFeeModel.digest(changed));
    }

    @Test
    void snapshotsDoNotCrossVersionAndNumericJsonIsRejected() {
        var snapshot = frozen().snapshot(mapper);
        assertThrows(IllegalArgumentException.class, () -> FrozenAccountFeeModel.digest(snapshot));
        snapshot.put("baseFeeScale", 8);
        assertThrows(
                IllegalArgumentException.class, () -> FrozenDirectionalFeeModel.digest(snapshot));
        snapshot.put("schemaVersion", "ACCOUNT_FEE_MODEL_V1");
        assertThrows(
                IllegalArgumentException.class, () -> FrozenDirectionalFeeModel.digest(snapshot));
    }

    @Test
    void freezeOutsideOriginalObservationWindowFailsWithoutChangingObservation() {
        var original = observation();
        String digest = original.sourceSha256();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new FrozenDirectionalFeeModel(
                                original,
                                AT.minusNanos(1),
                                Policy.RECEIVED_ASSET,
                                Precision.canonical()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new FrozenDirectionalFeeModel(
                                original,
                                original.expiresAt(),
                                Policy.RECEIVED_ASSET,
                                Precision.canonical()));
        assertEquals(digest, AccountFeeSourceIdentity.digest(original));
    }

    @Test
    void discountPolicyFreezesFundingRequirementWithoutManufacturingBalanceOrConversion() {
        var original = frozen();
        var model =
                new FrozenDirectionalFeeModel(
                        original.observation(),
                        AT,
                        Policy.DISCOUNT_ASSET_IF_FUNDED,
                        Precision.canonical());
        var snapshot = model.snapshot(mapper);
        assertEquals(
                "VERIFIED_POST_RESERVE_BALANCE_AND_ASOF_PRICE",
                snapshot.path("fundingRequirement").asText());
        assertFalse(snapshot.has("discountAssetBalance"));
        assertFalse(snapshot.has("quotePerDiscountAsset"));
        assertNotEquals(
                FrozenDirectionalFeeModel.digest(original.snapshot(mapper)),
                FrozenDirectionalFeeModel.digest(snapshot));
    }

    private FrozenDirectionalFeeModel frozen() {
        return new FrozenDirectionalFeeModel(
                observation(), AT, Policy.RECEIVED_ASSET, Precision.canonical());
    }

    private AccountFeeObservation observation() {
        var terms =
                new SpotCommissionTerms(
                        "BTCUSDT",
                        new Rates(d("-0.000000000001"), d("0.001"), d("0.0002"), d("0.0003")),
                        new Rates(d("0"), d("0"), d("0"), d("0")),
                        new Rates(d("0"), d("0"), d("0"), d("0")),
                        new Discount(true, true, "BNB", d("0.25")),
                        "a".repeat(64));
        var unsigned =
                new AccountFeeObservation(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        1,
                        2,
                        3,
                        "BINANCE",
                        "LIVE",
                        "BTC-USDT",
                        "b".repeat(64),
                        AccountFeeQuote.binance(terms, null, AT),
                        AT.plusSeconds(300),
                        "");
        return new AccountFeeObservation(
                unsigned.observationId(),
                unsigned.operationId(),
                1,
                2,
                3,
                "BINANCE",
                "LIVE",
                "BTC-USDT",
                unsigned.inputFingerprint(),
                unsigned.quote(),
                unsigned.expiresAt(),
                AccountFeeSourceIdentity.digest(unsigned));
    }

    private static BigDecimal d(String text) {
        return new BigDecimal(text);
    }
}
