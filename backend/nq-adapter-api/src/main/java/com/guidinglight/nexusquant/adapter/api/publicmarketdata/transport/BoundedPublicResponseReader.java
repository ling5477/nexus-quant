package com.guidinglight.nexusquant.adapter.api.publicmarketdata.transport;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.IntPredicate;

/** 公开来源只接受有限的完整正文；响应头之后的慢读取也受期限约束，超限立即取消上游。 */
public final class BoundedPublicResponseReader {
    private static final int MAX_BYTES = 256_000;

    private BoundedPublicResponseReader() {}

    public static HttpResponse<byte[]> read(HttpClient client, HttpRequest request, Duration timeout)
            throws IOException, InterruptedException {
        return read(client, request, timeout, status -> true);
    }

    static HttpResponse<byte[]> read(
            HttpClient client, HttpRequest request, Duration timeout, IntPredicate expectsBody)
            throws IOException, InterruptedException {
        var future = client.sendAsync(request, info -> expectsBody.test(info.statusCode())
                ? new Subscriber() : HttpResponse.BodySubscribers.replacing(new byte[0]));
        try {
            HttpResponse<byte[]> response = future.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
            String encoding = String.join(",", response.headers().allValues("Content-Encoding"));
            if (expectsBody.test(response.statusCode()) && !encoding.isBlank()
                    && !"identity".equalsIgnoreCase(encoding)) {
                throw new IllegalStateException("PUBLIC_MARKET_RESPONSE_ENCODING");
            }
            return response;
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new HttpTimeoutException("public response timed out");
        } catch (InterruptedException exception) {
            future.cancel(true);
            throw exception;
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof LimitException) {
                throw new IllegalStateException("PUBLIC_MARKET_RESPONSE_TOO_LARGE");
            }
            if (exception.getCause() instanceof IOException io) throw io;
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
