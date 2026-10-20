package com.guidinglight.nexusquant.trading.application.maintenance;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** 维护命令的原请求回执；执行及账务事实仍属于现有维护服务，查询不会再次执行。 */
public interface TradingMaintenanceOperations {
    Receipt submit(
            UUID operationId, long owner, Kind kind, String venue, Integer limit, String traceId);

    Optional<Receipt> find(UUID operationId, long owner);

    enum Kind {
        RECONCILE,
        RECOVERY
    }

    /** STARTED及UNKNOWN均无最终结果；只有COMPLETED携带原服务完整返回值。 */
    record Receipt(
            UUID operationId,
            Kind kind,
            String venue,
            Integer limit,
            String state,
            String traceId,
            String detail,
            Instant createdAt,
            Instant completedAt) {}
}
