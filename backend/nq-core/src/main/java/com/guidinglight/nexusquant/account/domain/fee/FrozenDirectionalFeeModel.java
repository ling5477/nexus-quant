package com.guidinglight.nexusquant.account.domain.fee;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.account.domain.fee.SpotCommissionTerms.LiquidityRole;
import com.guidinglight.nexusquant.account.domain.fee.SpotCommissionTerms.Side;
import com.guidinglight.nexusquant.strategy.domain.SpotFeeModel;
import com.guidinglight.nexusquant.strategy.domain.SpotFeeModel.Policy;
import com.guidinglight.nexusquant.strategy.domain.SpotFeeModel.Precision;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.TreeMap;

/** 完整方向费用的冻结身份；旧ACCOUNT_FEE_MODEL_V1及其字节/摘要合同保持独立，不能把V2伪装成旧标量。 */
public record FrozenDirectionalFeeModel(
        AccountFeeObservation observation,
        Instant frozenAt,
        Policy paymentPolicy,
        Precision precision) {
    public static final String VERSION = "ACCOUNT_FEE_MODEL_V2";

    public FrozenDirectionalFeeModel {
        Objects.requireNonNull(observation);
        Objects.requireNonNull(frozenAt);
        Objects.requireNonNull(paymentPolicy);
        Objects.requireNonNull(precision);
        if (observation.ownerUserId() <= 0
                || observation.exchangeAccountId() <= 0
                || observation.credentialId() <= 0
                || observation.observationId() == null
                || observation.operationId() == null
                || observation.quote() == null
                || observation.quote().commissionTerms() == null
                || !"BINANCE".equals(observation.exchangeCode())
                || !("SIM".equals(observation.tradeEnv()) || "LIVE".equals(observation.tradeEnv()))
                || observation.symbol() == null
                || !observation.symbol().matches("[A-Z0-9]{1,20}-[A-Z0-9]{1,20}")
                || !observation
                        .symbol()
                        .replace("-", "")
                        .equals(observation.quote().commissionTerms().nativeSymbol())
                || observation.expiresAt() == null
                || frozenAt.isBefore(observation.quote().observedAt())
                || !frozenAt.isBefore(observation.expiresAt())
                || !AccountFeeSourceIdentity.digest(observation).equals(observation.sourceSha256()))
            throw invalid("FEE_DIRECTIONAL_SOURCE_INVALID");
        frozenAt = frozenAt.truncatedTo(ChronoUnit.MICROS);
        SpotFeeModel.commission(observation.quote().commissionTerms(), paymentPolicy, precision);
    }

    /** 原观察、方向率、支付条件和实际消费精度一起冻结；缺失等级和provider时点仍保持null。 */
    public ObjectNode snapshot(ObjectMapper mapper) {
        var row = observation;
        var quote = row.quote();
        var terms = quote.commissionTerms();
        ObjectNode value = mapper.createObjectNode();
        value.put("schemaVersion", VERSION);
        value.put("ownerUserId", Long.toString(row.ownerUserId()));
        value.put("exchangeAccountId", Long.toString(row.exchangeAccountId()));
        value.put("credentialId", Long.toString(row.credentialId()));
        value.put("observationId", row.observationId().toString());
        value.put("operationId", row.operationId().toString());
        value.put("exchangeCode", row.exchangeCode());
        value.put("tradeEnv", row.tradeEnv());
        value.put("symbol", row.symbol());
        value.put("source", quote.source());
        value.put("rateConvention", quote.rateConvention());
        value.put("liquidityRole", "TAKER");
        value.put("paymentPolicy", paymentPolicy.name());
        value.put(
                "buyReceivedAssetRate",
                model().rate(Side.BUY, LiquidityRole.TAKER).toPlainString());
        value.put(
                "sellReceivedAssetRate",
                model().rate(Side.SELL, LiquidityRole.TAKER).toPlainString());
        value.set("commissionTerms", terms.snapshot(mapper));
        value.putNull("tier");
        value.put("providerTimeBasis", quote.providerTimeBasis());
        if (quote.providerAt() == null) value.putNull("providerAt");
        else value.put("providerAt", quote.providerAt().toString());
        value.put("observedAt", quote.observedAt().toString());
        value.put("expiresAt", row.expiresAt().toString());
        value.put("frozenAt", frozenAt.toString());
        value.put("sourceSha256", row.sourceSha256());
        value.put("baseFeeScale", Integer.toString(precision.baseScale()));
        value.put("quoteFeeScale", Integer.toString(precision.quoteScale()));
        value.put("discountAssetFeeScale", Integer.toString(precision.discountAssetScale()));
        value.put("feeRounding", "HALF_UP");
        value.put("reserveRounding", "CEILING_POSITIVE_COST_NO_REBATE_CREDIT");
        value.put(
                "fundingRequirement",
                paymentPolicy == Policy.DISCOUNT_ASSET_IF_FUNDED
                        ? "VERIFIED_POST_RESERVE_BALANCE_AND_ASOF_PRICE"
                        : "NONE");
        value.put("scope", "CURRENT_OBSERVATION_ADOPTED_RESEARCH_SIM_MODEL");
        return value;
    }

    /** 仅重建已采用的数学合同；所有者、账户生命周期和凭据可用性仍必须由正式服务核验。 */
    public SpotFeeModel model() {
        return SpotFeeModel.commission(
                observation.quote().commissionTerms(), paymentPolicy, precision);
    }

    /** V2摘要支持完整嵌套条件与缺失值；使用类型标记和长度前缀，不依赖JSON字段次序或浮点。 */
    public static String digest(JsonNode snapshot) {
        if (snapshot == null
                || !snapshot.isObject()
                || !VERSION.equals(snapshot.path("schemaVersion").asText()))
            throw invalid("FEE_DIRECTIONAL_SNAPSHOT_INVALID");
        StringBuilder canonical = new StringBuilder("NQ/FROZEN_ACCOUNT_FEE_MODEL/2\n");
        append(snapshot, canonical, 0);
        return AccountFeeSourceIdentity.sha256(canonical.toString());
    }

    private static void append(JsonNode node, StringBuilder out, int depth) {
        if (depth > 5 || node == null) throw invalid("FEE_DIRECTIONAL_SNAPSHOT_INVALID");
        if (node.isObject()) {
            if (node.isEmpty() || node.size() > 64)
                throw invalid("FEE_DIRECTIONAL_SNAPSHOT_INVALID");
            TreeMap<String, JsonNode> sorted = new TreeMap<>();
            node.properties().forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
            out.append('O').append(sorted.size()).append(':');
            sorted.forEach(
                    (key, value) -> {
                        token(key, out);
                        append(value, out, depth + 1);
                    });
        } else if (node.isTextual()) {
            out.append('T');
            token(node.textValue(), out);
        } else if (node.isBoolean()) out.append(node.booleanValue() ? "B1" : "B0");
        else if (node.isNull()) out.append('N');
        else throw invalid("FEE_DIRECTIONAL_SNAPSHOT_INVALID");
    }

    private static void token(String text, StringBuilder out) {
        if (text.length() > 4096) throw invalid("FEE_DIRECTIONAL_SNAPSHOT_INVALID");
        out.append(text.length()).append(':').append(text);
    }

    private static IllegalArgumentException invalid(String code) {
        return new IllegalArgumentException(code);
    }

    @Override
    public String toString() {
        return "FrozenDirectionalFeeModel[REDACTED]";
    }
}
