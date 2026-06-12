package com.guidinglight.nexusquant.app.integration0.support;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Int0Contract 冻结 NQ-DH Integration-0 contract test 的常量口径。
 *
 * <p>来源：{@code docs/current/NQ_DH_INTEGRATION0_CONTRACT_FREEZE.md} /
 * {@code NQ_DH_INTEGRATION0_SECURITY_POLICY.md} / {@code NQ_DH_INTEGRATION0_CONTRACT_TEST_PLAN.md}。
 *
 * <p>本类仅用于测试。Integration-0 不是真实集成：这里不连接任何真实 NQ 入站端点、真实 DH client、
 * 真实交易所或真实凭证；所有 secret / tenant / source 都是固定假值。
 */
public final class Int0Contract {

    private Int0Contract() {}

    // canonical headers（X-NQ-DH-* 族，见 SECURITY_POLICY §2）
    public static final String H_SOURCE = "X-NQ-DH-Source";
    public static final String H_TENANT = "X-NQ-DH-Tenant-Id";
    public static final String H_REQUEST_ID = "X-NQ-DH-Request-Id";
    public static final String H_TRACE_ID = "X-NQ-DH-Trace-Id";
    public static final String H_TIMESTAMP = "X-NQ-DH-Timestamp";
    public static final String H_NONCE = "X-NQ-DH-Nonce";
    public static final String H_SIGNATURE = "X-NQ-DH-Signature";
    public static final String H_CONTENT_TYPE = "Content-Type";

    public static final List<String> REQUIRED_HEADERS = List.of(
            H_SOURCE, H_TENANT, H_REQUEST_ID, H_TRACE_ID, H_TIMESTAMP, H_NONCE, H_SIGNATURE, H_CONTENT_TYPE);

    public static final String CONTENT_TYPE_JSON = "application/json";

    // test-only 固定假值，绝不读取真实环境变量 / .env / 真实凭证
    public static final String FAKE_SECRET = "int0-test-secret";
    public static final String FAKE_TENANT = "t-test-int0";
    public static final String FAKE_SOURCE = "dh-int0-test";

    public static final long TIMESTAMP_WINDOW_SECONDS = 300L;
    public static final int MAX_PAYLOAD_BYTES = 64 * 1024;

    public static final Set<String> ALLOWED_SOURCES = Set.of(FAKE_SOURCE);

    /** 禁止的敏感数据字段（归一化后比较）。见 CONTRACT_FREEZE §8。 */
    public static final List<String> FORBIDDEN_FIELDS = List.of(
            "apiKey", "apiSecret", "secret", "token", "cookie", "passphrase", "privateKey", "mnemonic",
            "walletPrivateKey", "exchangeCredential", "accountCredential", "rawRequest", "rawResponse",
            "fullPrompt", "fullContext", "rawPrompt", "rawContext", "signatureRawMaterial", "authorization",
            "databaseConnectionString", "password", "twoFactorSecret", "recoveryCode");

    /** 禁止的能力 / 执行动作 token（归一化后比较）。见 CONTRACT_FREEZE §4。 */
    public static final List<String> FORBIDDEN_CAPABILITIES = List.of(
            "placeOrder", "cancelOrder", "mutateOrderStatus", "mutateStrategyStatus", "startPaperRun",
            "stopPaperRun", "mutateRiskState", "readCredential", "writeNqDb", "triggerLive",
            "agentDirectTrade", "feedbackDirectExecute", "orderCommand", "liveExecution");

    /** Integration-0 contract test 用的反馈事件枚举（test-only，不耦合 NQ main code）。 */
    public static final List<String> ALLOWED_FEEDBACK_EVENT_TYPES = List.of(
            "PAPER_RUN_CREATED", "PAPER_RUN_ALERT_RAISED", "PAPER_RUN_STOPPED", "BACKTEST_RESULT_READY",
            "RISK_REJECTED", "STRATEGY_PUBLISHED", "EVALUATION_READY", "RECOVERY_COMPLETED");

    public static final List<String> CANDIDATE_REQUIRED_FIELDS = List.of(
            "contractName", "version", "direction", "tenantId", "requestId", "traceId", "symbol",
            "timeframe", "side", "confidence", "rationaleSummary", "riskSummary", "executionIntent");

    public static final List<String> FEEDBACK_REQUIRED_FIELDS = List.of(
            "contractName", "version", "direction", "tenantId", "requestId", "traceId", "eventType",
            "sanitizedSummary", "sourceSystem");

    /** 归一化：转小写并去除非字母数字，使 api_key / api-key / apiKey 视为同一 token。 */
    public static String normalize(String token) {
        return token.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
