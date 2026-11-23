package com.guidinglight.nexusquant.account.infra.okx.readonly;

import com.guidinglight.nexusquant.adapter.okx.privateread.model.OkxPrivateBalanceFact;
import com.guidinglight.nexusquant.adapter.okx.privateread.model.OkxPrivateOrderSnapshot;
import com.guidinglight.nexusquant.adapter.okx.privateread.model.OkxPrivatePositionFact;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.guidinglight.nexusquant.account.infra.okx.readonly.AccountDivergenceReport.Classification;
import static com.guidinglight.nexusquant.account.infra.okx.readonly.AccountDivergenceReport.Role;
import static com.guidinglight.nexusquant.account.infra.okx.readonly.AccountFactsSnapshot.Status;

/** 只读比较 NQ 管理的 LIVE 订单；全账户事实仅形成独立上下文。 */
final class AccountDivergenceComparator {
    private static final int MAX_ORDERS = 1000;
    private final JdbcTemplate jdbc;

    AccountDivergenceComparator(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    AccountDivergenceReport compare(UUID observationId, long exchangeAccountId, Long legacyAccountId,
            Instant externalObservedAt, Instant at,
            AccountFactsSnapshot.Fact<List<OkxPrivatePositionFact>> positions,
            Map<String, AccountFactsSnapshot.Fact<OkxPrivateBalanceFact>> balances,
            AccountFactsSnapshot.Fact<List<OkxPrivateOrderSnapshot>> orders,
            AccountFactsSnapshot.Fact<String> publicRule, BigDecimal minimumSize) {
        List<AccountDivergenceReport.Item> items = new ArrayList<>();
        List<String> externalDigest = new ArrayList<>();
        List<String> canonicalDigest = new ArrayList<>();
        addExternalContext(items, externalDigest, balances, positions, minimumSize, externalObservedAt, at);
        if (legacyAccountId == null || legacyAccountId <= 0) {
            unknown(items, "ACCOUNT", null, "CANONICAL_ACCOUNT_MAPPING_MISSING", at);
        } else {
            canonicalDigest.add("account-mapping|" + exchangeAccountId + "|" + legacyAccountId + "|LIVE|OKX");
        }
        if (orders.statusAt(at) != Status.OBSERVED || orders.value() == null) {
            unknown(items, "ORDER", null, orders.statusAt(at) == Status.STALE
                    ? "EXTERNAL_OPEN_ORDERS_STALE" : "EXTERNAL_OPEN_ORDERS_INCOMPLETE", at);
        } else if (orders.value().size() > MAX_ORDERS) {
            unknown(items, "ORDER", null, "EXTERNAL_OPEN_ORDERS_OVER_LIMIT", at);
        }
        if (items.stream().anyMatch(item -> item.role() == Role.MANAGED_RECONCILIATION)) {
            return report(observationId, exchangeAccountId, legacyAccountId, externalObservedAt,
                    at, externalDigest, canonicalDigest, publicRule, minimumSize, items);
        }
        try {
            var dataSource = jdbc.getDataSource();
            if (dataSource == null) {
                // 合成 Mockito fixture 不提供 DataSource；真实 JDBC 必须使用一致性只读事务。
                return readCanonical(observationId, exchangeAccountId, legacyAccountId, externalObservedAt,
                        at, orders, publicRule, minimumSize, items, externalDigest, canonicalDigest);
            }
            var manager = new DataSourceTransactionManager(dataSource);
            manager.setEnforceReadOnly(true);
            var transaction = new TransactionTemplate(manager);
            transaction.setReadOnly(true);
            transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
            transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            transaction.setTimeout(20);
            return Objects.requireNonNull(transaction.execute(status ->
                    readCanonical(observationId, exchangeAccountId, legacyAccountId, externalObservedAt,
                            at, orders, publicRule, minimumSize, items, externalDigest, canonicalDigest)));
        } catch (RuntimeException ex) {
            // 局部 JDBC 结果不能掩盖比较失败；外部上下文仍保留其独立观察身份。
            items.removeIf(item -> item.role() == Role.MANAGED_RECONCILIATION);
            canonicalDigest.clear();
            unknown(items, "CANONICAL", null, "CANONICAL_COMPARISON_UNAVAILABLE", at);
            return report(observationId, exchangeAccountId, legacyAccountId, externalObservedAt,
                    at, externalDigest, canonicalDigest, publicRule, minimumSize, items);
        }
    }

    private AccountDivergenceReport readCanonical(UUID observationId, long exchangeAccountId,
            Long legacyAccountId, Instant externalObservedAt, Instant at,
            AccountFactsSnapshot.Fact<List<OkxPrivateOrderSnapshot>> orders,
            AccountFactsSnapshot.Fact<String> publicRule, BigDecimal minimumSize,
            List<AccountDivergenceReport.Item> items, List<String> externalDigest,
            List<String> canonicalDigest) {
        List<Map<String, Object>> localOrders = jdbc.queryForList("""
                SELECT o.order_id, o.client_order_id, o.exchange_order_id, o.exchange_code, o.venue,
                       o.symbol, o.side, o.type, o.price, o.qty, o.status, o.updated_at,
                       COALESCE((SELECT SUM(t.qty) FROM trades t WHERE t.order_id=o.order_id
                           AND t.trade_env='LIVE'),0) AS filled
                FROM orders o WHERE o.account_id=? AND o.trade_env='LIVE'
                  AND UPPER(o.exchange_code)='OKX' AND UPPER(o.venue)='OKX'
                  AND o.status IN ('SENT','ACCEPTED','SUBMITTING','ACKED','PARTIALLY_FILLED',
                                   'CANCEL_REQUESTED','CANCEL_REJECTED')
                ORDER BY o.order_id LIMIT 1001
                """, legacyAccountId);
        if (localOrders.size() > MAX_ORDERS) {
            unknown(items, "ORDER", null, "CANONICAL_ORDERS_OVER_LIMIT", at);
        } else {
            compareOrders(items, externalDigest, canonicalDigest, orders, localOrders, at);
        }
        return report(observationId, exchangeAccountId, legacyAccountId, externalObservedAt,
                at, externalDigest, canonicalDigest, publicRule, minimumSize, items);
    }

    private static void addExternalContext(List<AccountDivergenceReport.Item> items,
            List<String> digest, Map<String, AccountFactsSnapshot.Fact<OkxPrivateBalanceFact>> balances,
            AccountFactsSnapshot.Fact<List<OkxPrivatePositionFact>> positions, BigDecimal minimumSize,
            Instant externalAt, Instant at) {
        boolean completeBalances = !balances.isEmpty();
        for (var entry : balances.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            var fact = entry.getValue();
            if (fact.statusAt(at) != Status.OBSERVED || fact.value() == null) {
                completeBalances = false;
                continue;
            }
            var value = fact.value();
            digest.add("balance|" + entry.getKey() + "|" + decimal(value.total()) + "|"
                    + decimal(value.available()) + "|" + decimal(value.frozen()) + "|"
                    + value.providerUpdatedAt());
            if (value.total().signum() > 0 && !Set.of("BTC", "USDT").contains(entry.getKey())) {
                items.add(item(Role.EXTERNAL_CONTEXT, Classification.EXTERNAL_UNMANAGED_ASSET,
                        "BALANCE", entry.getKey(), Status.OBSERVED, Status.NOT_APPLICABLE,
                        fact.observedAt(), null, "OUTSIDE_NQ_MANAGED_SCOPE",
                        digest(entry.getKey()), null));
            }
            if ("BTC".equals(entry.getKey()) && value.total().signum() > 0
                    && minimumSize != null && minimumSize.signum() > 0
                    && value.total().compareTo(minimumSize) < 0) {
                items.add(item(Role.EXTERNAL_CONTEXT, Classification.EXTERNAL_DUST,
                        "BALANCE", "BTC", Status.OBSERVED, Status.NOT_APPLICABLE,
                        fact.observedAt(), null, "POSITIVE_BELOW_CURRENT_MINIMUM_SIZE",
                        digest(decimal(value.total())), null));
            }
        }
        items.add(item(Role.EXTERNAL_CONTEXT, Classification.VENUE_BALANCE_NOT_SEMANTICALLY_COMPARABLE,
                "BALANCE", null, completeBalances ? Status.OBSERVED : Status.UNKNOWN,
                Status.NOT_APPLICABLE, externalAt, null,
                "NQ_MANAGED_PROJECTION_NOT_WHOLE_VENUE_EQUIVALENT", null, null));
        if (positions.statusAt(at) == Status.OBSERVED && positions.value() != null) {
            for (var position : positions.value()) {
                digest.add("position|" + position.instrumentType() + "|" + position.instrumentId()
                        + "|" + decimal(position.positionQuantity()));
                if (position.positionQuantity().signum() != 0) {
                    items.add(item(Role.EXTERNAL_CONTEXT, Classification.EXTERNAL_NON_SPOT_EXPOSURE,
                            "POSITION", position.instrumentId(), Status.OBSERVED, Status.NOT_APPLICABLE,
                            positions.observedAt(), null, "NONZERO_NON_SPOT_POSITION",
                            digest(decimal(position.positionQuantity())), null));
                }
            }
        }
    }

    private static void compareOrders(List<AccountDivergenceReport.Item> items,
            List<String> externalDigest, List<String> canonicalDigest,
            AccountFactsSnapshot.Fact<List<OkxPrivateOrderSnapshot>> externalFact,
            List<Map<String, Object>> local, Instant at) {
        Map<String, OkxPrivateOrderSnapshot> byClient = new HashMap<>();
        Map<String, OkxPrivateOrderSnapshot> byExchange = new HashMap<>();
        for (var order : externalFact.value()) {
            String clientId = order.clientOrderId();
            String exchangeId = order.exchangeOrderId();
            if (exchangeId == null || exchangeId.isBlank()
                    || byExchange.putIfAbsent(exchangeId, order) != null
                    || (clientId != null && !clientId.isBlank()
                        && byClient.putIfAbsent(clientId, order) != null)) {
                unknown(items, "ORDER", order.instrumentId(), "EXTERNAL_ORDER_IDENTITY_DUPLICATE", at);
                return;
            }
            externalDigest.add("order|" + clientId + "|" + exchangeId + "|"
                    + order.instrumentId() + "|" + order.side() + "|" + order.orderType()
                    + "|" + decimal(order.originalQuantity()) + "|"
                    + decimal(order.filledQuantity()) + "|" + decimal(order.price()) + "|" + order.status());
        }
        Set<String> localClientIds = new HashSet<>();
        Set<String> localExchangeIds = new HashSet<>();
        Set<OkxPrivateOrderSnapshot> claimed = new HashSet<>();
        for (var row : local) {
            String clientId = (String) row.get("client_order_id");
            String exchangeId = (String) row.get("exchange_order_id");
            String symbol = (String) row.get("symbol");
            Instant canonicalAt = instant(row.get("updated_at"));
            if (clientId == null || clientId.isBlank() || exchangeId == null || exchangeId.isBlank()) {
                unknown(items, "ORDER", symbol, "CANONICAL_ORDER_IDENTITY_INCOMPLETE", at);
                continue;
            }
            if (!localClientIds.add(clientId) || !localExchangeIds.add(exchangeId)) {
                unknown(items, "ORDER", symbol, "CANONICAL_ORDER_IDENTITY_DUPLICATE", at);
                continue;
            }
            canonicalDigest.add("order|" + clientId + "|" + exchangeId + "|" + symbol
                    + "|" + row.get("side") + "|" + row.get("type") + "|"
                    + decimalValue(row.get("qty")) + "|" + decimalValue(row.get("filled"))
                    + "|" + decimalValue(row.get("price")) + "|" + row.get("status"));
            if (canonicalAt == null || canonicalAt.isAfter(externalFact.observedAt())) {
                unknown(items, "ORDER", symbol, "CANONICAL_ORDER_AFTER_EXTERNAL_OBSERVATION", at);
                continue;
            }
            var byClientOrder = byClient.get(clientId);
            var byExchangeOrder = byExchange.get(exchangeId);
            if (byClientOrder != null && byClientOrder != byExchangeOrder
                    || byExchangeOrder != null && byExchangeOrder.clientOrderId() != null
                        && !clientId.equals(byExchangeOrder.clientOrderId())) {
                unknown(items, "ORDER", symbol, "ORDER_OWNERSHIP_IDENTITY_CONFLICT", at);
                continue;
            }
            OkxPrivateOrderSnapshot outside = byClientOrder != null ? byClientOrder : byExchangeOrder;
            String localState = (String) row.get("status");
            if (outside != null) claimed.add(outside);
            if (Set.of("SUBMITTING", "SENT", "CANCEL_REQUESTED", "CANCEL_REJECTED")
                    .contains(Objects.toString(localState, ""))) {
                unknown(items, "ORDER", symbol, "TRANSITIONAL_ORDER_STATE_UNPROVEN", at);
                continue;
            }
            if (outside == null) {
                items.add(item(Role.MANAGED_RECONCILIATION, Classification.LOCAL_ACTIVE_ORDER_ONLY,
                        "ORDER", symbol, Status.OBSERVED, Status.OBSERVED,
                        externalFact.observedAt(), canonicalAt, "LOCAL_MANAGED_ORDER_MISSING_EXTERNALLY",
                        null, digest(clientId + "|" + exchangeId)));
                continue;
            }
            if (outside.clientOrderId() != null && !clientId.equals(outside.clientOrderId())) {
                unknown(items, "ORDER", symbol, "ORDER_OWNERSHIP_IDENTITY_CONFLICT", at);
                continue;
            }
            int before = items.size();
            boolean sameInstrument = Objects.equals(outside.instrumentId(), symbol);
            boolean sameType = equalsIgnoreCase(outside.orderType(), row.get("type"));
            if (!sameInstrument || !sameType || !equalsIgnoreCase(outside.side(), row.get("side"))) {
                items.add(item(Role.MANAGED_RECONCILIATION, Classification.ORDER_IDENTITY_MISMATCH,
                        "ORDER", symbol, Status.OBSERVED, Status.OBSERVED,
                        outside.observedAt(), canonicalAt, "MANAGED_ORDER_INSTRUMENT_SIDE_OR_TYPE_DIFFERS",
                        digest(clientId), digest(clientId)));
            }
            if (sameInstrument && sameType) {
                if (!"limit".equalsIgnoreCase(outside.orderType())) {
                    // 市价等类型的 sz 单位未被当前订单合同证明，不能用相同数字推断一致或差异。
                    unknown(items, "ORDER", symbol, "MANAGED_ORDER_QUANTITY_UNIT_UNPROVEN", at);
                } else {
                    if (!same(outside.originalQuantity(), row.get("qty"))
                            || !same(outside.filledQuantity(), row.get("filled"))) {
                        items.add(item(Role.MANAGED_RECONCILIATION, Classification.ORDER_QUANTITY_MISMATCH,
                                "ORDER", symbol, Status.OBSERVED, Status.OBSERVED,
                                outside.observedAt(), canonicalAt, "ORIGINAL_OR_FILLED_QUANTITY_DIFFERS",
                                digest(decimal(outside.originalQuantity()) + "|" + decimal(outside.filledQuantity())),
                                digest(decimalValue(row.get("qty")) + "|" + decimalValue(row.get("filled")))));
                    }
                    if (!same(outside.price(), row.get("price"))) {
                        items.add(item(Role.MANAGED_RECONCILIATION, Classification.ORDER_PRICE_MISMATCH,
                                "ORDER", symbol, Status.OBSERVED, Status.OBSERVED,
                                outside.observedAt(), canonicalAt, "LIMIT_PRICE_DIFFERS",
                                digest(decimal(outside.price())), digest(decimalValue(row.get("price")))));
                    }
                }
            }
            String externalState = outside.status().toLowerCase(java.util.Locale.ROOT);
            if (!Set.of("live", "partially_filled").contains(externalState)) {
                unknown(items, "ORDER", symbol, "EXTERNAL_ORDER_STATE_UNPROVEN", at);
            } else if (!(Set.of("ACCEPTED", "ACKED").contains(localState) && "live".equals(externalState))
                    && !("PARTIALLY_FILLED".equals(localState) && "partially_filled".equals(externalState))) {
                items.add(item(Role.MANAGED_RECONCILIATION, Classification.ORDER_STATE_MISMATCH,
                        "ORDER", symbol, Status.OBSERVED, Status.OBSERVED,
                        outside.observedAt(), canonicalAt, "MANAGED_ACTIVE_ORDER_STATE_CLASS_DIFFERS",
                        digest(externalState), digest(localState)));
            }
            if (items.size() == before) {
                items.add(item(Role.MANAGED_RECONCILIATION, Classification.MATCH,
                        "ORDER", symbol, Status.OBSERVED, Status.OBSERVED,
                        outside.observedAt(), canonicalAt, "MANAGED_ORDER_MATCH",
                        digest(clientId), digest(clientId)));
            }
        }
        for (var outside : externalFact.value()) {
            if (!claimed.contains(outside)) {
                items.add(item(Role.EXTERNAL_CONTEXT, Classification.EXTERNAL_ORDER_OWNERSHIP_UNKNOWN,
                        "ORDER", outside.instrumentId(), Status.OBSERVED, Status.NOT_APPLICABLE,
                        outside.observedAt(), null, "EXTERNAL_ORDER_OWNERSHIP_UNPROVEN",
                        digest(outside.exchangeOrderId()), null));
            }
        }
    }

    private static AccountDivergenceReport report(UUID id, long accountId, Long legacyId,
            Instant externalAt, Instant at, List<String> external, List<String> canonical,
            AccountFactsSnapshot.Fact<String> rule, BigDecimal minimumSize,
            List<AccountDivergenceReport.Item> items) {
        List<AccountDivergenceReport.Item> sorted = items.stream().sorted(Comparator
                .comparing((AccountDivergenceReport.Item item) -> item.role().name())
                .thenComparing(AccountDivergenceReport.Item::dimension)
                .thenComparing(item -> Objects.toString(item.assetOrInstrument(), ""))
                .thenComparing(item -> item.classification().name())).toList();
        List<AccountDivergenceReport.Item> managed = sorted.stream()
                .filter(item -> item.role() == Role.MANAGED_RECONCILIATION).toList();
        Classification aggregate = managed.stream().anyMatch(item -> item.classification() == Classification.UNKNOWN)
                ? Classification.UNKNOWN
                : managed.stream().anyMatch(item -> item.classification() != Classification.MATCH)
                    ? Classification.DIVERGED
                    : managed.isEmpty() ? Classification.NOT_APPLICABLE : Classification.MATCH;
        return new AccountDivergenceReport(id, accountId, legacyId, externalAt, at,
                external.isEmpty() ? null : digestJoined(external),
                canonical.isEmpty() ? null : digestJoined(canonical),
                rule.statusAt(at) == Status.OBSERVED ? rule.value() : null,
                rule.statusAt(at) == Status.OBSERVED ? minimumSize : null, aggregate, sorted);
    }

    private static AccountDivergenceReport.Item item(Role role, Classification classification,
            String dimension, String asset, Status external, Status canonical,
            Instant externalAt, Instant canonicalAt, String reason,
            String externalIdentity, String canonicalIdentity) {
        return new AccountDivergenceReport.Item(role, classification, dimension, asset,
                external, canonical, externalAt, canonicalAt, reason, externalIdentity, canonicalIdentity);
    }

    private static void unknown(List<AccountDivergenceReport.Item> items, String dimension,
            String asset, String reason, Instant at) {
        items.add(item(Role.MANAGED_RECONCILIATION, Classification.UNKNOWN,
                dimension, asset, Status.UNKNOWN, Status.UNKNOWN,
                at, null, reason, null, null));
    }

    private static boolean same(BigDecimal left, Object right) {
        return left != null && right instanceof BigDecimal decimal && left.compareTo(decimal) == 0;
    }

    private static boolean equalsIgnoreCase(String left, Object right) {
        return left != null && right instanceof String text && left.equalsIgnoreCase(text);
    }

    private static Instant instant(Object value) {
        return value instanceof Timestamp timestamp ? timestamp.toInstant()
                : value instanceof Instant at ? at : null;
    }

    private static String decimalValue(Object value) {
        return value instanceof BigDecimal decimal ? decimal(decimal) : "NULL";
    }

    private static String decimal(BigDecimal value) {
        return value == null ? "NULL" : value.stripTrailingZeros().toPlainString();
    }

    private static String digestJoined(List<String> values) {
        return digest(String.join("\n", values.stream().sorted().toList()));
    }

    private static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
