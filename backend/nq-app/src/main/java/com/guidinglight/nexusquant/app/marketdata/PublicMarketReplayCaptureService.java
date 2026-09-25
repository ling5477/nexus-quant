package com.guidinglight.nexusquant.app.marketdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.adapter.okx.marketdata.OkxVenueRuleFactsReader;
import com.guidinglight.nexusquant.adapter.okx.model.OkxVenueRuleFact;
import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.marketdata.domain.PublicMarketReplayPayload;
import com.guidinglight.nexusquant.strategy.domain.SpotBarIdentity;
import com.guidinglight.nexusquant.strategy.domain.PublicReplayRuleIdentity;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** 在手动公开行情 profile 中捕获一次不可变的 OKX 历史窗口。 */
@Service
@Profile("public-marketdata-manual")
@ConditionalOnProperty(prefix = "nq.public-marketdata.outbound", name = "enabled", havingValue = "true")
public final class PublicMarketReplayCaptureService {
    static final String VISIBILITY_VERSION = "CLOSED_HOURLY_BOUNDARY_V1";
    static final String RULE_PATH = "/api/v5/public/instruments?instType=SPOT&instId=BTC-USDT";
    private static final URI OKX_ORIGIN = URI.create("https://www.okx.com");
    private static final Duration HOUR = Duration.ofHours(1);
    private final HttpClient http;
    private final URI origin;
    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final Clock clock;

    @Autowired
    public PublicMarketReplayCaptureService(ObjectMapper mapper, JdbcTemplate jdbc,
            TransactionTemplate transactions) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(), OKX_ORIGIN,
                mapper, jdbc, transactions, Clock.systemUTC());
    }

    public PublicMarketReplayCaptureService(HttpClient http, URI origin, ObjectMapper mapper, JdbcTemplate jdbc,
            TransactionTemplate transactions, Clock clock) {
        this.http = Objects.requireNonNull(http);
        this.origin = Objects.requireNonNull(origin);
        this.mapper = Objects.requireNonNull(mapper);
        this.jdbc = Objects.requireNonNull(jdbc);
        this.transactions = Objects.requireNonNull(transactions);
        this.clock = Objects.requireNonNull(clock);
    }

    public CaptureView capture(Instant start, Instant end, String createdBy) {
        int count = validateWindow(start, end);
        String path = "/api/v5/market/history-candles?instId=BTC-USDT&bar=1H&after="
                + end.toEpochMilli() + "&before=" + (start.toEpochMilli() - 1) + "&limit=" + count;
        byte[] response = fetch(path);
        Instant observedAt = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        ParsedCapture parsed = parse(response, start, end, observedAt);
        byte[] rawRules = fetch(RULE_PATH);
        Instant ruleObservedAt = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        com.guidinglight.nexusquant.adapter.okx.model.OkxVenueRuleFactsSnapshot snapshot;
        try {
            snapshot = OkxVenueRuleFactsReader.parseSnapshot(
                    mapper.readTree(rawRules), Set.of("BTC-USDT"), ruleObservedAt);
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("PUBLIC_RULE_RESPONSE_INVALID", ex);
        }
        if (snapshot.observedAt().isBefore(observedAt.minus(Duration.ofMinutes(5)))
                || snapshot.observedAt().isAfter(Instant.now(clock).plusSeconds(5))) {
            throw new IllegalStateException("PUBLIC_RULE_STALE");
        }
        OkxVenueRuleFact rule = snapshot.facts().getFirst();
        if (!"BTC-USDT".equals(rule.instId()) || !"SPOT".equals(rule.instType())
                || !"LIVE".equals(rule.state()) || rule.tickSize().signum() <= 0
                || rule.lotSize().signum() <= 0 || rule.minimumSize().signum() <= 0) {
            throw new IllegalStateException("PUBLIC_RULE_NOT_TRADABLE");
        }
        ObjectNode ruleJson = mapper.createObjectNode();
        ruleJson.put("policy", "CURRENTLY_OBSERVED_PUBLIC_RULES");
        ruleJson.put("instrument", "BTC-USDT");
        ruleJson.put("state", rule.state());
        ruleJson.put("tickSize", decimal(rule.tickSize()));
        ruleJson.put("lotSize", decimal(rule.lotSize()));
        ruleJson.put("minimumSize", decimal(rule.minimumSize()));
        ruleJson.put("observedAt", snapshot.observedAt().toString());
        String ruleDigest = PublicReplayRuleIdentity.sha256(ruleJson);
        UUID datasetId = UUID.randomUUID();
        ObjectNode request = mapper.createObjectNode();
        request.put("provider", "public-capture");
        request.put("requestPath", path);
        request.put("requestedStart", start.toString());
        request.put("requestedEnd", end.toString());
        request.put("observedAt", observedAt.toString());
        request.put("rawSha256", parsed.rawSha256());
        request.put("normalizedSha256", parsed.normalizedSha256());
        request.put("consumedSha256", parsed.consumed().sha256());
        request.put("replayVisibilityVersion", VISIBILITY_VERSION);
        request.put("replayVisibilitySource", "EXPERIMENT_ASSUMPTION");
        request.put("ruleSha256", ruleDigest);
        request.put("ruleRawSha256", sha256(rawRules));
        request.put("ruleRequestPath", RULE_PATH);
        request.set("rule", ruleJson);
        Instant createdAt = Instant.now(clock);
        transactions.executeWithoutResult(status -> {
            jdbc.update("""
                    INSERT INTO marketdata_datasets(dataset_id,dataset_name,exchange_code,market_type,
                        symbol,"interval",start_time,end_time,status,quality_status,bar_count,gap_count,
                        source,created_by,created_at,updated_at,request_json)
                    VALUES (?,?,'OKX','SPOT','BTC-USDT','1h',?,?,'READY','OK',?,0,
                        'OKX_PUBLIC_CAPTURE',?,?,?,?::jsonb)
                    """, datasetId, "okx-public-" + datasetId,
                    Timestamp.from(start), Timestamp.from(end.minusMillis(1)), count,
                    createdBy, Timestamp.from(createdAt), Timestamp.from(createdAt), request.toString());
            jdbc.update("""
                    INSERT INTO public_market_captures(dataset_id,observed_at,requested_start,requested_end,
                        request_path,raw_response,raw_sha256,normalized_sha256,consumed_sha256,
                        replay_visibility_version,replay_visibility_source,rule_request_path,
                        rule_raw_response,rule_raw_sha256,rule_observed_at,rule_sha256,
                        rule_json,bars_json,bar_count,first_open_time,last_open_time)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?::jsonb,?,?,?)
                    """, datasetId, Timestamp.from(observedAt), Timestamp.from(start), Timestamp.from(end),
                    path, new String(response, StandardCharsets.UTF_8), parsed.rawSha256(),
                    parsed.normalizedSha256(), parsed.consumed().sha256(), VISIBILITY_VERSION,
                    "EXPERIMENT_ASSUMPTION", RULE_PATH,
                    new String(rawRules, StandardCharsets.UTF_8), sha256(rawRules),
                    Timestamp.from(snapshot.observedAt()), ruleDigest,
                    ruleJson.toString(), parsed.consumed().canonicalJson(), count,
                    Timestamp.from(start), Timestamp.from(end.minus(HOUR)));
        });
        return new CaptureView(datasetId, "OKX", "SPOT", "BTC-USDT", "1h", start, end,
                count, observedAt, VISIBILITY_VERSION, "EXPERIMENT_ASSUMPTION",
                parsed.rawSha256(), parsed.normalizedSha256(), parsed.consumed().sha256(),
                snapshot.observedAt(), sha256(rawRules), ruleDigest,
                "CURRENTLY_OBSERVED_PUBLIC_RULES");
    }

    private byte[] fetch(String path) {
        HttpRequest request = HttpRequest.newBuilder(origin.resolve(path))
                .timeout(Duration.ofSeconds(8)).GET().build();
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() == 200 && response.body().length > 0
                        && response.body().length <= 256_000) return response.body();
                if (response.statusCode() == 429) {
                    throw new IllegalStateException("PUBLIC_MARKET_RATE_LIMITED");
                }
                if (response.statusCode() < 500) {
                    throw new IllegalStateException("PUBLIC_MARKET_RESPONSE_REJECTED_" + response.statusCode());
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("PUBLIC_MARKET_INTERRUPTED", ex);
            } catch (java.io.IOException ex) {
                if (attempt == 2) throw new IllegalStateException("PUBLIC_MARKET_TRANSPORT_FAILURE", ex);
            }
            if (attempt == 2) break;
            try { Thread.sleep(500L * (attempt + 1)); }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("PUBLIC_MARKET_INTERRUPTED", ex);
            }
        }
        throw new IllegalStateException("PUBLIC_MARKET_RETRY_EXHAUSTED");
    }

    int validateWindow(Instant start, Instant end) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        long hours = ChronoUnit.HOURS.between(start, end);
        if (start.toEpochMilli() % HOUR.toMillis() != 0 || end.toEpochMilli() % HOUR.toMillis() != 0
                || hours < 1 || hours > 100 || !end.equals(start.plus(HOUR.multipliedBy(hours)))
                || end.isAfter(Instant.now(clock).minus(HOUR))) {
            throw new IllegalArgumentException("PUBLIC_CAPTURE_WINDOW_INVALID");
        }
        return (int) hours;
    }

    ParsedCapture parse(byte[] response, Instant start, Instant end, Instant observedAt) {
        validateWindow(start, end);
        var snapshot = PublicMarketReplayPayload.parse(response, start, end, observedAt, mapper);
        return new ParsedCapture(snapshot.rawSha256(), snapshot.normalizedSha256(),
                snapshot.consumed());
    }

    private static String decimal(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }

    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    record ParsedCapture(String rawSha256, String normalizedSha256, SpotBarIdentity.Snapshot consumed) { }

    public record CaptureView(UUID datasetId, String venue, String marketType, String instrument,
            String interval, Instant start, Instant end, int barCount, Instant observedAt,
            String replayVisibilityVersion, String replayVisibilitySource, String rawSha256,
            String normalizedSha256, String consumedSha256, Instant ruleObservedAt,
            String ruleRawSha256, String ruleSha256, String rulePolicy) { }
}
