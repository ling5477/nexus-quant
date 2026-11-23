package com.guidinglight.nexusquant.marketdata.application.service;

import com.guidinglight.nexusquant.marketdata.domain.OrderBookSourceException;
import com.guidinglight.nexusquant.marketdata.domain.PublicOrderBookSnapshot;
import com.guidinglight.nexusquant.marketdata.domain.port.PublicOrderBookReadPort;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/** 固定三标的、每侧20档、5秒缓存；同标的并发读取不放大供应方请求，不持久化第二套交易事实。 */
@Service
public class PublicOrderBookService {
    public static final Set<String> SYMBOLS = Set.of("BTC-USDT", "ETH-USDT", "SOL-USDT");
    private final List<PublicOrderBookReadPort> sources;
    private final Clock clock;
    private final Map<String, PublicOrderBookSnapshot> snapshots = new ConcurrentHashMap<>();
    private final Map<String, Instant> nextAttempt = new ConcurrentHashMap<>();
    private final Map<String, ReentrantLock> locks =
            Map.of(
                    "BTC-USDT",
                    new ReentrantLock(),
                    "ETH-USDT",
                    new ReentrantLock(),
                    "SOL-USDT",
                    new ReentrantLock());

    @Autowired
    public PublicOrderBookService(List<PublicOrderBookReadPort> sources) {
        this(sources, Clock.systemUTC());
    }

    PublicOrderBookService(List<PublicOrderBookReadPort> sources, Clock clock) {
        this.sources = List.copyOf(sources);
        this.clock = clock;
    }

    /** 仅支持固定OKX现货标的；同标的读取用非阻塞锁合并，来源失败时保留旧档位但明确过期。 */
    public PublicOrderBookSnapshot read(String exchange, String market, String symbol) {
        if (!"OKX".equals(exchange)
                || !"SPOT".equals(market)
                || symbol == null
                || !SYMBOLS.contains(symbol))
            throw new IllegalArgumentException("PUBLIC_ORDER_BOOK_UNSUPPORTED");
        Instant now = clock.instant();
        var source = sources.stream().filter(item -> "OKX".equals(item.exchangeCode())).findFirst();
        if (source.isEmpty()) return empty(symbol, "DISABLED", null, now);
        var cached = snapshots.get(symbol);
        if (cached != null && now.isBefore(nextAttempt.getOrDefault(symbol, Instant.MIN)))
            return checked(cached, now, cached.errorCode());
        var lock = locks.get(symbol);
        if (!lock.tryLock())
            return cached == null
                    ? empty(symbol, "BUSY", null, now)
                    : checked(cached, now, "SOURCE_BUSY");
        try {
            cached = snapshots.get(symbol);
            now = clock.instant();
            if (now.isBefore(nextAttempt.getOrDefault(symbol, Instant.MIN)))
                return cached == null
                        ? empty(symbol, "SOURCE_UNAVAILABLE", "SOURCE_COOLDOWN", now)
                        : checked(cached, now, cached.errorCode());
            try {
                var fetched = source.get().fetch(symbol);
                if (!symbol.equals(fetched.symbol())
                        || !"OKX".equals(fetched.exchangeCode())
                        || !"SPOT".equals(fetched.marketType())
                        || fetched.sourceTime() == null
                        || fetched.receivedAt() == null
                        || fetched.bids().isEmpty()
                        || fetched.asks().isEmpty()
                        || fetched.bids().size() > 20
                        || fetched.asks().size() > 20)
                    throw new OrderBookSourceException("SOURCE_INVALID");
                now = clock.instant();
                var result = checked(fetched, now, null);
                snapshots.put(symbol, result);
                nextAttempt.put(symbol, now.plusSeconds(5));
                return result;
            } catch (OrderBookSourceException ex) {
                now = clock.instant();
                nextAttempt.put(symbol, now.plusSeconds(2));
                if (cached == null) return empty(symbol, "SOURCE_UNAVAILABLE", ex.code(), now);
                var failed = checked(cached, now, ex.code());
                snapshots.put(symbol, failed);
                return failed;
            }
        } finally {
            lock.unlock();
        }
    }

    private static PublicOrderBookSnapshot checked(
            PublicOrderBookSnapshot snapshot, Instant now, String error) {
        boolean stale =
                error != null
                        || snapshot.sourceTime().isBefore(now.minusSeconds(15))
                        || snapshot.receivedAt().isBefore(now.minusSeconds(15));
        return new PublicOrderBookSnapshot(
                "OKX",
                "SPOT",
                snapshot.symbol(),
                stale ? "STALE" : "AVAILABLE",
                snapshot.sourceTime(),
                snapshot.receivedAt(),
                now,
                snapshot.source(),
                20,
                error,
                snapshot.bids(),
                snapshot.asks());
    }

    private static PublicOrderBookSnapshot empty(
            String symbol, String status, String error, Instant now) {
        return new PublicOrderBookSnapshot(
                "OKX",
                "SPOT",
                symbol,
                status,
                null,
                null,
                now,
                "OKX_PUBLIC_REST",
                20,
                error,
                List.of(),
                List.of());
    }
}
