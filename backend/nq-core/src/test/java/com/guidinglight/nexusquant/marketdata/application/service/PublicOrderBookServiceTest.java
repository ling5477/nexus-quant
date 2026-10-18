package com.guidinglight.nexusquant.marketdata.application.service;

import static org.junit.jupiter.api.Assertions.*;

import com.guidinglight.nexusquant.marketdata.domain.OrderBookSourceException;
import com.guidinglight.nexusquant.marketdata.domain.PublicOrderBookSnapshot;
import com.guidinglight.nexusquant.marketdata.domain.port.PublicOrderBookReadPort;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** 固定时钟与显式并发屏障证明缓存、过期、来源失败及请求合并，不用概率性sleep。 */
class PublicOrderBookServiceTest {
    private static class Time extends Clock {
        Instant now = Instant.parse("2026-10-13T00:00:00Z");

        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        public Clock withZone(ZoneId zone) {
            return this;
        }

        public Instant instant() {
            return now;
        }
    }

    private static PublicOrderBookSnapshot book(String symbol, Instant time) {
        return new PublicOrderBookSnapshot(
                "OKX",
                "SPOT",
                symbol,
                "AVAILABLE",
                time,
                time,
                time,
                "OKX_PUBLIC_REST",
                20,
                null,
                List.of(new PublicOrderBookSnapshot.Level("100", "0.00000001", "0.00000001", 1)),
                List.of(new PublicOrderBookSnapshot.Level("101", "0.00000002", "0.00000002", 2)));
    }

    @Test
    void disabledDoesNotInferZeroLiquidityAndUnsupportedMarketsAreRejected() {
        var service = new PublicOrderBookService(List.of(), new Time());
        var view = service.read("OKX", "SPOT", "BTC-USDT");
        assertEquals("DISABLED", view.status());
        assertNull(view.sourceTime());
        assertNull(view.receivedAt());
        assertThrows(IllegalArgumentException.class, () -> service.read("OKX", "SWAP", "BTC-USDT"));
        assertThrows(
                IllegalArgumentException.class, () -> service.read("BINANCE", "SPOT", "BTC-USDT"));
    }

    @Test
    void cachesBySymbolAndKeepsRealSnapshotStaleWhenSourceFails() {
        var clock = new Time();
        var calls = new AtomicInteger();
        var source =
                new PublicOrderBookReadPort() {
                    public String exchangeCode() {
                        return "OKX";
                    }

                    public PublicOrderBookSnapshot fetch(String symbol) {
                        if (calls.incrementAndGet() > 1)
                            throw new OrderBookSourceException("SOURCE_UNAVAILABLE");
                        return book(symbol, clock.now);
                    }
                };
        var service = new PublicOrderBookService(List.of(source), clock);
        var first = service.read("OKX", "SPOT", "BTC-USDT");
        clock.now = clock.now.plusSeconds(4);
        assertEquals(first.receivedAt(), service.read("OKX", "SPOT", "BTC-USDT").receivedAt());
        assertEquals(1, calls.get());
        clock.now = clock.now.plusSeconds(2);
        var failed = service.read("OKX", "SPOT", "BTC-USDT");
        assertEquals("STALE", failed.status());
        assertEquals(first.bids(), failed.bids());
        assertEquals(first.receivedAt(), failed.receivedAt());
        service.read("OKX", "SPOT", "BTC-USDT");
        assertEquals(2, calls.get());
        assertEquals("SOURCE_UNAVAILABLE", service.read("OKX", "SPOT", "ETH-USDT").status());
    }

    @Test
    void staleSourceTimeIsNotReplacedByTheFreshReceiveTime() {
        var clock = new Time();
        var source =
                new PublicOrderBookReadPort() {
                    public String exchangeCode() {
                        return "OKX";
                    }

                    public PublicOrderBookSnapshot fetch(String symbol) {
                        return book(symbol, clock.now.minusSeconds(30));
                    }
                };
        var view =
                new PublicOrderBookService(List.of(source), clock).read("OKX", "SPOT", "BTC-USDT");
        assertEquals("STALE", view.status());
    }

    @Test
    void sameSymbolConcurrentReadersDoNotDuplicateOutboundFetch() throws Exception {
        var clock = new Time();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        var source =
                new PublicOrderBookReadPort() {
                    public String exchangeCode() {
                        return "OKX";
                    }

                    public PublicOrderBookSnapshot fetch(String symbol) {
                        calls.incrementAndGet();
                        entered.countDown();
                        try {
                            if (!release.await(3, TimeUnit.SECONDS))
                                throw new IllegalStateException("barrier timeout");
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(ex);
                        }
                        return book(symbol, clock.now);
                    }
                };
        var service = new PublicOrderBookService(List.of(source), clock);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var first = executor.submit(() -> service.read("OKX", "SPOT", "BTC-USDT"));
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            try {
                assertEquals("BUSY", service.read("OKX", "SPOT", "BTC-USDT").status());
                assertEquals(1, calls.get());
            } finally {
                release.countDown();
            }
            assertEquals("AVAILABLE", first.get(3, TimeUnit.SECONDS).status());
        } finally {
            release.countDown();
        }
    }
}
