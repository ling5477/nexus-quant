package com.guidinglight.nexusquant.news.infra.rss;

import static org.junit.jupiter.api.Assertions.*;

import com.guidinglight.nexusquant.news.domain.NewsSourceException;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** 仅模拟供应方RSS协议，不mock NQ的业务API；所有网络请求只发往本机夹具。 */
class CoinDeskNewsFeedAdapterTest {
    private static final Instant NOW = Instant.parse("2026-10-13T00:00:00Z");
    private static final String DATE = "Tue, 13 Oct 2026 00:00:00 GMT";

    private static String item(String title, String url, String date) {
        return "<item><title><![CDATA["
                + title
                + "]]></title><link>"
                + url
                + "</link><pubDate>"
                + date
                + "</pubDate><description>不转发的正文</description></item>";
    }

    private static String rss(String items) {
        return "<rss><channel>" + items + "</channel></rss>";
    }

    private static class Fixture implements AutoCloseable {
        final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        final ExecutorService handlers = Executors.newCachedThreadPool();
        final AtomicInteger requests = new AtomicInteger();
        final AtomicBoolean publicGet = new AtomicBoolean();
        CoinDeskNewsFeedAdapter adapter;

        Fixture(String payload, int status, boolean stalled) throws Exception {
            server.setExecutor(handlers);
            server.createContext(
                    "/rss",
                    exchange -> {
                        int attempt = requests.incrementAndGet();
                        publicGet.set(
                                "GET".equals(exchange.getRequestMethod())
                                        && exchange.getRequestHeaders().keySet().stream()
                                                .noneMatch(
                                                        key ->
                                                                key.equalsIgnoreCase(
                                                                                "Authorization")
                                                                        || key.toUpperCase()
                                                                                .startsWith(
                                                                                        "OK-ACCESS-")));
                        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
                        if (status == 302)
                            exchange.getResponseHeaders()
                                    .add(
                                            "Location",
                                            "http://127.0.0.1:"
                                                    + server.getAddress().getPort()
                                                    + "/unexpected");
                        try {
                            exchange.sendResponseHeaders(status, bytes.length);
                            if (stalled && attempt == 1) {
                                exchange.getResponseBody().write(bytes, 0, 1);
                                exchange.getResponseBody().flush();
                                headers.countDown();
                                if (!release.await(6, TimeUnit.SECONDS))
                                    throw new IOException("fixture release timed out");
                                exchange.getResponseBody().write(bytes, 1, bytes.length - 1);
                            } else {
                                exchange.getResponseBody().write(bytes);
                            }
                        } catch (InterruptedException failure) {
                            Thread.currentThread().interrupt();
                        } finally {
                            exchange.close();
                        }
                    });
            server.createContext(
                    "/unexpected",
                    exchange -> {
                        requests.incrementAndGet();
                        exchange.sendResponseHeaders(500, -1);
                        exchange.close();
                    });
            server.start();
            adapter =
                    new CoinDeskNewsFeedAdapter(
                            URI.create(
                                    "http://127.0.0.1:" + server.getAddress().getPort() + "/rss"),
                            Clock.fixed(NOW, ZoneOffset.UTC),
                            Duration.ofSeconds(1));
        }

        final CountDownLatch headers = new CountDownLatch(1), release = new CountDownLatch(1);

        @Override
        public void close() {
            release.countDown();
            adapter.destroy();
            server.stop(0);
            handlers.shutdownNow();
        }
    }

    @Test
    void readsOnlyHeadlineMetadataDeduplicatesAndKeepsSourceTimeDistinct() throws Exception {
        String payload =
                rss(
                        item(
                                        "<b>正式标题</b>",
                                        "https://www.coindesk.com/markets/one?utm_source=feed",
                                        DATE)
                                + item(
                                        "旧标题",
                                        "https://coindesk.com/markets/one",
                                        "Mon, 12 Oct 2026 23:00:00 GMT")
                                + item("危险链接", "javascript:alert(1)", DATE)
                                + item("跨域", "https://www.coindesk.com.evil.invalid/a", DATE)
                                + item("缺少日期", "https://www.coindesk.com/markets/two", "")
                                + item(
                                        "未来",
                                        "https://www.coindesk.com/markets/future",
                                        "Tue, 13 Oct 2026 01:00:00 GMT"));
        try (var fixture = new Fixture(payload, 200, false)) {
            var snapshot = fixture.adapter.fetch();
            assertEquals(1, snapshot.articles().size());
            var article = snapshot.articles().getFirst();
            assertEquals("正式标题", article.title());
            assertEquals("https://www.coindesk.com/markets/one", article.url());
            assertEquals(NOW, article.publishedAt());
            assertNull(snapshot.sourceUpdatedAt());
            assertEquals(NOW, snapshot.receivedAt());
            assertEquals(6, snapshot.itemsRead());
            assertEquals(4, snapshot.itemsSkipped());
            assertTrue(fixture.publicGet.get());
            assertEquals(1, fixture.requests.get());
        }
    }

    @Test
    void rejectsDoctypeAndExcessiveDepthWithoutExternalEntityReads() throws Exception {
        try (var fixture =
                new Fixture(
                        rss(item("标题", "https://www.coindesk.com/markets/a", DATE)), 200, false)) {
            String xxe =
                    "<!DOCTYPE rss [<!ENTITY external SYSTEM 'http://127.0.0.1:"
                            + fixture.server.getAddress().getPort()
                            + "/unexpected'>]><rss><channel><item><title>&external;</title></item></channel></rss>";
            assertEquals(
                    "SOURCE_INVALID",
                    assertThrows(
                                    NewsSourceException.class,
                                    () ->
                                            fixture.adapter.parse(
                                                    xxe.getBytes(StandardCharsets.UTF_8), NOW))
                            .code());
            String deep =
                    "<rss><channel>"
                            + "<nested>".repeat(110)
                            + "</nested>".repeat(110)
                            + "</channel></rss>";
            assertEquals(
                    "SOURCE_INVALID",
                    assertThrows(
                                    NewsSourceException.class,
                                    () ->
                                            fixture.adapter.parse(
                                                    deep.getBytes(StandardCharsets.UTF_8), NOW))
                            .code());
            assertEquals(0, fixture.requests.get());
        }
    }

    @Test
    void oversizedOriginalTitleIsSkippedWithoutUnboundedCleanupWork() throws Exception {
        String payload =
                rss(
                        item("<".repeat(250000), "https://www.coindesk.com/markets/huge", DATE)
                                + item("合法标题", "https://www.coindesk.com/markets/valid", DATE));
        assertTrue(
                payload.getBytes(StandardCharsets.UTF_8).length
                        < CoinDeskNewsFeedAdapter.MAX_BYTES);
        try (var fixture = new Fixture("", 200, false)) {
            var snapshot =
                    assertTimeoutPreemptively(
                            Duration.ofSeconds(2),
                            () ->
                                    fixture.adapter.parse(
                                            payload.getBytes(StandardCharsets.UTF_8), NOW));
            assertEquals(1, snapshot.articles().size());
            assertEquals("合法标题", snapshot.articles().getFirst().title());
            assertEquals(1, snapshot.itemsSkipped());
            assertEquals(0, fixture.requests.get());
        }
    }

    @Test
    void preservesEncodedPathSeparatorsAsDistinctOriginalArticleLocations() throws Exception {
        String payload =
                rss(
                        item("编码路径", "https://www.coindesk.com/markets/a%2Fb", DATE)
                                + item("普通路径", "https://www.coindesk.com/markets/a/b", DATE));
        try (var fixture = new Fixture(payload, 200, false)) {
            var urls =
                    fixture.adapter.fetch().articles().stream()
                            .map(article -> article.url())
                            .toList();
            assertEquals(2, urls.size());
            assertTrue(urls.contains("https://www.coindesk.com/markets/a%2Fb"));
            assertTrue(urls.contains("https://www.coindesk.com/markets/a/b"));
        }
    }

    @Test
    void responseLimitCancelsAndRedirectIsNotFollowed() throws Exception {
        try (var fixture =
                new Fixture(" ".repeat(CoinDeskNewsFeedAdapter.MAX_BYTES + 1), 200, false)) {
            assertEquals(
                    "SOURCE_UNAVAILABLE",
                    assertThrows(NewsSourceException.class, fixture.adapter::fetch).code());
            assertEquals(1, fixture.requests.get());
        }
        try (var fixture = new Fixture("<rss/>", 302, false)) {
            assertEquals(
                    "SOURCE_HTTP_ERROR",
                    assertThrows(NewsSourceException.class, fixture.adapter::fetch).code());
            assertEquals(1, fixture.requests.get());
        }
    }

    @Test
    void stalledBodyTimesOutBeforeSupplierReleaseAndNextRequestWorks() throws Exception {
        try (var fixture =
                        new Fixture(
                                rss(item("标题", "https://www.coindesk.com/markets/a", DATE)),
                                200,
                                true);
                var callers = Executors.newSingleThreadExecutor()) {
            var result =
                    callers.submit(
                            () -> assertThrows(NewsSourceException.class, fixture.adapter::fetch));
            try {
                assertTrue(fixture.headers.await(2, TimeUnit.SECONDS));
                assertEquals("SOURCE_TIMEOUT", result.get(3, TimeUnit.SECONDS).code());
                assertEquals(1, fixture.release.getCount());
                assertEquals(1, fixture.adapter.fetch().articles().size());
                assertEquals(2, fixture.requests.get());
            } finally {
                fixture.release.countDown();
            }
        }
    }

    @Test
    void limitsArticleCountAndRequiresManualProfileAndBothSwitches() throws Exception {
        StringBuilder items = new StringBuilder();
        for (int index = 0; index < 25; index++)
            items.append(item("标题" + index, "https://www.coindesk.com/markets/" + index, DATE));
        try (var fixture =
                new Fixture(
                        rss("<lastBuildDate>" + DATE + "</lastBuildDate>" + items), 200, false)) {
            var snapshot = fixture.adapter.fetch();
            assertEquals(20, snapshot.articles().size());
            assertEquals(25, snapshot.itemsRead());
            assertEquals(NOW, snapshot.sourceUpdatedAt());
        }
        for (var values :
                List.of(
                        new String[] {"local", "true", "true"},
                        new String[] {"public-marketdata-manual", "true", "false"},
                        new String[] {"public-marketdata-manual", "false", "true"},
                        new String[] {"public-marketdata-manual", "true", "true"})) {
            try (var context = new AnnotationConfigApplicationContext()) {
                context.getEnvironment().setActiveProfiles(values[0]);
                context.getEnvironment()
                        .getPropertySources()
                        .addFirst(
                                new MapPropertySource(
                                        "rss-fixture",
                                        Map.of(
                                                "nq.public-marketdata.outbound.enabled",
                                                values[1],
                                                "nq.public-news.enabled",
                                                values[2])));
                context.register(CoinDeskNewsFeedAdapter.class);
                context.refresh();
                assertEquals(
                        "public-marketdata-manual".equals(values[0])
                                && "true".equals(values[1])
                                && "true".equals(values[2]),
                        context.getBeanNamesForType(CoinDeskNewsFeedAdapter.class).length == 1);
            }
        }
    }
}
