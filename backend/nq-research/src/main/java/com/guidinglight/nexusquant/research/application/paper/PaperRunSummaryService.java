package com.guidinglight.nexusquant.research.application.paper;

import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRun;

import java.time.Clock;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * PaperRunSummaryService —— Paper run 只读聚合编排（GateJ 后产品化 Loop-8）。
 *
 * 职责：复用已有 Paper Trading 读服务拉取单个 run 的事实，委托 {@link PaperRunSummaryAssembler}
 * 归纳为 {@link PaperRunSummary}。
 * 边界：
 * 1) 只读；不触发 start / stop / run-once / monitor / schedule / recovery 等写动作。
 * 2) 不依赖 adapter / exchange / credential / permission probe / 外部 HTTP client（构造仅注入读服务）。
 * 3) run 不存在时由 runService.getById 抛出 IllegalArgumentException，上层映射 404。
 */
@Service
public class PaperRunSummaryService {

    private final PaperTradingRunService runService;
    private final PaperTradingMonitorService monitorService;
    private final PaperRunMonitorService paperRunMonitorService;
    private final PaperRunRecoveryService recoveryService;
    private final Clock clock;

    @Autowired
    public PaperRunSummaryService(
            PaperTradingRunService runService,
            PaperTradingMonitorService monitorService,
            PaperRunMonitorService paperRunMonitorService,
            PaperRunRecoveryService recoveryService
    ) {
        this(runService, monitorService, paperRunMonitorService, recoveryService, Clock.systemUTC());
    }

    public PaperRunSummaryService(
            PaperTradingRunService runService,
            PaperTradingMonitorService monitorService,
            PaperRunMonitorService paperRunMonitorService,
            PaperRunRecoveryService recoveryService,
            Clock clock
    ) {
        this.runService = Objects.requireNonNull(runService, "runService must not be null");
        this.monitorService = Objects.requireNonNull(monitorService, "monitorService must not be null");
        this.paperRunMonitorService = Objects.requireNonNull(paperRunMonitorService, "paperRunMonitorService must not be null");
        this.recoveryService = Objects.requireNonNull(recoveryService, "recoveryService must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
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

        return PaperRunSummaryAssembler.assemble(
                run,
                runService.listOrders(paperRunId),
                runService.listTrades(paperRunId),
                runService.listPositions(paperRunId),
                monitorService.listRiskResults(paperRunId),
                monitorService.listEquityCurve(paperRunId),
                paperRunMonitorService.listDailyReports(paperRunId),
                paperRunMonitorService.listAlerts(paperRunId, null, null),
                recoveryService.listRecoveryEvents(paperRunId, null, null),
                clock.instant()
        );
    }
}
