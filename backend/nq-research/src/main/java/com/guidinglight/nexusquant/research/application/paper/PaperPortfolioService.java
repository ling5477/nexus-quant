package com.guidinglight.nexusquant.research.application.paper;

import com.guidinglight.nexusquant.research.domain.BacktestPublishRecord;
import com.guidinglight.nexusquant.research.domain.paper.EquityCurveSnapshot;
import com.guidinglight.nexusquant.research.domain.paper.PaperRiskCheckResult;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunDailyReport;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRun;
import com.guidinglight.nexusquant.research.domain.paper.port.EquityCurveSnapshotRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRiskCheckResultRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRunAlertRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRunDailyReportRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperTradingRunRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperTradingTradeRepository;
import com.guidinglight.nexusquant.research.domain.port.BacktestPublishRecordRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

/**
 * PaperPortfolioService —— Paper 组合看板只读聚合编排（GateJ 后产品化 Loop-13）。
 *
 * 职责：复用已有 Paper Trading 只读仓储拉取多个 run 的事实，委托 {@link PaperPortfolioAssembler}
 * 归纳为 {@link PaperPortfolioSummary}。
 * 边界：
 * 1) 只读；不触发 start / stop / run-once / monitor / schedule / recovery / backtest / publish 等写动作。
 * 2) 仅依赖领域只读仓储端口（run / equity / dailyReport / risk / alert / trade / publish），
 *    不依赖 adapter / exchange / credential / permission probe / 外部 HTTP client（构造仅注入仓储）。
 * 3) 直接使用仓储批量只读端口（listByRunIds / countOpenByRunIds / countByRunIds），
 *    避免经 monitor/run service 的 getById 二次校验产生额外查询。
 *
 * 复杂度说明：组合聚合按「最近 {@link #MAX_PORTFOLIO_RUNS} 个 run」上限。run 清单一次 list()，
 * publish 来源一次 listAll() 建索引，equity / dailyReport / risk 一次 listByRunIds() 批量取，
 * alert / trade 一次 countOpenByRunIds() / countByRunIds() 批量聚合计数；run 循环内仅做内存 Map 查找，
 * 不再逐 run 查询仓储。读放大由「O(run 数)」收敛为「固定数量批量查询」，规模增长下不再放大。
 */
@Service
public class PaperPortfolioService {

    /** 组合聚合的最近 run 上限；超过部分按 updatedAt 倒序截断，保护单次聚合的查询规模。 */
    static final int MAX_PORTFOLIO_RUNS = 200;

    private final PaperTradingRunRepository runRepository;
    private final EquityCurveSnapshotRepository equityCurveRepository;
    private final PaperRunDailyReportRepository dailyReportRepository;
    private final PaperRiskCheckResultRepository riskCheckResultRepository;
    private final PaperRunAlertRepository alertRepository;
    private final PaperTradingTradeRepository tradeRepository;
    private final BacktestPublishRecordRepository publishRecordRepository;

    public PaperPortfolioService(
            PaperTradingRunRepository runRepository,
            EquityCurveSnapshotRepository equityCurveRepository,
            PaperRunDailyReportRepository dailyReportRepository,
            PaperRiskCheckResultRepository riskCheckResultRepository,
            PaperRunAlertRepository alertRepository,
            PaperTradingTradeRepository tradeRepository,
            BacktestPublishRecordRepository publishRecordRepository
    ) {
        this.runRepository = Objects.requireNonNull(runRepository, "runRepository must not be null");
        this.equityCurveRepository = Objects.requireNonNull(equityCurveRepository, "equityCurveRepository must not be null");
        this.dailyReportRepository = Objects.requireNonNull(dailyReportRepository, "dailyReportRepository must not be null");
        this.riskCheckResultRepository = Objects.requireNonNull(riskCheckResultRepository, "riskCheckResultRepository must not be null");
        this.alertRepository = Objects.requireNonNull(alertRepository, "alertRepository must not be null");
        this.tradeRepository = Objects.requireNonNull(tradeRepository, "tradeRepository must not be null");
        this.publishRecordRepository = Objects.requireNonNull(publishRecordRepository, "publishRecordRepository must not be null");
    }

    /**
     * 聚合全部（最多 {@link #MAX_PORTFOLIO_RUNS} 个）Paper run 的组合看板只读 summary。
     * 无 run 时返回稳定空结构（总数 0、各清单为空、可比收益率为 null）。
     */
    public PaperPortfolioSummary summarize() {
        List<PaperTradingRun> runs = new ArrayList<>(runRepository.list(null, null));
        // 按更新时间倒序截断到上限，保证「最近活跃」优先纳入聚合，并保护查询规模。
        runs.sort(Comparator.comparing(
                PaperTradingRun::updatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        if (runs.size() > MAX_PORTFOLIO_RUNS) {
            runs = runs.subList(0, MAX_PORTFOLIO_RUNS);
        }

        // 提取 bounded run 的 runIds，作为各类事实批量读取的统一键集合（空集合时各仓储返回空 Map）。
        List<String> runIds = runs.stream().map(PaperTradingRun::paperRunId).toList();

        // 一次 listAll() 建立 publishId -> record 索引，循环内只做内存查找，避免逐 run 查询 publish。
        Map<String, BacktestPublishRecord> publishById = publishRecordRepository.listAll().stream()
                .collect(Collectors.toMap(
                        BacktestPublishRecord::publishRecordId,
                        Function.identity(),
                        (left, right) -> left));

        // 各类 run 事实改为单次批量读取，返回 runId -> 事实；run 循环内只做 Map 查找，缺省取空列表 / 0 计数。
        Map<String, List<EquityCurveSnapshot>> equityByRun = equityCurveRepository.listByRunIds(runIds);
        Map<String, List<PaperRunDailyReport>> reportsByRun = dailyReportRepository.listByRunIds(runIds);
        Map<String, List<PaperRiskCheckResult>> riskByRun = riskCheckResultRepository.listByRunIds(runIds);
        Map<String, Long> openAlertCountByRun = alertRepository.countOpenByRunIds(runIds);
        Map<String, Long> tradeCountByRun = tradeRepository.countByRunIds(runIds);

        List<PaperPortfolioAssembler.RunInput> inputs = new ArrayList<>(runs.size());
        for (PaperTradingRun run : runs) {
            String runId = run.paperRunId();
            String publishId = run.publishId();
            BacktestPublishRecord publish = publishId != null ? publishById.get(publishId) : null;
            boolean hasPublishSource = publishId != null && !publishId.isBlank() && publish != null;
            boolean hasBacktestSource = publish != null
                    && publish.backtestRunId() != null && !publish.backtestRunId().isBlank();

            inputs.add(new PaperPortfolioAssembler.RunInput(
                    run,
                    equityByRun.getOrDefault(runId, List.of()),
                    reportsByRun.getOrDefault(runId, List.of()),
                    riskByRun.getOrDefault(runId, List.of()),
                    Math.toIntExact(openAlertCountByRun.getOrDefault(runId, 0L)),
                    Math.toIntExact(tradeCountByRun.getOrDefault(runId, 0L)),
                    hasPublishSource,
                    hasBacktestSource));
        }

        return PaperPortfolioAssembler.assemble(inputs);
    }
}
