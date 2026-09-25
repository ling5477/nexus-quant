package com.guidinglight.nexusquant.marketdata.infra.jdbc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.adapter.okx.marketdata.OkxVenueRuleFactsReader;
import com.guidinglight.nexusquant.adapter.okx.model.OkxVenueRuleFact;
import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalMarketDataQuery;
import com.guidinglight.nexusquant.marketdata.domain.PublicMarketReplayPayload;
import com.guidinglight.nexusquant.marketdata.domain.port.HistoricalMarketDataPort;
import com.guidinglight.nexusquant.strategy.domain.PublicReplayRuleIdentity;
import com.guidinglight.nexusquant.strategy.domain.SpotBarIdentity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * JdbcHistoricalMarketDataPort 提供 DB-backed historical bars 查询。
 * <p>
 * Why:
 * RC1-5 要把 `exchangeCode` 纳入 marketdata canonical 查询口径，因此 JDBC 查询必须同步收口
 * 到 `exchange_code + symbol + interval + range`，不能再依赖隐式默认交易所。
 */
public class JdbcHistoricalMarketDataPort implements HistoricalMarketDataPort {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    public JdbcHistoricalMarketDataPort(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<HistoricalBar> loadBars(HistoricalMarketDataQuery query) {
        if ("public-capture".equals(query.datasetSpec().provider())) {
            return loadPublicCapture(query);
        }
        return jdbcTemplate.query(
                """
                        SELECT exchange_code,
                               market_type,
                               symbol,
                               "interval",
                               open_time,
                               close_time,
                               open_price,
                               high_price,
                               low_price,
                               close_price,
                               volume,
                               quote_volume,
                               trade_count,
                               quality_status,
                               raw_payload_json,
                               COALESCE(available_at, ingested_at) AS available_at
                        FROM marketdata_bars
                        WHERE exchange_code = ?
                          AND market_type = ?
                          AND symbol = ?
                          AND "interval" = ?
                          AND open_time >= ?
                          AND close_time <= ?
                        ORDER BY open_time
                        LIMIT ?
                        OFFSET ?
                        """,
                (resultSet, rowNum) -> new HistoricalBar(
                        resultSet.getString("exchange_code"),
                        resultSet.getString("market_type"),
                        resultSet.getString("symbol"),
                        BarInterval.fromWireValue(resultSet.getString("interval")),
                        resultSet.getTimestamp("open_time").toInstant(),
                        resultSet.getTimestamp("close_time").toInstant(),
                        resultSet.getBigDecimal("open_price"),
                        resultSet.getBigDecimal("high_price"),
                        resultSet.getBigDecimal("low_price"),
                        resultSet.getBigDecimal("close_price"),
                        resultSet.getBigDecimal("volume"),
                        resultSet.getBigDecimal("quote_volume"),
                        resultSet.getObject("trade_count", Long.class),
                        resultSet.getString("quality_status"),
                        resultSet.getString("raw_payload_json"),
                        resultSet.getTimestamp("available_at").toInstant()
                ),
                query.exchangeCode(),
                query.marketType(),
                query.symbol(),
                query.interval().wireValue(),
                Timestamp.from(query.startTime()),
                Timestamp.from(query.endTime()),
                Math.max(1, Math.min(query.size(), 500)),
                Math.max(0, query.page()) * Math.max(1, Math.min(query.size(), 500))
        );
    }

    private List<HistoricalBar> loadPublicCapture(HistoricalMarketDataQuery query) {
        if (!"OKX".equals(query.exchangeCode()) || !"SPOT".equals(query.marketType())
                || !"BTC-USDT".equals(query.symbol()) || query.interval() != BarInterval.ONE_HOUR
                || query.page() != 0 || query.size() > 500 || query.size() < 1
                || !query.datasetSpec().datasetId().equals(query.datasetSpec().resourcePath())) {
            throw new IllegalStateException("PUBLIC_CAPTURE_SCOPE_INVALID");
        }
        UUID datasetId = UUID.fromString(query.datasetSpec().datasetId());
        List<CaptureRow> rows = jdbcTemplate.query("""
                SELECT c.raw_response,c.raw_sha256,c.normalized_sha256,c.consumed_sha256,
                       c.observed_at,c.rule_raw_response,c.rule_raw_sha256,c.rule_sha256,
                       c.rule_observed_at,c.rule_json::text AS rule_json,c.rule_request_path,
                       c.request_path,c.replay_visibility_version,c.replay_visibility_source,
                       c.bars_json::text AS bars_json,c.requested_start,c.requested_end,
                       c.bar_count,d.source,d.status,d.request_json::text AS request_json,
                       d.exchange_code,d.market_type,d.symbol,d."interval" AS dataset_interval,
                       d.start_time AS dataset_start,d.end_time AS dataset_end,
                       d.bar_count AS dataset_count
                FROM public_market_captures c JOIN marketdata_datasets d USING (dataset_id)
                WHERE c.dataset_id=?
                """, (rs, n) -> new CaptureRow(rs.getString("raw_response"),
                rs.getString("raw_sha256"), rs.getString("normalized_sha256"),
                rs.getString("consumed_sha256"), rs.getTimestamp("observed_at").toInstant(),
                rs.getString("rule_raw_response"), rs.getString("rule_raw_sha256"),
                rs.getString("rule_sha256"), rs.getTimestamp("rule_observed_at").toInstant(),
                rs.getString("rule_json"), rs.getString("rule_request_path"),
                rs.getString("request_path"), rs.getString("replay_visibility_version"),
                rs.getString("replay_visibility_source"),
                rs.getString("bars_json"), rs.getTimestamp("requested_start").toInstant(),
                rs.getTimestamp("requested_end").toInstant(), rs.getInt("bar_count"),
                rs.getString("source"), rs.getString("status"),
                rs.getString("request_json"), rs.getString("exchange_code"),
                rs.getString("market_type"), rs.getString("symbol"),
                rs.getString("dataset_interval"),
                rs.getTimestamp("dataset_start").toInstant(),
                rs.getTimestamp("dataset_end").toInstant(),
                rs.getInt("dataset_count")), datasetId);
        if (rows.size() != 1) throw new IllegalStateException("PUBLIC_CAPTURE_NOT_FOUND");
        CaptureRow row = rows.getFirst();
        if (!"OKX_PUBLIC_CAPTURE".equals(row.source()) || !"READY".equals(row.status())
                || !"OKX".equals(row.exchangeCode()) || !"SPOT".equals(row.marketType())
                || !"BTC-USDT".equals(row.symbol()) || !"1h".equals(row.datasetInterval())
                || !row.datasetStart().equals(row.start())
                || !row.datasetEnd().equals(row.end().minusMillis(1))
                || row.datasetCount() != row.barCount()
                || !query.startTime().equals(row.start())
                || !query.endTime().equals(row.end().minusMillis(1))
                || row.barCount() > query.size() || !sha256(row.raw().getBytes(StandardCharsets.UTF_8))
                .equals(row.rawSha256().trim()) || row.ruleRaw() == null
                || !sha256(row.ruleRaw().getBytes(StandardCharsets.UTF_8))
                .equals(row.ruleRawSha256().trim())) {
            throw new IllegalStateException("PUBLIC_CAPTURE_IDENTITY_MISMATCH");
        }
        try {
            JsonNode request = mapper.readTree(row.requestJson());
            if (!row.rawSha256().trim().equals(request.path("rawSha256").asText())
                    || !row.normalizedSha256().trim().equals(request.path("normalizedSha256").asText())
                    || !row.consumedSha256().trim().equals(request.path("consumedSha256").asText())
                    || !row.ruleRawSha256().trim().equals(request.path("ruleRawSha256").asText())
                    || !row.ruleSha256().trim().equals(request.path("ruleSha256").asText())
                    || !"public-capture".equals(request.path("provider").asText())
                    || !row.start().toString().equals(request.path("requestedStart").asText())
                    || !row.end().toString().equals(request.path("requestedEnd").asText())
                    || !row.observedAt().toString().equals(request.path("observedAt").asText())
                    || !row.requestPath().equals(request.path("requestPath").asText())
                    || !row.visibilityVersion().equals(request.path("replayVisibilityVersion").asText())
                    || !row.visibilitySource().equals(request.path("replayVisibilitySource").asText())
                    || !"CLOSED_HOURLY_BOUNDARY_V1".equals(row.visibilityVersion())
                    || !"EXPERIMENT_ASSUMPTION".equals(row.visibilitySource())
                    || row.ruleRequestPath() == null
                    || !row.ruleRequestPath().equals(request.path("ruleRequestPath").asText())
                    || !mapper.readTree(row.ruleJson()).equals(request.path("rule"))
                    || !row.ruleObservedAt().toString().equals(
                            request.path("rule").path("observedAt").asText())) {
                throw new IllegalStateException("PUBLIC_CAPTURE_DATASET_IDENTITY_MISMATCH");
            }
            var derived = PublicMarketReplayPayload.parse(row.raw().getBytes(StandardCharsets.UTF_8),
                    row.start(), row.end(), row.observedAt(), mapper);
            if (!derived.normalizedSha256().equals(row.normalizedSha256().trim())
                    || !derived.consumed().sha256().equals(row.consumedSha256().trim())) {
                throw new IllegalStateException("PUBLIC_CAPTURE_SOURCE_LINK_MISMATCH");
            }
            var ruleSnapshot = OkxVenueRuleFactsReader.parseSnapshot(
                    mapper.readTree(row.ruleRaw()), Set.of("BTC-USDT"), row.ruleObservedAt());
            OkxVenueRuleFact rule = ruleSnapshot.facts().getFirst();
            ObjectNode derivedRule = mapper.createObjectNode();
            derivedRule.put("policy", "CURRENTLY_OBSERVED_PUBLIC_RULES");
            derivedRule.put("instrument", "BTC-USDT");
            derivedRule.put("state", rule.state());
            derivedRule.put("tickSize", decimal(rule.tickSize()));
            derivedRule.put("lotSize", decimal(rule.lotSize()));
            derivedRule.put("minimumSize", decimal(rule.minimumSize()));
            derivedRule.put("observedAt", row.ruleObservedAt().toString());
            JsonNode storedRule = mapper.readTree(row.ruleJson());
            if (!derivedRule.equals(storedRule)
                    || !PublicReplayRuleIdentity.sha256(derivedRule).equals(row.ruleSha256().trim())) {
                throw new IllegalStateException("PUBLIC_RULE_SOURCE_LINK_MISMATCH");
            }
            JsonNode stored = mapper.readTree(row.barsJson());
            if (!stored.isArray() || stored.size() != row.barCount())
                throw new IllegalStateException("PUBLIC_CAPTURE_BAR_COUNT_MISMATCH");
            List<HistoricalBar> bars = new ArrayList<>(stored.size());
            for (JsonNode bar : stored) {
                bars.add(new HistoricalBar(bar.path("exchangeCode").asText(),
                        bar.path("marketType").asText(), bar.path("symbol").asText(),
                        BarInterval.fromWireValue(bar.path("interval").asText()),
                        Instant.parse(bar.path("openTime").asText()),
                        Instant.parse(bar.path("closeTime").asText()),
                        new java.math.BigDecimal(bar.path("openPrice").asText()),
                        new java.math.BigDecimal(bar.path("highPrice").asText()),
                        new java.math.BigDecimal(bar.path("lowPrice").asText()),
                        new java.math.BigDecimal(bar.path("closePrice").asText()),
                        new java.math.BigDecimal(bar.path("volume").asText()),
                        bar.path("quoteVolume").isNull() ? null
                                : new java.math.BigDecimal(bar.path("quoteVolume").asText()),
                        bar.path("tradeCount").isNull() ? null : bar.path("tradeCount").asLong(),
                        bar.path("qualityStatus").asText(), bar.path("rawPayloadJson").asText(),
                        Instant.parse(bar.path("availableAt").asText())));
            }
            if (!SpotBarIdentity.capture(bars, mapper).sha256().equals(row.consumedSha256().trim()))
                throw new IllegalStateException("PUBLIC_CAPTURE_CONTENT_MISMATCH");
            for (int i = 0; i < bars.size(); i++) {
                HistoricalBar bar = bars.get(i);
                if (!"OKX".equals(bar.exchangeCode()) || !"SPOT".equals(bar.marketType())
                        || !"BTC-USDT".equals(bar.symbol()) || bar.interval() != BarInterval.ONE_HOUR
                        || !bar.openTime().equals(row.start().plusSeconds(3600L * i))
                        || !bar.availableAt().equals(bar.openTime().plusSeconds(3600)))
                    throw new IllegalStateException("PUBLIC_CAPTURE_VISIBILITY_OR_GAP");
            }
            return List.copyOf(bars);
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("PUBLIC_CAPTURE_JSON_INVALID", ex);
        }
    }

    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    private static String decimal(java.math.BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private record CaptureRow(String raw, String rawSha256, String normalizedSha256,
            String consumedSha256, Instant observedAt, String ruleRaw, String ruleRawSha256,
            String ruleSha256, Instant ruleObservedAt, String ruleJson,
            String ruleRequestPath, String requestPath, String visibilityVersion,
            String visibilitySource, String barsJson, Instant start, Instant end, int barCount,
            String source, String status, String requestJson, String exchangeCode,
            String marketType, String symbol, String datasetInterval, Instant datasetStart,
            Instant datasetEnd, int datasetCount) { }
}
