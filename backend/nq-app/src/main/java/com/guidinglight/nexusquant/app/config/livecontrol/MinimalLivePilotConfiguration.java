package com.guidinglight.nexusquant.app.config.livecontrol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountRepository;
import com.guidinglight.nexusquant.account.application.CredentialPermissionProbeService;
import com.guidinglight.nexusquant.account.infra.okx.readonly.OkxPrivateCredentialExecutor;
import com.guidinglight.nexusquant.adapter.okx.service.OkxSpotEndpointGuard;
import com.guidinglight.nexusquant.adapter.okx.service.OkxSpotProviderAdapter;
import com.guidinglight.nexusquant.livecontrol.application.LiveSessionControlService;
import com.guidinglight.nexusquant.livecontrol.application.MinimalLivePilotCommand;
import com.guidinglight.nexusquant.livecontrol.application.MinimalLivePilotControlPlane;
import com.guidinglight.nexusquant.livecontrol.application.PilotExecutionLeaseControlPlane;
import com.guidinglight.nexusquant.livecontrol.application.PilotScopeControlPlane;
import com.guidinglight.nexusquant.livecontrol.domain.ExactPilotBinding;
import com.guidinglight.nexusquant.livecontrol.domain.PilotExecutionLease;
import com.guidinglight.nexusquant.livecontrol.domain.port.ExactPilotBindingRepository;
import com.guidinglight.nexusquant.livecontrol.domain.port.LiveControlRepository;
import com.guidinglight.nexusquant.livecontrol.domain.port.PilotExecutionLeaseRepository;
import com.guidinglight.nexusquant.livecontrol.domain.port.PilotScopeRepository;
import com.guidinglight.nexusquant.livecontrol.execution.application.port.ExecutionIntentRepository;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotExecutionProviderPort;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderRequests;
import com.guidinglight.nexusquant.livecontrol.execution.infra.MinimalPilotTradingVenueGateway;
import com.guidinglight.nexusquant.livecontrol.infra.MinimalLivePilotControlService;
import com.guidinglight.nexusquant.livecontrol.infra.PilotExecutionLeaseService;
import com.guidinglight.nexusquant.livecontrol.infra.okx.CredentialScopedOkxSpotProviderTransport;
import com.guidinglight.nexusquant.marketdata.domain.instrument.port.InstrumentCatalogReadPort;
import com.guidinglight.nexusquant.risk.service.KillSwitchService;
import com.guidinglight.nexusquant.trading.application.CancelOrderRequest;
import com.guidinglight.nexusquant.trading.application.OrderCommandService;
import com.guidinglight.nexusquant.trading.application.OrderLifecycleService;
import com.guidinglight.nexusquant.trading.application.PlaceOrderRequest;
import com.guidinglight.nexusquant.trading.domain.port.OrderRepository;
import com.guidinglight.nexusquant.scheduler.model.PaperTradeRecord;
import com.guidinglight.nexusquant.scheduler.service.TradeLedgerGateway;
import com.guidinglight.nexusquant.scheduler.service.port.TradeRepository;
import com.guidinglight.nexusquant.ledger.contracts.model.TradeLedgerRequest;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

/** 默认关闭的single-purpose minimal live pilot composition；普通runtime不装配provider。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "nq.runtime.minimal-live-pilot",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false
)
@ConditionalOnProperty(
        prefix = "nq.runtime.minimal-live-pilot",
        name = {"order-submission-enabled", "cancel-enabled"},
        havingValue = "true",
        matchIfMissing = false
)
@ConditionalOnProperty(
        prefix = "nq.runtime.minimal-live-pilot",
        name = {"transfer-enabled", "withdraw-enabled"},
        havingValue = "false",
        matchIfMissing = false
)
public class MinimalLivePilotConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(MinimalLivePilotConfiguration.class);

    @Bean
    public PilotExecutionLeaseControlPlane pilotExecutionLeaseControlPlane(
            PilotExecutionLeaseRepository leases,
            LiveSessionControlService sessions,
            KillSwitchService killSwitchService
    ) {
        return new PilotExecutionLeaseService(leases, sessions, killSwitchService, Clock.systemUTC());
    }

    @Bean
    public MinimalLivePilotControlPlane minimalLivePilotControlPlane(
            ExchangeAccountRepository accounts,
            InstrumentCatalogReadPort instruments,
            CredentialPermissionProbeService permissionProbeService,
            PilotScopeControlPlane scopes,
            PilotScopeRepository scopeRepository,
            com.guidinglight.nexusquant.livecontrol.application.ExactPilotBindingControlPlane bindings,
            PilotExecutionLeaseControlPlane leases
    ) {
        return new MinimalLivePilotControlService(
                accounts, instruments, permissionProbeService, scopes, scopeRepository,
                bindings, leases, Clock.systemUTC());
    }

    @Bean
    public SpotExecutionProviderPort minimalLivePilotProvider(
            LiveControlRepository sessions,
            OkxPrivateCredentialExecutor credentials
    ) {
        return new OkxSpotProviderAdapter(
                new CredentialScopedOkxSpotProviderTransport(sessions, credentials),
                new OkxSpotEndpointGuard(), new SpotProviderRequests.ResponseBounds(262_144, 100),
                Clock.systemUTC());
    }

    @Bean
    @Primary
    public MinimalPilotTradingVenueGateway minimalPilotTradingVenueGateway(
            ExecutionIntentRepository intents,
            ExactPilotBindingRepository bindings,
            PilotExecutionLeaseRepository leases,
            PilotExecutionLeaseControlPlane leaseControl,
            SpotExecutionProviderPort provider,
            JdbcTemplate jdbc
    ) {
        return new MinimalPilotTradingVenueGateway(
                intents, bindings, leases, leaseControl, provider, jdbc, Clock.systemUTC());
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public ApplicationRunner minimalPilotStartupRecovery(PilotExecutionLeaseControlPlane leases) {
        return arguments -> leases.recoverAtStartup();
    }

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    public ApplicationRunner minimalLivePilotRunner(
            MinimalLivePilotControlPlane control,
            PilotExecutionLeaseControlPlane leases,
            OrderCommandService orders,
            OrderLifecycleService lifecycle,
            OrderRepository orderRepository,
            MinimalPilotTradingVenueGateway gateway,
            TradeRepository trades,
            TradeLedgerGateway ledger,
            com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository audit,
            ExchangeAccountRepository accounts,
            ExactPilotBindingRepository bindingRepository,
            JdbcTemplate jdbc,
            ConfigurableApplicationContext context,
            ObjectMapper objectMapper,
            @Value("${nq.runtime.minimal-live-pilot.exchange-account-id}") long accountId,
            @Value("${nq.runtime.minimal-live-pilot.credential-reference-id}") long credentialReferenceId,
            @Value("${nq.runtime.minimal-live-pilot.instrument}") String instrument,
            @Value("${nq.runtime.minimal-live-pilot.side}") ExactPilotBinding.Side side,
            @Value("${nq.runtime.minimal-live-pilot.configured-max-notional}") BigDecimal maxNotional
    ) {
        return new MinimalPilotRunner(
                control, leases, orders, lifecycle, orderRepository, gateway, trades, ledger, audit,
                accounts, bindingRepository, jdbc, context, objectMapper,
                new MinimalLivePilotCommand(
                        accountId, credentialReferenceId, instrument, side, maxNotional));
    }

    private static final class MinimalPilotRunner implements ApplicationRunner {
        private static final long OPEN_ORDER_OBSERVATION_WINDOW_MILLIS = 2_000L;

        private final MinimalLivePilotControlPlane control;
        private final PilotExecutionLeaseControlPlane leases;
        private final OrderCommandService orders;
        private final OrderLifecycleService lifecycle;
        private final OrderRepository orderRepository;
        private final MinimalPilotTradingVenueGateway gateway;
        private final TradeRepository trades;
        private final TradeLedgerGateway ledger;
        private final com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository audit;
        private final ExchangeAccountRepository accounts;
        private final ExactPilotBindingRepository bindingRepository;
        private final JdbcTemplate jdbc;
        private final ConfigurableApplicationContext context;
        private final ObjectMapper objectMapper;
        private final MinimalLivePilotCommand command;

        private MinimalPilotRunner(
                MinimalLivePilotControlPlane control,
                PilotExecutionLeaseControlPlane leases,
                OrderCommandService orders,
                OrderLifecycleService lifecycle,
                OrderRepository orderRepository,
                MinimalPilotTradingVenueGateway gateway,
                TradeRepository trades,
                TradeLedgerGateway ledger,
                com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository audit,
                ExchangeAccountRepository accounts,
                ExactPilotBindingRepository bindingRepository,
                JdbcTemplate jdbc,
                ConfigurableApplicationContext context,
                ObjectMapper objectMapper,
                MinimalLivePilotCommand command
        ) {
            this.control = Objects.requireNonNull(control);
            this.leases = Objects.requireNonNull(leases);
            this.orders = Objects.requireNonNull(orders);
            this.lifecycle = Objects.requireNonNull(lifecycle);
            this.orderRepository = Objects.requireNonNull(orderRepository);
            this.gateway = Objects.requireNonNull(gateway);
            this.trades = Objects.requireNonNull(trades);
            this.ledger = Objects.requireNonNull(ledger);
            this.audit = Objects.requireNonNull(audit);
            this.accounts = Objects.requireNonNull(accounts);
            this.bindingRepository = Objects.requireNonNull(bindingRepository);
            this.jdbc = Objects.requireNonNull(jdbc);
            this.context = Objects.requireNonNull(context);
            this.objectMapper = Objects.requireNonNull(objectMapper);
            this.command = Objects.requireNonNull(command);
        }

        @Override
        public void run(ApplicationArguments arguments) throws Exception {
            try {
                var recovery = leases.findConsumedForRecovery();
                if (recovery.isPresent()) {
                    recoverConsumed(recovery.get());
                    return;
                }
                executeNewPilot();
            } finally {
                context.close();
            }
        }

        private void executeNewPilot() throws Exception {
            var permit = control.prepare(command);
            var account = accounts.findById(command.exchangeAccountId()).orElseThrow();
            if (account.legacyAccountId() == null) {
                throw new IllegalStateException("legacy account identity bridge is required");
            }
            var correlation = new ExactPilotBinding.Correlation(
                    permit.requestId(), permit.traceId(), permit.clientOrderId());
            boolean success = false;
            try {
                var result = orders.placeOrder(new PlaceOrderRequest(
                        permit.requestId(), account.legacyAccountId(),
                        permit.leaseId() + "|" + permit.placeIntentId(), "OKX", command.instrument(),
                        permit.clientOrderId(), permit.clientOrderId(), MinimalPilotTradingVenueGateway.SOURCE,
                        com.guidinglight.nexusquant.contracts.model.OrderSide.valueOf(command.side().name()),
                        com.guidinglight.nexusquant.contracts.model.OrderType.LIMIT,
                        permit.limitPrice(), permit.quantity(), "GTC", permit.traceId()));
                var stored = orderRepository.findByOrderId(result.orderId()).orElseThrow();
                completeReconciliation(stored, permit.requestId(), permit.traceId());
                success = true;
                LOGGER.info("MINIMAL_LIVE_PILOT_RESULT={}", objectMapper.writeValueAsString(result));
            } finally {
                var actor = new com.guidinglight.nexusquant.livecontrol.application.AuthenticatedLiveControlActor(
                        permit.ownerId());
                if (success) {
                    leases.close(actor, permit.leaseId(), PilotExecutionLease.Status.CLOSED,
                            "PILOT_COMPLETED", correlation);
                } else {
                    suspendOrFail(actor, permit.leaseId(), correlation, "PILOT_FAILED");
                }
            }
        }

        private void recoverConsumed(PilotExecutionLease lease) {
            ExactPilotBinding binding = bindingRepository.find(lease.liveSessionId(), lease.bindingId())
                    .filter(value -> value.bindingDigest().equals(lease.bindingDigest()))
                    .orElseThrow();
            if (binding.account().exchangeAccountId() != command.exchangeAccountId()
                    || binding.account().credentialReferenceId() != command.credentialReferenceId()
                    || !binding.order().exchangeInstrumentId().equals(command.instrument())
                    || binding.order().side() != command.side()
                    || lease.maxNotional().compareTo(command.configuredPilotMaxNotional()) != 0) {
                throw new IllegalStateException("PILOT_RECOVERY_OPERATOR_SCOPE_MISMATCH");
            }
            var actor = new com.guidinglight.nexusquant.livecontrol.application.AuthenticatedLiveControlActor(
                    binding.account().ownerId());
            leases.resumeConsumed(actor, lease, binding.correlation());
            String orderId = jdbc.queryForObject("""
                    SELECT intent.local_order_id
                    FROM pilot_execution_lease_intents link
                    JOIN execution_intents intent ON intent.intent_id=link.intent_id
                    WHERE link.lease_id=? AND link.action='PLACE'
                    """, String.class, lease.id());
            boolean success = false;
            try {
                completeReconciliation(
                        orderRepository.findByOrderId(orderId).orElseThrow(),
                        binding.correlation().requestId(), binding.correlation().traceId());
                success = true;
            } finally {
                if (success) {
                    leases.close(actor, lease.id(), PilotExecutionLease.Status.CLOSED,
                            "PILOT_RECOVERY_COMPLETED", binding.correlation());
                } else {
                    leases.suspendConsumedForRecovery(
                            actor, lease, "PILOT_RECOVERY_INCOMPLETE", binding.correlation());
                }
            }
        }

        private void suspendOrFail(
                com.guidinglight.nexusquant.livecontrol.application.AuthenticatedLiveControlActor actor,
                java.util.UUID leaseId,
                ExactPilotBinding.Correlation correlation,
                String reasonCode
        ) {
            var consumed = leases.findConsumedForRecovery()
                    .filter(candidate -> candidate.id().equals(leaseId));
            if (consumed.isPresent()) {
                leases.suspendConsumedForRecovery(actor, consumed.get(),
                        "PILOT_RECONCILIATION_REQUIRED", correlation);
                return;
            }
            leases.close(actor, leaseId, PilotExecutionLease.Status.FAILED, reasonCode, correlation);
        }

        private void completeReconciliation(
                com.guidinglight.nexusquant.trading.domain.OrderRecord stored,
                String requestId,
                String traceId
        ) {
            var reconciliation = gateway.reconcile(stored);
            if (isOpen(reconciliation)) {
                awaitOpenOrderObservationWindow();
                stored = orderRepository.findByOrderId(stored.orderId()).orElseThrow();
                reconciliation = gateway.reconcile(stored);
            }
            if (isOpen(reconciliation)) {
                orders.cancelOrder(new CancelOrderRequest(
                        requestId + "-cancel", stored.orderId(), stored.accountId(), "OKX",
                        stored.symbol(), stored.clientOrderId(), stored.externalOrderId(),
                        "PILOT_WINDOW_CLOSE", traceId));
                stored = orderRepository.findByOrderId(stored.orderId()).orElseThrow();
                reconciliation = gateway.reconcile(stored);
            }
            alignOrder(stored, reconciliation, traceId);
            persistFills(trades, ledger, audit, stored, reconciliation, traceId);
            requireTerminal(reconciliation);
        }

        private static boolean isOpen(MinimalPilotTradingVenueGateway.PilotReconciliation reconciliation) {
            return reconciliation.observation().state()
                    == com.guidinglight.nexusquant.livecontrol.execution.application.provider
                    .SpotProviderResults.OrderState.OPEN
                    || reconciliation.observation().state()
                    == com.guidinglight.nexusquant.livecontrol.execution.application.provider
                    .SpotProviderResults.OrderState.PARTIALLY_FILLED;
        }

        private static void awaitOpenOrderObservationWindow() {
            try {
                Thread.sleep(OPEN_ORDER_OBSERVATION_WINDOW_MILLIS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("pilot open-order observation window was interrupted", exception);
            }
        }

        private void alignOrder(
                com.guidinglight.nexusquant.trading.domain.OrderRecord order,
                MinimalPilotTradingVenueGateway.PilotReconciliation reconciliation,
                String traceId
        ) {
            OrderStatus target = switch (reconciliation.observation().state()) {
                case FILLED -> OrderStatus.FILLED;
                case CANCELED -> OrderStatus.CANCELLED;
                case REJECTED -> OrderStatus.REJECTED;
                case PARTIALLY_FILLED -> OrderStatus.PARTIALLY_FILLED;
                case OPEN -> OrderStatus.ACCEPTED;
                case NOT_FOUND, UNKNOWN -> throw new IllegalStateException("pilot final order state is unresolved");
            };
            if (order.status() != target) {
                lifecycle.applyExternalStatus(order.orderId(), target, "PILOT_RECONCILIATION", traceId);
            }
        }

        private static void requireTerminal(
                MinimalPilotTradingVenueGateway.PilotReconciliation reconciliation
        ) {
            if (!java.util.Set.of(
                    com.guidinglight.nexusquant.livecontrol.execution.application.provider
                            .SpotProviderResults.OrderState.FILLED,
                    com.guidinglight.nexusquant.livecontrol.execution.application.provider
                            .SpotProviderResults.OrderState.CANCELED,
                    com.guidinglight.nexusquant.livecontrol.execution.application.provider
                            .SpotProviderResults.OrderState.REJECTED
            ).contains(reconciliation.observation().state())) {
                throw new IllegalStateException("REAL_ORDER_RECONCILIATION_DIVERGENCE");
            }
        }
    }

    static void persistFills(
            TradeRepository trades,
            TradeLedgerGateway ledger,
            com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository audit,
            com.guidinglight.nexusquant.trading.domain.OrderRecord order,
            MinimalPilotTradingVenueGateway.PilotReconciliation reconciliation,
            String traceId
    ) {
        for (var fill : reconciliation.fills().fills()) {
            PaperTradeRecord trade = trades.findByExchangeAndExchangeTradeId("OKX", fill.exchangeTradeId())
                    .map(existing -> requireMatchingTrade(existing, order, reconciliation, fill))
                    .orElseGet(() -> {
                        PaperTradeRecord created = new PaperTradeRecord(
                                stableTradeId(fill.exchangeTradeId()), order.orderId(), order.accountId(),
                                order.symbol(), "OKX", reconciliation.observation().exchangeOrderId(),
                                fill.exchangeTradeId(), fill.price(), fill.quantity(), fill.fee().abs(),
                                fill.feeCurrency(), traceId, fill.filledAt());
                        trades.insert(created);
                        return created;
                    });
            var posted = ledger.postTrade(new TradeLedgerRequest(
                    trade.tradeId(), trade.orderId(), trade.accountId(), trade.symbol(),
                    com.guidinglight.nexusquant.contracts.model.OrderSide.valueOf(order.side()),
                    trade.price(), trade.qty(), trade.fee(), trade.feeCurrency(), traceId, trade.ts()));
            if (!posted.posted() && !posted.idempotentHit()) {
                throw new IllegalStateException("REAL_ORDER_RECONCILIATION_DIVERGENCE");
            }
            audit.append("RECONCILE", "GATEY_PILOT_FILL_LEDGER_RECONCILED", order.orderId(), traceId,
                    java.util.Map.of("trade_id", trade.tradeId(), "exchange_trade_id", fill.exchangeTradeId()));
        }
    }

    private static PaperTradeRecord requireMatchingTrade(
            PaperTradeRecord trade,
            com.guidinglight.nexusquant.trading.domain.OrderRecord order,
            MinimalPilotTradingVenueGateway.PilotReconciliation reconciliation,
            com.guidinglight.nexusquant.livecontrol.execution.application.provider
                    .SpotProviderResults.FillReference fill
    ) {
        if (!trade.orderId().equals(order.orderId())
                || !trade.accountId().equals(order.accountId())
                || !trade.symbol().equals(order.symbol())
                || !trade.exchange().equals("OKX")
                || !Objects.equals(trade.externalOrderId(), reconciliation.observation().exchangeOrderId())
                || !trade.exchangeTradeId().equals(fill.exchangeTradeId())
                || trade.price().compareTo(fill.price()) != 0
                || trade.qty().compareTo(fill.quantity()) != 0
                || trade.fee().compareTo(fill.fee().abs()) != 0
                || !Objects.equals(trade.feeCurrency(), fill.feeCurrency())) {
            throw new IllegalStateException("REAL_ORDER_RECONCILIATION_DIVERGENCE");
        }
        return trade;
    }

    private static String stableTradeId(String exchangeTradeId) {
        return "trd-" + java.util.UUID.nameUUIDFromBytes(
                ("OKX\n" + exchangeTradeId).getBytes(StandardCharsets.UTF_8));
    }
}
