package com.guidinglight.nexusquant.trading.application;

import com.guidinglight.nexusquant.trading.application.RecoveryReport;

/**
 * TradingMaintenanceService 对 reconcile / recovery 暴露 application-facing service。
 */
public interface TradingMaintenanceService {

    int runReconcile(String venue, int limit);

    RecoveryReport runRecovery(String venue, String traceId);
}



