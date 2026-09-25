package com.guidinglight.nexusquant.scheduler.paper;

import com.guidinglight.nexusquant.scheduler.port.OrderExecutionGateway;
import com.guidinglight.nexusquant.scheduler.port.TradeLedgerGateway;

import com.guidinglight.nexusquant.common.numeric.NumericPolicy;
import com.guidinglight.nexusquant.common.numeric.NumericType;
import com.guidinglight.nexusquant.contracts.event.EventEnvelope;
import com.guidinglight.nexusquant.contracts.event.TopicNames;
import com.guidinglight.nexusquant.contracts.event.TradeExecuted;
import com.guidinglight.nexusquant.contracts.model.OrderSide;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.contracts.model.OrderType;
import com.guidinglight.nexusquant.trading.application.port.TradingOrderStatusSnapshot;
import com.guidinglight.nexusquant.trading.application.port.TradingVenueGateway;
import com.guidinglight.nexusquant.trading.domain.OrderRecord;
import com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository;
import com.guidinglight.nexusquant.contracts.event.port.EventPublisherPort;
import com.guidinglight.nexusquant.ledger.contracts.model.LedgerPostingResult;
import com.guidinglight.nexusquant.ledger.contracts.model.TradeLedgerRequest;
import com.guidinglight.nexusquant.scheduler.model.PaperTradeRecord;
import com.guidinglight.nexusquant.scheduler.service.port.TradeRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.guidinglight.nexusquant.trading.domain.TradingVenue;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PaperMatchingService 负责订单状态契约/交易所适配契约的本地 paper 成交同步。
 * <p>
 * Why:
 * 交易所适配契约虽然仍保留 paper 本地成交能力用于回归，但 scheduler 不能再绕过 trading 边界假定订单可撮合。
 * 因此这里先向 `TradingVenueGateway` 查询统一订单快照，再决定是否继续执行本地成交与记账。
 */
@Component
@ConditionalOnProperty(
        prefix = "nq.runtime.trading-components",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class PaperMatchingService {

    private static final String SOURCE = "nq-scheduler.paper-matching";
    private static final int DEFAULT_LIMIT = 100;

    private final OrderExecutionGateway orderExecutionGateway;
    private final TradeRepository tradeRepository;
    private final TradeLedgerGateway tradeLedgerGateway;
    private final EventPublisherPort eventPublisherPort;
    private final AuditLogRepository auditLogRepository;
    private final TradingVenueGateway tradingVenueGateway;
    private final StrategySimDecisionRepository strategySimDecisions;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /**
     * @param orderExecutionGateway 订单查询与迁移网关
     * @param tradeRepository       成交仓储端口
     * @param tradeLedgerGateway    记账网关
     * @param eventPublisherPort    事件事实链追加端口
     * @param auditLogRepository    审计日志仓储
     * @param tradingVenueGateway   trading anti-corruption boundary
     */
    @Autowired
    public PaperMatchingService(
            OrderExecutionGateway orderExecutionGateway,
            TradeRepository tradeRepository,
            TradeLedgerGateway tradeLedgerGateway,
            EventPublisherPort eventPublisherPort,
            AuditLogRepository auditLogRepository,
            TradingVenueGateway tradingVenueGateway,
            StrategySimDecisionRepository strategySimDecisions,
            PlatformTransactionManager transactionManager
    ) {
        this.orderExecutionGateway = Objects.requireNonNull(orderExecutionGateway, "orderExecutionGateway must not be null");
        this.tradeRepository = Objects.requireNonNull(tradeRepository, "tradeRepository must not be null");
        this.tradeLedgerGateway = Objects.requireNonNull(tradeLedgerGateway, "tradeLedgerGateway must not be null");
        this.eventPublisherPort = Objects.requireNonNull(eventPublisherPort, "eventPublisherPort must not be null");
        this.auditLogRepository = Objects.requireNonNull(auditLogRepository, "auditLogRepository must not be null");
        this.tradingVenueGateway = Objects.requireNonNull(
                tradingVenueGateway,
                "tradingVenueGateway must not be null"
        );
        this.strategySimDecisions = strategySimDecisions;
        this.transactionTemplate = transactionManager == null ? null : new TransactionTemplate(transactionManager);
        this.clock = Clock.systemUTC();
    }

    public PaperMatchingService(OrderExecutionGateway orders, TradeRepository trades,
            TradeLedgerGateway ledger, EventPublisherPort events, AuditLogRepository audit,
            TradingVenueGateway venue) {
        this(orders, trades, ledger, events, audit, venue, null, null);
    }

    /**
     * 定时执行 paper 撮合。
     */
    @Scheduled(
            fixedDelayString = "${nq.paper.matching.fixed-delay-ms:2000}",
            initialDelayString = "${nq.paper.matching.initial-delay-ms:2000}"
    )
    public void scheduledMatch() {
        matchOnce(DEFAULT_LIMIT);
    }

    /**
     * 执行一次撮合 tick。
     *
     * @param limit 本次扫描上限
     * @return 本次新生成的 trade 数量
     */
    public int matchOnce(int limit) {
        List<OrderRecord> orders = orderExecutionGateway.findMatchableOrders(limit);
        int newTradeCount = 0;
        for (OrderRecord order : orders) {
            if (order.canonicalVenue() != TradingVenue.PAPER) {
                // Why: 交易所适配契约实盘/模拟盘订单必须由各自 adapter + reconcile 驱动，不能再被 paper 本地撮合器碰到。
                continue;
            }
            try {
                boolean matched = order.clientOrderId().startsWith("coid-sim-")
                        ? Objects.requireNonNull(strategySimDecisions, "strategy SIM decision repository unavailable")
                            .withAccountMutex(order.accountId(), () -> matchInTransaction(order))
                        : matchInTransaction(order);
                if (matched) {
                    newTradeCount++;
                }
            } catch (RuntimeException ex) {
                auditLogRepository.append(
                        "MATCHING",
                        "PAPER_MATCH_FAILED",
                        order.orderId(),
                        order.traceId(),
                        detail("error", ex.getMessage(), "order_id", order.orderId())
                );
            }
        }
        return newTradeCount;
    }

    private boolean matchInTransaction(OrderRecord order) {
        return transactionTemplate == null
                ? matchSingleOrder(order)
                : Boolean.TRUE.equals(transactionTemplate.execute(status -> matchSingleOrder(order)));
    }

    private boolean matchSingleOrder(OrderRecord order) {
        if (order.clientOrderId().startsWith("coid-sim-")) {
            // 与 SIM 决策争用同一账户预算锁；成交和账本必须在同一事务提交。
            Objects.requireNonNull(strategySimDecisions, "strategy SIM decision repository unavailable")
                    .lockAccount(order.accountId());
        }
        TradingOrderStatusSnapshot adapterSnapshot = tradingVenueGateway.getOrderStatus(order, order.traceId());
        // Why: 只有当 adapter 反馈的状态与本地状态一致时，scheduler 才允许继续做本地成交副作用，
        // 这样 paper 路径不再是“完全绕过 adapter 的专用链路”。
        if (!order.status().name().equals(adapterSnapshot.externalStatus())) {
            auditLogRepository.append(
                    "MATCHING",
                    "ADAPTER_STATE_NOT_READY",
                    order.orderId(),
                    order.traceId(),
                    detail(
                            "order_id", order.orderId(),
                            "order_status", order.status().name(),
                            "adapter_status", adapterSnapshot.externalStatus(),
                            "result_category", adapterSnapshot.resultCategory().name(),
                            "venue", order.venue()
                    )
            );
            return false;
        }

        boolean strategySimOrder = order.clientOrderId().startsWith("coid-sim-");
        StrategySimDecisionRepository.DecisionView decision = strategySimOrder
                ? Objects.requireNonNull(strategySimDecisions, "strategy SIM decision repository unavailable")
                    .findByOrderId(order.orderId())
                    .filter(value -> "ACCEPTED".equals(value.status()))
                    .orElseThrow(() -> new IllegalStateException("STRATEGY_SIM_DECISION_NOT_MATERIALIZED"))
                : null;
        BigDecimal marketPrice = NumericPolicy.normalize(NumericType.PRICE,
                decision == null ? resolveMarketPrice() : decision.executionPrice());
        if (!isExecutable(order, marketPrice)) {
            auditLogRepository.append(
                    "MATCHING",
                    "LIMIT_NOT_REACHED",
                    order.orderId(),
                    order.traceId(),
                    detail(
                            "order_id", order.orderId(),
                            "order_type", order.type(),
                            "limit_price", order.price(),
                            "market_price", marketPrice
                    )
            );
            return false;
        }

        Optional<PaperTradeRecord> existingTrade = tradeRepository.findByOrderId(order.orderId());
        PaperTradeRecord trade = existingTrade.orElseGet(() -> createTrade(order, marketPrice,
                decision == null ? BigDecimal.ZERO : decision.feeRate()));
        if (existingTrade.isEmpty()) {
            tradeRepository.insert(trade);
            publishTradeEvent(order, trade);
            auditLogRepository.append(
                    "MATCHING",
                    "TRADE_EXECUTED",
                    trade.tradeId(),
                    trade.traceId(),
                    detail("order_id", order.orderId(), "trade_id", trade.tradeId())
            );
        } else {
            auditLogRepository.append(
                    "MATCHING",
                    "TRADE_DEDUP_HIT",
                    order.orderId(),
                    order.traceId(),
                    detail("order_id", order.orderId(), "trade_id", trade.tradeId())
            );
        }

        LedgerPostingResult postingResult = tradeLedgerGateway.postTrade(new TradeLedgerRequest(
                trade.tradeId(),
                order.orderId(),
                order.accountId(),
                order.symbol(),
                OrderSide.valueOf(order.side()),
                trade.price(),
                trade.qty(),
                trade.fee(),
                trade.feeCurrency(),
                trade.traceId(),
                trade.ts()
        ));
        if (!postingResult.posted()) {
            auditLogRepository.append(
                    "MATCHING",
                    "LEDGER_POSTING_FAILED",
                    trade.tradeId(),
                    trade.traceId(),
                    detail("trade_id", trade.tradeId(), "reason", postingResult.reason())
            );
            if (strategySimOrder) {
                throw new IllegalStateException("STRATEGY_SIM_LEDGER_POSTING_INCOMPLETE");
            }
        }

        if (order.status() != OrderStatus.FILLED) {
            orderExecutionGateway.markFilled(order.orderId(), "PAPER_MATCH_FILLED", order.traceId());
        }
        return existingTrade.isEmpty();
    }

    private PaperTradeRecord createTrade(OrderRecord order, BigDecimal marketPrice, BigDecimal feeRate) {
        BigDecimal price = NumericPolicy.normalize(NumericType.PRICE, resolveExecutionPrice(order, marketPrice));
        BigDecimal qty = NumericPolicy.normalize(NumericType.QTY, order.qty());
        BigDecimal fee = NumericPolicy.normalize(NumericType.FEE, price.multiply(qty).multiply(feeRate));
        return new PaperTradeRecord(
                "trd-" + UUID.randomUUID(),
                order.orderId(),
                order.accountId(),
                order.symbol(),
                order.venue(),
                order.externalOrderId(),
                null,
                price,
                qty,
                fee,
                resolveFeeCurrency(order.symbol()),
                order.traceId(),
                Instant.now(clock)
        );
    }

    private void publishTradeEvent(OrderRecord order, PaperTradeRecord trade) {
        TradeExecuted payload = new TradeExecuted(
                trade.tradeId(),
                order.orderId(),
                order.clientOrderId(),
                order.accountId(),
                order.symbol(),
                order.venue(),
                trade.exchange(),
                order.externalOrderId(),
                trade.exchangeTradeId(),
                trade.price(),
                trade.qty(),
                trade.fee(),
                trade.feeCurrency(),
                trade.ts()
        );
        EventEnvelope<TradeExecuted> envelope = new EventEnvelope<>(
                "evt-" + UUID.randomUUID(),
                payload.getClass().getSimpleName(),
                1,
                Instant.now(clock),
                SOURCE,
                order.traceId(),
                order.clientOrderId(),
                payload
        );
        eventPublisherPort.append(TopicNames.TRADE_EVENT_V1, envelope);
    }

    private BigDecimal resolveMarketPrice() {
        // Gate B/交易所适配契约不接真实行情网络，使用固定价格提供器保证本地验证可重复。
        return new BigDecimal("100.00000000");
    }

    private BigDecimal resolveExecutionPrice(OrderRecord order, BigDecimal marketPrice) {
        if (OrderType.LIMIT.name().equals(order.type())
                && order.price() != null
                && order.price().compareTo(BigDecimal.ZERO) > 0) {
            return order.price();
        }
        return marketPrice;
    }

    private boolean isExecutable(OrderRecord order, BigDecimal marketPrice) {
        if (!OrderType.LIMIT.name().equals(order.type())) {
            return true;
        }
        if (order.price() == null || order.price().compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        OrderSide side = OrderSide.valueOf(order.side());
        if (side == OrderSide.BUY) {
            return marketPrice.compareTo(order.price()) <= 0;
        }
        return marketPrice.compareTo(order.price()) >= 0;
    }

    private String resolveFeeCurrency(String symbol) {
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

    private Map<String, Object> detail(Object... fields) {
        LinkedHashMap<String, Object> detail = new LinkedHashMap<>();
        for (int index = 0; index < fields.length; index += 2) {
            detail.put(String.valueOf(fields[index]), fields[index + 1]);
        }
        return detail;
    }
}
