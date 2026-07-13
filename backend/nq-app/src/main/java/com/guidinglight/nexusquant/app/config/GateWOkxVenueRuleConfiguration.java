package com.guidinglight.nexusquant.app.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.adapter.okx.service.OkxHttpClient;
import com.guidinglight.nexusquant.adapter.okx.service.OkxVenueRuleFactsReader;
import com.guidinglight.nexusquant.app.marketdata.OkxVenueRuleFactsSyncService;
import com.guidinglight.nexusquant.marketdata.application.instrument.InstrumentCatalogService;
import com.guidinglight.nexusquant.marketdata.domain.instrument.VenueRuleFreshnessEvaluator;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * GateWOkxVenueRuleConfiguration 是 GateW-3 public-only venue-rule sync 的装配边界。
 *
 * <p>默认/test/CI 不装配 reader 或 sync service，因此保持 no-egress。只有显式
 * `gatew-venue-rules-manual` profile 与 enabled=true 同时满足时才构造 public client；构造和启动不发请求，
 * 实际网络读取仍只能由 operator 直接调用 service。该配置不读取 API key/secret/passphrase，不装配
 * Controller、scheduler、runner、private transport 或 trading adapter。</p>
 */
@Configuration
public class GateWOkxVenueRuleConfiguration {

    /**
     * 新鲜度 evaluator 始终可用，但配置缺失/非数字/越界时由 evaluator 返回 UNKNOWN/BLOCKED。
     */
    @Bean
    public VenueRuleFreshnessEvaluator venueRuleFreshnessEvaluator(
            @Value("${nq.gatew.okx-venue-rules.stale-after-seconds:}") String staleAfterSeconds
    ) {
        return new VenueRuleFreshnessEvaluator(Clock.systemUTC(), parseLongOrNull(staleAfterSeconds));
    }

    /**
     * 构造固定 public instruments endpoint reader；base URL 必须显式注入，默认仅为不可连接的 localhost。
     */
    @Bean
    @Profile("gatew-venue-rules-manual")
    @ConditionalOnProperty(
            prefix = "nq.gatew.okx-venue-rules",
            name = "enabled",
            havingValue = "true"
    )
    public OkxVenueRuleFactsReader okxVenueRuleFactsReader(
            ObjectMapper objectMapper,
            @Value("${nq.gatew.okx-venue-rules.base-url:http://127.0.0.1:0}") String baseUrl,
            @Value("${nq.gatew.okx-venue-rules.timeout:PT5S}") String timeout
    ) {
        URI validatedBaseUrl = validateBaseUrl(baseUrl);
        Duration requestTimeout = DurationStyle.detectAndParse(timeout);
        OkxHttpClient publicClient = new OkxHttpClient(
                HttpClient.newBuilder().connectTimeout(requestTimeout).build(),
                objectMapper,
                validatedBaseUrl.toString(),
                requestTimeout
        );
        return new OkxVenueRuleFactsReader(publicClient, Clock.systemUTC());
    }

    /**
     * 装配无 HTTP 入口的 operator-triggered bounded application service。
     */
    @Bean
    @Profile("gatew-venue-rules-manual")
    @ConditionalOnProperty(
            prefix = "nq.gatew.okx-venue-rules",
            name = "enabled",
            havingValue = "true"
    )
    public OkxVenueRuleFactsSyncService okxVenueRuleFactsSyncService(
            InstrumentCatalogService instrumentCatalogService,
            OkxVenueRuleFactsReader venueRuleFactsReader,
            @Value("${nq.gatew.okx-venue-rules.allowlist:}") String allowlist
    ) {
        return new OkxVenueRuleFactsSyncService(
                instrumentCatalogService,
                venueRuleFactsReader,
                Clock.systemUTC(),
                parseAllowlist(allowlist)
        );
    }

    private static Long parseLongOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Set<String> parseAllowlist(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static URI validateBaseUrl(String raw) {
        URI uri = URI.create(raw == null ? "" : raw.trim());
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null
                || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null
                || (uri.getPath() != null && !uri.getPath().isBlank() && !"/".equals(uri.getPath()))) {
            throw new IllegalArgumentException("venue-rule base-url must be an HTTP(S) origin without credentials/path/query");
        }
        String normalized = uri.toString();
        return normalized.endsWith("/") ? URI.create(normalized.substring(0, normalized.length() - 1)) : uri;
    }
}
