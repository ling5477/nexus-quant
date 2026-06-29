package com.guidinglight.nexusquant.trading.application.boundary;

import com.guidinglight.nexusquant.trading.application.CancelOrderRequest;
import com.guidinglight.nexusquant.trading.application.PlaceOrderRequest;

import java.util.Locale;
import java.util.Objects;

/**
 * PaperToRealBoundaryGuard 固化 Paper artefact 不能进入真实交易授权路径的最小运行时边界。
 *
 * <p>Why:
 * GateM-4 要求 Paper run / Paper order / Paper fill / Paper position / Paper risk 只能作为
 * SIM/Paper 内部事实使用，不能被误当成 LIVE / real trading authorization。本 guard 不判断 venue
 * 是否等于 PAPER，因为既有 Paper 回归仍会通过本地 stub venue 运行；它只拒绝带有 Paper artefact
 * 标识的来源、幂等键和对象 ID 进入正式 mutating path。</p>
 *
 * <p>线程安全 / 副作用：纯静态校验，无 IO、无 credential、无网络调用。</p>
 */
public final class PaperToRealBoundaryGuard {

    public static final String PAPER_ORDER_NOT_REAL_AUTHORIZATION =
            "PAPER_ORDER_NOT_REAL_AUTHORIZATION";
    public static final String PAPER_FILL_NOT_REAL_FILL =
            "PAPER_FILL_NOT_REAL_FILL";
    public static final String PAPER_POSITION_NOT_REAL_ACCOUNT_POSITION =
            "PAPER_POSITION_NOT_REAL_ACCOUNT_POSITION";
    public static final String PAPER_RISK_NOT_LIVE_RISK_APPROVAL =
            "PAPER_RISK_NOT_LIVE_RISK_APPROVAL";
    public static final String STRATEGY_PUBLISH_NOT_LIVE_ENABLE =
            "STRATEGY_PUBLISH_NOT_LIVE_ENABLE";

    private PaperToRealBoundaryGuard() {
    }

    /**
     * 校验正式下单入口不得携带 Paper artefact。
     *
     * @param request 下单请求；不可为空
     * @throws IllegalArgumentException 当 source / strategyRunId / clientOrderId / requestId /
     *                                  idempotencyKey 呈现 Paper artefact 语义时抛出
     */
    public static void requireNoPaperArtifactForRealOrderPath(PlaceOrderRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        rejectIfPaperMarker(request.source(), "source", PAPER_ORDER_NOT_REAL_AUTHORIZATION);
        rejectIfPaperMarker(request.strategyRunId(), "strategyRunId", PAPER_ORDER_NOT_REAL_AUTHORIZATION);
        rejectIfPaperMarker(request.clientOrderId(), "clientOrderId", PAPER_ORDER_NOT_REAL_AUTHORIZATION);
        rejectIfPaperMarker(request.requestId(), "requestId", PAPER_ORDER_NOT_REAL_AUTHORIZATION);
        rejectIfPaperMarker(request.idempotencyKey(), "idempotencyKey", PAPER_ORDER_NOT_REAL_AUTHORIZATION);
    }

    /**
     * 校验正式撤单入口不得使用 Paper order 标识定位真实撤单目标。
     *
     * @param request 撤单请求；不可为空
     * @throws IllegalArgumentException 当 orderId / clientOrderId / requestId 呈现 Paper artefact 语义时抛出
     */
    public static void requireNoPaperArtifactForRealCancelPath(CancelOrderRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        rejectIfPaperMarker(request.orderId(), "orderId", PAPER_ORDER_NOT_REAL_AUTHORIZATION);
        rejectIfPaperMarker(request.clientOrderId(), "clientOrderId", PAPER_ORDER_NOT_REAL_AUTHORIZATION);
        rejectIfPaperMarker(request.requestId(), "requestId", PAPER_ORDER_NOT_REAL_AUTHORIZATION);
    }

    /**
     * 校验 Paper fill / Paper trade ID 不能作为真实成交事实进入 real ledger 或 reconcile 解释。
     *
     * @param tradeId 成交 ID；允许为空，非空时若命中 Paper marker 则拒绝
     */
    public static void requireNotPaperFillForRealFill(String tradeId) {
        rejectIfPaperMarker(tradeId, "tradeId", PAPER_FILL_NOT_REAL_FILL);
    }

    /**
     * 校验 Paper position / Paper run ID 不能作为真实账户持仓或余额授权依据。
     *
     * @param artefactId Paper position / run / balance 相关标识；允许为空，非空时若命中 Paper marker 则拒绝
     */
    public static void requireNotPaperPositionForRealAccount(String artefactId) {
        rejectIfPaperMarker(artefactId, "artefactId", PAPER_POSITION_NOT_REAL_ACCOUNT_POSITION);
    }

    /**
     * 判断文本是否呈现 Paper artefact 语义。
     *
     * @param value 输入文本
     * @return true 表示该文本看起来来自 Paper domain，不得作为 real authorization
     */
    public static boolean isPaperArtifactMarker(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("paper")
                || normalized.contains("paper_")
                || normalized.contains("paper-")
                || normalized.contains("paper:")
                || normalized.startsWith("ptr-")
                || normalized.startsWith("po-")
                || normalized.startsWith("pt-")
                || normalized.startsWith("pos-");
    }

    private static void rejectIfPaperMarker(String value, String fieldName, String reasonCode) {
        if (isPaperArtifactMarker(value)) {
            throw new IllegalArgumentException(
                    reasonCode + ": " + fieldName + " is a Paper artefact and cannot authorize real trading");
        }
    }
}
