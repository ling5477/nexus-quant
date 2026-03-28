package com.guidinglight.nexusquant.scheduler.service;

import com.guidinglight.nexusquant.core.recovery.RecoveryReport;
import com.guidinglight.nexusquant.core.recovery.RecoveryService;
import com.guidinglight.nexusquant.core.service.TradingMaintenanceService;

import java.util.Locale;
import java.util.Objects;

/**
 * SchedulerTradingMaintenanceService 收口 scheduler 侧的 reconcile / recovery 具体实现。
 */
public class SchedulerTradingMaintenanceService implements TradingMaintenanceService {

    private final OkxRestReconcileService okxRestReconcileService;
    private final BinanceRestReconcileService binanceRestReconcileService;
    private final BinanceRecoveryService binanceRecoveryService;
    private final RecoveryService recoveryService;

    public SchedulerTradingMaintenanceService(
            OkxRestReconcileService okxRestReconcileService,
            BinanceRestReconcileService binanceRestReconcileService,
            BinanceRecoveryService binanceRecoveryService,
            RecoveryService recoveryService
    ) {
        this.okxRestReconcileService = Objects.requireNonNull(okxRestReconcileService, "okxRestReconcileService must not be null");
        this.binanceRestReconcileService = Objects.requireNonNull(
                binanceRestReconcileService,
                "binanceRestReconcileService must not be null"
        );
        this.binanceRecoveryService = Objects.requireNonNull(binanceRecoveryService, "binanceRecoveryService must not be null");
        this.recoveryService = Objects.requireNonNull(recoveryService, "recoveryService must not be null");
    }

    @Override
    public int runReconcile(String venue, int limit) {
        String normalizedVenue = normalizeVenue(venue);
        return switch (normalizedVenue) {
            case "OKX" -> okxRestReconcileService.reconcileOnce(limit);
            case "BINANCE" -> binanceRestReconcileService.reconcileOnce(limit);
            default -> throw new IllegalArgumentException("unsupported reconcile venue: " + normalizedVenue);
        };
    }

    @Override
    public RecoveryReport runRecovery(String venue, String traceId) {
        String normalizedVenue = normalizeVenue(venue);
        return switch (normalizedVenue) {
            case "OKX" -> recoveryService.rebuild(traceId);
            case "BINANCE" -> binanceRecoveryService.rebuild(traceId);
            default -> throw new IllegalArgumentException("unsupported recovery venue: " + normalizedVenue);
        };
    }

    private String normalizeVenue(String venue) {
        return venue == null || venue.isBlank() ? "OKX" : venue.trim().toUpperCase(Locale.ROOT);
    }
}
