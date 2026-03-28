package com.guidinglight.nexusquant.app.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.adapter.binance.service.BinanceExchangeAdapter;
import com.guidinglight.nexusquant.adapter.binance.ws.BinanceWsClient;
import com.guidinglight.nexusquant.adapter.binance.ws.BinanceWsEventMapper;
import com.guidinglight.nexusquant.adapter.okx.model.OkxApiCredentials;
import com.guidinglight.nexusquant.adapter.okx.service.OkxApiException;
import com.guidinglight.nexusquant.adapter.okx.service.OkxExchangeAdapter;
import com.guidinglight.nexusquant.adapter.okx.service.OkxHttpClient;
import com.guidinglight.nexusquant.adapter.okx.service.OkxInstrumentsCache;
import com.guidinglight.nexusquant.adapter.okx.service.OkxWsClient;
import com.guidinglight.nexusquant.adapter.okx.service.OkxWsEventMapper;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ExchangeAdapterConfiguration 负责真实交易所适配器与 WS 连接治理装配。
 */
@Configuration
public class ExchangeAdapterConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ExchangeAdapterConfiguration.class);

    @Bean
    public OkxExchangeAdapter okxTradingAdapter(
            @Value("${nq.okx.adapter.stub-on-bootstrap-failure:false}") boolean stubOnBootstrapFailure
    ) {
        try {
            return new OkxExchangeAdapter();
        } catch (RuntimeException ex) {
            if (!stubOnBootstrapFailure) {
                throw ex;
            }
            log.warn(
                    "okx_adapter_bootstrap_fallback_enabled reason={} impact=okx_calls_return_stub_rejection_until_real_adapter_enabled",
                    ex.getMessage()
            );
            return createBootstrapSafeOkxAdapter(ex);
        }
    }

    @Bean
    public BinanceExchangeAdapter binanceTradingAdapter() {
        return new BinanceExchangeAdapter();
    }

    @Bean
    public OkxWsClient okxWsClient() {
        return new OkxWsClient();
    }

    @Bean
    public OkxWsEventMapper okxWsEventMapper() {
        return new OkxWsEventMapper();
    }

    @Bean
    public BinanceWsClient binanceWsClient() {
        return new BinanceWsClient();
    }

    @Bean
    public BinanceWsEventMapper binanceWsEventMapper() {
        return new BinanceWsEventMapper();
    }

    private OkxExchangeAdapter createBootstrapSafeOkxAdapter(RuntimeException bootstrapFailure) {
        ObjectMapper objectMapper = new ObjectMapper();
        Clock clock = Clock.systemUTC();
        OkxHttpClient publicStubClient = new OkxHttpClient(
                HttpClient.newHttpClient(),
                objectMapper,
                "http://127.0.0.1",
                Duration.ofSeconds(1),
                new com.guidinglight.nexusquant.adapter.okx.service.OkxRequestSigner(),
                () -> "1970-01-01T00:00:00Z",
                new OkxApiCredentials("", "", ""),
                false
        ) {
            @Override
            public JsonNode get(String requestPathWithQuery, String traceId) {
                return buildStubInstrumentsPayload(objectMapper);
            }
        };
        OkxHttpClient authenticatedStubClient = new OkxHttpClient(
                HttpClient.newHttpClient(),
                objectMapper,
                "http://127.0.0.1",
                Duration.ofSeconds(1),
                new com.guidinglight.nexusquant.adapter.okx.service.OkxRequestSigner(),
                () -> "1970-01-01T00:00:00Z",
                new OkxApiCredentials("", "", ""),
                false
        ) {
            @Override
            public JsonNode get(String requestPathWithQuery, String traceId) {
                throw disabledStubException(requestPathWithQuery, traceId, bootstrapFailure);
            }

            @Override
            public JsonNode post(String requestPath, String requestBodyJson, String traceId) {
                throw disabledStubException(requestPath, traceId, bootstrapFailure);
            }
        };
        OkxInstrumentsCache instrumentsCache = new OkxInstrumentsCache(publicStubClient, clock, Duration.ofDays(1));
        return new OkxExchangeAdapter(new OkxExchangeAdapter.Dependencies(
                objectMapper,
                authenticatedStubClient,
                instrumentsCache,
                clock
        ));
    }

    private JsonNode buildStubInstrumentsPayload(ObjectMapper objectMapper) {
        var root = objectMapper.createObjectNode();
        root.put("code", "0");
        var data = root.putArray("data");
        var btcUsdt = data.addObject();
        btcUsdt.put("instId", "BTC-USDT");
        btcUsdt.put("tickSz", "0.01");
        btcUsdt.put("lotSz", "0.00000001");
        btcUsdt.put("minSz", "0.00000001");
        btcUsdt.put("state", "live");
        return root;
    }

    private OkxApiException disabledStubException(String endpoint, String traceId, RuntimeException bootstrapFailure) {
        return new OkxApiException(
                "OKX adapter bootstrap fallback active, endpoint=" + endpoint
                        + ", trace_id=" + traceId
                        + ", bootstrap_reason=" + bootstrapFailure.getMessage(),
                0,
                endpoint,
                "OKX_ADAPTER_BOOTSTRAP_STUB",
                traceId,
                bootstrapFailure
        );
    }
}
