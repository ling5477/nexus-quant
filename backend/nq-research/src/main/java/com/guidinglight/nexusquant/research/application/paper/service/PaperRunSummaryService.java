package com.guidinglight.nexusquant.research.application.paper.service;

import com.guidinglight.nexusquant.research.application.paper.model.PaperRunSummary;

import com.guidinglight.nexusquant.research.application.paper.assembler.PaperRunSummaryAssembler;

import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRun;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRunCanonicalEconomicsQuery;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * PaperRunSummaryService —— Paper run 只读聚合编排。
 *
 * 职责：复用已有 Paper Trading 读服务拉取单个 run 的事实，委托 {@link PaperRunSummaryAssembler}
 * 归纳为 {@link PaperRunSummary}。
 * 边界：
 * 1) 只读；不触发 start / stop / run-once / monitor / schedule / recovery 等写动作。
 * 2) 策略 SIM 的净盈亏通过只读口径复用 /facts；不调用交易 mutation 或外部 HTTP。
 * 3) run 不存在时由 runService.getById 抛出 IllegalArgumentException，上层映射 404。
 */
@Service
public class PaperRunSummaryService {

    private final PaperTradingRunService runService;
    private final PaperTradingMonitorService monitorService;
    private final PaperRunMonitorService paperRunMonitorService;
    private final PaperRunRecoveryService recoveryService;
    private final Clock clock;
    private final PaperRunCanonicalEconomicsQuery canonicalEconomics;

    @Autowired
    public PaperRunSummaryService(
            PaperTradingRunService runService,
            PaperTradingMonitorService monitorService,
            PaperRunMonitorService paperRunMonitorService,
            PaperRunRecoveryService recoveryService,
            ObjectProvider<PaperRunCanonicalEconomicsQuery> canonicalEconomics
    ) {
        this(runService, monitorService, paperRunMonitorService, recoveryService,
                Clock.systemUTC(), canonicalEconomics.getIfAvailable());
    }

    public PaperRunSummaryService(
            PaperTradingRunService runService,
            PaperTradingMonitorService monitorService,
            PaperRunMonitorService paperRunMonitorService,
            PaperRunRecoveryService recoveryService,
            Clock clock
    ) {
        this(runService, monitorService, paperRunMonitorService, recoveryService, clock, null);
    }

    public PaperRunSummaryService(
            PaperTradingRunService runService,
            PaperTradingMonitorService monitorService,
            PaperRunMonitorService paperRunMonitorService,
            PaperRunRecoveryService recoveryService,
            Clock clock,
            PaperRunCanonicalEconomicsQuery canonicalEconomics
    ) {
        this.runService = Objects.requireNonNull(runService, "runService must not be null");
        this.monitorService = Objects.requireNonNull(monitorService, "monitorService must not be null");
        this.paperRunMonitorService = Objects.requireNonNull(paperRunMonitorService, "paperRunMonitorService must not be null");
        this.recoveryService = Objects.requireNonNull(recoveryService, "recoveryService must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.canonicalEconomics = canonicalEconomics;
    }

    /**
     * 聚合指定 Paper run 的只读 summary。
     *
     * @param paperRunId Paper run 主键
     * @return 聚合结果
     * @throws IllegalArgumentException run 不存在（上层映射 404）
     */
    public PaperRunSummary summarize(String paperRunId) {
        // 先校验 run 存在；不存在抛 IllegalArgumentException，由 API 层转 404。
        PaperTradingRun run = runService.getById(paperRunId);
        boolean strategySim = runService.isStrategySim(paperRunId);
        BigDecimal pnl = strategySim && canonicalEconomics != null
                ? canonicalEconomics.pnl(paperRunId) : null;

        return PaperRunSummaryAssembler.assemble(
                run,
                runService.listOrders(paperRunId),
                runService.listTrades(paperRunId),
                runService.listPositions(paperRunId),
                monitorService.listRiskResults(paperRunId),
                strategySim ? List.of() : monitorService.listEquityCurve(paperRunId),
                strategySim ? List.of() : paperRunMonitorService.listDailyReports(paperRunId),
                paperRunMonitorService.listAlerts(paperRunId, null, null),
                recoveryService.listRecoveryEvents(paperRunId, null, null),
                clock.instant(),
                pnl
        );
    }
}
