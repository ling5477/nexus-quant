package com.guidinglight.nexusquant.adapter.api.service;

import com.guidinglight.nexusquant.adapter.api.model.AdapterCapability;
import com.guidinglight.nexusquant.adapter.api.model.AdapterReadinessDecision;
import com.guidinglight.nexusquant.adapter.api.model.AdapterReadinessReason;
import com.guidinglight.nexusquant.adapter.api.model.AdapterReadinessStatus;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * DefaultAdapterReadinessService 是 GateM-0 的静态 fail-closed readiness 策略。
 * <p>
 * Why:
 * 当前 GateL baseline 下没有真实 provider / RealClient / 真实 credential / LIVE 授权，因此 readiness
 * 不需要读取进程环境、credential 或访问网络——本实现是纯函数式的策略表：把已冻结的 No-Real 边界
 * （Noop 无真实能力、OKX/Binance endpoint sentinel + credential 未配置 + LIVE 未授权、未知一律复核）
 * 固化为运行时决策。任何真实交易能力恒为 allowed=false。
 *
 * <p>无 IO、无 credential、无网络、无副作用；无状态，线程安全。未来若真实接入，须另起 Gate 提供
 * 一个会查询真实 runtime 状态的实现，而不是放宽本默认策略。
 */
public final class DefaultAdapterReadinessService implements AdapterReadinessService {

    // 已知 no-real / paper 家族 venue 标识。Noop 永远不是真实 provider。
    private static final String VENUE_NOOP = "NOOP";
    private static final String VENUE_PAPER = "PAPER";
    private static final String VENUE_SIM = "SIM";
    private static final String VENUE_OKX = "OKX";
    private static final String VENUE_BINANCE = "BINANCE";

    private final Clock clock;

    /** 生产装配使用系统时钟。 */
    public DefaultAdapterReadinessService() {
        this(Clock.systemUTC());
    }

    /**
     * 测试可注入固定时钟以稳定 checkedAt。
     *
     * @param clock 评估时间来源；不可为空
     */
    public DefaultAdapterReadinessService(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public AdapterReadinessDecision evaluate(String venue, AdapterCapability capability) {
        Instant checkedAt = Instant.now(clock);
        // capability 缺失：无法判断范围，fail-closed。
        if (capability == null) {
            return new AdapterReadinessDecision(
                    normalizeForReport(venue),
                    // 用一个占位能力无意义；capability 为 null 时直接走 unknown 决策但保留可读 venue。
                    AdapterCapability.PLACE_ORDER,
                    AdapterReadinessStatus.UNKNOWN_REQUIRES_REVIEW,
                    false,
                    false,
                    List.of(AdapterReadinessReason.UNKNOWN_REQUIRES_REVIEW),
                    checkedAt,
                    "capability is null; fail-closed pending review");
        }

        String normalized = normalizeVenue(venue);
        if (normalized == null) {
            return unknownVenue(venue, capability, checkedAt);
        }

        return switch (normalized) {
            case VENUE_NOOP, VENUE_PAPER, VENUE_SIM -> evaluateNoop(normalized, capability, checkedAt);
            case VENUE_OKX, VENUE_BINANCE -> evaluateExchange(normalized, capability, checkedAt);
            default -> unknownVenue(venue, capability, checkedAt);
        };
    }

    /**
     * Noop / paper stub：不是真实 provider，任何能力 allowed=false，状态 NO_REAL。
     */
    private AdapterReadinessDecision evaluateNoop(
            String venue, AdapterCapability capability, Instant checkedAt) {
        return new AdapterReadinessDecision(
                venue,
                capability,
                AdapterReadinessStatus.NO_REAL,
                false,
                false,
                List.of(AdapterReadinessReason.NO_REAL_DISABLED),
                checkedAt,
                "no-real stub adapter; not a real provider");
    }

    /**
     * OKX / Binance：当前 endpoint 为 disabled:// sentinel、credential 未配置、LIVE 未授权，
     * real provider 未实现。所有能力 allowed=false，状态 NOT_READY，并按能力附上具体原因。
     */
    private AdapterReadinessDecision evaluateExchange(
            String venue, AdapterCapability capability, Instant checkedAt) {
        List<AdapterReadinessReason> reasons = new ArrayList<>();
        // 默认 endpoint 为 disabled:// sentinel，恒成立，保证 reasons 非空。
        reasons.add(AdapterReadinessReason.ENDPOINT_DISABLED_SENTINEL);
        if (capability.requiresCredentials()) {
            // runtime credential 默认 *.unconfigured()，私有能力 fail-closed。
            reasons.add(AdapterReadinessReason.CREDENTIALS_MISSING);
        }
        if (capability.isLiveMutating()) {
            // LIVE DISABLED，真实下单 / 撤单类能力 fail-closed。
            reasons.add(AdapterReadinessReason.LIVE_DISABLED);
        }
        if (capability == AdapterCapability.PERMISSION_PROBE || capability.isMarketData()) {
            // real permission probe / real marketdata provider 均 NOT IMPLEMENTED。
            reasons.add(AdapterReadinessReason.REAL_PROVIDER_NOT_IMPLEMENTED);
        }
        return new AdapterReadinessDecision(
                venue,
                capability,
                AdapterReadinessStatus.NOT_READY,
                false,
                false,
                reasons,
                checkedAt,
                "exchange adapter not authorized: endpoint sentinel / credential unconfigured / live disabled");
    }

    /**
     * 未知 venue：证据不足，fail-closed，要求复核。
     */
    private AdapterReadinessDecision unknownVenue(
            String venue, AdapterCapability capability, Instant checkedAt) {
        return new AdapterReadinessDecision(
                normalizeForReport(venue),
                capability,
                AdapterReadinessStatus.UNKNOWN_REQUIRES_REVIEW,
                false,
                false,
                List.of(AdapterReadinessReason.UNKNOWN_REQUIRES_REVIEW),
                checkedAt,
                "unknown venue; fail-closed pending review");
    }

    /**
     * 规范化 venue 为大写、去空白；null / blank 返回 null（视为未知）。
     */
    private static String normalizeVenue(String venue) {
        if (venue == null) {
            return null;
        }
        String trimmed = venue.trim();
        return trimmed.isEmpty() ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    /**
     * 仅用于决策 message / venue 字段展示；null / blank 用稳定占位避免空指针。
     */
    private static String normalizeForReport(String venue) {
        String normalized = normalizeVenue(venue);
        return normalized == null ? "UNKNOWN" : normalized;
    }
}
