package com.guidinglight.nexusquant.adapter.okx.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.guidinglight.nexusquant.adapter.okx.model.OkxVenueRuleFact;
import com.guidinglight.nexusquant.adapter.okx.model.OkxVenueRuleFactsSnapshot;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * OkxVenueRuleFactsReader 读取并解析 OKX Public Instruments 的 bounded Spot venue-rule facts。
 *
 * <p>职责边界：只调用固定 public endpoint，不鉴权、不签名、不读取 credential；每次 operator-triggered
 * sync 只发起一次 public GET，再按 server-side allowlist 过滤 1..3 个 symbol。非法 decimal、重复/missing
 * symbol 或非 Spot payload 都整批 fail-closed，不返回部分 snapshot。`upcChg` 在能够完整持久化其 canonical
 * representation 之前明确后置，不能只保留 effective time 造成 checksum 无法重算。</p>
 */
public final class OkxVenueRuleFactsReader implements OkxVenueRuleFactsProvider {

    static final String INSTRUMENTS_ENDPOINT = "/api/v5/public/instruments?instType=SPOT";
    private final OkxHttpClient publicHttpClient;
    private final Clock clock;

    public OkxVenueRuleFactsReader(OkxHttpClient publicHttpClient, Clock clock) {
        this.publicHttpClient = Objects.requireNonNull(publicHttpClient, "publicHttpClient must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 显式读取 allowlisted Spot symbols；调用方必须由 operator workflow 触发，不能从 preview 请求线程调用。
     *
     * @param allowlistedSymbols server-side allowlist 中本次选择的 1..3 个 OKX instId
     * @param traceId 脱敏 trace id；不得包含 credential
     * @return 完整、已校验且带 observedAt 的 snapshot
     */
    @Override
    public OkxVenueRuleFactsSnapshot fetch(Set<String> allowlistedSymbols, String traceId) {
        Set<String> normalizedSymbols = normalizeSymbols(allowlistedSymbols);
        JsonNode payload = publicHttpClient.get(INSTRUMENTS_ENDPOINT, traceId);
        if (!"0".equals(payload.path("code").asText())) {
            throw new IllegalStateException("OKX public instruments returned non-success code");
        }
        JsonNode data = payload.path("data");
        if (!data.isArray()) {
            throw new IllegalStateException("OKX public instruments data must be an array");
        }
        List<ParsedFact> parsedFacts = new ArrayList<>();
        Set<String> seenSymbols = new HashSet<>();
        for (JsonNode item : data) {
            String instId = requiredText(item, "instId").toUpperCase(Locale.ROOT);
            if (!normalizedSymbols.contains(instId)) {
                continue;
            }
            if (!seenSymbols.add(instId)) {
                throw new IllegalStateException("duplicate OKX instrument in public response: " + instId);
            }
            parsedFacts.add(parseFact(item, instId));
        }
        if (!seenSymbols.equals(normalizedSymbols)) {
            Set<String> missing = new HashSet<>(normalizedSymbols);
            missing.removeAll(seenSymbols);
            throw new IllegalStateException("OKX public instruments missing allowlisted symbols: " + missing);
        }
        Instant observedAt = Instant.now(clock);
        List<OkxVenueRuleFact> facts = parsedFacts.stream()
                .map(parsed -> parsed.toFact(observedAt))
                .toList();
        return new OkxVenueRuleFactsSnapshot(facts, observedAt);
    }

    private static ParsedFact parseFact(JsonNode item, String instId) {
        String instType = requiredText(item, "instType").toUpperCase(Locale.ROOT);
        if (!"SPOT".equals(instType)) {
            throw new IllegalStateException("OKX venue-rule reader only accepts SPOT instruments");
        }
        String state = requiredText(item, "state").toUpperCase(Locale.ROOT);
        String baseCurrency = requiredText(item, "baseCcy").toUpperCase(Locale.ROOT);
        String quoteCurrency = requiredText(item, "quoteCcy").toUpperCase(Locale.ROOT);
        BigDecimal maximumMarketSize = optionalPositiveDecimal(item, "maxMktSz");
        return new ParsedFact(
                instId,
                instType,
                state,
                baseCurrency,
                quoteCurrency,
                requiredPositiveDecimal(item, "tickSz"),
                requiredPositiveDecimal(item, "lotSz"),
                requiredPositiveDecimal(item, "minSz"),
                optionalPositiveDecimal(item, "maxLmtSz"),
                maximumMarketSize,
                maximumMarketSize == null ? null : "USDT",
                optionalPositiveDecimal(item, "maxLmtAmt"),
                optionalPositiveDecimal(item, "maxMktAmt"),
                null
        );
    }

    private static BigDecimal requiredPositiveDecimal(JsonNode item, String field) {
        return parsePositiveDecimal(requiredText(item, field), field);
    }

    private static BigDecimal optionalPositiveDecimal(JsonNode item, String field) {
        String raw = optionalText(item, field);
        return raw == null ? null : parsePositiveDecimal(raw, field);
    }

    private static BigDecimal parsePositiveDecimal(String raw, String field) {
        try {
            BigDecimal value = new BigDecimal(raw);
            if (value.signum() <= 0) {
                throw new IllegalStateException(field + " must be positive");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalStateException(field + " must be a valid decimal", ex);
        }
    }

    private static String requiredText(JsonNode item, String field) {
        String value = optionalText(item, field);
        if (value == null) {
            throw new IllegalStateException("missing required OKX public instrument field: " + field);
        }
        return value;
    }

    private static String optionalText(JsonNode item, String field) {
        JsonNode node = item.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Set<String> normalizeSymbols(Set<String> symbols) {
        Objects.requireNonNull(symbols, "allowlistedSymbols must not be null");
        if (symbols.isEmpty() || symbols.size() > 3) {
            throw new IllegalArgumentException("allowlistedSymbols must contain 1..3 values");
        }
        Set<String> normalized = new HashSet<>();
        for (String symbol : symbols) {
            if (symbol == null || symbol.isBlank()) {
                throw new IllegalArgumentException("allowlistedSymbols must not contain blank values");
            }
            normalized.add(symbol.trim().toUpperCase(Locale.ROOT));
        }
        if (normalized.size() != symbols.size()) {
            throw new IllegalArgumentException("allowlistedSymbols must not contain duplicates");
        }
        return Set.copyOf(normalized);
    }

    private record ParsedFact(
            String instId,
            String instType,
            String state,
            String baseCurrency,
            String quoteCurrency,
            BigDecimal tickSize,
            BigDecimal lotSize,
            BigDecimal minimumSize,
            BigDecimal maximumLimitSize,
            BigDecimal maximumMarketSize,
            String maximumMarketSizeUnit,
            BigDecimal maximumLimitAmountUsd,
            BigDecimal maximumMarketAmountUsd,
            Instant nextRuleEffectiveAt
    ) {
        private OkxVenueRuleFact toFact(Instant observedAt) {
            return new OkxVenueRuleFact(
                    instId,
                    instType,
                    state,
                    baseCurrency,
                    quoteCurrency,
                    tickSize,
                    lotSize,
                    minimumSize,
                    maximumLimitSize,
                    maximumMarketSize,
                    maximumMarketSizeUnit,
                    maximumLimitAmountUsd,
                    maximumMarketAmountUsd,
                    nextRuleEffectiveAt
            );
        }
    }
}
