package com.guidinglight.nexusquant.scheduler.paper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.marketdata.domain.DiscountAssetPriceSnapshot;
import com.guidinglight.nexusquant.strategy.domain.SpotFeeModel.Charge;
import com.guidinglight.nexusquant.strategy.domain.SpotFeeModel.DiscountFunding;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/** 决策时冻结支付选择及已扣除其他预留的资金输入；撮合和重启不能按后来的余额重新选择收费币种。 */
record FrozenSimFeeCharge(
        String symbol,
        String side,
        BigDecimal quantity,
        BigDecimal price,
        Instant executionAt,
        Charge expected,
        DiscountFunding funding,
        Instant priceVisibleAt,
        String priceSourceJson) {
    private static final Set<String> FIELDS =
            Set.of(
                    "version",
                    "symbol",
                    "side",
                    "quantity",
                    "price",
                    "executionAt",
                    "currency",
                    "amount",
                    "quoteValue",
                    "paymentStatus",
                    "positiveReserve",
                    "asset",
                    "quoteCurrency",
                    "quotePerAsset",
                    "availableAfterReserves",
                    "observedAt",
                    "visibleAt",
                    "maxPriceAgeSeconds",
                    "sourceSha256",
                    "sourceJson");

    FrozenSimFeeCharge {
        if (symbol == null
                || !symbol.matches("[A-Z0-9]{1,20}-USDT")
                || !("BUY".equals(side) || "SELL".equals(side))
                || quantity == null
                || quantity.signum() <= 0
                || price == null
                || price.signum() <= 0
                || executionAt == null
                || expected == null
                || funding == null
                || priceVisibleAt == null
                || priceSourceJson == null
                || priceSourceJson.isBlank()
                || priceSourceJson.length() > 16_384
                || funding.priceObservedAt().isAfter(priceVisibleAt)
                || priceVisibleAt.isAfter(executionAt)
                || executionAt.getNano() % 1000 != 0
                || priceVisibleAt.getNano() % 1000 != 0) throw invalid();
        // lot运算会保留输入尾零；精确规范为canonical八位，不能把表示长度误判为新数值精度，也不能舍入越界输入。
        quantity = canonical(quantity);
        price = canonical(price);
    }

    String json(ObjectMapper mapper) {
        ObjectNode node = mapper.createObjectNode();
        node.put("version", "SIM_FEE_PAYMENT_V1");
        node.put("symbol", symbol);
        node.put("side", side);
        node.put("quantity", quantity.toPlainString());
        node.put("price", price.toPlainString());
        node.put("executionAt", executionAt.toString());
        node.put("currency", expected.currency());
        node.put("amount", expected.amount().toPlainString());
        node.put("quoteValue", expected.quoteValue().toPlainString());
        node.put("paymentStatus", expected.paymentStatus());
        node.put("positiveReserve", expected.positiveReserve().toPlainString());
        node.put("asset", funding.asset());
        node.put("quoteCurrency", funding.quoteCurrency());
        node.put("quotePerAsset", funding.quotePerAsset().toPlainString());
        node.put("availableAfterReserves", funding.availableAfterReserves().toPlainString());
        node.put("observedAt", funding.priceObservedAt().toString());
        node.put("visibleAt", priceVisibleAt.toString());
        node.put("maxPriceAgeSeconds", Long.toString(funding.maxPriceAge().getSeconds()));
        node.put("sourceSha256", funding.sourceSha256());
        node.put("sourceJson", priceSourceJson);
        return node.toString();
    }

    static FrozenSimFeeCharge read(String json, ObjectMapper mapper) {
        if (json == null || json.length() > 32_768) throw invalid();
        try {
            JsonNode node = mapper.readTree(json);
            if (!node.isObject()
                    || node.size() != FIELDS.size()
                    || FIELDS.stream().anyMatch(key -> !node.path(key).isTextual())
                    || !"SIM_FEE_PAYMENT_V1".equals(node.path("version").asText())) throw invalid();
            DiscountFunding funding =
                    new DiscountFunding(
                            text(node, "asset"),
                            text(node, "quoteCurrency"),
                            number(node, "quotePerAsset"),
                            number(node, "availableAfterReserves"),
                            Instant.parse(text(node, "observedAt")),
                            Duration.ofSeconds(Long.parseLong(text(node, "maxPriceAgeSeconds"))),
                            text(node, "sourceSha256"));
            String status = text(node, "paymentStatus");
            if (!Set.of("DISCOUNT_ASSET_USED", "DISCOUNT_ASSET_INSUFFICIENT_FALLBACK")
                    .contains(status)) throw invalid();
            Charge charge =
                    new Charge(
                            text(node, "currency"),
                            number(node, "amount"),
                            number(node, "quoteValue"),
                            status,
                            "DISCOUNT_ASSET_USED".equals(status) ? funding.sourceSha256() : null);
            if (charge.positiveReserve().compareTo(number(node, "positiveReserve")) != 0)
                throw invalid();
            return new FrozenSimFeeCharge(
                    text(node, "symbol"),
                    text(node, "side"),
                    number(node, "quantity"),
                    number(node, "price"),
                    Instant.parse(text(node, "executionAt")),
                    charge,
                    funding,
                    Instant.parse(text(node, "visibleAt")),
                    text(node, "sourceJson"));
        } catch (JsonProcessingException | IllegalArgumentException failure) {
            throw invalid();
        }
    }

    /** 原冻结输入重算后还要逐项比较，不能只接受可自行重算的JSON摘要或幂等命中的旧Trade。 */
    Charge charge(
            StrategySimFeeTerms terms,
            String symbol,
            String side,
            BigDecimal quantity,
            BigDecimal price,
            Instant executionAt) {
        if (!this.symbol.equals(symbol)
                || !this.side.equals(side)
                || this.quantity.compareTo(quantity) != 0
                || this.price.compareTo(price) != 0
                || !this.executionAt.equals(executionAt)) throw invalid();
        Charge actual = terms.charge(symbol, side, quantity, price, executionAt, funding);
        if (!actual.currency().equals(expected.currency())
                || actual.amount().compareTo(expected.amount()) != 0
                || actual.quoteValue().compareTo(expected.quoteValue()) != 0
                || !actual.paymentStatus().equals(expected.paymentStatus())
                || !Objects.equals(actual.fundingSourceSha256(), expected.fundingSourceSha256()))
            throw invalid();
        return actual;
    }

    /** 价格来源必须对应服务端冻结序列中的同一点；后续行情更新不能换掉已冻结的费用选择。 */
    void verifySource(DiscountAssetPriceSnapshot snapshot) {
        var point = snapshot.priceAt(executionAt);
        if (!snapshot.assetCurrency().equals(funding.asset())
                || !snapshot.quoteCurrency().equals(funding.quoteCurrency())
                || point.quotePerAsset().compareTo(funding.quotePerAsset()) != 0
                || !point.observedAt().equals(funding.priceObservedAt())
                || !point.visibleAt().equals(priceVisibleAt)
                || !point.sourceSha256().equals(funding.sourceSha256())
                || !point.sourceJson().equals(priceSourceJson)) throw invalid();
    }

    private static String text(JsonNode node, String key) {
        return node.path(key).asText();
    }

    private static BigDecimal number(JsonNode node, String key) {
        String value = text(node, key);
        if (!value.matches("-?[0-9]{1,38}(\\.[0-9]{1,18})?")) throw invalid();
        return new BigDecimal(value);
    }

    private static BigDecimal canonical(BigDecimal value) {
        try {
            return value.setScale(8, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException excessPrecision) {
            throw invalid();
        }
    }

    private static IllegalStateException invalid() {
        return new IllegalStateException("SIM_FROZEN_FEE_PAYMENT_INVALID");
    }
}
