package com.guidinglight.nexusquant.app.config;

import com.guidinglight.nexusquant.adapter.api.publicmarketdata.DisabledPublicMarketDataOutboundClient;
import com.guidinglight.nexusquant.adapter.api.publicmarketdata.JdkPublicMarketDataOutboundClient;
import com.guidinglight.nexusquant.adapter.api.publicmarketdata.PublicMarketDataOutboundClient;
import com.guidinglight.nexusquant.adapter.api.publicmarketdata.PublicMarketDataOutboundPolicy;
import com.guidinglight.nexusquant.adapter.api.publicmarketdata.PublicMarketDataOutboundSettings;
import com.guidinglight.nexusquant.adapter.api.publicmarketdata.PublicMarketDataQualitySummary;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * PublicMarketDataOutboundConfiguration 是 GateO O-1 public outbound 的 Spring 装配边界。
 *
 * <p>Why: 默认 local/test/CI/paper/freeze 必须 no-egress，不得构造真实 HTTP client。只有
 * `public-marketdata-manual` profile 且 `nq.public-marketdata.outbound.enabled=true` 时，才装配
 * {@link JdkPublicMarketDataOutboundClient}。flag 缺失或关闭时仅装配 disabled fallback client，
 * 不读取 credential、不启用 LIVE、不接 RealClient 或 private adapter。</p>
 */
@Configuration
public class PublicMarketDataOutboundConfiguration {

    /**
     * feature flag 关闭或缺失时的 fallback client。
     *
     * @param fallbackOrigin LOCAL_DB / FIXTURE / FAKE_SERVER；默认 LOCAL_DB
     * @return 不创建 HTTP client、不访问网络的 disabled client
     */
    @Bean
    @ConditionalOnMissingBean(PublicMarketDataOutboundClient.class)
    @ConditionalOnProperty(
            prefix = "nq.public-marketdata.outbound",
            name = "enabled",
            havingValue = "false",
            matchIfMissing = true)
    public PublicMarketDataOutboundClient disabledPublicMarketDataOutboundClient(
            @Value("${nq.public-marketdata.outbound.fallback-origin:LOCAL_DB}") String fallbackOrigin) {
        return new DisabledPublicMarketDataOutboundClient(parseFallbackOrigin(fallbackOrigin));
    }

    /**
     * 手动 profile 下唯一允许构造的 public REST HTTP client。
     *
     * <p>边界：该 Bean 只消费 public outbound 配置，不读取任何 exchange credential；policy 仍会在
     * 每次请求前拒绝 private/signed/credential-like endpoint，retry 也不会绕过 policy。</p>
     *
     * @param baseUrl             public marketdata base URL；O-1 测试只使用 localhost fake server
     * @param connectTimeout      connect timeout，默认 3s
     * @param readTimeout         read timeout 语义，默认 5s
     * @param totalRequestTimeout 单次请求总超时，默认 8s
     * @param maxRetries          最大 retry 次数，默认 2 且上限 2
     * @param firstBackoff        第一次 retry backoff，默认 500ms
     * @param secondBackoff       第二次 retry backoff，默认 1000ms
     * @return 受 policy 保护的 JDK HTTP public outbound client
     */
    @Bean
    @Profile("public-marketdata-manual")
    @ConditionalOnProperty(
            prefix = "nq.public-marketdata.outbound",
            name = "enabled",
            havingValue = "true")
    public PublicMarketDataOutboundClient publicMarketDataOutboundClient(
            @Value("${nq.public-marketdata.outbound.base-url:http://127.0.0.1:0}") String baseUrl,
            @Value("${nq.public-marketdata.outbound.connect-timeout:PT3S}") String connectTimeout,
            @Value("${nq.public-marketdata.outbound.read-timeout:PT5S}") String readTimeout,
            @Value("${nq.public-marketdata.outbound.total-request-timeout:PT8S}") String totalRequestTimeout,
            @Value("${nq.public-marketdata.outbound.max-retries:2}") int maxRetries,
            @Value("${nq.public-marketdata.outbound.first-backoff:PT0.5S}") String firstBackoff,
            @Value("${nq.public-marketdata.outbound.second-backoff:PT1S}") String secondBackoff) {
        return new JdkPublicMarketDataOutboundClient(
                URI.create(baseUrl),
                new PublicMarketDataOutboundPolicy(),
                new PublicMarketDataOutboundSettings(
                        parseDuration(connectTimeout),
                        parseDuration(readTimeout),
                        parseDuration(totalRequestTimeout),
                        maxRetries,
                        parseDuration(firstBackoff),
                        parseDuration(secondBackoff)));
    }

    private static PublicMarketDataQualitySummary.DataOrigin parseFallbackOrigin(String value) {
        PublicMarketDataQualitySummary.DataOrigin origin = PublicMarketDataQualitySummary.DataOrigin.valueOf(
                value.trim().toUpperCase(Locale.ROOT));
        if (origin == PublicMarketDataQualitySummary.DataOrigin.PUBLIC_OUTBOUND) {
            throw new IllegalArgumentException("fallback-origin must not be PUBLIC_OUTBOUND");
        }
        return origin;
    }

    private static Duration parseDuration(String value) {
        return DurationStyle.detectAndParse(value);
    }
}
