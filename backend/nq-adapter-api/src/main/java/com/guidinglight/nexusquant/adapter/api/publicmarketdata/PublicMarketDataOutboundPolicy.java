package com.guidinglight.nexusquant.adapter.api.publicmarketdata;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * PublicMarketDataOutboundPolicy 固化 GateO O-1 的 allowlist / denylist。
 *
 * <p>Why: public outbound 的安全边界不能依赖调用方记忆，也不能通过 URL 字符串散落判断。
 * 本策略先检查 endpoint category，再检查请求是否要求 authentication / signature，最后用路径关键字
 * 做纵深防御。任何未列入 allowlist、任何 signed/private/credential-like 请求一律 fail-closed。</p>
 *
 * <p>线程安全：无可变状态；失败模式：返回 denied decision，不抛出包含敏感内容的异常。</p>
 */
public final class PublicMarketDataOutboundPolicy {

    private static final Set<String> PRIVATE_ENDPOINT_TOKENS = Set.of(
            "account",
            "balance",
            "order",
            "orders",
            "cancel",
            "amend",
            "position",
            "positions",
            "wallet",
            "transfer",
            "withdraw",
            "deposit",
            "subaccount",
            "private",
            "listenkey",
            "api_key",
            "apikey",
            "secret",
            "passphrase",
            "signature",
            "sign"
    );

    private final Clock clock;

    /**
     * 生产默认使用 UTC 系统时钟。
     */
    public PublicMarketDataOutboundPolicy() {
        this(Clock.systemUTC());
    }

    /**
     * 测试可注入固定时钟，保证 policy decision 可重复。
     *
     * @param clock 决策时间来源；不可为空
     */
    public PublicMarketDataOutboundPolicy(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 评估请求是否可以进入 HTTP client。
     *
     * @param request 已脱敏请求模型；不可为空
     * @return allow 或 fail-closed denied decision
     */
    public PublicMarketDataOutboundDecision evaluate(PublicMarketDataOutboundRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Instant checkedAt = Instant.now(clock);
        PublicMarketDataEndpointCategory category = request.endpointCategory();
        if (category == null) {
            return PublicMarketDataOutboundDecision.deny(
                    PublicMarketDataEndpointCategory.UNKNOWN,
                    "endpoint category missing; fail-closed",
                    checkedAt);
        }
        if (category.privateOrSigned() || request.requiresAuthentication() || request.signedRequest()) {
            return PublicMarketDataOutboundDecision.deny(
                    category,
                    "private, authenticated, signed, credential, or permission-probe endpoint is forbidden",
                    checkedAt);
        }
        if (!category.allowedByDefault()) {
            return PublicMarketDataOutboundDecision.deny(
                    category,
                    "endpoint category is not in GateO O-1 public REST allowlist",
                    checkedAt);
        }
        if (isInvalidEndpointReference(request.endpointPath())) {
            return PublicMarketDataOutboundDecision.deny(
                    category,
                    "endpoint path must be a relative public REST path without scheme, authority, or fragment",
                    checkedAt);
        }
        if (containsPrivateEndpointToken(request.endpointPath())) {
            return PublicMarketDataOutboundDecision.deny(
                    category,
                    "endpoint path contains private or credential-like token",
                    checkedAt);
        }
        return PublicMarketDataOutboundDecision.allow(category, checkedAt);
    }

    /**
     * 检查 endpointPath 是否是可安全 resolve 到配置 base URI 的相对路径。
     *
     * <p>Why: {@link URI#resolve(URI)} 会把 `//host/path` 解释为 network-path reference 并切换
     * authority。O-1 允许 path-only + query，但必须拒绝 scheme、authority、userInfo、fragment、
     * only-query 和非法 URI，避免 manual profile 下 endpointPath 改写出站 host。</p>
     */
    public boolean isInvalidEndpointReference(String endpointPath) {
        if (endpointPath == null || endpointPath.isBlank()) {
            return true;
        }
        String trimmed = endpointPath.trim();
        if (trimmed.startsWith("?") || trimmed.startsWith("#")) {
            return true;
        }
        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException ex) {
            return true;
        }
        return uri.getScheme() != null
                || uri.getRawAuthority() != null
                || uri.getHost() != null
                || uri.getUserInfo() != null
                || uri.getRawFragment() != null
                || uri.getRawPath() == null
                || uri.getRawPath().isBlank();
    }

    /**
     * 检查路径与 query 中是否含私有 endpoint 或 credential-like token。
     *
     * <p>Why: category 是主边界，路径 token 是纵深防御，避免调用方把私有路径误标成 public ticker。
     * 返回值只表示是否命中，不回传原始路径，防止 query string 泄露。</p>
     */
    public boolean containsPrivateEndpointToken(String endpointPath) {
        if (endpointPath == null || endpointPath.isBlank()) {
            return false;
        }
        String lower = endpointPath.toLowerCase(Locale.ROOT);
        if (lower.contains("://")) {
            return true;
        }
        return PRIVATE_ENDPOINT_TOKENS.stream().anyMatch(lower::contains);
    }
}
