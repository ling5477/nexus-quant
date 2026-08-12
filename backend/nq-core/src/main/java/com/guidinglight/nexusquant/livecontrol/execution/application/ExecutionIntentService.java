package com.guidinglight.nexusquant.livecontrol.execution.application;

import com.guidinglight.nexusquant.livecontrol.domain.LiveControlException;
import com.guidinglight.nexusquant.livecontrol.execution.application.port.ExecutionIntentRepository;
import com.guidinglight.nexusquant.livecontrol.execution.application.port.FakeExchangeMutationPort;
import com.guidinglight.nexusquant.livecontrol.execution.application.port.FakeExchangeQueryResult;
import com.guidinglight.nexusquant.livecontrol.execution.application.port.FakeExchangeResult;
import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionIntent;
import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionIntentCanonicalEncoder;
import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionIntentDraft;
import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionIntentState;
import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionReceiptCanonicalEncoder;
import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionReceiptDraft;
import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionReceiptOutcome;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Fake/local execution orchestration。Repository 方法各自提交短事务；mutation 调用永远位于事务外。
 */
public final class ExecutionIntentService {

    private final ExecutionIntentRepository repository;
    private final FakeExchangeMutationPort exchange;

    public ExecutionIntentService(ExecutionIntentRepository repository, FakeExchangeMutationPort exchange) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.exchange = Objects.requireNonNull(exchange, "exchange must not be null");
    }

    public ExecutionIntent createOrGetPlace(
            UUID intentId,
            UUID sessionId,
            String symbol,
            String side,
            BigDecimal quantity,
            BigDecimal limitPrice,
            String localOrderId
    ) {
        return repository.createOrGet(ExecutionIntentCanonicalEncoder.place(
                intentId, sessionId, symbol, side, quantity, limitPrice, localOrderId));
    }

    public ExecutionIntent createOrGetCancel(
            UUID intentId,
            UUID sessionId,
            String symbol,
            String localOrderId,
            String originalClientOrderId
    ) {
        return repository.createOrGet(ExecutionIntentCanonicalEncoder.cancel(
                intentId, sessionId, symbol, localOrderId, originalClientOrderId));
    }

    /**
     * claim 后先独立提交 SEND_STARTED，再执行一次 fake mutation。任何重复/恢复调用都不会再次 mutation。
     */
    public ExecutionIntent claimAndExecute(
            UUID intentId,
            String workerId,
            UUID claimToken,
            Duration lease,
            Instant observedAt
    ) {
        Optional<ExecutionIntent> claimed = repository.claim(intentId, workerId, claimToken, lease);
        if (claimed.isEmpty()) {
            return repository.find(intentId)
                    .orElseThrow(() -> new LiveControlException("EXECUTION_INTENT_NOT_FOUND", "intent was not found"));
        }
        ExecutionIntent sendStarted = repository.markSendStarted(
                        intentId, claimed.get().version(), claimToken)
                .orElseThrow(() -> new LiveControlException(
                        "EXECUTION_INTENT_SEND_CAS_CONFLICT", "intent changed before SEND_STARTED"));

        FakeExchangeResult result = sendStarted.action()
                == com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionIntentAction.PLACE
                ? exchange.place(sendStarted)
                : exchange.cancel(sendStarted);
        ResultMapping mapping = mapMutation(result);
        ExecutionReceiptDraft receipt = ExecutionReceiptCanonicalEncoder.draft(
                UUID.randomUUID(), intentId, mapping.outcome(), result.exchangeRequestId(),
                result.exchangeOrderId(), result.errorCategory(), result.errorCode(), observedAt);
        return repository.appendReceiptAndTransition(
                intentId, sendStarted.version(), claimToken, receipt, mapping.target());
    }

    /**
     * crash/timeout 恢复只按 stable clientOrderId 查询；绝不调用 place/cancel。
     */
    public ExecutionIntent reconcileUnknown(UUID intentId, Instant observedAt) {
        ExecutionIntent current = repository.find(intentId)
                .orElseThrow(() -> new LiveControlException("EXECUTION_INTENT_NOT_FOUND", "intent was not found"));
        if (current.state() == ExecutionIntentState.SEND_STARTED) {
            current = repository.markAmbiguousForRecovery(
                            current.intentId(), current.version(), current.claimToken())
                    .orElseThrow(() -> new LiveControlException(
                            "EXECUTION_INTENT_RECOVERY_CAS_CONFLICT", "intent changed before UNKNOWN recovery"));
        }
        if (current.state() != ExecutionIntentState.UNKNOWN) {
            return current;
        }

        FakeExchangeQueryResult query = exchange.queryByClientOrderId(current.clientOrderId());
        if (query.status() == FakeExchangeQueryResult.Status.UNKNOWN) {
            return current;
        }
        ExecutionReceiptOutcome outcome = query.status() == FakeExchangeQueryResult.Status.NOT_FOUND
                ? ExecutionReceiptOutcome.QUERY_NOT_FOUND : ExecutionReceiptOutcome.QUERY_CONFIRMED;
        String category = query.errorCategory() == null ? "FAKE_RECONCILIATION" : query.errorCategory();
        String code = query.errorCode() == null ? query.status().name() : query.errorCode();
        ExecutionReceiptDraft receipt = ExecutionReceiptCanonicalEncoder.draft(
                UUID.randomUUID(), intentId, outcome, query.exchangeRequestId(), query.exchangeOrderId(),
                category, code, observedAt);
        return repository.appendReceiptAndTransition(
                intentId, current.version(), current.claimToken(), receipt, ExecutionIntentState.RECONCILED);
    }

    private static ResultMapping mapMutation(FakeExchangeResult result) {
        return switch (result.outcome()) {
            case ACKNOWLEDGED -> new ResultMapping(
                    ExecutionReceiptOutcome.ACKNOWLEDGED, ExecutionIntentState.SEND_SUCCEEDED);
            case REJECTED -> new ResultMapping(
                    ExecutionReceiptOutcome.REJECTED, ExecutionIntentState.FAILED);
            case TIMEOUT -> new ResultMapping(
                    ExecutionReceiptOutcome.TIMEOUT, ExecutionIntentState.UNKNOWN);
            case TRANSPORT_ERROR -> new ResultMapping(
                    ExecutionReceiptOutcome.TRANSPORT_ERROR, ExecutionIntentState.UNKNOWN);
            case UNKNOWN -> new ResultMapping(
                    ExecutionReceiptOutcome.UNKNOWN, ExecutionIntentState.UNKNOWN);
        };
    }

    private record ResultMapping(ExecutionReceiptOutcome outcome, ExecutionIntentState target) {
    }
}
