package com.guidinglight.nexusquant.ledger.contracts.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/** 冻结新 SIM 的总预算拆分；只描述初始化身份，当前余额必须读取 canonical Ledger。 */
public record SimAssetFundingPlan(
        BigDecimal totalBudget,
        String quoteCurrency,
        String assetCurrency,
        BigDecimal assetBudgetFraction,
        BigDecimal conversionPrice,
        String conversionPriceUnit,
        Instant observedAt,
        Instant visibleAt,
        Instant asOf,
        String sourceSha256,
        String sourceJson) {
    public SimAssetFundingPlan {
        if (totalBudget == null
                || totalBudget.signum() <= 0
                || totalBudget.compareTo(new BigDecimal("100000")) > 0
                || totalBudget.scale() > 8
                || !"USDT".equals(quoteCurrency)
                || assetCurrency == null
                || !assetCurrency.matches("[A-Z0-9]{2,20}")
                || quoteCurrency.equals(assetCurrency)
                || assetBudgetFraction == null
                || assetBudgetFraction.signum() <= 0
                || assetBudgetFraction.compareTo(BigDecimal.ONE) >= 0
                || assetBudgetFraction.stripTrailingZeros().scale() > 18
                || conversionPrice == null
                || conversionPrice.signum() <= 0
                || conversionPrice.stripTrailingZeros().scale() > 18
                || conversionPrice.compareTo(BigDecimal.TEN.pow(20)) >= 0
                || !("USDT_PER_" + assetCurrency).equals(conversionPriceUnit)) {
            throw new IllegalArgumentException("INVALID_SIM_ASSET_FUNDING_PLAN");
        }
        totalBudget = totalBudget.setScale(8);
        assetBudgetFraction = assetBudgetFraction.setScale(18);
        conversionPrice = conversionPrice.setScale(18);
        // PostgreSQL 只保留微秒；禁止被静默舍入的时间成为重启后不同的冻结身份。
        if (observedAt == null
                || visibleAt == null
                || asOf == null
                || observedAt.isAfter(visibleAt)
                || visibleAt.isAfter(asOf)
                || observedAt.getNano() % 1000 != 0
                || visibleAt.getNano() % 1000 != 0
                || asOf.getNano() % 1000 != 0) {
            throw new IllegalArgumentException("INVALID_SIM_ASSET_FUNDING_TIME");
        }
        // 摘要仅证明原文本未漂移，正式市场来源和新鲜度由上层独立核验。
        if (sourceJson == null
                || sourceJson.getBytes(StandardCharsets.UTF_8).length > 65536
                || sourceSha256 == null
                || !sourceSha256.matches("[a-f0-9]{64}")
                || !sourceSha256.equals(sha256(sourceJson))) {
            throw new IllegalArgumentException("INVALID_SIM_ASSET_FUNDING_SOURCE_IDENTITY");
        }
        BigDecimal quantity =
                totalBudget
                        .multiply(assetBudgetFraction)
                        .divide(conversionPrice, 8, RoundingMode.DOWN);
        BigDecimal value = quantity.multiply(conversionPrice).setScale(8, RoundingMode.HALF_UP);
        if (quantity.signum() <= 0 || value.signum() <= 0 || value.compareTo(totalBudget) > 0) {
            throw new IllegalArgumentException("SIM_ASSET_FUNDING_BELOW_PRECISION");
        }
    }

    public BigDecimal assetQuantity() {
        return totalBudget
                .multiply(assetBudgetFraction)
                .divide(conversionPrice, 8, RoundingMode.DOWN);
    }

    public BigDecimal assetInitialQuoteValue() {
        return assetQuantity().multiply(conversionPrice).setScale(8, RoundingMode.HALF_UP);
    }

    public BigDecimal cashBudget() {
        return totalBudget.subtract(assetInitialQuoteValue());
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 unavailable", failure);
        }
    }
}
