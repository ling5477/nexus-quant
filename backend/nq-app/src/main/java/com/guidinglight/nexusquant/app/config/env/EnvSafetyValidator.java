package com.guidinglight.nexusquant.app.config.env;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * EnvSafetyValidator 负责 GateK Batch 5B-ENV 的纯规则校验。
 *
 * <p>Why: CI/test/profile 安全边界必须 fail closed，但启动期 guard 不应读取真实
 * {@code .env} 文件，也不能把 secret material 打到日志里。本类只接收调用方已经解析出的
 * 环境事实，并仅输出配置项名称与冲突原因，便于单元测试覆盖冲突矩阵，同时避免 Spring
 * {@code Environment} 或进程环境变量污染测试可重复性。</p>
 */
final class EnvSafetyValidator {

    private static final Set<String> SAFE_PLACEHOLDERS = Set.of(
            "PLACEHOLDER_ONLY",
            "DO_NOT_COMMIT_REAL_VALUE",
            "REPLACE_WITH_LOCAL_PLACEHOLDER"
    );

    private static final Set<String> REAL_EXCHANGE_HOST_MARKERS = Set.of(
            "okx.com",
            "binance.com",
            "bybit.com",
            "gate.io",
            "gateio.ws",
            "coinbase.com",
            "kraken.com",
            "crypto.com",
            "hyperliquid.xyz"
    );

    private EnvSafetyValidator() {
    }

    /**
     * 校验 CI/test/no-outbound 环境事实。
     *
     * <p>Why: Batch 5B-ENV 的核心是把危险组合挡在启动期，而不是等到 adapter 或任务调度器
     * 进入真实 HTTP 路径后才失败。返回值包含全部违规项，调用方再统一 fail closed，便于一次
     * 启动失败暴露所有配置冲突。</p>
     *
     * @param facts 已脱敏的环境事实；credential map 只用于判断是否非空/非 placeholder，不输出值
     * @return 违规项列表；空列表表示可以继续启动
     */
    static List<String> validate(Facts facts) {
        Objects.requireNonNull(facts, "facts must not be null");
        List<String> violations = new ArrayList<>();
        boolean ciOrTestBoundary = facts.ci() || facts.testProfileActive();
        boolean noOutbound = facts.effectiveNoOutbound();

        if (facts.liveEnabled() && noOutbound) {
            violations.add("LIVE=true conflicts with no-outbound=true");
        }
        if (facts.ci() && facts.liveEnabled()) {
            violations.add("CI=true forbids LIVE=true");
        }
        if (facts.ci() && (facts.realProviderEnabled() || facts.realClientEnabled())) {
            violations.add("CI=true forbids real provider/client");
        }
        if (ciOrTestBoundary && facts.realExchangeEnabled()) {
            violations.add("CI/test profile forbids real exchange enabled");
        }
        if (ciOrTestBoundary && facts.aiEnabled()) {
            violations.add("CI/test profile forbids AI runtime");
        }
        if (ciOrTestBoundary && facts.dhRuntimeEnabled()) {
            violations.add("CI/test profile forbids DH runtime");
        }
        if (facts.publicMarketDataManualProfileActive()) {
            if (facts.liveEnabled()) {
                violations.add("public-marketdata-manual profile forbids LIVE=true");
            }
            if (facts.aiEnabled()) {
                violations.add("public-marketdata-manual profile forbids AI runtime");
            }
            if (facts.dhRuntimeEnabled()) {
                violations.add("public-marketdata-manual profile forbids DH runtime");
            }
            if (facts.realProviderEnabled() || facts.realClientEnabled() || facts.realExchangeEnabled()) {
                violations.add("public-marketdata-manual profile forbids real provider/client/exchange");
            }
        }
        if (noOutbound) {
            facts.endpointValues().forEach((name, value) -> {
                if (isRealExchangeEndpoint(value)) {
                    violations.add("no-outbound=true forbids real exchange endpoint: " + name);
                }
            });
        }
        if (ciOrTestBoundary) {
            facts.credentialValues().forEach((name, value) -> {
                if (looksLikeCredentialMaterial(value)) {
                    violations.add("CI/test profile forbids real credential material: " + name);
                }
            });
        }
        return violations;
    }

    /**
     * 判断示例值或配置值是否属于允许的 placeholder。
     *
     * <p>Why: 5B-ENV 只允许三类统一 placeholder，避免 {@code .env.example}、CI 或测试配置
     * 混入看起来像真实 key/secret 的样例。该方法保持 package-private，供回归测试直接固化。</p>
     */
    static boolean isPlaceholder(String value) {
        return value != null && SAFE_PLACEHOLDERS.contains(value.trim());
    }

    private static boolean looksLikeCredentialMaterial(String value) {
        if (value == null || value.isBlank() || isPlaceholder(value)) {
            return false;
        }
        return true;
    }

    private static boolean isRealExchangeEndpoint(String value) {
        if (value == null || value.isBlank() || isPlaceholder(value)) {
            return false;
        }
        String lower = value.trim().toLowerCase(Locale.ROOT);
        if (lower.startsWith("disabled") || lower.startsWith("documentation-only")) {
            return false;
        }
        String host = parseHost(lower);
        String candidate = host == null ? lower : host;
        for (String marker : REAL_EXCHANGE_HOST_MARKERS) {
            if (candidate.equals(marker) || candidate.endsWith("." + marker) || candidate.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private static String parseHost(String value) {
        try {
            URI uri = URI.create(value);
            return uri.getHost() == null ? null : uri.getHost().toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * Facts 是启动期 guard 的脱敏输入模型。
     *
     * <p>Why: 把 profile、开关、endpoint 和 credential 名称集中成不可变记录，可以让测试覆盖
     * 所有冲突组合，同时确保 guard 错误信息只引用配置名，不泄露 credential value。</p>
     */
    record Facts(
            Set<String> activeProfiles,
            boolean ci,
            boolean liveEnabled,
            boolean aiEnabled,
            boolean dhRuntimeEnabled,
            boolean realProviderEnabled,
            boolean realClientEnabled,
            boolean realExchangeEnabled,
            boolean configuredNoOutbound,
            Map<String, String> credentialValues,
            Map<String, String> endpointValues
    ) {
        Facts {
            activeProfiles = Set.copyOf(activeProfiles);
            credentialValues = Map.copyOf(credentialValues);
            endpointValues = Map.copyOf(endpointValues);
        }

        boolean testProfileActive() {
            return activeProfiles.stream().map(profile -> profile.toLowerCase(Locale.ROOT)).anyMatch(profile ->
                    profile.equals("test")
                            || profile.equals("ci")
                            || profile.equals("ci-app-smoke")
                            || profile.equals("paper")
                            || profile.endsWith("-test")
                            || profile.endsWith("-smoke")
            );
        }

        boolean publicMarketDataManualProfileActive() {
            return activeProfiles.stream().map(profile -> profile.toLowerCase(Locale.ROOT)).anyMatch(profile ->
                    profile.equals("public-marketdata-manual")
            );
        }

        boolean effectiveNoOutbound() {
            return configuredNoOutbound || ci || testProfileActive();
        }
    }
}
