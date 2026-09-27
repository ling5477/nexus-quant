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
import java.time.Duration;
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
import static com.guidinglight.nexusquant.account.infra.okx.readonly.AccountFactsSnapshot.Status;

/** 只读比较已解析的外部事实与已映射的 LIVE 本地账户事实。 */
final class AccountDivergenceComparator {
    private static final Set<String> TARGET_ASSETS = Set.of("BTC", "USDT");
    private static final int MAX_ORDERS = 1000;
    private static final int MAX_ASSETS = 100;
    private static final Duration MAX_CANONICAL_AGE = Duration.ofMinutes(1);
    private final JdbcTemplate jdbc;

    AccountDivergenceComparator(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    AccountDivergenceReport compare(UUID observationId, long exchangeAccountId, Long legacyAccountId,
            Instant externalObservedAt, Instant at, AccountFactsSnapshot.Fact<String> mode,
            AccountFactsSnapshot.Fact<List<OkxPrivatePositionFact>> positions,
            Map<String, AccountFactsSnapshot.Fact<OkxPrivateBalanceFact>> balances,
            AccountFactsSnapshot.Fact<List<OkxPrivateOrderSnapshot>> orders,
            AccountFactsSnapshot.Fact<String> publicRule, BigDecimal minimumSize) {
        List<AccountDivergenceReport.Item> items = new ArrayList<>();
        List<String> externalDigest = new ArrayList<>();
        List<String> canonicalDigest = new ArrayList<>();
        if (legacyAccountId != null && legacyAccountId > 0) {
            canonicalDigest.add("account-mapping|" + exchangeAccountId + "|" + legacyAccountId + "|LIVE|OKX");
        }
        if (mode.statusAt(at) != Status.OBSERVED || !Set.of("1", "2").contains(mode.value())) {
            unknown(items, "ACCOUNT_MODE", null, "ACCOUNT_MODE_NOT_YET_QUALIFIED", at);
        } else if ("2".equals(mode.value()) && positions.statusAt(at) != Status.OBSERVED) {
            unknown(items, "POSITION", null, "POSITION_OBSERVATION_INCOMPLETE", at);
        }
        if (legacyAccountId == null || legacyAccountId <= 0) {
            unknown(items, "ACCOUNT", null, "CANONICAL_ACCOUNT_MAPPING_MISSING", at);
        }
        if (orders.statusAt(at) != Status.OBSERVED || orders.value() == null) {
            if (orders.statusAt(at) == Status.STALE) {
                items.add(item(Classification.EXTERNAL_FACT_STALE, "ORDER", null, Status.STALE,
                        Status.UNKNOWN, orders.observedAt(), null, "EXTERNAL_OPEN_ORDERS_STALE", null, null));
            } else {
                unknown(items, "ORDER", null, "EXTERNAL_OPEN_ORDERS_INCOMPLETE", at);
            }
        }
        if (publicRule.statusAt(at) != Status.OBSERVED) {
            unknown(items, "RULE", null, "PUBLIC_RULE_INCOMPLETE", at);
        }
        for (String asset : TARGET_ASSETS) {
            var fact = balances.get(asset);
            if (fact == null || fact.statusAt(at) != Status.OBSERVED) {
                items.add(item(fact != null && fact.statusAt(at) == Status.STALE
                                ? Classification.EXTERNAL_FACT_STALE : Classification.BALANCE_FACT_MISSING,
                        "BALANCE", asset, fact == null ? Status.UNKNOWN : fact.statusAt(at), Status.UNKNOWN,
                        fact == null ? at : fact.observedAt(), null, "EXTERNAL_BALANCE_INCOMPLETE", null, null));
            }
        }
        if (balances.size() > MAX_ASSETS || balances.values().stream().anyMatch(f -> f.statusAt(at) != Status.OBSERVED)) {
            unknown(items, "BALANCE", null, "EXTERNAL_ASSET_SET_INCOMPLETE", at);
        }
        if (!items.isEmpty()) {
            return report(observationId, exchangeAccountId, legacyAccountId, externalObservedAt, at,
                    externalDigest, canonicalDigest, publicRule, minimumSize, items);
        }
        try {
            var dataSource = jdbc.getDataSource();
            if (dataSource == null) {
                // 只用于不提供 DataSource 的合成 Mockito fixture；真实 JDBC 必须进入一致性事务。
                return readCanonical(observationId, exchangeAccountId, legacyAccountId, externalObservedAt,
                        at, mode, positions, balances, orders, publicRule, minimumSize,
                        items, externalDigest, canonicalDigest);
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
                            at, mode, positions, balances, orders, publicRule, minimumSize,
                            items, externalDigest, canonicalDigest)));
        } catch (RuntimeException ex) {
            // SQL、事务与数据错误不进入 API；局部比较结果不得掩盖失败。
            items.clear();
            externalDigest.clear();
            canonicalDigest.clear();
            unknown(items, "CANONICAL", null, "CANONICAL_COMPARISON_UNAVAILABLE", at);
            return report(observationId, exchangeAccountId, legacyAccountId, externalObservedAt, at,
                    externalDigest, canonicalDigest, publicRule, minimumSize, items);
        }
    }

    private AccountDivergenceReport readCanonical(UUID observationId, long exchangeAccountId,
            Long legacyAccountId, Instant externalObservedAt, Instant at,
            AccountFactsSnapshot.Fact<String> mode,
            AccountFactsSnapshot.Fact<List<OkxPrivatePositionFact>> positions,
            Map<String, AccountFactsSnapshot.Fact<OkxPrivateBalanceFact>> balances,
            AccountFactsSnapshot.Fact<List<OkxPrivateOrderSnapshot>> orders,
            AccountFactsSnapshot.Fact<String> publicRule, BigDecimal minimumSize,
            List<AccountDivergenceReport.Item> items, List<String> externalDigest,
            List<String> canonicalDigest) {
        List<Map<String, Object>> localBalances = jdbc.queryForList("""
                SELECT DISTINCT ON (currency) snapshot_id, currency, balance, available, frozen, ts, created_at
                FROM account_snapshots WHERE account_id=?
                ORDER BY currency, snapshot_id DESC
                LIMIT 101
                """, legacyAccountId);
        List<Map<String, Object>> localOrders = jdbc.queryForList("""
                SELECT o.order_id, o.client_order_id, o.exchange_order_id, o.exchange_code, o.venue,
                       o.symbol, o.side, o.type, o.price, o.qty, o.status, o.created_at, o.updated_at,
                       COALESCE((SELECT SUM(t.qty) FROM trades t WHERE t.order_id=o.order_id
                           AND t.trade_env='LIVE'),0) AS filled
                FROM orders o WHERE o.account_id=? AND o.trade_env='LIVE'
                  AND o.status IN ('SENT','ACCEPTED','SUBMITTING','ACKED','PARTIALLY_FILLED',
                                   'CANCEL_REQUESTED','CANCEL_REJECTED')
                ORDER BY o.order_id LIMIT 1001
                """, legacyAccountId);
        List<Map<String, Object>> localPositions = jdbc.queryForList("""
                SELECT symbol, qty, available_qty, frozen_qty, updated_at
                FROM positions WHERE account_id=? ORDER BY symbol LIMIT 101
                """, legacyAccountId);
        if (localBalances.size() > MAX_ASSETS || localOrders.size() > MAX_ORDERS
                || localPositions.size() > MAX_ASSETS) {
            unknown(items, "CANONICAL", null, "CANONICAL_FACTS_OVER_LIMIT", at);
            return report(observationId, exchangeAccountId, legacyAccountId, externalObservedAt, at,
                    externalDigest, canonicalDigest, publicRule, minimumSize, items);
        }
        // account_snapshots 没有 trade_env；混入 SIM 写入时不能证明余额属于 LIVE。
        Boolean simFacts = jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM trades WHERE account_id=? AND trade_env='SIM')
                    OR EXISTS(SELECT 1 FROM ledger_entries WHERE account_id=?
                              AND ref_type IN ('SIM_FUNDING_CASH','SIM_FUNDING_CONTRA'))
                """, Boolean.class, legacyAccountId, legacyAccountId);
        if (!Boolean.FALSE.equals(simFacts)) {
            unknown(items, "BALANCE", null, "BALANCE_SEMANTIC_MISMATCH", at);
            return report(observationId, exchangeAccountId, legacyAccountId, externalObservedAt, at,
                    externalDigest, canonicalDigest, publicRule, minimumSize, items);
        }
        compareBalances(items, externalDigest, canonicalDigest, balances, localBalances, at, minimumSize,
                orders.value().isEmpty() && localOrders.isEmpty());
        compareOrders(items, externalDigest, canonicalDigest, orders, localOrders, at);
        BigDecimal canonicalBtc = localBalances.stream()
                .filter(row -> "BTC".equals(row.get("currency")))
                .map(row -> number(row.get("balance"))).filter(Objects::nonNull).findFirst().orElse(null);
        for (var row : localPositions) {
            canonicalDigest.add("spot-position|" + row.get("symbol") + "|" + decimalValue(row.get("qty"))
                    + "|" + decimalValue(row.get("available_qty")) + "|"
                    + decimalValue(row.get("frozen_qty")) + "|" + instant(row.get("updated_at")));
            if ("BTC-USDT".equals(row.get("symbol")) && canonicalBtc != null
                    && !same(canonicalBtc, row.get("qty"))) {
                Instant positionAt = instant(row.get("updated_at"));
                if (positionAt == null || positionAt.isAfter(at)
                        || positionAt.isBefore(at.minus(MAX_CANONICAL_AGE))) {
                    items.add(item(Classification.CANONICAL_FACT_STALE, "POSITION", "BTC-USDT",
                            Status.NOT_APPLICABLE, Status.STALE, null, positionAt,
                            "LOCAL_POSITION_OUTSIDE_COMPARISON_WINDOW_CAUSE_UNPROVEN", null, null));
                } else {
                    items.add(item(Classification.POSITION_MISMATCH, "POSITION", "BTC-USDT",
                            Status.NOT_APPLICABLE, Status.OBSERVED, null, positionAt,
                            "LOCAL_SPOT_POSITION_AND_BTC_SNAPSHOT_DIFFER", null,
                            digest(decimal(canonicalBtc) + "|" + decimalValue(row.get("qty")))));
                }
            }
        }
        if ("2".equals(mode.value())) {
            if (positions.value().stream().noneMatch(p -> p.positionQuantity().signum() != 0)) {
                items.add(item(Classification.MATCH, "POSITION", null, Status.OBSERVED,
                        Status.NOT_APPLICABLE, positions.observedAt(), null,
                        "NO_EXTERNAL_NON_SPOT_POSITION", null, null));
            }
            for (OkxPrivatePositionFact position : positions.value()) {
                externalDigest.add("position|" + position.instrumentId() + "|" + decimal(position.positionQuantity()));
                if (position.positionQuantity().signum() != 0) {
                    items.add(item(Classification.EXTERNAL_NON_SPOT_POSITION_PRESENT, "POSITION",
                            position.instrumentId(), Status.OBSERVED, Status.NOT_APPLICABLE,
                            positions.observedAt(), null, "NONZERO_NON_SPOT_POSITION",
                            digest(decimal(position.positionQuantity())), null));
                }
            }
        }

        return report(observationId, exchangeAccountId, legacyAccountId, externalObservedAt, at,
                externalDigest, canonicalDigest, publicRule, minimumSize, items);
    }

    private static void compareBalances(List<AccountDivergenceReport.Item> items, List<String> externalDigest,
            List<String> canonicalDigest, Map<String, AccountFactsSnapshot.Fact<OkxPrivateBalanceFact>> external,
            List<Map<String, Object>> local, Instant at, BigDecimal minimumSize, boolean noOpenOrders) {
        Map<String, Map<String, Object>> byAsset = new HashMap<>();
        for (var row : local) {
            String asset = (String) row.get("currency");
            if (asset == null || byAsset.put(asset, row) != null) {
                unknown(items, "BALANCE", asset, "CANONICAL_ASSET_DUPLICATE", at);
                return;
            }
            canonicalDigest.add("balance|" + asset + "|" + row.get("snapshot_id") + "|"
                    + decimalValue(row.get("balance")) + "|" + decimalValue(row.get("available")) + "|"
                    + decimalValue(row.get("frozen")) + "|" + instant(row.get("ts")));
        }
        for (var entry : external.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            String asset = entry.getKey();
            var fact = entry.getValue();
            var value = fact.value();
            String normalized = decimal(value.total()) + "|" + decimal(value.available()) + "|"
                    + decimal(value.frozen());
            externalDigest.add("balance|" + asset + "|" + normalized + "|" + value.providerUpdatedAt());
            var row = byAsset.get(asset);
            if (row == null) {
                if (TARGET_ASSETS.contains(asset)) {
                    items.add(item(Classification.BALANCE_FACT_MISSING, "BALANCE", asset, Status.OBSERVED,
                            Status.UNKNOWN, fact.observedAt(), null, "CANONICAL_BALANCE_MISSING",
                            digest(normalized), null));
                } else if (value.total().signum() > 0) {
                    items.add(item(Classification.UNEXPECTED_EXTERNAL_ASSET, "BALANCE", asset, Status.OBSERVED,
                            Status.UNKNOWN, fact.observedAt(), null, "POSITIVE_EXTERNAL_ASSET_OUTSIDE_CANONICAL_SCOPE",
                            digest(normalized), null));
                }
                continue;
            }
            BigDecimal total = number(row.get("balance"));
            BigDecimal available = number(row.get("available"));
            BigDecimal frozen = number(row.get("frozen"));
            Instant canonicalAt = instant(row.get("ts"));
            if (total == null || available == null || frozen == null || canonicalAt == null) {
                unknown(items, "BALANCE", asset, "CANONICAL_BALANCE_MALFORMED", at);
                continue;
            }
            if (canonicalAt.isAfter(at) || canonicalAt.isBefore(at.minus(MAX_CANONICAL_AGE))) {
                items.add(item(Classification.CANONICAL_FACT_STALE, "BALANCE", asset, Status.OBSERVED,
                        Status.STALE, fact.observedAt(), canonicalAt,
                        "CANONICAL_SNAPSHOT_OUTSIDE_COMPARISON_WINDOW_CAUSE_UNPROVEN", null, null));
            }
            if (value.total().compareTo(total) != 0) {
                String localValue = decimal(total) + "|" + decimal(available) + "|" + decimal(frozen);
                boolean dust = "BTC".equals(asset) && minimumSize != null && value.total().signum() > 0
                        && value.total().compareTo(minimumSize) < 0 && total.signum() == 0;
                items.add(item(dust ? Classification.EXTERNAL_DUST_BALANCE : Classification.BALANCE_MISMATCH,
                        "BALANCE", asset, Status.OBSERVED,
                        Status.OBSERVED, fact.observedAt(), canonicalAt,
                        dust ? "POSITIVE_BTC_BELOW_CURRENT_MINIMUM_SIZE" : "TOTAL_EXACT_DIFFERENCE",
                        digest(normalized), digest(localValue)));
            }
            // 本地可用/冻结由成交投影生成，只有双方均无挂单且两侧均明确无冻结时才有同义口径。
            if (!noOpenOrders || value.frozen().signum() != 0 || frozen.signum() != 0
                    || value.available().compareTo(value.total()) != 0 || available.compareTo(total) != 0) {
                items.add(item(Classification.BALANCE_SEMANTIC_MISMATCH, "BALANCE", asset,
                        Status.OBSERVED, Status.UNKNOWN, fact.observedAt(), canonicalAt,
                        "AVAILABLE_FROZEN_PROJECTION_NOT_PROVIDER_EQUIVALENT", null, null));
            }
        }
        for (var entry : byAsset.entrySet()) {
            if (!external.containsKey(entry.getKey())) {
                var row = entry.getValue();
                BigDecimal total = number(row.get("balance"));
                if (total != null && total.signum() > 0) {
                    Instant canonicalAt = instant(row.get("ts"));
                    boolean stale = canonicalAt == null || canonicalAt.isAfter(at)
                            || canonicalAt.isBefore(at.minus(MAX_CANONICAL_AGE));
                    items.add(item(stale ? Classification.CANONICAL_FACT_STALE
                                    : Classification.UNEXPECTED_LOCAL_ASSET,
                            "BALANCE", entry.getKey(), Status.UNKNOWN,
                            stale ? Status.STALE : Status.OBSERVED, null, canonicalAt,
                            stale ? "LOCAL_ASSET_SNAPSHOT_OUTSIDE_COMPARISON_WINDOW_CAUSE_UNPROVEN"
                                    : "POSITIVE_CANONICAL_ASSET_NOT_RETURNED_EXTERNALLY",
                            null, digest(decimal(total))));
                }
            }
        }
    }

    private static void compareOrders(List<AccountDivergenceReport.Item> items, List<String> externalDigest,
            List<String> canonicalDigest, AccountFactsSnapshot.Fact<List<OkxPrivateOrderSnapshot>> externalFact,
            List<Map<String, Object>> local, Instant at) {
        Map<String, OkxPrivateOrderSnapshot> external = new HashMap<>();
        for (var order : externalFact.value()) {
            String id = order.clientOrderId();
            if (id == null || id.isBlank() || external.putIfAbsent(id, order) != null) {
                unknown(items, "ORDER", order.instrumentId(), "EXTERNAL_ORDER_IDENTITY_DUPLICATE", at);
                return;
            }
            externalDigest.add("order|" + id + "|" + order.exchangeOrderId() + "|"
                    + order.instrumentId() + "|" + order.side() + "|"
                    + order.orderType() + "|" + decimal(order.originalQuantity()) + "|"
                    + decimal(order.filledQuantity()) + "|" + decimal(order.price()) + "|" + order.status());
        }
        Set<String> localIds = new HashSet<>();
        for (var row : local) {
            String id = (String) row.get("client_order_id");
            if (id == null || !localIds.add(id)) {
                unknown(items, "ORDER", (String) row.get("symbol"), "CANONICAL_ORDER_IDENTITY_DUPLICATE", at);
                return;
            }
            canonicalDigest.add("order|" + id + "|" + row.get("exchange_order_id") + "|"
                    + row.get("exchange_code") + "|" + row.get("venue") + "|"
                    + row.get("symbol") + "|" + row.get("side") + "|"
                    + row.get("type") + "|" + decimalValue(row.get("qty")) + "|"
                    + decimalValue(row.get("filled")) + "|" + decimalValue(row.get("price")) + "|"
                    + row.get("status"));
            var outside = external.remove(id);
            Instant canonicalAt = instant(row.get("updated_at"));
            if (outside == null) {
                items.add(item(Classification.LOCAL_ACTIVE_ORDER_ONLY, "ORDER", (String) row.get("symbol"),
                        Status.UNKNOWN, Status.OBSERVED, null, canonicalAt, "ACTIVE_LIVE_ORDER_ABSENT_EXTERNALLY",
                        null, digest(id)));
                continue;
            }
            Instant observed = outside.observedAt();
            if (!"OKX".equals(row.get("exchange_code")) || !"OKX".equals(row.get("venue"))
                    || !Objects.equals(outside.exchangeOrderId(), row.get("exchange_order_id"))
                    || !Objects.equals(outside.instrumentId(), row.get("symbol"))
                    || !equalsIgnoreCase(outside.side(), row.get("side"))
                    || !equalsIgnoreCase(outside.orderType(), row.get("type"))) {
                items.add(item(Classification.ORDER_IDENTITY_MISMATCH, "ORDER", outside.instrumentId(),
                        Status.OBSERVED, Status.OBSERVED, observed, canonicalAt,
                        "VENUE_INSTRUMENT_SIDE_TYPE_OR_EXCHANGE_ID_DIFFERS", digest(id), digest(id)));
            }
            if (!same(outside.originalQuantity(), row.get("qty"))
                    || !same(outside.filledQuantity(), row.get("filled"))) {
                items.add(item(Classification.ORDER_QUANTITY_MISMATCH, "ORDER", outside.instrumentId(),
                        Status.OBSERVED, Status.OBSERVED, observed, canonicalAt,
                        "ORIGINAL_OR_FILLED_QUANTITY_DIFFERS", digest(decimal(outside.originalQuantity()) + "|"
                                + decimal(outside.filledQuantity())), digest(decimalValue(row.get("qty")) + "|"
                                + decimalValue(row.get("filled")))));
            }
            if ("limit".equalsIgnoreCase(outside.orderType()) && !same(outside.price(), row.get("price"))) {
                items.add(item(Classification.ORDER_PRICE_MISMATCH, "ORDER", outside.instrumentId(),
                        Status.OBSERVED, Status.OBSERVED, observed, canonicalAt, "LIMIT_PRICE_DIFFERS",
                        digest(decimal(outside.price())), digest(decimalValue(row.get("price")))));
            }
            String localState = (String) row.get("status");
            String externalState = outside.status().toLowerCase(java.util.Locale.ROOT);
            boolean stateEquivalent = switch (localState == null ? "" : localState) {
                case "ACCEPTED", "ACKED" -> "live".equals(externalState);
                case "PARTIALLY_FILLED" -> "partially_filled".equals(externalState);
                default -> false;
            };
            if (!stateEquivalent) {
                items.add(item(Classification.ORDER_STATE_MISMATCH, "ORDER", outside.instrumentId(),
                        Status.OBSERVED, Status.OBSERVED, observed, canonicalAt,
                        "ACTIVE_ORDER_STATE_CLASS_DIFFERS_OR_UNPROVEN",
                        digest(outside.status()), digest(Objects.toString(localState, "NULL"))));
            }
        }
        for (var outside : external.values()) {
            items.add(item(Classification.EXTERNAL_OPEN_ORDER_ONLY, "ORDER", outside.instrumentId(),
                    Status.OBSERVED, Status.UNKNOWN, outside.observedAt(), null,
                    "EXTERNAL_OPEN_ORDER_ABSENT_LOCALLY", digest(outside.clientOrderId()), null));
        }
    }

    private static AccountDivergenceReport report(UUID id, long accountId, Long legacyId,
            Instant externalAt, Instant at, List<String> external, List<String> canonical,
            AccountFactsSnapshot.Fact<String> rule, BigDecimal minimumSize,
            List<AccountDivergenceReport.Item> items) {
        List<AccountDivergenceReport.Item> sorted = items.stream().sorted(Comparator
                .comparing((AccountDivergenceReport.Item item) -> item.dimension())
                .thenComparing(item -> Objects.toString(item.assetOrInstrument(), ""))
                .thenComparing(item -> item.classification().name())).toList();
        Classification aggregate;
        List<AccountDivergenceReport.Item> divergences = sorted.stream()
                .filter(item -> item.classification() != Classification.MATCH).toList();
        if (divergences.stream().anyMatch(item -> item.classification() == Classification.UNKNOWN
                || item.classification() == Classification.BALANCE_FACT_MISSING
                || item.classification() == Classification.BALANCE_SEMANTIC_MISMATCH
                || item.classification() == Classification.EXTERNAL_FACT_STALE
                || item.classification() == Classification.CANONICAL_FACT_STALE)) {
            aggregate = Classification.UNKNOWN;
        } else if (divergences.isEmpty()) {
            aggregate = Classification.MATCH;
        } else if (divergences.size() == 1) {
            aggregate = divergences.getFirst().classification();
        } else {
            aggregate = Classification.MULTIPLE_DIVERGENCES;
        }
        return new AccountDivergenceReport(id, accountId, legacyId, externalAt, at,
                external.isEmpty() ? null : digestJoined(external),
                canonical.isEmpty() ? null : digestJoined(canonical),
                rule.statusAt(at) == Status.OBSERVED ? rule.value() : null,
                rule.statusAt(at) == Status.OBSERVED ? minimumSize : null, aggregate, sorted);
    }

    private static AccountDivergenceReport.Item item(Classification classification, String dimension,
            String asset, Status external, Status canonical, Instant externalAt, Instant canonicalAt,
            String reason, String externalIdentity, String canonicalIdentity) {
        return new AccountDivergenceReport.Item(classification, dimension, asset, external, canonical,
                externalAt, canonicalAt, reason, externalIdentity, canonicalIdentity);
    }

    private static void unknown(List<AccountDivergenceReport.Item> items, String dimension, String asset,
            String reason, Instant at) {
        items.add(item(Classification.UNKNOWN, dimension, asset, Status.UNKNOWN, Status.UNKNOWN,
                at, null, reason, null, null));
    }

    private static BigDecimal number(Object value) {
        return value instanceof BigDecimal decimal ? decimal : null;
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
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
