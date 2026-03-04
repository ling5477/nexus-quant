package com.guidinglight.nexusquant.core.execution;

import com.guidinglight.nexusquant.adapter.api.service.AccountAdapter;
import com.guidinglight.nexusquant.adapter.api.service.MarketDataAdapter;
import com.guidinglight.nexusquant.adapter.api.service.TradingAdapter;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/**
 * AdapterRouter 负责把 core 的统一调用路由到具体 adapter 端口集合。
 * <p>
 * Why:
 * GateC-0 的目标不是把 OKX/Binance 逻辑塞进 core，而是让 core 只认识 adapter-api。
 * 因此路由逻辑必须在一个集中点完成，后续扩展新 venue 只需注册新 adapter Bean。
 */
@Component
public class AdapterRouter {

    private final Map<String, TradingAdapter> tradingAdapters;
    private final Map<String, MarketDataAdapter> marketDataAdapters;
    private final Map<String, AccountAdapter> accountAdapters;

    /**
     * @param tradingAdapters    交易端口集合
     * @param marketDataAdapters 行情端口集合
     * @param accountAdapters    账户端口集合
     */
    public AdapterRouter(
            Collection<TradingAdapter> tradingAdapters,
            Collection<MarketDataAdapter> marketDataAdapters,
            Collection<AccountAdapter> accountAdapters
    ) {
        this.tradingAdapters = indexByVenue(tradingAdapters, TradingAdapter::venue);
        this.marketDataAdapters = indexByVenue(marketDataAdapters, MarketDataAdapter::venue);
        this.accountAdapters = indexByVenue(accountAdapters, AccountAdapter::venue);
    }

    /**
     * 按 accountId + venue 返回完整适配器集合。
     * <p>
     * Why:
     * 当前阶段主要按 venue 路由，但 accountId 仍保留在签名中，为后续账户级限流、
     * 账户路由或多 key 场景预留稳定接口，避免再次改 core 调用点。
     *
     * @param accountId 账户 ID
     * @param venue     交易场所
     * @return 同一 venue 的 adapter 集合
     */
    public RoutedAdapters route(Long accountId, String venue) {
        if (accountId == null || accountId <= 0) {
            throw new IllegalArgumentException("accountId must be positive");
        }
        String normalizedVenue = normalizeVenue(venue);
        return new RoutedAdapters(
                getRequired(tradingAdapters, normalizedVenue, "TradingAdapter"),
                getRequired(marketDataAdapters, normalizedVenue, "MarketDataAdapter"),
                getRequired(accountAdapters, normalizedVenue, "AccountAdapter")
        );
    }

    private static <T> Map<String, T> indexByVenue(Collection<T> adapters, Function<T, String> venueExtractor) {
        return adapters.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableMap(
                        adapter -> normalizeVenue(venueExtractor.apply(adapter)),
                        Function.identity(),
                        (left, right) -> {
                            throw new IllegalStateException("duplicate adapter for venue: " + venueExtractor.apply(left));
                        }
                ));
    }

    private static String normalizeVenue(String venue) {
        if (venue == null || venue.isBlank()) {
            throw new IllegalArgumentException("venue must not be blank");
        }
        return venue.trim().toUpperCase(Locale.ROOT);
    }

    private static <T> T getRequired(Map<String, T> adapters, String venue, String adapterType) {
        T adapter = adapters.get(venue);
        if (adapter == null) {
            throw new IllegalArgumentException(adapterType + " not configured for venue: " + venue);
        }
        return adapter;
    }

    /**
     * RoutedAdapters 聚合同一 venue 的三类端口。
     */
    public record RoutedAdapters(
            TradingAdapter trading,
            MarketDataAdapter marketData,
            AccountAdapter account
    ) {
    }
}
