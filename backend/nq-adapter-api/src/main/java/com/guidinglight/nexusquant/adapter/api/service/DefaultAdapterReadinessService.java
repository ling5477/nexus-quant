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

    // 已知 no-real / fake / stub / future-real venue 标识。它们均不得被解释为真实 provider。
    private static final String VENUE_NOOP = "NOOP";
    private static final String VENUE_PAPER = "PAPER";
    private static final String VENUE_SIM = "SIM";
    private static final String VENUE_FAKE = "FAKE";
    private static final String VENUE_STUB = "STUB";
    private static final String VENUE_FUTURE_REAL = "FUTURE_REAL";
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
        // capability 缺失或显式未指定：无法判断范围，fail-closed（GateM-1 修正 GateM-0 P2-2，
        // 不再用 PLACE_ORDER 占位误报为交易能力，统一用 UNSPECIFIED 自描述）。
        if (capability == null || capability == AdapterCapability.UNSPECIFIED) {
            return new AdapterReadinessDecision(
                    normalizeForReport(venue),
                    AdapterCapability.UNSPECIFIED,
                    AdapterReadinessStatus.UNKNOWN_REQUIRES_REVIEW,
                    false,
                    false,
                    List.of(AdapterReadinessReason.UNKNOWN_REQUIRES_REVIEW),
                    checkedAt,
                    "capability unspecified; fail-closed pending review");
        }

        String normalized = normalizeVenue(venue);
        if (normalized == null) {
            return unknownVenue(venue, capability, checkedAt);
        }

        return switch (normalized) {
            case VENUE_NOOP, VENUE_PAPER, VENUE_SIM -> evaluateNoopFamily(normalized, capability, checkedAt);
            case VENUE_FAKE -> evaluateSyntheticAdapter(
                    normalized,
                    capability,
                    checkedAt,
                    AdapterReadinessStatus.FAKE,
                    List.of(AdapterReadinessReason.FAKE_ADAPTER_DISABLED),
                    "fake adapter is disabled for real exchange readiness");
            case VENUE_STUB -> evaluateSyntheticAdapter(
                    normalized,
                    capability,
                    checkedAt,
                    AdapterReadinessStatus.STUB,
                    List.of(AdapterReadinessReason.STUB_ADAPTER_DISABLED),
                    "stub adapter is disabled for real exchange readiness");
            case VENUE_FUTURE_REAL -> evaluateFutureReal(normalized, capability, checkedAt);
            case VENUE_OKX, VENUE_BINANCE -> evaluateExchange(normalized, capability, checkedAt);
            default -> unknownVenue(venue, capability, checkedAt);
        };
    }

    /**
     * Noop / paper stub：不是真实 provider，任何能力 allowed=false，状态 NO_REAL。
     * PAPER / SIM 额外携带 READY_FOR_PAPER_ONLY reason，显式说明纸面可用语义不能提升为 real-ready。
     */
    private AdapterReadinessDecision evaluateNoopFamily(
            String venue, AdapterCapability capability, Instant checkedAt) {
        List<AdapterReadinessReason> reasons = new ArrayList<>();
        reasons.add(AdapterReadinessReason.NO_REAL_DISABLED);
        reasons.add(AdapterReadinessReason.NO_REAL_PROVIDER);
        if (VENUE_PAPER.equals(venue) || VENUE_SIM.equals(venue)) {
            reasons.add(AdapterReadinessReason.READY_FOR_PAPER_ONLY);
        }
        if (capability == AdapterCapability.PERMISSION_PROBE) {
            reasons.add(AdapterReadinessReason.PERMISSION_PROBE_DISABLED);
        }
        return new AdapterReadinessDecision(
                venue,
                capability,
                AdapterReadinessStatus.NO_REAL,
                false,
                false,
                reasons,
                checkedAt,
                "no-real stub adapter; not a real provider");
    }

    /**
     * Fake / stub adapter：只作为测试替身或占位实现，永远不得穿透到真实交易能力。
     */
    private AdapterReadinessDecision evaluateSyntheticAdapter(
            String venue,
            AdapterCapability capability,
            Instant checkedAt,
            AdapterReadinessStatus status,
            List<AdapterReadinessReason> baseReasons,
            String message) {
        List<AdapterReadinessReason> reasons = new ArrayList<>(baseReasons);
        reasons.add(AdapterReadinessReason.NO_REAL_PROVIDER);
        if (capability == AdapterCapability.PERMISSION_PROBE) {
            reasons.add(AdapterReadinessReason.PERMISSION_PROBE_DISABLED);
        }
        return new AdapterReadinessDecision(
                venue,
                capability,
                status,
                false,
                false,
                reasons,
                checkedAt,
                message);
    }

    /**
     * FutureReal 是未来真实接入 Gate 的占位，不是当前 real provider。
     */
    private AdapterReadinessDecision evaluateFutureReal(
            String venue, AdapterCapability capability, Instant checkedAt) {
        List<AdapterReadinessReason> reasons = new ArrayList<>();
        reasons.add(AdapterReadinessReason.FUTURE_REAL_DISABLED);
        reasons.add(AdapterReadinessReason.NO_REAL_PROVIDER);
        reasons.add(AdapterReadinessReason.REAL_PROVIDER_NOT_IMPLEMENTED);
        if (capability == AdapterCapability.PERMISSION_PROBE) {
            reasons.add(AdapterReadinessReason.PERMISSION_PROBE_DISABLED);
        }
        if (capability.isLiveMutating()) {
            reasons.add(AdapterReadinessReason.LIVE_DISABLED);
            reasons.add(AdapterReadinessReason.LIVE_NOT_AUTHORIZED);
        }
        return new AdapterReadinessDecision(
                venue,
                capability,
                AdapterReadinessStatus.NOT_IMPLEMENTED,
                false,
                false,
                reasons,
                checkedAt,
                "future-real adapter not implemented; disabled until a separate Gate authorizes it");
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
            reasons.add(AdapterReadinessReason.CREDENTIAL_UNCONFIGURED);
        }
        if (capability.isLiveMutating()) {
            // LIVE DISABLED，真实下单 / 撤单类能力 fail-closed。
            reasons.add(AdapterReadinessReason.LIVE_DISABLED);
            reasons.add(AdapterReadinessReason.LIVE_NOT_AUTHORIZED);
        }
        if (capability == AdapterCapability.PERMISSION_PROBE || capability.isMarketData()) {
            // real permission probe / real marketdata provider 均 NOT IMPLEMENTED。
            reasons.add(AdapterReadinessReason.REAL_PROVIDER_NOT_IMPLEMENTED);
        }
        if (capability == AdapterCapability.PERMISSION_PROBE) {
            // 默认 NoReal / skipped probe 不得被解释为真实权限已验证。
            reasons.add(AdapterReadinessReason.PERMISSION_PROBE_DISABLED);
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
