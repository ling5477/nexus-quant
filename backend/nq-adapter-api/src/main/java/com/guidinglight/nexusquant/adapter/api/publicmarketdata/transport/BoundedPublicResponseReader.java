package com.guidinglight.nexusquant.adapter.api.publicmarketdata.transport;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/** 公开来源只接受有限的完整正文；响应头之后的慢读取也受期限约束，超限立即取消上游。 */
public final class BoundedPublicResponseReader {
    private static final int MAX_BYTES = 256_000;

    private BoundedPublicResponseReader() {}

    public static HttpResponse<byte[]> read(HttpClient client, HttpRequest request, Duration timeout)
            throws IOException, InterruptedException {
        var future = client.sendAsync(request, info -> new Subscriber());
        try {
            var response = await(future, timeout);
            requireIdentityEncoding(response);
            return response;
        } finally {
            future.cancel(true);
        }
    }

    static SnapshotRead startSnapshot(HttpClient client, HttpRequest request,
                                      Consumer<HttpResponse.ResponseInfo> onErrorHeaders) {
        var result = new CompletableFuture<SnapshotBody>();
        var requestFuture = client.sendAsync(request, info -> {
            if (info.statusCode() == 200) return new Subscriber();
            // 错误状态与限流头已经完整，调用方据此取消正文读取。
            onErrorHeaders.accept(info);
            result.complete(new SnapshotBody(info.statusCode(), null));
            return HttpResponse.BodySubscribers.replacing(new byte[0]);
        });
        requestFuture.whenComplete((response, error) -> {
            if (error != null) {
                result.completeExceptionally(error);
            } else if (response.statusCode() == 200) {
                try {
                    requireIdentityEncoding(response);
                    result.complete(new SnapshotBody(response.statusCode(), response.body()));
                } catch (IllegalStateException invalid) {
                    result.completeExceptionally(invalid);
                }
            }
        });
        return new SnapshotRead(requestFuture, result);
    }

    record SnapshotRead(CompletableFuture<HttpResponse<byte[]>> request,
                        CompletableFuture<SnapshotBody> result) {
        SnapshotBody awaitBody(Duration timeout) throws IOException, InterruptedException {
            try {
                return await(result, timeout);
            } finally {
                request.cancel(true);
            }
        }
    }

    record SnapshotBody(int statusCode, byte[] body) {}

    private static void requireIdentityEncoding(HttpResponse<?> response) {
        String encoding = String.join(",", response.headers().allValues("Content-Encoding"));
        if (!encoding.isBlank() && !"identity".equalsIgnoreCase(encoding)) {
            throw new IllegalStateException("PUBLIC_MARKET_RESPONSE_ENCODING");
        }
    }

    private static <T> T await(CompletableFuture<T> future, Duration timeout)
            throws IOException, InterruptedException {
        try {
            return future.get(Math.max(0, timeout.toNanos()), TimeUnit.NANOSECONDS);
        } catch (TimeoutException exception) {
            throw new HttpTimeoutException("public response timed out");
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof LimitException) {
                throw new IllegalStateException("PUBLIC_MARKET_RESPONSE_TOO_LARGE");
            }
            if (exception.getCause() instanceof IOException io) throw io;
            if (exception.getCause() instanceof IllegalStateException invalid) throw invalid;
            throw new IOException("public capture response failed", exception.getCause());
        }
    }

    private static final class LimitException extends IOException {}

    private static final class Subscriber implements HttpResponse.BodySubscriber<byte[]> {
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
        public void onNext(List<ByteBuffer> buffers) {
            if (finished) return;
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > MAX_BYTES - received) {
                    finished = true;
                    subscription.cancel();
                    delegate.onError(new LimitException());
                    return;
                }
                received += buffer.remaining();
            }
            delegate.onNext(buffers);
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
