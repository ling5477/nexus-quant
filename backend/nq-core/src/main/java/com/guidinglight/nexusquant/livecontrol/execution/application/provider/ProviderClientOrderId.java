package com.guidinglight.nexusquant.livecontrol.execution.application.provider;

import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionIntent;
import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionIntentCanonicalEncoder;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 将 GateY-3 durable intent identity 映射为 OKX provider client order identity。
 *
 * <p>provider value 使用 UUID 的 32 位 lowercase hex 表示，因此映射是可逆且无截断的；同一
 * intent 在 replay/restart 后得到相同值，不同 intent 不会因摘要截断产生额外碰撞域。构造时同时
 * 校验 GateY-3 execution clientOrderId，任何篡改或把已有 provider value 绑定到另一 intent 都会
 * fail closed。</p>
 */
public record ProviderClientOrderId(
        UUID intentId,
        String executionClientOrderId,
        String value
) {
    public static final int OKX_MAX_LENGTH = 32;
    private static final Pattern OKX_VALUE = Pattern.compile("[0-9a-f]{32}");

    public ProviderClientOrderId {
        Objects.requireNonNull(intentId, "intentId must not be null");
        executionClientOrderId = requireText(executionClientOrderId, "executionClientOrderId");
        value = requireText(value, "value");
        String expectedExecutionId = ExecutionIntentCanonicalEncoder.stableClientOrderId(intentId);
        if (!expectedExecutionId.equals(executionClientOrderId)) {
            throw new IllegalArgumentException("execution clientOrderId does not bind the intent identity");
        }
        String expectedProviderId = canonicalProviderValue(intentId);
        if (!expectedProviderId.equals(value) || value.length() > OKX_MAX_LENGTH
                || !OKX_VALUE.matcher(value).matches()) {
            throw new IllegalArgumentException("provider clientOrderId is not the canonical OKX identity");
        }
    }

    public static ProviderClientOrderId fromIntent(ExecutionIntent intent) {
        Objects.requireNonNull(intent, "intent must not be null");
        return from(intent.intentId(), intent.clientOrderId());
    }

    public static ProviderClientOrderId from(UUID intentId, String executionClientOrderId) {
        return new ProviderClientOrderId(
                intentId,
                executionClientOrderId,
                canonicalProviderValue(Objects.requireNonNull(intentId, "intentId must not be null"))
        );
    }

    private static String canonicalProviderValue(UUID intentId) {
        return intentId.toString().replace("-", "");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank() || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(name + " must be a nonblank single-line value");
        }
        return value;
    }
}
