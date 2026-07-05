package com.guidinglight.nexusquant.integration.dh;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * DhDryRunRuntimeClient 是 NQ 侧 Integration-1 limited dry-run client 的隔离实现。
 *
 * <p>Why: client 只生成 signed dry-run request、调用测试 fake transport、解析 DH response envelope，并把结果写入
 * record-only recorder。它默认关闭，不创建真实 HTTP client，不接 provider，不接 order / execution / risk / ledger /
 * account / paper / live，也不把 LONG_BIAS / SHORT_BIAS 映射为 BUY / SELL。</p>
 */
public final class DhDryRunRuntimeClient {

    /** 当前唯一允许的 DH dry-run endpoint path。 */
    public static final String DECISION_DRY_RUNS_PATH = "/api/ai/decision-dry-runs";

    private static final List<String> DEFAULT_FORBIDDEN_CAPABILITIES = List.of(
            DhDryRunForbiddenCapability.PLACE_ORDER.name(),
            DhDryRunForbiddenCapability.CANCEL_ORDER.name(),
            DhDryRunForbiddenCapability.MUTATE_NQ_STATE.name(),
            DhDryRunForbiddenCapability.READ_NQ_DB.name(),
            DhDryRunForbiddenCapability.WRITE_NQ_DB.name(),
            DhDryRunForbiddenCapability.START_PAPER_RUN.name(),
            DhDryRunForbiddenCapability.START_LIVE_RUN.name(),
            DhDryRunForbiddenCapability.CALL_PROVIDER.name(),
            DhDryRunForbiddenCapability.FORWARD_CREDENTIAL.name());

    private final DhDryRunRuntimeProperties properties;
    private final DhDryRunTransport transport;
    private final DhDryRunRecorder recorder;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final DhDryRunNonceGenerator nonceGenerator;

    /**
     * 构造 limited dry-run client。
     *
     * @param properties     disabled-by-default runtime 配置
     * @param transport      fake/disabled transport；本轮不得是真实 HTTP 实现
     * @param recorder       record-only recorder；不得写交易事实源
     * @param objectMapper   JSON serializer/parser
     * @param clock          可控时钟，用于生成 UTC Z timestamp 和测试
     * @param nonceGenerator nonce 生成器
     */
    public DhDryRunRuntimeClient(
            DhDryRunRuntimeProperties properties,
            DhDryRunTransport transport,
            DhDryRunRecorder recorder,
            ObjectMapper objectMapper,
            Clock clock,
            DhDryRunNonceGenerator nonceGenerator) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.recorder = Objects.requireNonNull(recorder, "recorder");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.nonceGenerator = Objects.requireNonNull(nonceGenerator, "nonceGenerator");
    }

    /**
     * 在非 production profile 语义下执行 dry-run client。
     *
     * @param command 只读 dry-run request command
     * @return accepted 或 fail-closed record 摘要
     */
    public DhDryRunClientResult execute(DhDryRunRequestCommand command) {
        return execute(command, false);
    }

    /**
     * 执行一次 limited dry-run。
     *
     * <p>Why: production profile 默认被 `productionEnabled=false` 阻断；所有 preflight failure 都不会调用
     * transport，所有 response failure 都只写 fail-closed record。</p>
     *
     * @param command           只读 dry-run request command；必须包含 requestId/traceId/tenantId
     * @param productionProfile 当前是否按 production profile 运行
     * @return accepted 或 fail-closed record 摘要
     */
    public DhDryRunClientResult execute(DhDryRunRequestCommand command, boolean productionProfile) {
        PreflightFailure failure = preflightFailure(command, productionProfile);
        if (failure != null) {
            return failClosed(command, null, null, failure.errorCode(), failure.reason());
        }

        DhDryRunRequestEnvelope envelope = requestEnvelope(command);
        String body;
        try {
            body = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException ex) {
            return failClosed(command, null, null, DhDryRunErrorCode.CLIENT_PARSE_ERROR, "request_serialization_failed");
        }

        String path;
        try {
            path = DhDryRunSigning.endpointPath(properties.endpointUrl());
        } catch (IllegalArgumentException ex) {
            return failClosed(command, null, null, DhDryRunErrorCode.CLIENT_DISABLED, "endpoint_url_invalid");
        }
        if (!DECISION_DRY_RUNS_PATH.equals(path)) {
            return failClosed(command, null, null, DhDryRunErrorCode.CLIENT_DISABLED, "endpoint_path_denied");
        }

        Map<String, String> headers = canonicalHeaders(envelope, body, path);
        DhDryRunTransportRequest transportRequest =
                new DhDryRunTransportRequest(properties.endpointUrl(), properties.timeout(), headers, body);

        try {
            DhDryRunTransportResponse response = transport.send(transportRequest);
            return handleResponse(command, response);
        } catch (DhDryRunTransportTimeoutException ex) {
            return failClosed(command, null, null, DhDryRunErrorCode.CLIENT_TIMEOUT, "client_timeout");
        } catch (RuntimeException ex) {
            return failClosed(command, null, null, DhDryRunErrorCode.UNKNOWN_ERROR, "transport_error");
        }
    }

    private PreflightFailure preflightFailure(DhDryRunRequestCommand command, boolean productionProfile) {
        if (!properties.enabled()) {
            return new PreflightFailure(DhDryRunErrorCode.CLIENT_DISABLED, "runtime_disabled");
        }
        if (!properties.clientEnabled()) {
            return new PreflightFailure(DhDryRunErrorCode.CLIENT_DISABLED, "client_disabled");
        }
        if (properties.killSwitch()) {
            return new PreflightFailure(DhDryRunErrorCode.CLIENT_DISABLED, "kill_switch_enabled");
        }
        if (productionProfile && !properties.productionEnabled()) {
            return new PreflightFailure(DhDryRunErrorCode.CLIENT_DISABLED, "production_disabled");
        }
        if (!properties.hasEndpointUrl()) {
            return new PreflightFailure(DhDryRunErrorCode.CLIENT_DISABLED, "endpoint_url_missing");
        }
        if (!properties.hasSigningSecret()) {
            return new PreflightFailure(DhDryRunErrorCode.CLIENT_DISABLED, "signing_secret_missing");
        }
        if (!DhDryRunRuntimeProperties.DEFAULT_SOURCE.equals(properties.source())) {
            return new PreflightFailure(DhDryRunErrorCode.SOURCE_DENIED, "source_denied");
        }
        if (command == null || isBlank(command.requestId()) || isBlank(command.traceId()) || isBlank(command.tenantId())) {
            return new PreflightFailure(DhDryRunErrorCode.RESPONSE_POLICY_VIOLATION, "request_identity_missing");
        }
        if (command.decisionContext() == null || containsUnsafeRequestContext(command.decisionContext())) {
            return new PreflightFailure(DhDryRunErrorCode.RESPONSE_POLICY_VIOLATION, "request_context_policy_violation");
        }
        return null;
    }

    private DhDryRunRequestEnvelope requestEnvelope(DhDryRunRequestCommand command) {
        String timestamp = clock.instant().toString();
        return new DhDryRunRequestEnvelope(
                command.requestId(),
                command.traceId(),
                command.tenantId(),
                properties.source(),
                timestamp,
                nonceGenerator.newNonce(),
                properties.schemaVersion(),
                true,
                command.decisionContext(),
                DEFAULT_FORBIDDEN_CAPABILITIES);
    }

    private Map<String, String> canonicalHeaders(DhDryRunRequestEnvelope envelope, String body, String path) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(DhDryRunHeaderNames.REQUEST_ID, envelope.requestId());
        headers.put(DhDryRunHeaderNames.TRACE_ID, envelope.traceId());
        headers.put(DhDryRunHeaderNames.TENANT_ID, envelope.tenantId());
        headers.put(DhDryRunHeaderNames.SOURCE, envelope.source());
        headers.put(DhDryRunHeaderNames.TIMESTAMP, envelope.timestamp());
        headers.put(DhDryRunHeaderNames.NONCE, envelope.nonce());
        headers.put(DhDryRunHeaderNames.SCHEMA_VERSION, envelope.schemaVersion());
        String material = DhDryRunSigning.signatureMaterial(
                path,
                envelope.source(),
                envelope.tenantId(),
                envelope.requestId(),
                envelope.traceId(),
                envelope.timestamp(),
                envelope.nonce(),
                envelope.schemaVersion(),
                body);
        headers.put(DhDryRunHeaderNames.SIGNATURE, DhDryRunSigning.hmacSha256Hex(properties.signingSecret(), material));
        return Map.copyOf(headers);
    }

    private DhDryRunClientResult handleResponse(DhDryRunRequestCommand command, DhDryRunTransportResponse response) {
        if (response == null || isBlank(response.body())) {
            return failClosed(command, null, null, DhDryRunErrorCode.CLIENT_PARSE_ERROR, "response_body_missing");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(response.body());
        } catch (JsonProcessingException ex) {
            return failClosed(command, null, null, DhDryRunErrorCode.CLIENT_PARSE_ERROR, "response_parse_failed");
        }

        String decisionId = text(root, "decisionId");
        String auditRef = text(root, "auditRef");
        JsonNode error = root.get("error");
        if (error != null && !error.isNull()) {
            DhDryRunErrorCode errorCode = DhDryRunErrorCode.fromWireCode(text(error, "code"));
            return failClosed(command, decisionId, auditRef, errorCode, "dh_error_envelope");
        }
        if (response.statusCode() >= 400) {
            return failClosed(command, decisionId, auditRef, DhDryRunErrorCode.UNKNOWN_ERROR, "dh_status_error");
        }
        if (hasExecutableInstruction(root)) {
            return failClosed(
                    command,
                    decisionId,
                    auditRef,
                    DhDryRunErrorCode.RESPONSE_POLICY_VIOLATION,
                    "response_contains_executable_instruction");
        }

        DhDryRunResponseEnvelope envelope;
        try {
            envelope = objectMapper.treeToValue(root, DhDryRunResponseEnvelope.class);
        } catch (JsonProcessingException ex) {
            return failClosed(command, decisionId, auditRef, DhDryRunErrorCode.CLIENT_PARSE_ERROR, "response_mapping_failed");
        }

        String validationError = responseValidationError(envelope);
        if (validationError != null) {
            return failClosed(
                    command,
                    decisionId,
                    auditRef,
                    DhDryRunErrorCode.RESPONSE_POLICY_VIOLATION,
                    validationError);
        }

        DhDryRunAction action = DhDryRunAction.valueOf(envelope.action());
        boolean biasOnly = action == DhDryRunAction.LONG_BIAS || action == DhDryRunAction.SHORT_BIAS;
        DhDryRunRecord record = new DhDryRunRecord(
                command.requestId(),
                command.traceId(),
                command.tenantId(),
                envelope.decisionId(),
                envelope.auditRef(),
                action,
                biasOnly,
                true,
                false,
                null,
                null,
                Instant.now(clock));
        recorder.save(record);
        return new DhDryRunClientResult(true, false, record);
    }

    private String responseValidationError(DhDryRunResponseEnvelope envelope) {
        if (envelope == null) {
            return "response_envelope_missing";
        }
        if (!envelope.dryRun()) {
            return "response_dry_run_false";
        }
        if (isBlank(envelope.decisionId())) {
            return "response_decision_id_missing";
        }
        if (!properties.schemaVersion().equals(envelope.schemaVersion())) {
            return "response_schema_version_invalid";
        }
        if (!isAllowedAction(envelope.action())) {
            return "response_action_denied";
        }
        if (envelope.confidence() == null || envelope.confidence() < 0.0d || envelope.confidence() > 1.0d) {
            return "response_confidence_invalid";
        }
        if (isBlank(envelope.riskLevel())) {
            return "response_risk_level_missing";
        }
        if (envelope.reasons() == null || envelope.reasons().isEmpty()) {
            return "response_reasons_missing";
        }
        if (isBlank(envelope.traceSummary())) {
            return "response_trace_summary_missing";
        }
        if (isBlank(envelope.replayRef())) {
            return "response_replay_ref_missing";
        }
        if (isBlank(envelope.auditRef())) {
            return "response_audit_ref_missing";
        }
        return null;
    }

    private DhDryRunClientResult failClosed(
            DhDryRunRequestCommand command,
            String decisionId,
            String auditRef,
            DhDryRunErrorCode errorCode,
            String reason) {
        DhDryRunRecord record = new DhDryRunRecord(
                command == null ? null : command.requestId(),
                command == null ? null : command.traceId(),
                command == null ? null : command.tenantId(),
                decisionId,
                auditRef,
                null,
                false,
                false,
                true,
                errorCode,
                reason,
                Instant.now(clock));
        recorder.save(record);
        return new DhDryRunClientResult(false, true, record);
    }

    private static boolean isAllowedAction(String action) {
        if (isBlank(action)) {
            return false;
        }
        for (DhDryRunAction value : DhDryRunAction.values()) {
            if (value.name().equals(action)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsUnsafeRequestContext(DhDryRunDecisionContext context) {
        List<String> values = new ArrayList<>();
        values.add(context.symbol());
        values.add(context.market());
        values.add(context.timeframe());
        values.add(context.scenario());
        values.add(context.evidenceSummary());
        values.add(context.riskSummary());
        return values.stream().filter(Objects::nonNull).anyMatch(DhDryRunRuntimeClient::containsForbiddenText);
    }

    private static boolean hasExecutableInstruction(JsonNode node) {
        if (node == null || node.isNull()) {
            return false;
        }
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                if (isExecutableFieldName(field.getKey()) || hasExecutableInstruction(field.getValue())) {
                    return true;
                }
            }
            return false;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (hasExecutableInstruction(item)) {
                    return true;
                }
            }
            return false;
        }
        return node.isTextual() && containsForbiddenText(node.asText());
    }

    private static boolean isExecutableFieldName(String fieldName) {
        String normalized = fieldName == null ? "" : fieldName.toLowerCase(Locale.ROOT);
        return normalized.equals("executableorder")
                || normalized.equals("executable_order")
                || normalized.equals("quantity")
                || normalized.equals("qty")
                || normalized.equals("leverage")
                || normalized.equals("orderprice")
                || normalized.equals("order_price")
                || normalized.equals("limitprice")
                || normalized.equals("limit_price")
                || normalized.equals("stopprice")
                || normalized.equals("stop_price");
    }

    private static boolean containsForbiddenText(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.toUpperCase(Locale.ROOT);
        return normalized.contains("BUY")
                || normalized.contains("SELL")
                || normalized.contains("PLACE_ORDER")
                || normalized.contains("CANCEL_ORDER")
                || normalized.contains("APIKEY")
                || normalized.contains("API_KEY")
                || normalized.contains("APISECRET")
                || normalized.contains("API_SECRET")
                || normalized.contains("PASSPHRASE")
                || normalized.contains("CREDENTIAL")
                || normalized.contains("TOKEN")
                || normalized.contains("COOKIE")
                || normalized.contains("ACCOUNTSECRET")
                || normalized.contains("ACCOUNT_SECRET");
    }

    private static String text(JsonNode node, String fieldName) {
        if (node == null || node.get(fieldName) == null || node.get(fieldName).isNull()) {
            return null;
        }
        JsonNode value = node.get(fieldName);
        return value.isTextual() ? value.asText() : value.toString();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record PreflightFailure(DhDryRunErrorCode errorCode, String reason) {
    }
}
