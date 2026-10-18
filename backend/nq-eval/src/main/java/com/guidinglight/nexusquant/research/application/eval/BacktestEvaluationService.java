package com.guidinglight.nexusquant.research.application.eval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import com.guidinglight.nexusquant.research.application.BacktestRunService;
import com.guidinglight.nexusquant.research.application.config.BacktestConfigService;
import com.guidinglight.nexusquant.research.domain.BacktestRunStatus;
import com.guidinglight.nexusquant.research.domain.eval.BacktestEvaluationReport;
import com.guidinglight.nexusquant.research.domain.eval.EvaluationMetricCalculator;
import com.guidinglight.nexusquant.research.domain.eval.EvaluationStatus;
import com.guidinglight.nexusquant.research.domain.eval.ResearchValidityCalculator;
import com.guidinglight.nexusquant.research.domain.eval.port.BacktestEvaluationReportRepository;
import com.guidinglight.nexusquant.research.domain.eval.port.SimOrderQueryRepository;
import com.guidinglight.nexusquant.research.domain.eval.port.SimPnlSnapshotQueryRepository;
import com.guidinglight.nexusquant.research.domain.eval.port.SimPositionQueryRepository;
import com.guidinglight.nexusquant.research.domain.eval.port.SimTradeQueryRepository;
import com.guidinglight.nexusquant.research.domain.port.ResearchFeeSourcePort;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** BacktestEvaluationService 提供研究与回测契约的显式 evaluate 主链。 */
@Service
public class BacktestEvaluationService {

  // 评估仍须校验原费用来源；传递冻结参数时不先改写其数值精度。
  private static final ObjectReader SNAPSHOT_READER =
      new ObjectMapper()
          .reader()
          .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
          .without(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES);

  private static final Set<String> EVENT_ORDER_REJECTIONS =
      Set.of(
          "RESEARCH_TRADE_ORDER_UNAVAILABLE",
          "RESEARCH_TRADE_SEQUENCE_INCONSISTENT",
          "RESEARCH_VALUATION_ORDER_UNAVAILABLE",
          "RESEARCH_VALUATION_SEQUENCE_INCONSISTENT");

  private final BacktestRunService backtestRunService;
  private final BacktestConfigService backtestConfigService;
  private final SimOrderQueryRepository simOrderQueryRepository;
  private final SimTradeQueryRepository simTradeQueryRepository;
  private final SimPositionQueryRepository simPositionQueryRepository;
  private final SimPnlSnapshotQueryRepository simPnlSnapshotQueryRepository;
  private final EvaluationMetricCalculator evaluationMetricCalculator;
  private final BacktestEvaluationReportRepository backtestEvaluationReportRepository;
  private final Clock clock;
  private ResearchFeeSourcePort feeSource = ResearchFeeSourcePort.disabled();

  /** 与执行复用同一原观察核验；单纯重算摘要不能让伪造或跨所有者的费用进入评估和发布。 */
  @Autowired(required = false)
  private void bindFeeSource(ObjectProvider<ResearchFeeSourcePort> provider) {
    feeSource = provider.getIfAvailable(ResearchFeeSourcePort::disabled);
  }

  /**
   * 显式指定运行时构造器，避免测试专用 Clock 构造器导致容器误走无参实例化路径。 Why: 评估服务需要保留可注入 Clock 的包级构造器给回归测试控制时间，
   * 但生产启动必须稳定使用完整依赖注入构造器，否则控制台验收无法拉起 nq-app。
   */
  @Autowired
  public BacktestEvaluationService(
      BacktestRunService backtestRunService,
      BacktestConfigService backtestConfigService,
      SimOrderQueryRepository simOrderQueryRepository,
      SimTradeQueryRepository simTradeQueryRepository,
      SimPositionQueryRepository simPositionQueryRepository,
      SimPnlSnapshotQueryRepository simPnlSnapshotQueryRepository,
      EvaluationMetricCalculator evaluationMetricCalculator,
      BacktestEvaluationReportRepository backtestEvaluationReportRepository) {
    this(
        backtestRunService,
        backtestConfigService,
        simOrderQueryRepository,
        simTradeQueryRepository,
        simPositionQueryRepository,
        simPnlSnapshotQueryRepository,
        evaluationMetricCalculator,
        backtestEvaluationReportRepository,
        Clock.systemUTC());
  }

  BacktestEvaluationService(
      BacktestRunService backtestRunService,
      BacktestConfigService backtestConfigService,
      SimOrderQueryRepository simOrderQueryRepository,
      SimTradeQueryRepository simTradeQueryRepository,
      SimPositionQueryRepository simPositionQueryRepository,
      SimPnlSnapshotQueryRepository simPnlSnapshotQueryRepository,
      EvaluationMetricCalculator evaluationMetricCalculator,
      BacktestEvaluationReportRepository backtestEvaluationReportRepository,
      Clock clock) {
    this.backtestRunService =
        Objects.requireNonNull(backtestRunService, "backtestRunService must not be null");
    this.backtestConfigService =
        Objects.requireNonNull(backtestConfigService, "backtestConfigService must not be null");
    this.simOrderQueryRepository =
        Objects.requireNonNull(simOrderQueryRepository, "simOrderQueryRepository must not be null");
    this.simTradeQueryRepository =
        Objects.requireNonNull(simTradeQueryRepository, "simTradeQueryRepository must not be null");
    this.simPositionQueryRepository =
        Objects.requireNonNull(
            simPositionQueryRepository, "simPositionQueryRepository must not be null");
    this.simPnlSnapshotQueryRepository =
        Objects.requireNonNull(
            simPnlSnapshotQueryRepository, "simPnlSnapshotQueryRepository must not be null");
    this.evaluationMetricCalculator =
        Objects.requireNonNull(
            evaluationMetricCalculator, "evaluationMetricCalculator must not be null");
    this.backtestEvaluationReportRepository =
        Objects.requireNonNull(
            backtestEvaluationReportRepository,
            "backtestEvaluationReportRepository must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  public BacktestEvaluationReport evaluate(String backtestRunId) {
    Instant evaluatedAt = Instant.now(clock);
    try {
      var backtestRun = backtestRunService.getByBacktestRunId(backtestRunId);
      if (backtestRun.status() != BacktestRunStatus.SUCCEEDED) {
        throw new IllegalStateException("only SUCCEEDED runs can be evaluated");
      }
      try {
        String frozen = backtestRun.configSnapshotJson();
        var spec =
            SNAPSHOT_READER
                .readTree(frozen == null || frozen.isBlank() ? "{}" : frozen)
                .path("executionSpec");
        feeSource.validateFrozenExecution(
            spec.isMissingNode() ? "{}" : spec.toString(), backtestRun.datasetSnapshotJson());
      } catch (JsonProcessingException ex) {
        throw new IllegalStateException("FEE_MODEL_JSON_INVALID", ex);
      }
      var researchFacts = ResearchValidityCalculator.facts(backtestRun);
      // 新报告使用运行时冻结的资本；后续编辑配置不能改变既有运行的收益分母。
      BigDecimal initialCapital =
          researchFacts != null
              ? researchFacts.assumptions().initialCapital()
              : backtestConfigService
                  .getByBacktestConfigId(backtestRun.backtestConfigId())
                  .initialCapital();
      if (initialCapital.compareTo(BigDecimal.ZERO) <= 0) {
        throw new IllegalStateException("initialCapital must be positive for evaluation");
      }
      var simOrders = simOrderQueryRepository.listByBacktestRunId(backtestRunId);
      var simTrades = simTradeQueryRepository.listByBacktestRunId(backtestRunId);
      var simPositions = simPositionQueryRepository.listByBacktestRunId(backtestRunId);
      var simPnlSnapshots = simPnlSnapshotQueryRepository.listByBacktestRunId(backtestRunId);
      if (simPnlSnapshots.isEmpty()) {
        throw new IllegalStateException("sim_pnl_snapshots must exist for evaluation");
      }
      BacktestEvaluationReport report =
          evaluationMetricCalculator.calculate(
              backtestRunId,
              initialCapital,
              simOrders,
              simTrades,
              simPositions,
              simPnlSnapshots,
              evaluatedAt,
              // 与执行服务选择冻结策略路径的条件一致，该路径的fill price已包含滑点。
              backtestRun.strategyVersionId() != null
                  && !backtestRun.strategyVersionId().isBlank());
      report =
          report.withReportJson(
              ResearchValidityCalculator.append(
                  report.reportJson(),
                  ResearchValidityCalculator.calculate(
                      researchFacts, simPnlSnapshots, simTrades, evaluatedAt)));
      backtestEvaluationReportRepository.upsert(report);
      return report;
    } catch (RuntimeException ex) {
      // 新顺序守卫只能拒绝重算，不能将旧成功报告改成失败并破坏原冻结结果与发布血缘。
      if (ex.getMessage() != null
          && EVENT_ORDER_REJECTIONS.contains(ex.getMessage())
          && backtestEvaluationReportRepository
              .findByBacktestRunId(backtestRunId)
              .filter(existing -> existing.evaluationStatus() == EvaluationStatus.SUCCEEDED)
              .isPresent()) {
        throw new IllegalStateException("backtest evaluation failed: " + safeMessage(ex), ex);
      }
      BacktestEvaluationReport failedReport =
          new BacktestEvaluationReport(
              "eval-" + UUID.randomUUID(),
              backtestRunId,
              EvaluationStatus.FAILED,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              "{}",
              "EVALUATION_FAILED",
              safeMessage(ex),
              evaluatedAt,
              evaluatedAt,
              evaluatedAt);
      backtestEvaluationReportRepository.upsert(failedReport);
      throw new BacktestEvaluationFailedException(failedReport, ex);
    }
  }

  public Optional<BacktestEvaluationReport> getByBacktestRunId(String backtestRunId) {
    return backtestEvaluationReportRepository.findByBacktestRunId(backtestRunId);
  }

  /** 按评估报告 ID 查询详情。 Why: 策略版本增加独立 Evaluation API，调用方可以从报告列表直接打开详情， 不再必须先知道 backtestRunId。 */
  public Optional<BacktestEvaluationReport> getByEvalReportId(String evalReportId) {
    return backtestEvaluationReportRepository.findByEvalReportId(evalReportId);
  }

  /**
   * 查询评估报告列表。 Why: 策略版本第一版只需要展示已生成报告的核心指标，不引入复杂分页或跨表聚合； 页面需要 run 血缘时仍可通过 backtest run 详情读取完整输入快照。
   */
  public List<BacktestEvaluationReport> listAll() {
    return backtestEvaluationReportRepository.listAll();
  }

  /** 按研究配置或回测配置读取评估报告，避免跨配置混入其他 run 的指标。 */
  public List<BacktestEvaluationReport> list(String researchConfigId, String backtestConfigId) {
    return backtestEvaluationReportRepository.list(
        normalizeFilter(researchConfigId), normalizeFilter(backtestConfigId));
  }

  private static String normalizeFilter(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private String safeMessage(RuntimeException exception) {
    return exception.getMessage() == null || exception.getMessage().isBlank()
        ? exception.getClass().getSimpleName()
        : exception.getMessage();
  }
}
