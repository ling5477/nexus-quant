package com.guidinglight.nexusquant.app.config;

import com.guidinglight.nexusquant.adapter.binance.service.BinanceExchangeAdapter;
import com.guidinglight.nexusquant.adapter.api.service.AdapterReadinessService;
import com.guidinglight.nexusquant.adapter.api.service.DefaultAdapterReadinessService;
import com.guidinglight.nexusquant.adapter.api.service.TradingAdapter;
import com.guidinglight.nexusquant.adapter.binance.ws.BinanceWsClient;
import com.guidinglight.nexusquant.adapter.binance.ws.BinanceWsEventMapper;
import com.guidinglight.nexusquant.adapter.okx.service.OkxBootstrapFallbackFactory;
import com.guidinglight.nexusquant.adapter.okx.service.OkxExchangeAdapter;
import com.guidinglight.nexusquant.adapter.okx.service.OkxWsClient;
import com.guidinglight.nexusquant.adapter.okx.service.OkxWsEventMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

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

    /**
     * GateM 默认 readiness service 必须 always-on 且 fail-closed。
     * <p>
     * Why:
     * OKX / Binance trading Bean 已在 app 装配层依赖 readiness guard；即使未启用 local/test fallback profile，
     * Spring context 也必须获得一个无 IO、无凭证读取、永不产出 READY 的保守实现，避免缺 Bean 或误放行真实交易。
     */
    @Bean
    @Profile("!local & !test & !gated-verify")
    @ConditionalOnMissingBean(AdapterReadinessService.class)
    public AdapterReadinessService adapterReadinessService() {
        return new DefaultAdapterReadinessService();
    }

    /**
     * OKX adapter 在 app 装配层接入 readiness guard，同时保留具体类型 Bean。
     * <p>
     * Why:
     * 现有 scheduler 组件仍按 {@link OkxExchangeAdapter} 具体类型注入，而 trading gateway 按
     * {@link TradingAdapter} 集合消费。单 Bean 策略避免 duplicate venue，同时让四个交易动作在 adapter 本体入口
     * 先走 readiness fail-closed，当前 no-real / LIVE disabled baseline 下不会触达真实 OKX HTTP 逻辑。
     */
    @Bean
    @Profile("!gatew")
    public OkxExchangeAdapter okxTradingAdapter(
            @Value("${nq.okx.adapter.stub-on-bootstrap-failure:false}") boolean stubOnBootstrapFailure,
            AdapterReadinessService readinessService
    ) {
        try {
            return new OkxExchangeAdapter(readinessService);
        } catch (RuntimeException ex) {
            if (!stubOnBootstrapFailure) {
                throw ex;
            }
            log.warn(
                    "okx_adapter_bootstrap_fallback_enabled reason={} impact=okx_calls_return_stub_rejection_until_real_adapter_enabled",
                    ex.getMessage()
            );
            return OkxBootstrapFallbackFactory.create(ex, readinessService);
        }
    }

    /**
     * Binance adapter 在 app 装配层接入 readiness guard，同时保留具体类型 Bean。
     */
    @Bean
    @Profile("!gatew")
    public BinanceExchangeAdapter binanceTradingAdapter(AdapterReadinessService readinessService) {
        return new BinanceExchangeAdapter(readinessService);
    }

    @Bean
    @Profile("!gatew")
    public OkxWsClient okxWsClient() {
        return new OkxWsClient();
    }

    @Bean
    public OkxWsEventMapper okxWsEventMapper() {
        return new OkxWsEventMapper();
    }

    @Bean
    @Profile("!gatew")
    public BinanceWsClient binanceWsClient() {
        return new BinanceWsClient();
    }

    @Bean
    public BinanceWsEventMapper binanceWsEventMapper() {
        return new BinanceWsEventMapper();
    }
}
