package com.guidinglight.nexusquant.news.application;

import com.guidinglight.nexusquant.news.domain.NewsFeedSnapshot;
import com.guidinglight.nexusquant.news.domain.NewsSourceException;
import com.guidinglight.nexusquant.news.domain.port.PublicNewsReadPort;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/** 单一有界RSS快照，五分钟缓存与三十秒失败冷却；读取不触碰账户和交易事实。 */
@Service
public class PublicNewsService {
    public static final String SOURCE = "CoinDesk";
    public static final String SOURCE_URL = "https://www.coindesk.com/arc/outboundfeeds/rss";
    private final List<PublicNewsReadPort> sources;
    private final Clock clock;
    private final ReentrantLock lock = new ReentrantLock();
    private volatile NewsFeedSnapshot snapshot;
    private volatile Instant nextAttempt = Instant.MIN;

    @Autowired
    public PublicNewsService(List<PublicNewsReadPort> sources) {
        this(sources, Clock.systemUTC());
    }

    PublicNewsService(List<PublicNewsReadPort> sources, Clock clock) {
        this.sources = List.copyOf(sources);
        this.clock = clock;
        if (sources.size() > 1) throw new IllegalArgumentException("PUBLIC_NEWS_SOURCE_AMBIGUOUS");
    }

    /** 并发请求共用单一缓存；成功缓存五分钟，失败冷却三十秒，保留旧文章时明确过期及失败原因。 */
    public NewsFeedSnapshot read() {
        Instant now = clock.instant();
        if (sources.isEmpty()) return empty("DISABLED", null, now);
        NewsFeedSnapshot cached = snapshot;
        if (now.isBefore(nextAttempt))
            return cached == null
                    ? empty("SOURCE_UNAVAILABLE", "SOURCE_COOLDOWN", now)
                    : checked(cached, now, cached.errorCode());
        if (!lock.tryLock())
            return cached == null ? empty("BUSY", null, now) : checked(cached, now, "SOURCE_BUSY");
        try {
            now = clock.instant();
            cached = snapshot;
            if (now.isBefore(nextAttempt))
                return cached == null
                        ? empty("SOURCE_UNAVAILABLE", "SOURCE_COOLDOWN", now)
                        : checked(cached, now, cached.errorCode());
            try {
                NewsFeedSnapshot fetched = sources.getFirst().fetch();
                if (!SOURCE.equals(fetched.source())
                        || !SOURCE_URL.equals(fetched.sourceUrl())
                        || fetched.receivedAt() == null
                        || fetched.articles().isEmpty()
                        || fetched.articles().size() > 20)
                    throw new NewsSourceException("SOURCE_INVALID");
                now = clock.instant();
                NewsFeedSnapshot result = checked(fetched, now, null);
                snapshot = result;
                nextAttempt = now.plusSeconds(300);
                return result;
            } catch (NewsSourceException failure) {
                now = clock.instant();
                nextAttempt = now.plusSeconds(30);
                NewsFeedSnapshot result =
                        cached == null
                                ? empty("SOURCE_UNAVAILABLE", failure.code(), now)
                                : checked(cached, now, failure.code());
                snapshot = cached == null ? null : result;
                return result;
            }
        } finally {
            lock.unlock();
        }
    }

    private static NewsFeedSnapshot checked(NewsFeedSnapshot value, Instant now, String error) {
        boolean stale = error != null || value.receivedAt().isBefore(now.minusSeconds(900));
        return new NewsFeedSnapshot(
                SOURCE,
                SOURCE_URL,
                stale ? "STALE" : "AVAILABLE",
                value.sourceUpdatedAt(),
                value.receivedAt(),
                now,
                error,
                value.itemsRead(),
                value.itemsSkipped(),
                20,
                300,
                value.articles());
    }

    private static NewsFeedSnapshot empty(String status, String error, Instant now) {
        return new NewsFeedSnapshot(
                SOURCE, SOURCE_URL, status, null, null, now, error, 0, 0, 20, 300, List.of());
    }
}
