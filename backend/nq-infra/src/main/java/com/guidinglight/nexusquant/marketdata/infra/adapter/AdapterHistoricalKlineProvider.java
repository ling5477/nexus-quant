package com.guidinglight.nexusquant.marketdata.infra.adapter;

import com.guidinglight.nexusquant.adapter.api.model.HistoricalKlineRequest;
import com.guidinglight.nexusquant.adapter.api.service.port.HistoricalKlineAdapter;
import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataIngestionJob;
import com.guidinglight.nexusquant.marketdata.domain.port.HistoricalKlineProvider;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AdapterHistoricalKlineProvider 把 adapter-api 的交易所 K 线结果桥接为 core HistoricalBar。
 * <p>
 * Why:
 * core 不能依赖 OKX/Binance 具体实现，adapter 也不能直接写库；infra 在这里负责依赖装配和模型转换。
 */
@Component
public class AdapterHistoricalKlineProvider implements HistoricalKlineProvider {

    private final Map<String, HistoricalKlineAdapter> adapters;
    private final Environment environment;

    public AdapterHistoricalKlineProvider(
            List<HistoricalKlineAdapter> adapters, Environment environment) {
        this.environment = Objects.requireNonNull(environment, "environment must not be null");
        this.adapters =
                Objects.requireNonNull(adapters, "adapters must not be null").stream()
                        .collect(
                                Collectors.toUnmodifiableMap(
                                        adapter -> adapter.exchangeCode().toUpperCase(Locale.ROOT),
                                        Function.identity()));
    }

    @Override
    public List<HistoricalBar> fetchBars(
            MarketdataIngestionJob job, Instant startTime, Instant endTime) {
        requireManualPublicRead();
        HistoricalKlineAdapter adapter = adapters.get(job.exchangeCode());
        if (adapter == null) {
            throw new IllegalArgumentException(
                    "historical kline adapter not found: " + job.exchangeCode());
        }
        return adapter
                .fetchHistoricalKlines(
                        new HistoricalKlineRequest(
                                job.exchangeCode(),
                                job.marketType(),
                                job.symbol(),
                                job.interval().wireValue(),
                                startTime,
                                endTime,
                                500))
                .stream()
                .map(
                        bar ->
                                new HistoricalBar(
                                        bar.exchangeCode(),
                                        bar.marketType(),
                                        bar.symbol(),
                                        BarInterval.fromWireValue(bar.interval()),
                                        bar.openTime(),
                                        bar.closeTime(),
                                        bar.openPrice(),
                                        bar.highPrice(),
                                        bar.lowPrice(),
                                        bar.closePrice(),
                                        bar.volume(),
                                        bar.quoteVolume(),
                                        bar.tradeCount(),
                                        "OK",
                                        bar.rawPayloadJson()))
                .toList();
    }

    /** 接口可达不等于允许外联；在任何适配器调用之前检查当前公开读取开关。 */
    private void requireManualPublicRead() {
        Set<String> profiles = Set.copyOf(Arrays.asList(environment.getActiveProfiles()));
        boolean enabled =
                environment.getProperty(
                        "nq.public-marketdata.outbound.enabled", Boolean.class, false);
        boolean noOutbound =
                environment.getProperty(
                        "nq.env-safety.no-outbound",
                        Boolean.class,
                        environment.getProperty("NQ_NO_OUTBOUND", Boolean.class, false));
        boolean ci =
                environment.getProperty(
                        "nq.env-safety.ci",
                        Boolean.class,
                        environment.getProperty("CI", Boolean.class, false));
        // 当前正式手动来源只支持local/prod与manual组合；其他测试或未知profile不获得隐式外联能力。
        if (!enabled
                || noOutbound
                || ci
                || !profiles.contains("public-marketdata-manual")
                || !Set.of("local", "prod", "public-marketdata-manual").containsAll(profiles)) {
            throw new IllegalStateException("PUBLIC_HISTORY_OUTBOUND_DISABLED");
        }
    }
}
