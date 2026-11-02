package com.guidinglight.nexusquant.account.domain.fee;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.TreeMap;

/** 本次研究/SIM采用的当前观察模型；不是历史费率，也不授权使用来源账户进行真实交易。 */
public record FrozenAccountFeeModel(AccountFeeObservation observation, Instant frozenAt) {
    public static final String MODE = "CURRENT_ACCOUNT_OBSERVATION";
    public static final String VERSION = "ACCOUNT_FEE_MODEL_V1";

    /** 所有数值以规范十进制字符串保存，完整来源另有摘要，外层旧标量假设摘要合同保持可用。 */
    public ObjectNode snapshot(ObjectMapper mapper) {
        var row = observation;
        var quote = row.quote();
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
        value.put("source", AccountFeeObservation.SOURCE);
        value.put("rateConvention", AccountFeeObservation.CONVENTION);
        value.put("makerProviderRate", quote.makerProviderRate().toPlainString());
        value.put("takerProviderRate", quote.takerProviderRate().toPlainString());
        value.put("makerCostRate", quote.makerCostRate().toPlainString());
        value.put("takerCostRate", quote.takerCostRate().toPlainString());
        value.put("selectedRate", quote.takerCostRate().toPlainString());
        value.put("liquidityRole", "TAKER");
        value.put("feeCurrency", row.symbol().substring(row.symbol().indexOf('-') + 1));
        value.put("tier", quote.tier());
        value.put("providerAt", quote.providerAt().toString());
        value.put("observedAt", quote.observedAt().toString());
        value.put("expiresAt", row.expiresAt().toString());
        value.put("frozenAt", frozenAt.toString());
        value.put("sourceSha256", row.sourceSha256());
        value.put("scope", "CURRENT_OBSERVATION_ADOPTED_RESEARCH_SIM_MODEL");
        return value;
    }

    /** 摘要绑定完整标量来源和冻结时点；拒绝嵌套或空值，不用浮点及JSON字段顺序建立身份。 */
    public static String digest(JsonNode snapshot) {
        if (snapshot == null || !snapshot.isObject() || snapshot.isEmpty())
            throw new IllegalArgumentException("FEE_MODEL_SNAPSHOT_INVALID");
        TreeMap<String, String> values = new TreeMap<>();
        snapshot.properties()
                .forEach(
                        entry -> {
                            if (entry.getValue().isNull() || !entry.getValue().isTextual())
                                throw new IllegalArgumentException("FEE_MODEL_SNAPSHOT_INVALID");
                            values.put(entry.getKey(), entry.getValue().asText());
                        });
        StringBuilder value = new StringBuilder("NQ/FROZEN_ACCOUNT_FEE_MODEL/1\n");
        values.forEach(
                (key, text) ->
                        value.append(key.length())
                                .append(':')
                                .append(key)
                                .append(text.length())
                                .append(':')
                                .append(text)
                                .append('\n'));
        return AccountFeeSourceIdentity.sha256(value.toString());
    }

    @Override
    public String toString() {
        return "FrozenAccountFeeModel[REDACTED]";
    }
}
