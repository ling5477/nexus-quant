package com.guidinglight.nexusquant.core.service;

import com.guidinglight.nexusquant.core.recovery.RecoveryReport;

/**
 * TradingMaintenanceService 对 reconcile / recovery 暴露 application-facing service。
 */
public interface TradingMaintenanceService {

    int runReconcile(String venue, int limit);

    RecoveryReport runRecovery(String venue, String traceId);
}
