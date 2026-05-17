package com.guidinglight.nexusquant.marketdata.infra.adapter;

import com.guidinglight.nexusquant.adapter.api.model.HistoricalKlineRequest;
import com.guidinglight.nexusquant.adapter.api.service.HistoricalKlineAdapter;
import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataIngestionJob;
import com.guidinglight.nexusquant.marketdata.domain.port.HistoricalKlineProvider;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/**
 * AdapterHistoricalKlineProvider 把 adapter-api 的交易所 K 线结果桥接为 core HistoricalBar。
 * <p>
 * Why:
 * core 不能依赖 OKX/Binance 具体实现，adapter 也不能直接写库；infra 在这里负责依赖装配和模型转换。
 */
@Component
public class AdapterHistoricalKlineProvider implements HistoricalKlineProvider {

    private final Map<String, HistoricalKlineAdapter> adapters;

    public AdapterHistoricalKlineProvider(List<HistoricalKlineAdapter> adapters) {
        this.adapters = Objects.requireNonNull(adapters, "adapters must not be null")
                .stream()
                .collect(Collectors.toUnmodifiableMap(
                        adapter -> adapter.exchangeCode().toUpperCase(Locale.ROOT),
                        Function.identity()
                ));
    }

    @Override
    public List<HistoricalBar> fetchBars(MarketdataIngestionJob job, java.time.Instant startTime, java.time.Instant endTime) {
        HistoricalKlineAdapter adapter = adapters.get(job.exchangeCode());
        if (adapter == null) {
            throw new IllegalArgumentException("historical kline adapter not found: " + job.exchangeCode());
        }
        return adapter.fetchHistoricalKlines(new HistoricalKlineRequest(
                        job.exchangeCode(),
                        job.marketType(),
                        job.symbol(),
                        job.interval().wireValue(),
                        startTime,
                        endTime,
                        500
                ))
                .stream()
                .map(bar -> new HistoricalBar(
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
                        bar.rawPayloadJson()
                ))
                .toList();
    }
}
