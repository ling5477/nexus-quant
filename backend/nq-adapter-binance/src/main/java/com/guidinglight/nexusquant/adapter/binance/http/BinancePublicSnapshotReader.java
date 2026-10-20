package com.guidinglight.nexusquant.adapter.binance.http;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.zip.GZIPInputStream;

/**
 * 公开目录必须完整读取后才交给规则解析；对慢正文、超大传输和压缩展开分别设置边界。
 * 仅供无签名目录请求使用，失败时取消原读取，不重试，也不返回部分规则。
 */
final class BinancePublicSnapshotReader {
    private static final int MAX_WIRE_BYTES = 8 * 1024 * 1024;
    private static final int MAX_DECODED_BYTES = 24 * 1024 * 1024;

    private BinancePublicSnapshotReader() {}

    static Snapshot read(HttpClient client, HttpRequest request, Duration timeout)
            throws IOException, InterruptedException {
        var future = client.sendAsync(request, ignored -> new LimitedBodySubscriber());
        HttpResponse<byte[]> response;
        try {
            // HttpRequest.timeout不能单独证明响应头之后的正文期限；等待完整订阅结果并取消超时读取。
            response = future.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new HttpTimeoutException("public catalogue body timed out");
        } catch (InterruptedException exception) {
            future.cancel(true);
            throw exception;
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException(
                    "public catalogue response could not be read", exception.getCause());
        }
        String encoding =
                String.join(",", response.headers().allValues("Content-Encoding"))
                        .trim()
                        .toLowerCase(Locale.ROOT);
        byte[] body = response.body();
        if ("gzip".equals(encoding)) {
            try (var gzip = new GZIPInputStream(new ByteArrayInputStream(body))) {
                body = gzip.readNBytes(MAX_DECODED_BYTES + 1);
            }
        } else if (!encoding.isEmpty() && !"identity".equals(encoding)) {
            throw new IOException("unsupported public catalogue content encoding");
        }
        if (body.length > MAX_DECODED_BYTES) {
            throw new IOException("public catalogue decoded body exceeds limit");
        }
        return new Snapshot(response.statusCode(), new String(body, StandardCharsets.UTF_8));
    }

    record Snapshot(int statusCode, String body) {}

    /** 在收集字节前检查累计传输量；超过边界立即取消上游，避免先无限缓存再检查。 */
    private static final class LimitedBodySubscriber
            implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate =
                HttpResponse.BodySubscribers.ofByteArray();
        private Flow.Subscription subscription;
        private int received;
        private boolean finished;

        @Override
        public CompletionStage<byte[]> getBody() {
            return delegate.getBody();
        }

        @Override
        public void onSubscribe(Flow.Subscription value) {
            subscription = value;
            delegate.onSubscribe(value);
        }

        @Override
        public void onNext(List<ByteBuffer> items) {
            if (finished) {
                return;
            }
            for (ByteBuffer item : items) {
                if (item.remaining() > MAX_WIRE_BYTES - received) {
                    finished = true;
                    subscription.cancel();
                    delegate.onError(new IOException("public catalogue wire body exceeds limit"));
                    return;
                }
                received += item.remaining();
            }
            delegate.onNext(items);
        }

        @Override
        public void onError(Throwable failure) {
            if (!finished) {
                finished = true;
                delegate.onError(failure);
            }
        }

        @Override
        public void onComplete() {
            if (!finished) {
                finished = true;
                delegate.onComplete();
            }
        }
    }
}
