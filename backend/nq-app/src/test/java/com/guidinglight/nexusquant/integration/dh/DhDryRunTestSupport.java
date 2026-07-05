package com.guidinglight.nexusquant.integration.dh;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * DhDryRunTestSupport 集中提供 Integration-1 limited client 的测试固定输入和 fake transport。
 *
 * <p>Why: 所有测试都必须 no-real-DH-call、no-real-HTTP、no-provider，只允许通过内存 fake 观察 signed
 * request 和 response validation。</p>
 */
final class DhDryRunTestSupport {

    static final String ENDPOINT = "http://dh.invalid/api/ai/decision-dry-runs";
    static final String SIGNING_KEY = "test-only-signing-key";
    static final Instant FIXED_INSTANT = Instant.parse("2026-07-05T01:02:03Z");

    private DhDryRunTestSupport() {
    }

    /**
     * 构造默认 enabled 测试 client。
     *
     * @param transport fake transport
     * @param recorder  in-memory recorder
     * @return 使用固定时钟和测试签名 key 的 client
     */
    static DhDryRunRuntimeClient enabledClient(FakeDhDryRunTransport transport, InMemoryDhDryRunRecorder recorder) {
        return client(DhDryRunRuntimeProperties.enabledForTest(ENDPOINT, SIGNING_KEY), transport, recorder);
    }

    /**
     * 构造指定配置的测试 client。
     *
     * @param properties dry-run runtime properties
     * @param transport  fake transport
     * @param recorder   in-memory recorder
     * @return 使用固定时钟的 client
     */
    static DhDryRunRuntimeClient client(
            DhDryRunRuntimeProperties properties,
            DhDryRunTransport transport,
            InMemoryDhDryRunRecorder recorder) {
        return new DhDryRunRuntimeClient(
                properties,
                transport,
                recorder,
                new ObjectMapper(),
                Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC),
                new DhDryRunNonceGenerator());
    }

    /**
     * 返回安全的 request command。
     *
     * @return 不含 credential、交易动作、数量、价格或杠杆的只读 command
     */
    static DhDryRunRequestCommand command() {
        return new DhDryRunRequestCommand(
                "nq-i1-req-001",
                "nq-i1-trace-001",
                "tenant-i1",
                new DhDryRunDecisionContext(
                        "BTC-USDT",
                        "CRYPTO_SPOT",
                        "1h",
                        "readonly dry-run scenario",
                        "sanitized evidence summary",
                        "readonly risk context"));
    }

    /**
     * 构造成功 response JSON。
     *
     * @param action allowed action
     * @return response body
     */
    static String validResponse(String action) {
        return """
                {
                  "decisionId": "dh-dec-001",
                  "dryRun": true,
                  "action": "%s",
                  "confidence": 0.72,
                  "riskLevel": "LOW",
                  "reasons": ["READ_ONLY_DRY_RUN"],
                  "traceSummary": "trace-summary",
                  "replayRef": "replay-001",
                  "auditRef": "audit-001",
                  "schemaVersion": "%s"
                }
                """
                .formatted(action, DhDryRunRuntimeProperties.DEFAULT_SCHEMA_VERSION);
    }

    /**
     * FakeDhDryRunTransport 只在内存中捕获请求并返回预设 JSON。
     */
    static final class FakeDhDryRunTransport implements DhDryRunTransport {
        private final List<DhDryRunTransportRequest> requests = new ArrayList<>();
        private String responseBody = validResponse("OBSERVE");
        private int statusCode = 200;
        private boolean timeout;

        /**
         * 发送 fake request；不访问网络。
         *
         * @param request signed request
         * @return 预设 response
         */
        @Override
        public DhDryRunTransportResponse send(DhDryRunTransportRequest request) {
            requests.add(request);
            if (timeout) {
                throw new DhDryRunTransportTimeoutException("test timeout");
            }
            return new DhDryRunTransportResponse(statusCode, responseBody);
        }

        /**
         * 设置下一次 fake response。
         *
         * @param responseBody response body
         */
        void responseBody(String responseBody) {
            this.responseBody = responseBody;
        }

        /**
         * 设置 fake status code。
         *
         * @param statusCode HTTP-like status code
         */
        void statusCode(int statusCode) {
            this.statusCode = statusCode;
        }

        /**
         * 设置是否抛出 timeout。
         *
         * @param timeout true 表示 send 时抛出 timeout
         */
        void timeout(boolean timeout) {
            this.timeout = timeout;
        }

        /**
         * 返回已捕获请求数量。
         *
         * @return send 调用次数
         */
        int callCount() {
            return requests.size();
        }

        /**
         * 返回最后一个 captured request。
         *
         * @return last request
         */
        DhDryRunTransportRequest lastRequest() {
            return requests.get(requests.size() - 1);
        }
    }
}
