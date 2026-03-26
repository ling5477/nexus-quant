package com.guidinglight.nexusquant.app.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.adapter.api.service.AccountAdapter;
import com.guidinglight.nexusquant.adapter.api.service.MarketDataAdapter;
import com.guidinglight.nexusquant.adapter.api.service.NoopAccountAdapter;
import com.guidinglight.nexusquant.adapter.api.service.NoopMarketDataAdapter;
import com.guidinglight.nexusquant.adapter.okx.model.OkxApiCredentials;
import com.guidinglight.nexusquant.adapter.binance.service.BinanceExchangeAdapter;
import com.guidinglight.nexusquant.adapter.binance.ws.BinanceWsClient;
import com.guidinglight.nexusquant.adapter.binance.ws.BinanceWsEventMapper;
import com.guidinglight.nexusquant.adapter.okx.service.OkxApiException;
import com.guidinglight.nexusquant.adapter.okx.service.OkxExchangeAdapter;
import com.guidinglight.nexusquant.adapter.okx.service.OkxHttpClient;
import com.guidinglight.nexusquant.adapter.okx.service.OkxInstrumentsCache;
import com.guidinglight.nexusquant.adapter.okx.service.OkxWsClient;
import com.guidinglight.nexusquant.adapter.okx.service.OkxWsEventMapper;
import com.guidinglight.nexusquant.api.service.CoreTradingQueryFacade;
import com.guidinglight.nexusquant.api.service.TradingQueryFacade;
import com.guidinglight.nexusquant.config.service.ConfigSnapshotService;
import com.guidinglight.nexusquant.config.service.InMemoryConfigSnapshotService;
import com.guidinglight.nexusquant.core.recovery.RecoveryService;
import com.guidinglight.nexusquant.core.state.InMemoryOrderStateMachine;
import com.guidinglight.nexusquant.core.state.OrderStateMachine;
import com.guidinglight.nexusquant.ledger.service.LedgerService;
import com.guidinglight.nexusquant.ledger.service.NoopLedgerService;
import com.guidinglight.nexusquant.risk.service.KillSwitchService;
import com.guidinglight.nexusquant.risk.service.PreTradeRiskService;
import com.guidinglight.nexusquant.risk.service.PreTradeRiskSettings;
import com.guidinglight.nexusquant.risk.service.AccountTradingEnabledRule;
import com.guidinglight.nexusquant.risk.service.DuplicateRequestRule;
import com.guidinglight.nexusquant.risk.service.KillSwitchRiskRule;
import com.guidinglight.nexusquant.risk.service.MaxOrderAmountRule;
import com.guidinglight.nexusquant.risk.service.MinNotionalRule;
import com.guidinglight.nexusquant.risk.service.OrderPrecisionRule;
import com.guidinglight.nexusquant.risk.service.RiskGate;
import com.guidinglight.nexusquant.risk.service.RiskRuleRegistry;
import com.guidinglight.nexusquant.risk.service.RateLimitRule;
import com.guidinglight.nexusquant.risk.service.SymbolEnabledRule;
import com.guidinglight.nexusquant.scheduler.service.PaperTradingAdapter;
import com.guidinglight.nexusquant.scheduler.service.OkxRecoveryService;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * ModuleWiringConfiguration 负责模块级占位 Bean 装配。
 * <p>
 * Why:
 * Gate A 要求工程“可启动但空业务”，因此需要在启动模块提供可运行默认实现，
 * 后续各模块实现替换时只需覆写对应 Bean。
 */
@Configuration
public class ModuleWiringConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ModuleWiringConfiguration.class);

    @Bean
    public OrderStateMachine orderStateMachine() {
        return new InMemoryOrderStateMachine();
    }

    @Bean
    public RecoveryService recoveryService(OkxRecoveryService okxRecoveryService) {
        return okxRecoveryService;
    }

    @Bean
    public LedgerService ledgerService() {
        return new NoopLedgerService();
    }

    @Bean
    public PreTradeRiskSettings preTradeRiskSettings() {
        return PreTradeRiskSettings.defaults();
    }

    @Bean
    public RiskGate riskGate(KillSwitchService killSwitchService, PreTradeRiskSettings preTradeRiskSettings) {
        return new PreTradeRiskService(new RiskRuleRegistry(java.util.List.of(
                new KillSwitchRiskRule(killSwitchService),
                new AccountTradingEnabledRule(preTradeRiskSettings),
                new SymbolEnabledRule(preTradeRiskSettings),
                new DuplicateRequestRule(preTradeRiskSettings),
                new RateLimitRule(preTradeRiskSettings),
                new OrderPrecisionRule(preTradeRiskSettings),
                new MinNotionalRule(preTradeRiskSettings),
                new MaxOrderAmountRule(preTradeRiskSettings)
        )));
    }

    @Bean
    public KillSwitchService killSwitchService() {
        return new KillSwitchService();
    }

    @Bean
    public ConfigSnapshotService configSnapshotService() {
        return new InMemoryConfigSnapshotService();
    }

    @Bean
    public TradingQueryFacade tradingQueryFacade(JdbcTemplate jdbcTemplate) {
        return new CoreTradingQueryFacade(jdbcTemplate);
    }

    /**
     * 提供 PAPER 的交易端口实现。
     */
    @Bean
    public PaperTradingAdapter paperTradingAdapter() {
        return new PaperTradingAdapter();
    }

    /**
     * 提供 OKX 的交易端口实现。
     * <p>
     * Why:
     * 本地验收与 CI 环境并不总能连外网；若在 bean 构造阶段就强依赖 OKX public instruments，
     * `nq-app` 会在 health 之前直接失败。这里允许 local profile 在 bootstrap 失败时退回到本地 stub，
     * 但真实验收仍可通过显式关闭 fallback 来保留 fail-fast。
     */
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

    /**
     * 提供 Binance 的交易端口实现。
     */
    @Bean
    public BinanceExchangeAdapter binanceTradingAdapter() {
        return new BinanceExchangeAdapter();
    }

    /**
     * 提供 OKX 私有 WS 治理客户端（PR-W1）。
     * <p>
     * Why:
     * 该 bean 只负责连接治理，不做业务落库；是否启动由 `nq.okx.ws.enabled` 控制的 local smoke runner 决定。
     */
    @Bean
    public OkxWsClient okxWsClient() {
        return new OkxWsClient();
    }

    /**
     * 提供 OKX WS 消息映射器（PR-W2）。
     * <p>
     * Why:
     * mapper 只负责把 WS 原始消息转为标准 EventEnvelope，不直接依赖数据库或业务服务。
     */
    @Bean
    public OkxWsEventMapper okxWsEventMapper() {
        return new OkxWsEventMapper();
    }

    /**
     * 提供 Binance 私有 WS 治理客户端（PR-BW1）。
     * <p>
     * Why:
     * 该 bean 只负责 listenKey 生命周期与连接治理，不做 executionReport 映射、不写业务表；
     * 是否启动由 `nq.binance.ws.enabled` 控制的 local smoke runner 决定。
     */
    @Bean
    public BinanceWsClient binanceWsClient() {
        return new BinanceWsClient();
    }

    /**
     * 提供 Binance WS 消息映射器（PR-BW2）。
     * <p>
     * Why:
     * mapper 只负责把 Binance WS 原始消息转成标准 EventEnvelope，不直接依赖数据库或业务服务。
     */
    @Bean
    public BinanceWsEventMapper binanceWsEventMapper() {
        return new BinanceWsEventMapper();
    }

    /**
     * 提供 PAPER 的行情端口 stub。
     */
    @Bean
    public MarketDataAdapter paperMarketDataAdapter() {
        return new NoopMarketDataAdapter("PAPER");
    }

    /**
     * 提供 OKX 的行情端口 stub。
     */
    @Bean
    public MarketDataAdapter okxMarketDataAdapter() {
        return new NoopMarketDataAdapter("OKX");
    }

    /**
     * 提供 Binance 的行情端口 stub。
     */
    @Bean
    public MarketDataAdapter binanceMarketDataAdapter() {
        return new NoopMarketDataAdapter("BINANCE");
    }

    /**
     * 提供 PAPER 的账户端口 stub。
     */
    @Bean
    public AccountAdapter paperAccountAdapter() {
        return new NoopAccountAdapter("PAPER");
    }

    /**
     * 提供 OKX 的账户端口 stub。
     */
    @Bean
    public AccountAdapter okxAccountAdapter() {
        return new NoopAccountAdapter("OKX");
    }

    /**
     * 提供 Binance 的账户端口 stub。
     */
    @Bean
    public AccountAdapter binanceAccountAdapter() {
        return new NoopAccountAdapter("BINANCE");
    }

    /**
     * 生成只用于本地启动与 health 验证的 OKX stub adapter。
     * <p>
     * Why:
     * GateD 需要 `nq-app` 在 local/verify 环境先能稳定起起来；若外网不可达，最小可运行方案应该是
     * “启动成功 + 明确拒绝 OKX 实单调用”，而不是在构造 bean 时直接把整个应用拖死。
     */
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
