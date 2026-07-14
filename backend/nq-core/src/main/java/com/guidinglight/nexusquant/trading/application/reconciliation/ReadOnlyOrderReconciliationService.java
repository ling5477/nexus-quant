package com.guidinglight.nexusquant.trading.application.reconciliation;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * GateW-3 internal-only 只读编排与纯比较器。
 *
 * <p>该类不是 Spring bean，无 controller/scheduler/runner，不持有 write repository，也不执行 repair。
 * 相同 Clock、request 与端口快照会产生相同结果。</p>
 */
public final class ReadOnlyOrderReconciliationService {
    public static final Duration STALE_THRESHOLD = Duration.ofMinutes(5);

    private final LocalOrderSnapshotReadPort localReadPort;
    private final RemoteOrderSnapshotReadPort remoteReadPort;
    private final Clock clock;

    public ReadOnlyOrderReconciliationService(
            LocalOrderSnapshotReadPort localReadPort,
            RemoteOrderSnapshotReadPort remoteReadPort,
            Clock clock
    ) {
        this.localReadPort = Objects.requireNonNull(localReadPort, "localReadPort must not be null");
        this.remoteReadPort = Objects.requireNonNull(remoteReadPort, "remoteReadPort must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /** 执行一次 bounded diagnostic；任何 finding 都不会触发写侧动作。 */
    public ReconciliationResult reconcile(ReconciliationRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Instant now = clock.instant();
        if (request.windowEnd().isAfter(now)) {
            throw new IllegalArgumentException("windowEnd must not be in the future");
        }
        List<LocalOrderSnapshot> local = List.copyOf(localReadPort.read(request));
        RemoteSnapshotBatch remote = remoteReadPort.read(request);
        List<ReconciliationFinding> matches = new ArrayList<>();
        List<ReconciliationFinding> differences = new ArrayList<>();
        List<ReconciliationFinding> blockers = new ArrayList<>();
        List<ReconciliationFinding> warnings = new ArrayList<>();
        List<ReconciliationFinding> unknowns = new ArrayList<>();
        List<ReconciliationFinding> notEvaluated = new ArrayList<>();

        blockers.add(globalFinding(ReconciliationTaxonomy.EXECUTION_NOT_AUTHORIZED, null, null,
                "diagnostic result never authorizes execution"));
        if (!remote.readOnlyPermissionConfirmed()) {
            unknowns.add(globalFinding(ReconciliationTaxonomy.REMOTE_PERMISSION_UNKNOWN, null, null,
                    "remote Read permission was not confirmed"));
            notEvaluated.add(globalFinding(ReconciliationTaxonomy.REMOTE_NOT_EVALUATED, null, null,
                    "comparison blocked by permission uncertainty"));
            return result(matches, differences, blockers, warnings, unknowns, notEvaluated, now);
        }
        int maxRecords = request.symbols().size() * request.recordLimit() * 3;
        if (!remote.complete() || remote.pageCount() > request.pageLimit() || remote.recordCount() > maxRecords) {
            blockers.add(globalFinding(ReconciliationTaxonomy.PARTIAL_REMOTE_SNAPSHOT, null, null,
                    "remote snapshot exceeded or could not prove bounded completeness"));
        }
        if (isFutureOrStale(remote.observedAt(), now)) {
            blockers.add(globalFinding(ReconciliationTaxonomy.STALE_REMOTE_SNAPSHOT, null, null,
                    "remote observedAt is future or older than threshold"));
        }

        DuplicateState duplicates = findDuplicates(local, remote.orders(), blockers);
        for (LocalOrderSnapshot item : local) {
            if (!request.symbols().contains(item.symbol().toUpperCase(Locale.ROOT))) {
                blockers.add(finding(ReconciliationTaxonomy.SNAPSHOT_SCOPE_MISMATCH, item, null,
                        "local snapshot returned a symbol outside the request allowlist"));
            }
        }
        for (RemoteOrderSnapshot item : remote.orders()) {
            if (!request.symbols().contains(item.symbol().toUpperCase(Locale.ROOT))) {
                blockers.add(finding(ReconciliationTaxonomy.SNAPSHOT_SCOPE_MISMATCH, null, item,
                        "remote snapshot returned a symbol outside the request allowlist"));
            }
        }
        Map<String, RemoteOrderSnapshot> byExchange = indexRemote(remote.orders(), true, duplicates.remoteExchangeIds());
        Map<String, RemoteOrderSnapshot> byClient = indexRemote(remote.orders(), false, duplicates.remoteClientIds());
        Set<RemoteOrderSnapshot> matchedRemote = new HashSet<>();

        for (LocalOrderSnapshot localOrder : local) {
            if (isFutureOrStale(localOrder.updatedAt(), now)) {
                warnings.add(finding(ReconciliationTaxonomy.STALE_LOCAL_SNAPSHOT, localOrder, null,
                        "local updatedAt is future or older than threshold"));
            }
            if (localOrder.exchangeOrderId() == null && localOrder.clientOrderId() == null) {
                unknowns.add(finding(ReconciliationTaxonomy.UNMATCHED_IDENTITY, localOrder, null,
                        "local order has no reliable identity"));
                continue;
            }
            if (duplicates.containsLocal(localOrder)) {
                continue;
            }
            RemoteOrderSnapshot remoteOrder = localOrder.exchangeOrderId() == null
                    ? null : byExchange.get(localOrder.exchangeOrderId());
            // exchangeOrderId 是最高优先级身份；本地已有该 ID 时绝不因 clientOrderId 相同而跨 ID 猜配。
            if (remoteOrder == null && localOrder.exchangeOrderId() == null && localOrder.clientOrderId() != null) {
                remoteOrder = byClient.get(localOrder.clientOrderId());
            }
            if (remoteOrder == null) {
                differences.add(finding(ReconciliationTaxonomy.LOCAL_ONLY, localOrder, null,
                        "no remote match by exchangeOrderId or clientOrderId"));
                continue;
            }
            matchedRemote.add(remoteOrder);
            compare(localOrder, remoteOrder, matches, differences, unknowns);
        }
        for (RemoteOrderSnapshot remoteOrder : remote.orders()) {
            if (remoteOrder.exchangeOrderId() == null && remoteOrder.clientOrderId() == null) {
                unknowns.add(finding(ReconciliationTaxonomy.UNMATCHED_IDENTITY, null, remoteOrder,
                        "remote order has no reliable identity"));
            } else if (!duplicates.containsRemote(remoteOrder) && !matchedRemote.contains(remoteOrder)) {
                differences.add(finding(ReconciliationTaxonomy.REMOTE_ONLY, null, remoteOrder,
                        "no local match by exchangeOrderId or clientOrderId"));
            }
        }
        return result(matches, differences, blockers, warnings, unknowns, notEvaluated, now);
    }

    private static void compare(
            LocalOrderSnapshot local,
            RemoteOrderSnapshot remote,
            List<ReconciliationFinding> matches,
            List<ReconciliationFinding> differences,
            List<ReconciliationFinding> unknowns
    ) {
        boolean same = true;
        String mapped = mapRemoteStatus(remote.remoteStatus());
        if (mapped == null) {
            unknowns.add(finding(ReconciliationTaxonomy.UNMAPPABLE_REMOTE_STATUS, local, remote,
                    "remote status is outside the frozen mapping"));
            same = false;
        } else if (!mapped.equals(local.localStatus().toUpperCase(Locale.ROOT))) {
            differences.add(finding(ReconciliationTaxonomy.STATUS_MISMATCH, local, remote, "status differs"));
            same = false;
        }
        if (!decimalEquals(local.price(), remote.price())) {
            differences.add(finding(ReconciliationTaxonomy.PRICE_MISMATCH, local, remote, "price differs"));
            same = false;
        }
        if (!decimalEquals(local.originalQuantity(), remote.originalQuantity())) {
            differences.add(finding(ReconciliationTaxonomy.QUANTITY_MISMATCH, local, remote, "quantity differs"));
            same = false;
        }
        if (!decimalEquals(local.filledQuantity(), remote.filledQuantity())) {
            differences.add(finding(ReconciliationTaxonomy.FILLED_QUANTITY_MISMATCH, local, remote,
                    "filled quantity differs"));
            same = false;
        }
        if (!local.symbol().equalsIgnoreCase(remote.symbol())) {
            differences.add(finding(ReconciliationTaxonomy.SYMBOL_MISMATCH, local, remote, "symbol differs"));
            same = false;
        }
        if (!local.side().equalsIgnoreCase(remote.side())) {
            differences.add(finding(ReconciliationTaxonomy.SIDE_MISMATCH, local, remote, "side differs"));
            same = false;
        }
        if (!local.orderType().equalsIgnoreCase(remote.orderType())) {
            differences.add(finding(ReconciliationTaxonomy.ORDER_TYPE_MISMATCH, local, remote, "order type differs"));
            same = false;
        }
        if (local.clientOrderId() != null && remote.clientOrderId() != null
                && !local.clientOrderId().equals(remote.clientOrderId())) {
            differences.add(finding(ReconciliationTaxonomy.CLIENT_ORDER_ID_MISMATCH, local, remote,
                    "client order ID differs for the matched exchange order ID"));
            same = false;
        }
        if (same) {
            matches.add(finding(ReconciliationTaxonomy.MATCHED, local, remote,
                    "snapshot fields matched at evaluation time"));
        }
    }

    private static DuplicateState findDuplicates(
            List<LocalOrderSnapshot> local,
            List<RemoteOrderSnapshot> remote,
            List<ReconciliationFinding> blockers
    ) {
        Set<String> localExchange = duplicates(local.stream().map(LocalOrderSnapshot::exchangeOrderId).toList());
        Set<String> localClient = duplicates(local.stream().map(LocalOrderSnapshot::clientOrderId).toList());
        Set<String> remoteExchange = duplicates(remote.stream().map(RemoteOrderSnapshot::exchangeOrderId).toList());
        Set<String> remoteClient = duplicates(remote.stream().map(RemoteOrderSnapshot::clientOrderId).toList());
        localExchange.forEach(id -> blockers.add(globalFinding(ReconciliationTaxonomy.DUPLICATE_LOCAL_ID, id, null,
                "duplicate local exchangeOrderId")));
        localClient.forEach(id -> blockers.add(globalFinding(ReconciliationTaxonomy.DUPLICATE_LOCAL_ID, null, id,
                "duplicate local clientOrderId")));
        remoteExchange.forEach(id -> blockers.add(globalFinding(ReconciliationTaxonomy.DUPLICATE_REMOTE_ID, id, null,
                "duplicate remote exchangeOrderId")));
        remoteClient.forEach(id -> blockers.add(globalFinding(ReconciliationTaxonomy.DUPLICATE_REMOTE_ID, null, id,
                "duplicate remote clientOrderId")));
        return new DuplicateState(localExchange, localClient, remoteExchange, remoteClient);
    }

    private static Set<String> duplicates(List<String> values) {
        Set<String> seen = new HashSet<>();
        Set<String> duplicate = new HashSet<>();
        values.stream().filter(Objects::nonNull).forEach(value -> {
            if (!seen.add(value)) duplicate.add(value);
        });
        return duplicate;
    }

    private static Map<String, RemoteOrderSnapshot> indexRemote(
            List<RemoteOrderSnapshot> remote,
            boolean exchange,
            Set<String> duplicateIds
    ) {
        Map<String, RemoteOrderSnapshot> result = new HashMap<>();
        for (RemoteOrderSnapshot item : remote) {
            String id = exchange ? item.exchangeOrderId() : item.clientOrderId();
            if (id != null && !duplicateIds.contains(id)) result.put(id, item);
        }
        return result;
    }

    private static String mapRemoteStatus(String status) {
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "live" -> "ACCEPTED";
            case "partially_filled" -> "PARTIALLY_FILLED";
            case "filled" -> "FILLED";
            case "canceled", "mmp_canceled" -> "CANCELLED";
            default -> null;
        };
    }

    private static boolean decimalEquals(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) return left == right;
        return left.compareTo(right) == 0;
    }

    private static boolean isFutureOrStale(Instant value, Instant now) {
        return value.isAfter(now) || value.isBefore(now.minus(STALE_THRESHOLD));
    }

    private static ReconciliationFinding finding(
            ReconciliationTaxonomy taxonomy,
            LocalOrderSnapshot local,
            RemoteOrderSnapshot remote,
            String detail
    ) {
        return new ReconciliationFinding(
                taxonomy,
                local == null ? null : local.localOrderReference(),
                remote == null ? (local == null ? null : local.exchangeOrderId()) : remote.exchangeOrderId(),
                remote == null ? (local == null ? null : local.clientOrderId()) : remote.clientOrderId(),
                detail
        );
    }

    private static ReconciliationFinding globalFinding(
            ReconciliationTaxonomy taxonomy,
            String exchangeOrderId,
            String clientOrderId,
            String detail
    ) {
        return new ReconciliationFinding(taxonomy, null, exchangeOrderId, clientOrderId, detail);
    }

    private static ReconciliationResult result(
            List<ReconciliationFinding> matches,
            List<ReconciliationFinding> differences,
            List<ReconciliationFinding> blockers,
            List<ReconciliationFinding> warnings,
            List<ReconciliationFinding> unknowns,
            List<ReconciliationFinding> notEvaluated,
            Instant now
    ) {
        boolean clean = differences.isEmpty() && warnings.isEmpty() && unknowns.isEmpty() && notEvaluated.isEmpty()
                && blockers.stream().allMatch(item -> item.taxonomy() == ReconciliationTaxonomy.EXECUTION_NOT_AUTHORIZED);
        return new ReconciliationResult(matches, differences, blockers, warnings, unknowns, notEvaluated, now,
                clean ? "SNAPSHOT_MATCHED_AT_EVALUATION_TIME" : "SNAPSHOT_DIFFERENCES_OR_UNKNOWNS_PRESENT");
    }

    private record DuplicateState(
            Set<String> localExchangeIds,
            Set<String> localClientIds,
            Set<String> remoteExchangeIds,
            Set<String> remoteClientIds
    ) {
        boolean containsLocal(LocalOrderSnapshot order) {
            return localExchangeIds.contains(order.exchangeOrderId()) || localClientIds.contains(order.clientOrderId());
        }

        boolean containsRemote(RemoteOrderSnapshot order) {
            return remoteExchangeIds.contains(order.exchangeOrderId()) || remoteClientIds.contains(order.clientOrderId());
        }
    }
}
