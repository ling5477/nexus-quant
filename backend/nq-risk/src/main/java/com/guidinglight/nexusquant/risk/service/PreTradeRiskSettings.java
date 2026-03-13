package com.guidinglight.nexusquant.risk.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * PreTradeRiskSettings 定义 GateD 第一批 pre-trade 风控规则所需的最小配置。
 * <p>
 * Why:
 * 当前阶段先收敛“统一规则入口 + 可验证默认值”，而不是提前引入复杂配置中心。
 * 该对象负责把默认允许范围、数值阈值与限频窗口集中收口，避免规则把魔法值散落在实现里。
 */
public record PreTradeRiskSettings(
        boolean accountTradingEnabledByDefault,
        Set<Long> disabledAccounts,
        Map<String, Set<String>> enabledSymbolsByVenue,
        int maxPriceScale,
        int maxQuantityScale,
        BigDecimal minNotional,
        BigDecimal maxOrderNotional,
        Duration duplicateWindow,
        Duration rateLimitWindow,
        int rateLimitMaxRequests
) {

    /**
     * 构造一个适合 GateD 第一批实施的默认配置。
     *
     * @return 默认配置
     */
    public static PreTradeRiskSettings defaults() {
        return new PreTradeRiskSettings(
                true,
                Set.of(),
                Map.of(),
                8,
                8,
                BigDecimal.ZERO,
                new BigDecimal("1000000"),
                Duration.ofMinutes(5),
                Duration.ofSeconds(1),
                5
        );
    }

    public PreTradeRiskSettings {
        Objects.requireNonNull(disabledAccounts, "disabledAccounts must not be null");
        Objects.requireNonNull(enabledSymbolsByVenue, "enabledSymbolsByVenue must not be null");
        Objects.requireNonNull(minNotional, "minNotional must not be null");
        Objects.requireNonNull(maxOrderNotional, "maxOrderNotional must not be null");
        Objects.requireNonNull(duplicateWindow, "duplicateWindow must not be null");
        Objects.requireNonNull(rateLimitWindow, "rateLimitWindow must not be null");
        if (maxPriceScale < 0) {
            throw new IllegalArgumentException("maxPriceScale must be >= 0");
        }
        if (maxQuantityScale < 0) {
            throw new IllegalArgumentException("maxQuantityScale must be >= 0");
        }
        if (rateLimitMaxRequests <= 0) {
            throw new IllegalArgumentException("rateLimitMaxRequests must be > 0");
        }
        enabledSymbolsByVenue = enabledSymbolsByVenue.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        entry -> normalize(entry.getKey()),
                        entry -> entry.getValue().stream().map(PreTradeRiskSettings::normalize).collect(Collectors.toUnmodifiableSet())
                ));
    }

    /**
     * 账户交易开关默认走 allow-list 兼容模式，仅对显式禁用账户拒绝。
     */
    public boolean isAccountTradingEnabled(Long accountId) {
        if (accountId == null) {
            return false;
        }
        if (disabledAccounts.contains(accountId)) {
            return false;
        }
        return accountTradingEnabledByDefault;
    }

    /**
     * symbol 允许校验默认兼容旧链路：未配置 allow-list 时认为所有 symbol 都允许。
     */
    public boolean isSymbolEnabled(String venue, String symbol) {
        String normalizedVenue = normalize(venue);
        String normalizedSymbol = normalize(symbol);
        Set<String> enabledSymbols = enabledSymbolsByVenue.get(normalizedVenue);
        if (enabledSymbols == null || enabledSymbols.isEmpty()) {
            return true;
        }
        return enabledSymbols.contains(normalizedSymbol);
    }

    /**
     * 在 contracts 仍未提供稳定 venue 访问器前，先支持按全局 symbol allow-list 校验。
     */
    public boolean isSymbolEnabled(String symbol) {
        if (enabledSymbolsByVenue.isEmpty()) {
            return true;
        }
        String normalizedSymbol = normalize(symbol);
        return enabledSymbolsByVenue.values().stream()
                .filter(symbols -> !symbols.isEmpty())
                .anyMatch(symbols -> symbols.contains(normalizedSymbol));
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("risk setting value must not be blank");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
