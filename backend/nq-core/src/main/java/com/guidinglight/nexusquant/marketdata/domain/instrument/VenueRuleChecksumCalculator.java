package com.guidinglight.nexusquant.marketdata.domain.instrument;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;

/**
 * VenueRuleChecksumCalculator 生成确定性的 venue-rule SHA-256。
 *
 * <p>固定字段顺序、JSON null 和 decimal 规范化属于持久化合同。观察/写入/新鲜度时间、请求标识、
 * 数据库主键及内部 symbol 均被排除，因此相同官方 facts 在不同刷新时间必须得到相同 checksum。</p>
 */
public final class VenueRuleChecksumCalculator {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 计算 lowercase SHA-256。
     *
     * @param item 已完成官方字段校验的 catalog item
     * @return 64 位 lowercase hexadecimal checksum
     */
    public String calculate(InstrumentCatalogItem item) {
        byte[] canonicalBytes = canonicalDocument(item).getBytes(StandardCharsets.UTF_8);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonicalBytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    /**
     * 输出 checksum 使用的 canonical JSON，主要用于固定合同测试和审计。
     */
    public String canonicalDocument(InstrumentCatalogItem item) {
        Objects.requireNonNull(item, "item must not be null");
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("sourceSchemaVersion", item.sourceSchemaVersion());
        fields.put("instType", item.instrumentType());
        fields.put("instId", item.exchangeSymbol());
        fields.put("state", item.status());
        fields.put("baseCcy", item.baseAsset());
        fields.put("quoteCcy", item.quoteAsset());
        fields.put("tickSz", normalizeDecimal(item.tickSize()));
        fields.put("lotSz", normalizeDecimal(item.stepSize()));
        fields.put("minSz", normalizeDecimal(item.minQuantity()));
        fields.put("maxLmtSz", normalizeDecimal(item.maxLimitQuantity()));
        fields.put("maxMktSz", normalizeDecimal(item.maxMarketSize()));
        fields.put("maxMktSzUnit", item.maxMarketSizeUnit());
        fields.put("maxLmtAmt", normalizeDecimal(item.maxLimitNotionalUsd()));
        fields.put("maxMktAmt", normalizeDecimal(item.maxMarketNotionalUsd()));
        fields.put(
                "nextRuleEffectiveAt",
                item.nextRuleEffectiveAt() == null ? null : item.nextRuleEffectiveAt().toString()
        );
        try {
            return OBJECT_MAPPER.writeValueAsString(fields);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize canonical venue-rule facts", ex);
        }
    }

    private static String normalizeDecimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }
}
