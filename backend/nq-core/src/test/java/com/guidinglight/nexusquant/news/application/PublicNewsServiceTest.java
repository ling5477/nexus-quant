package com.guidinglight.nexusquant.news.application;

import static org.junit.jupiter.api.Assertions.*;

import com.guidinglight.nexusquant.news.domain.NewsArticle;
import com.guidinglight.nexusquant.news.domain.NewsFeedSnapshot;
import com.guidinglight.nexusquant.news.domain.NewsSourceException;
import com.guidinglight.nexusquant.news.domain.port.PublicNewsReadPort;

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

/** 用受控时钟和同步闩锁验证缓存、失败冷却及并发合并；测试快照不代表联网拉取成功。 */
class PublicNewsServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-13T00:00:00Z");

    private static final class MutableClock extends Clock {
        Instant now = NOW;

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static NewsFeedSnapshot snapshot() {
        return new NewsFeedSnapshot(
                PublicNewsService.SOURCE,
                PublicNewsService.SOURCE_URL,
                "AVAILABLE",
                NOW.minusSeconds(60),
                NOW,
                NOW,
                null,
                1,
                0,
                20,
                300,
                List.of(
                        new NewsArticle(
                                "供应方测试标题",
                                "https://www.coindesk.com/markets/fixture",
                                NOW.minusSeconds(60))));
    }

    @Test
    void disabledSourceDoesNotInventAnEmptySuccessfulFeed() {
        var response = new PublicNewsService(List.of(), Clock.fixed(NOW, ZoneOffset.UTC)).read();
        assertEquals("DISABLED", response.status());
        assertNull(response.receivedAt());
        assertNull(response.sourceUpdatedAt());
        assertTrue(response.articles().isEmpty());
    }

    @Test
    void cachesForFiveMinutesAndPreservesReceiptTime() {
        var clock = new MutableClock();
        var calls = new AtomicInteger();
        var service =
                new PublicNewsService(
                        List.of(
                                () -> {
                                    calls.incrementAndGet();
                                    return snapshot();
                                }),
                        clock);
        assertEquals("AVAILABLE", service.read().status());
        clock.now = NOW.plusSeconds(299);
        var cached = service.read();
        assertEquals(1, calls.get());
        assertEquals(NOW, cached.receivedAt());
        assertEquals(clock.now, cached.checkedAt());
        clock.now = NOW.plusSeconds(300);
        service.read();
        assertEquals(2, calls.get());
    }

    @Test
    void failurePreservesRealArticlesWithStaleStateAndCooldown() {
        var clock = new MutableClock();
        var calls = new AtomicInteger();
        var service =
                new PublicNewsService(
                        List.of(
                                () -> {
                                    if (calls.incrementAndGet() > 1)
                                        throw new NewsSourceException("SOURCE_TIMEOUT");
                                    return snapshot();
                                }),
                        clock);
        var first = service.read();
        clock.now = NOW.plusSeconds(300);
        var failed = service.read();
        assertEquals("STALE", failed.status());
        assertEquals("SOURCE_TIMEOUT", failed.errorCode());
        assertEquals(first.articles(), failed.articles());
        assertEquals(NOW, failed.receivedAt());
        clock.now = NOW.plusSeconds(329);
        assertEquals("STALE", service.read().status());
        assertEquals(2, calls.get());
        clock.now = NOW.plusSeconds(330);
        service.read();
        assertEquals(3, calls.get());
    }

    @Test
    void concurrentCallerReturnsBusyWithoutCreatingAnotherSourceRequest() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        PublicNewsReadPort source =
                () -> {
                    calls.incrementAndGet();
                    entered.countDown();
                    try {
                        if (!release.await(3, TimeUnit.SECONDS))
                            throw new AssertionError("fixture release timed out");
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(failure);
                    }
                    return snapshot();
                };
        var service = new PublicNewsService(List.of(source), Clock.fixed(NOW, ZoneOffset.UTC));
        try (var executor = Executors.newSingleThreadExecutor()) {
            var reading = executor.submit(service::read);
            try {
                assertTrue(entered.await(2, TimeUnit.SECONDS));
                assertEquals("BUSY", service.read().status());
                assertEquals(1, calls.get());
            } finally {
                release.countDown();
            }
            assertEquals("AVAILABLE", reading.get(2, TimeUnit.SECONDS).status());
        }
    }

    @Test
    void rejectsMultipleSourcesAndInvalidSourceIdentity() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PublicNewsService(
                                List.of(
                                        PublicNewsServiceTest::snapshot,
                                        PublicNewsServiceTest::snapshot),
                                Clock.systemUTC()));
        var invalid =
                new NewsFeedSnapshot(
                        "other",
                        PublicNewsService.SOURCE_URL,
                        "AVAILABLE",
                        NOW,
                        NOW,
                        NOW,
                        null,
                        1,
                        0,
                        20,
                        300,
                        snapshot().articles());
        var result =
                new PublicNewsService(List.of(() -> invalid), Clock.fixed(NOW, ZoneOffset.UTC))
                        .read();
        assertEquals("SOURCE_UNAVAILABLE", result.status());
        assertEquals("SOURCE_INVALID", result.errorCode());
        assertTrue(result.articles().isEmpty());
    }
}
