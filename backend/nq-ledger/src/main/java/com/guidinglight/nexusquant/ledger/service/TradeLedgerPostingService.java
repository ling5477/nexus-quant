package com.guidinglight.nexusquant.ledger.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.common.numeric.NumericPolicy;
import com.guidinglight.nexusquant.common.numeric.NumericType;
import com.guidinglight.nexusquant.contracts.event.*;
import com.guidinglight.nexusquant.contracts.model.LedgerDirection;
import com.guidinglight.nexusquant.contracts.model.OrderSide;
import com.guidinglight.nexusquant.ledger.model.*;
import com.guidinglight.nexusquant.ledger.service.port.LedgerPostingRepository;
import com.guidinglight.nexusquant.ledger.service.port.LedgerRiskAuditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

/**
 * TradeLedgerPostingService 负责 Gate B 成交记账与仓位投影。
 * <p>
 * Why:
 * 记账、风险、审计、事件发布必须在同一业务编排里统一执行，
 * 否则容易出现“分录成功但事件缺失”或“仓位更新重复”等不可恢复偏差。
 */
@Service
public class TradeLedgerPostingService {

    private static final String SOURCE = "nq-ledger.trade-posting";

    private final LedgerPostingRepository ledgerPostingRepository;
    private final LedgerRiskAuditRepository ledgerRiskAuditRepository;
    private final EventPublisherPort eventPublisherPort;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * @param ledgerPostingRepository   账本与仓位仓储
     * @param ledgerRiskAuditRepository 风险/审计仓储
     * @param eventPublisherPort        事件事实链追加端口
     * @param objectMapper              JSON 序列化器
     */
    public TradeLedgerPostingService(
            LedgerPostingRepository ledgerPostingRepository,
            LedgerRiskAuditRepository ledgerRiskAuditRepository,
            EventPublisherPort eventPublisherPort,
            ObjectMapper objectMapper
    ) {
        this.ledgerPostingRepository = Objects.requireNonNull(
                ledgerPostingRepository,
                "ledgerPostingRepository must not be null"
        );
        this.ledgerRiskAuditRepository = Objects.requireNonNull(
                ledgerRiskAuditRepository,
                "ledgerRiskAuditRepository must not be null"
        );
        this.eventPublisherPort = Objects.requireNonNull(eventPublisherPort, "eventPublisherPort must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.clock = Clock.systemUTC();
    }

    /**
     * 对单笔成交执行记账与投影。
     *
     * @param request 成交记账请求
     * @return 记账结果
     */
    @Transactional
    public LedgerPostingResult postTrade(TradeLedgerRequest request) {
        validateRequest(request);
        List<LedgerPostingEntry> entries = buildEntries(request);
        if (!isBalanced(entries)) {
            return handleImbalance(request, entries, "LEDGER_NOT_BALANCED");
        }
        boolean allEntriesAlreadyPosted = entries.stream()
                .allMatch(entry -> ledgerPostingRepository.existsByIdempotencyKey(entry.idempotencyKey()));
        if (allEntriesAlreadyPosted) {
            return new LedgerPostingResult(true, true, "IDEMPOTENT_HIT");
        }

        for (LedgerPostingEntry entry : entries) {
            if (ledgerPostingRepository.existsByIdempotencyKey(entry.idempotencyKey())) {
                continue;
            }
            BigDecimal balanceAfter = ledgerPostingRepository.currentBalance(entry.accountId(), entry.currency())
                    .add(entry.delta());
            LedgerPostingEntry persistedEntry = new LedgerPostingEntry(
                    entry.entryId(),
                    entry.accountId(),
                    entry.currency(),
                    entry.delta(),
                    balanceAfter,
                    entry.direction(),
                    entry.refType(),
                    entry.refId(),
                    entry.idempotencyKey(),
                    entry.traceId(),
                    entry.ts()
            );
            ledgerPostingRepository.insertEntry(persistedEntry);
            ledgerPostingRepository.insertLedgerEvent(
                    persistedEntry.entryId(),
                    "POSTED",
                    toJson(detail("entry_id", persistedEntry.entryId(), "delta", persistedEntry.delta())),
                    persistedEntry.traceId()
            );
        }

        PositionProjection positionProjection = updatePositionProjection(request);
        writeAccountSnapshots(request, positionProjection);
        publishEvent(
                TopicNames.LEDGER_EVENT_V1,
                request.tradeId(),
                request.traceId(),
                new LedgerPosted(request.tradeId(), request.accountId(), "POSTED", "BALANCE_CHECK_PASS", Instant.now(clock))
        );
        publishEvent(
                TopicNames.POSITION_EVENT_V1,
                request.orderId(),
                request.traceId(),
                new PositionUpdated(
                        positionProjection.accountId(),
                        positionProjection.symbol(),
                        positionProjection.qty(),
                        positionProjection.availableQty(),
                        positionProjection.avgPrice(),
                        "TRADE",
                        Instant.now(clock)
                )
        );
        ledgerRiskAuditRepository.appendAudit(
                "LEDGER",
                "LEDGER_POSTED",
                request.tradeId(),
                request.traceId(),
                detail("trade_id", request.tradeId(), "entry_count", entries.size())
        );
        return new LedgerPostingResult(true, false, "POSTED");
    }

    /**
     * 校验分录是否平衡。
     * <p>
     * Why:
     * 该方法用于强制“净额必须归零”的最小口径，防止错误分录进入账本事实层。
     *
     * @param entries 分录集合
     * @return true 表示平衡
     */
    boolean isBalanced(List<LedgerPostingEntry> entries) {
        BigDecimal sum = entries.stream().map(LedgerPostingEntry::delta).reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.compareTo(BigDecimal.ZERO) == 0;
    }

    private LedgerPostingResult handleImbalance(
            TradeLedgerRequest request,
            List<LedgerPostingEntry> entries,
            String reason
    ) {
        ledgerRiskAuditRepository.appendRiskEvent(
                "LEDGER",
                request.tradeId(),
                "REJECT",
                reason,
                "HIGH",
                request.traceId()
        );
        ledgerRiskAuditRepository.appendAudit(
                "LEDGER",
                "LEDGER_POST_FAILED",
                request.tradeId(),
                request.traceId(),
                detail("trade_id", request.tradeId(), "reason", reason, "entry_count", entries.size())
        );
        publishEvent(
                TopicNames.LEDGER_EVENT_V1,
                request.tradeId(),
                request.traceId(),
                new LedgerPostFailed(request.tradeId(), request.accountId(), "FAILED", reason, Instant.now(clock))
        );
        publishEvent(
                TopicNames.RISK_EVENT_V1,
                request.tradeId(),
                request.traceId(),
                new RiskEventRaised("LEDGER", request.tradeId(), "REJECT", reason, "HIGH", Instant.now(clock))
        );
        return new LedgerPostingResult(false, false, reason);
    }

    private List<LedgerPostingEntry> buildEntries(TradeLedgerRequest request) {
        BigDecimal amount = NumericPolicy.normalize(NumericType.AMOUNT, request.price().multiply(request.qty()));
        String currency = resolveQuoteCurrency(request.symbol());
        BigDecimal leftDelta = request.side() == OrderSide.BUY ? amount.negate() : amount;
        BigDecimal rightDelta = leftDelta.negate();
        Instant ts = request.ts() == null ? Instant.now(clock) : request.ts();
        List<LedgerPostingEntry> entries = new ArrayList<>();
        entries.add(createEntry(request, currency, leftDelta, "1", ts));
        entries.add(createEntry(request, currency, rightDelta, "2", ts));
        if (request.fee() != null && request.fee().compareTo(BigDecimal.ZERO) > 0) {
            // Why: GateC 接入真实交易所后，fee 已经是稳定事实，不能再沿用 GateB 的“故意不平衡”占位逻辑。
            // 这里先用成对分录保证账本平衡与幂等，后续再按真实会计科目细化。
            entries.add(createEntry(
                    request,
                    request.feeCurrency() == null ? currency : request.feeCurrency(),
                    NumericPolicy.normalize(NumericType.FEE, request.fee()).negate(),
                    "FEE_1",
                    ts
            ));
            entries.add(createEntry(
                    request,
                    request.feeCurrency() == null ? currency : request.feeCurrency(),
                    NumericPolicy.normalize(NumericType.FEE, request.fee()),
                    "FEE_2",
                    ts
            ));
        }
        return entries;
    }

    private LedgerPostingEntry createEntry(
            TradeLedgerRequest request,
            String currency,
            BigDecimal delta,
            String suffix,
            Instant ts
    ) {
        return new LedgerPostingEntry(
                "le-" + UUID.randomUUID(),
                request.accountId(),
                currency,
                delta,
                BigDecimal.ZERO,
                delta.compareTo(BigDecimal.ZERO) >= 0 ? LedgerDirection.CREDIT : LedgerDirection.DEBIT,
                "TRADE",
                request.tradeId(),
                request.tradeId() + ":LEDGER:" + suffix,
                request.traceId(),
                ts
        );
    }

    private PositionProjection updatePositionProjection(TradeLedgerRequest request) {
        PositionProjection current = ledgerPostingRepository.findPosition(request.accountId(), request.symbol())
                .orElse(new PositionProjection(
                        request.accountId(),
                        request.symbol(),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        request.traceId()
                ));
        BigDecimal qtyChange = request.side() == OrderSide.BUY ? request.qty() : request.qty().negate();
        String baseCurrency = resolveBaseCurrency(request.symbol());
        if (request.fee() != null
                && request.fee().compareTo(BigDecimal.ZERO) > 0
                && request.feeCurrency() != null
                && request.feeCurrency().equalsIgnoreCase(baseCurrency)) {
            // Why: 若手续费以 base 资产收取，实际持仓应扣减 fee，避免 positions 高估。
            qtyChange = qtyChange.subtract(request.fee());
        }
        BigDecimal nextQty = NumericPolicy.normalize(NumericType.QTY, current.qty().add(qtyChange));
        BigDecimal nextAvgPrice = calculateNextAvgPrice(current, request, nextQty);
        PositionProjection projection = new PositionProjection(
                request.accountId(),
                request.symbol(),
                nextQty,
                nextQty,
                nextAvgPrice,
                request.traceId()
        );
        ledgerPostingRepository.upsertPosition(projection, Instant.now(clock));
        return projection;
    }

    /**
     * 写入本地最小账户快照。
     * <p>
     * Why:
     * 第五批要打通 `/__gated/accounts/{accountId}`，而当前 PAPER 成交后只有 ledger_entries 与 positions，
     * 没有任何 `account_snapshots` 写入。这里按“base 资产来自持仓投影、quote/fee 资产来自账本余额”的
     * 最小口径补快照，先让本地读链可验证，再留待后续真实交易所同步路径继续细化。
     *
     * @param request            成交记账请求
     * @param positionProjection 最新持仓投影
     */
    private void writeAccountSnapshots(TradeLedgerRequest request, PositionProjection positionProjection) {
        Instant snapshotTs = request.ts() == null ? Instant.now(clock) : request.ts();
        Map<String, AccountSnapshotProjection> snapshots = new LinkedHashMap<>();

        String baseCurrency = resolveBaseCurrency(request.symbol());
        snapshots.put(
                baseCurrency,
                new AccountSnapshotProjection(
                        request.accountId(),
                        baseCurrency,
                        NumericPolicy.normalize(NumericType.QTY, positionProjection.qty()),
                        NumericPolicy.normalize(NumericType.QTY, positionProjection.availableQty()),
                        NumericPolicy.normalize(
                                NumericType.QTY,
                                positionProjection.qty().subtract(positionProjection.availableQty())
                        ),
                        snapshotTs,
                        request.traceId()
                )
        );

        appendLedgerBackedSnapshot(snapshots, request.accountId(), resolveQuoteCurrency(request.symbol()), snapshotTs, request.traceId());
        if (request.feeCurrency() != null && !request.feeCurrency().isBlank()) {
            appendLedgerBackedSnapshot(snapshots, request.accountId(), request.feeCurrency(), snapshotTs, request.traceId());
        }

        for (AccountSnapshotProjection snapshot : snapshots.values()) {
            ledgerPostingRepository.insertAccountSnapshot(snapshot);
        }
    }

    private void appendLedgerBackedSnapshot(
            Map<String, AccountSnapshotProjection> snapshots,
            Long accountId,
            String currency,
            Instant snapshotTs,
            String traceId
    ) {
        if (currency == null || currency.isBlank() || snapshots.containsKey(currency)) {
            return;
        }
        BigDecimal balance = NumericPolicy.normalize(
                NumericType.AMOUNT,
                ledgerPostingRepository.currentBalance(accountId, currency)
        );
        snapshots.put(
                currency,
                new AccountSnapshotProjection(
                        accountId,
                        currency,
                        balance,
                        balance,
                        NumericPolicy.normalize(NumericType.AMOUNT, BigDecimal.ZERO),
                        snapshotTs,
                        traceId
                )
        );
    }

    private BigDecimal calculateNextAvgPrice(PositionProjection current, TradeLedgerRequest request, BigDecimal nextQty) {
        if (request.side() == OrderSide.SELL) {
            if (nextQty.compareTo(BigDecimal.ZERO) <= 0) {
                return BigDecimal.ZERO.setScale(8);
            }
            return NumericPolicy.normalize(NumericType.PRICE, current.avgPrice());
        }
        BigDecimal currentQty = current.qty();
        if (currentQty.compareTo(BigDecimal.ZERO) <= 0) {
            return NumericPolicy.normalize(NumericType.PRICE, request.price());
        }
        BigDecimal weightedAmount = current.avgPrice().multiply(currentQty).add(request.price().multiply(request.qty()));
        return NumericPolicy.normalize(NumericType.PRICE, weightedAmount.divide(nextQty, 8, RoundingMode.HALF_UP));
    }

    private void publishEvent(String topic, String key, String traceId, Object payload) {
        EventEnvelope<Object> envelope = new EventEnvelope<>(
                "evt-" + UUID.randomUUID(),
                payload.getClass().getSimpleName(),
                1,
                Instant.now(clock),
                SOURCE,
                traceId,
                key,
                payload
        );
        eventPublisherPort.append(topic, envelope);
    }

    private String resolveQuoteCurrency(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return "USDT";
        }
        if (symbol.contains("-")) {
            return symbol.substring(symbol.indexOf('-') + 1);
        }
        if (symbol.contains("/")) {
            return symbol.substring(symbol.indexOf('/') + 1);
        }
        return "USDT";
    }

    private String resolveBaseCurrency(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return "BASE";
        }
        if (symbol.contains("-")) {
            return symbol.substring(0, symbol.indexOf('-'));
        }
        if (symbol.contains("/")) {
            return symbol.substring(0, symbol.indexOf('/'));
        }
        return symbol;
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize json payload", ex);
        }
    }

    private void validateRequest(TradeLedgerRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (request.tradeId() == null || request.tradeId().isBlank()) {
            throw new IllegalArgumentException("tradeId must not be blank");
        }
        if (request.orderId() == null || request.orderId().isBlank()) {
            throw new IllegalArgumentException("orderId must not be blank");
        }
        if (request.accountId() == null || request.accountId() <= 0) {
            throw new IllegalArgumentException("accountId must be positive");
        }
        if (request.side() == null) {
            throw new IllegalArgumentException("side must not be null");
        }
        if (request.price() == null || request.price().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("price must be positive");
        }
        if (request.qty() == null || request.qty().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("qty must be positive");
        }
        if (request.fee() != null && request.fee().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("fee must not be negative");
        }
        if (request.traceId() == null || request.traceId().isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
    }

    private Map<String, Object> detail(Object... fields) {
        LinkedHashMap<String, Object> detail = new LinkedHashMap<>();
        for (int index = 0; index < fields.length; index += 2) {
            detail.put(String.valueOf(fields[index]), fields[index + 1]);
        }
        return detail;
    }
}
