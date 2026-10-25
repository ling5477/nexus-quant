package com.guidinglight.nexusquant.news.infra.rss;

import com.guidinglight.nexusquant.news.application.PublicNewsService;
import com.guidinglight.nexusquant.news.domain.NewsArticle;
import com.guidinglight.nexusquant.news.domain.NewsFeedSnapshot;
import com.guidinglight.nexusquant.news.domain.NewsSourceException;
import com.guidinglight.nexusquant.news.domain.port.PublicNewsReadPort;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/** 固定发布者公开RSS；无凭据、任意URL代理或正文转发，完整响应受字节和时间双重上限约束。 */
@Component
@Profile("public-marketdata-manual")
@ConditionalOnExpression(
        "${nq.public-marketdata.outbound.enabled:false} and ${nq.public-news.enabled:false}")
public class CoinDeskNewsFeedAdapter implements PublicNewsReadPort, DisposableBean {
    static final int MAX_BYTES = 262144;
    private final URI endpoint;
    private final Clock clock;
    private final Duration timeout;
    private final HttpClient http;

    public CoinDeskNewsFeedAdapter() {
        this(URI.create(PublicNewsService.SOURCE_URL), Clock.systemUTC(), Duration.ofSeconds(8));
    }

    CoinDeskNewsFeedAdapter(URI endpoint, Clock clock, Duration timeout) {
        boolean official = PublicNewsService.SOURCE_URL.equals(endpoint.toString());
        boolean fixture =
                "http".equals(endpoint.getScheme())
                        && "127.0.0.1".equals(endpoint.getHost())
                        && endpoint.getUserInfo() == null
                        && endpoint.getRawQuery() == null
                        && endpoint.getFragment() == null;
        if ((!official && !fixture)
                || timeout.isNegative()
                || timeout.isZero()
                || timeout.compareTo(Duration.ofSeconds(8)) > 0)
            throw new IllegalArgumentException("PUBLIC_NEWS_CONFIGURATION_INVALID");
        this.endpoint = endpoint;
        this.clock = clock;
        this.timeout = timeout;
        this.http =
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(3))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build();
    }

    @Override
    public NewsFeedSnapshot fetch() {
        HttpRequest request =
                HttpRequest.newBuilder(endpoint)
                        .GET()
                        .timeout(timeout)
                        .header("Accept", "application/rss+xml, application/xml, text/xml")
                        .header("User-Agent", "NexusQuant-Public-RSS-Reader/1.0")
                        .build();
        LimitedBodyHandler handler = new LimitedBodyHandler();
        long startedAt = System.nanoTime();
        CompletableFuture<HttpResponse<byte[]>> exchange = http.sendAsync(request, handler);
        try {
            // 约束完整body，而非仅响应头；超时和中断都取消底层订阅及HTTP交换。
            HttpResponse<byte[]> response =
                    exchange.get(
                            Math.max(1, timeout.toNanos() - (System.nanoTime() - startedAt)),
                            TimeUnit.NANOSECONDS);
            if (response.statusCode() != 200) throw new NewsSourceException("SOURCE_HTTP_ERROR");
            return parse(response.body(), clock.instant());
        } catch (TimeoutException failure) {
            handler.abort(failure);
            exchange.cancel(true);
            throw new NewsSourceException("SOURCE_TIMEOUT");
        } catch (InterruptedException failure) {
            handler.abort(failure);
            exchange.cancel(true);
            Thread.currentThread().interrupt();
            throw new NewsSourceException("SOURCE_INTERRUPTED");
        } catch (ExecutionException failure) {
            handler.abort(failure);
            exchange.cancel(true);
            throw new NewsSourceException("SOURCE_UNAVAILABLE");
        }
    }

    NewsFeedSnapshot parse(byte[] xml, Instant receivedAt) {
        if (xml == null || xml.length == 0 || xml.length > MAX_BYTES) throw invalid();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newDefaultInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setAttribute(
                    "http://www.oracle.com/xml/jaxp/properties/maxElementDepth", "100");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder();
            builder.setEntityResolver(
                    (publicId, systemId) -> {
                        throw new SAXException("EXTERNAL_ENTITY_DENIED");
                    });
            builder.setErrorHandler(
                    new DefaultHandler() {
                        @Override
                        public void error(SAXParseException failure) throws SAXException {
                            throw failure;
                        }

                        @Override
                        public void fatalError(SAXParseException failure) throws SAXException {
                            throw failure;
                        }
                    });
            var document = builder.parse(new ByteArrayInputStream(xml));
            if (!"rss".equals(document.getDocumentElement().getTagName())) throw invalid();
            var channels = document.getElementsByTagName("channel");
            if (channels.getLength() != 1) throw invalid();
            Element channel = (Element) channels.item(0);
            var items = channel.getElementsByTagName("item");
            int read = Math.min(items.getLength(), 100),
                    skipped = Math.max(0, items.getLength() - read);
            var articles = new LinkedHashMap<String, NewsArticle>();
            for (int index = 0; index < read; index++) {
                Element item = (Element) items.item(index);
                String originalTitle = text(item, "title");
                // 响应字节上限不能约束正则的CPU代价；先限制原始标题，再做线性清理。
                if (originalTitle.length() > 2048) {
                    skipped++;
                    continue;
                }
                String title = plainTitle(originalTitle);
                String link = safeArticleUrl(text(item, "link"));
                Instant publishedAt = time(text(item, "pubDate"));
                if (title.isBlank()
                        || title.length() > 300
                        || link == null
                        || publishedAt == null
                        || publishedAt.isAfter(receivedAt.plusSeconds(300))) {
                    skipped++;
                    continue;
                }
                NewsArticle article = new NewsArticle(title, link, publishedAt);
                articles.merge(
                        link,
                        article,
                        (old, next) -> next.publishedAt().isAfter(old.publishedAt()) ? next : old);
            }
            if (articles.isEmpty()) throw invalid();
            List<NewsArticle> selected =
                    articles.values().stream()
                            .sorted(
                                    Comparator.comparing(NewsArticle::publishedAt)
                                            .reversed()
                                            .thenComparing(NewsArticle::url))
                            .limit(20)
                            .toList();
            // channel的时点必须来自其直接子节点，不能借用某篇文章日期冒充源更新时间。
            Instant sourceTime = time(directText(channel, "lastBuildDate"));
            if (sourceTime != null && sourceTime.isAfter(receivedAt.plusSeconds(300)))
                sourceTime = null;
            return new NewsFeedSnapshot(
                    PublicNewsService.SOURCE,
                    PublicNewsService.SOURCE_URL,
                    "AVAILABLE",
                    sourceTime,
                    receivedAt,
                    receivedAt,
                    null,
                    read,
                    skipped,
                    20,
                    300,
                    selected);
        } catch (ParserConfigurationException | SAXException | IOException failure) {
            throw invalid();
        }
    }

    private static String text(Element element, String name) {
        var nodes = element.getElementsByTagName(name);
        return nodes.getLength() == 1 ? nodes.item(0).getTextContent().strip() : "";
    }

    private static String directText(Element element, String name) {
        var children = element.getChildNodes();
        for (int index = 0; index < children.getLength(); index++)
            if (children.item(index) instanceof Element child && name.equals(child.getTagName()))
                return child.getTextContent().strip();
        return "";
    }

    private static Instant time(String value) {
        try {
            return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (DateTimeException failure) {
            return null;
        }
    }

    private static String plainTitle(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '<'
                    && index + 1 < value.length()
                    && (Character.isLetter(value.charAt(index + 1))
                            || value.charAt(index + 1) == '/')) {
                int end = value.indexOf('>', index + 1);
                if (end < 0) {
                    result.append(value, index, value.length());
                    break;
                }
                index = end;
            } else {
                result.append(Character.isISOControl(character) ? ' ' : character);
            }
        }
        return result.toString().strip();
    }

    private static String safeArticleUrl(String value) {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (!"https".equals(uri.getScheme())
                    || !List.of("www.coindesk.com", "coindesk.com").contains(host)
                    || uri.getUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)
                    || uri.getRawPath() == null
                    || !uri.getRawPath().startsWith("/")
                    || value.length() > 2048) return null;
            // %2F等保留字符不能先解码再重建，否则原文定位及去重身份会改变。
            return new URI("https://www.coindesk.com" + uri.getRawPath()).toASCIIString();
        } catch (IllegalArgumentException | URISyntaxException failure) {
            return null;
        }
    }

    private static NewsSourceException invalid() {
        return new NewsSourceException("SOURCE_INVALID");
    }

    @Override
    public void destroy() {
        http.shutdownNow();
    }

    private static final class LimitedBodyHandler implements HttpResponse.BodyHandler<byte[]> {
        private LimitedBodySubscriber subscriber;
        private Throwable abortCause;

        @Override
        public synchronized HttpResponse.BodySubscriber<byte[]> apply(
                HttpResponse.ResponseInfo response) {
            subscriber = new LimitedBodySubscriber();
            if (abortCause != null) subscriber.abort(abortCause);
            return subscriber;
        }

        synchronized void abort(Throwable cause) {
            abortCause = cause;
            if (subscriber != null) subscriber.abort(cause);
        }
    }

    private static final class LimitedBodySubscriber
            implements HttpResponse.BodySubscriber<byte[]> {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private Flow.Subscription subscription;

        @Override
        public CompletionStage<byte[]> getBody() {
            return body;
        }

        @Override
        public synchronized void onSubscribe(Flow.Subscription value) {
            if (subscription != null || body.isDone()) {
                value.cancel();
                return;
            }
            subscription = value;
            value.request(Long.MAX_VALUE);
        }

        @Override
        public synchronized void onNext(List<ByteBuffer> buffers) {
            if (body.isDone()) return;
            if (buffers.stream().mapToLong(ByteBuffer::remaining).sum()
                    > MAX_BYTES - bytes.size()) {
                abort(new IOException("PUBLIC_RESPONSE_LIMIT_EXCEEDED"));
                return;
            }
            for (ByteBuffer buffer : buffers) {
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
        }

        synchronized void abort(Throwable failure) {
            if (body.completeExceptionally(failure)) {
                bytes.reset();
                if (subscription != null) subscription.cancel();
            }
        }

        @Override
        public void onError(Throwable failure) {
            abort(failure);
        }

        @Override
        public synchronized void onComplete() {
            if (!body.isDone()) {
                body.complete(bytes.toByteArray());
                bytes.reset();
            }
        }
    }
}
