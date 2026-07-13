package com.guidinglight.nexusquant.adapter.okx.service;

import com.guidinglight.nexusquant.adapter.api.model.EndpointAccessClass;
import com.guidinglight.nexusquant.adapter.api.model.EndpointGuardReason;
import com.guidinglight.nexusquant.adapter.api.model.EndpointPolicyDecision;
import com.guidinglight.nexusquant.adapter.api.model.ExchangeCapability;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * OkxSpotEndpointGuard 在 private transport 之前执行无 IO 的 default-deny 决策。
 *
 * <p>GateW-1 不建立 concrete private endpoint allowlist：所有 private read 都先被 runtime
 * disabled 拒绝；下单、撤单和资金动作永久拒绝。仅保留仓库既有 public marketdata path 的精确
 * GET allowlist，且 path 使用 URI 校验、大小写归一、重复斜线归一和 percent-encoded path 拒绝。
 * query string 从不参与 endpoint 分类，因此不能改变 public/private/mutating 判定。</p>
 *
 * <p>该 guard 不持有 HTTP client、credential、signer 或 Spring 依赖，不会读取环境或执行网络请求。
 * public allow 也不表示 trading authorization。</p>
 */
public final class OkxSpotEndpointGuard {

    // 仅复用仓库已有的 public marketdata 路径；private allowlist 在 GateW-1 故意为空。
    private static final Set<String> PUBLIC_READ_PATHS = Set.of(
            "/api/v5/public/instruments",
            "/api/v5/market/history-candles"
    );

    private final OkxSpotCapabilityMatrix capabilityMatrix;

    public OkxSpotEndpointGuard() {
        this(new OkxSpotCapabilityMatrix());
    }

    OkxSpotEndpointGuard(OkxSpotCapabilityMatrix capabilityMatrix) {
        this.capabilityMatrix = capabilityMatrix;
    }

    /**
     * 按 capability、HTTP method 与 endpoint reference 判断是否允许继续。
     *
     * @param capability 声明的 operation capability；null 视作 UNKNOWN
     * @param method     HTTP method；只允许公开路径使用 GET
     * @param endpoint   path-only reference；query 可存在但不参与分类
     * @return 无敏感字段的 allow/deny 决策
     */
    public EndpointPolicyDecision evaluate(ExchangeCapability capability, String method, String endpoint) {
        OkxSpotCapabilityDefinition definition = capabilityMatrix.definitionFor(capability);
        String canonicalPath = canonicalPath(endpoint);
        if (canonicalPath == null || normalizedMethod(method) == null) {
            return deny(definition, EndpointGuardReason.DENY_UNKNOWN_ENDPOINT);
        }

        return switch (definition.endpointAccessClass()) {
            case FUNDS_MOVEMENT -> deny(definition, EndpointGuardReason.DENY_FUNDS_MOVEMENT);
            case PRIVATE_MUTATING -> deny(definition, EndpointGuardReason.DENY_MUTATING_ENDPOINT);
            case PRIVATE_READ_ONLY -> deny(definition, EndpointGuardReason.DENY_PRIVATE_RUNTIME_DISABLED);
            case LOCAL_ONLY, UNKNOWN -> deny(definition, EndpointGuardReason.DENY_UNKNOWN_ENDPOINT);
            case PUBLIC_READ -> evaluatePublicRead(definition, method, canonicalPath);
        };
    }

    private EndpointPolicyDecision evaluatePublicRead(
            OkxSpotCapabilityDefinition definition,
            String method,
            String canonicalPath
    ) {
        if (!"GET".equals(normalizedMethod(method))) {
            return deny(definition, EndpointGuardReason.DENY_MUTATING_ENDPOINT);
        }
        if (!PUBLIC_READ_PATHS.contains(canonicalPath)) {
            return deny(definition, EndpointGuardReason.DENY_UNKNOWN_ENDPOINT);
        }
        return EndpointPolicyDecision.allowPublicRead(definition.capability());
    }

    private EndpointPolicyDecision deny(
            OkxSpotCapabilityDefinition definition,
            EndpointGuardReason reason
    ) {
        return EndpointPolicyDecision.deny(definition.capability(), definition.endpointAccessClass(), reason);
    }

    /**
     * 将 path-only input 规范化为匹配 key；任何 scheme、authority、fragment、编码 path、反斜线或
     * dot segment 都拒绝，避免调用方以 URL 变体绕过精确 allowlist。
     */
    private static String canonicalPath(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return null;
        }
        URI uri;
        try {
            uri = URI.create(endpoint.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
        if (uri.getScheme() != null
                || uri.getRawAuthority() != null
                || uri.getUserInfo() != null
                || uri.getRawFragment() != null) {
            return null;
        }
        String rawPath = uri.getRawPath();
        if (rawPath == null
                || rawPath.isBlank()
                || !rawPath.startsWith("/")
                || rawPath.indexOf('%') >= 0
                || rawPath.indexOf('\\') >= 0) {
            return null;
        }
        String normalized = rawPath.replaceAll("/{2,}", "/");
        for (String segment : normalized.split("/")) {
            if (".".equals(segment) || "..".equals(segment)) {
                return null;
            }
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private static String normalizedMethod(String method) {
        if (method == null || method.isBlank()) {
            return null;
        }
        return method.trim().toUpperCase(Locale.ROOT);
    }
}
