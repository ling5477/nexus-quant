package com.guidinglight.nexusquant.app.config;

import com.guidinglight.nexusquant.adapter.binance.service.BinanceExchangeAdapter;
import com.guidinglight.nexusquant.adapter.binance.ws.BinanceWsClient;
import com.guidinglight.nexusquant.adapter.binance.ws.BinanceWsEventMapper;
import com.guidinglight.nexusquant.adapter.okx.service.OkxBootstrapFallbackFactory;
import com.guidinglight.nexusquant.adapter.okx.service.OkxExchangeAdapter;
import com.guidinglight.nexusquant.adapter.okx.service.OkxWsClient;
import com.guidinglight.nexusquant.adapter.okx.service.OkxWsEventMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ExchangeAdapterConfiguration 负责真实交易所适配器与 WS 连接 Bean 装配。
 * <p>
 * Why:
 * PRE-CLEAN-2 后，`nq-app` 只决定 profile/Bean 选择，不再内联 OKX fallback HTTP stub 细节；
 * 具体 fallback adapter 构造已经下沉到 `nq-adapter-okx`。
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
            return OkxBootstrapFallbackFactory.create(ex);
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
}
