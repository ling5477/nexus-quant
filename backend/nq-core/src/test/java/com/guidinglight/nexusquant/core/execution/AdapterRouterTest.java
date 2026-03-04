package com.guidinglight.nexusquant.core.execution;

import static org.junit.jupiter.api.Assertions.assertSame;

import com.guidinglight.nexusquant.adapter.api.model.AdapterCancelAck;
import com.guidinglight.nexusquant.adapter.api.model.AdapterCancelRequest;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOpenOrdersQuery;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderAck;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderQuery;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderRequest;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderSnapshot;
import com.guidinglight.nexusquant.adapter.api.service.AccountAdapter;
import com.guidinglight.nexusquant.adapter.api.service.MarketDataAdapter;
import com.guidinglight.nexusquant.adapter.api.service.NoopAccountAdapter;
import com.guidinglight.nexusquant.adapter.api.service.NoopMarketDataAdapter;
import com.guidinglight.nexusquant.adapter.api.service.TradingAdapter;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * AdapterRouterTest 验证 accountId + venue 可以稳定命中对应 adapter 集合。
 */
class AdapterRouterTest {

    /**
     * 验证按 venue 路由时会返回同一 venue 的交易/行情/账户端口。
     */
    @Test
    void shouldRouteAdaptersByVenue() {
        TradingAdapter paperTrading = new StubTradingAdapter("PAPER");
        TradingAdapter okxTrading = new StubTradingAdapter("OKX");
        MarketDataAdapter paperMarketData = new NoopMarketDataAdapter("PAPER");
        MarketDataAdapter okxMarketData = new NoopMarketDataAdapter("OKX");
        AccountAdapter paperAccount = new NoopAccountAdapter("PAPER");
        AccountAdapter okxAccount = new NoopAccountAdapter("OKX");
        AdapterRouter router = new AdapterRouter(
                List.of(paperTrading, okxTrading),
                List.of(paperMarketData, okxMarketData),
                List.of(paperAccount, okxAccount)
        );

        AdapterRouter.RoutedAdapters paperAdapters = router.route(1001L, "PAPER");
        AdapterRouter.RoutedAdapters okxAdapters = router.route(1002L, "okx");

        assertSame(paperTrading, paperAdapters.trading());
        assertSame(paperMarketData, paperAdapters.marketData());
        assertSame(paperAccount, paperAdapters.account());
        assertSame(okxTrading, okxAdapters.trading());
        assertSame(okxMarketData, okxAdapters.marketData());
        assertSame(okxAccount, okxAdapters.account());
    }

    private record StubTradingAdapter(String venue) implements TradingAdapter {

        @Override
        public AdapterOrderAck placeOrder(AdapterOrderRequest request) {
            return new AdapterOrderAck(true, venue, venue + "-external", null, Instant.now(), request.traceId());
        }

        @Override
        public AdapterCancelAck cancelOrder(AdapterCancelRequest request) {
            return new AdapterCancelAck(true, venue, request.externalOrderId(), null, Instant.now(), request.traceId());
        }

        @Override
        public AdapterOrderSnapshot getOrder(AdapterOrderQuery query) {
            return new AdapterOrderSnapshot(
                    query.accountId(),
                    venue,
                    query.symbol(),
                    query.clientOrderId(),
                    query.externalOrderId(),
                    "ACCEPTED",
                    query.traceId()
            );
        }

        @Override
        public List<AdapterOrderSnapshot> listOpenOrders(AdapterOpenOrdersQuery query) {
            return List.of();
        }
    }
}
