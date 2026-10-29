package com.guidinglight.nexusquant.app.research;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.common.tenant.TenantContext;
import com.guidinglight.nexusquant.common.tenant.TenantKind;
import com.guidinglight.nexusquant.research.application.BacktestPublishService;
import com.guidinglight.nexusquant.research.application.BacktestRunService;
import com.guidinglight.nexusquant.research.application.eval.BacktestEvaluationService;
import com.guidinglight.nexusquant.research.application.paper.service.PaperTradingRunService;
import com.guidinglight.nexusquant.research.domain.BacktestRun;
import com.guidinglight.nexusquant.research.domain.BacktestRunStatus;
import com.guidinglight.nexusquant.research.domain.PublishStatus;
import com.guidinglight.nexusquant.research.domain.eval.EvaluationStatus;
import com.guidinglight.nexusquant.research.domain.port.StrategyVersionSnapshotQueryPort;
import com.guidinglight.nexusquant.research.infra.ResearchFeeReadScope;
import com.guidinglight.nexusquant.scheduler.paper.ContinuousSimQueryService;
import com.guidinglight.nexusquant.scheduler.paper.ContinuousSimRunService;
import com.guidinglight.nexusquant.scheduler.paper.StrategySimAnalysisQuery;
import com.guidinglight.nexusquant.scheduler.paper.StrategySimPerformanceQuery;
import com.guidinglight.nexusquant.scheduler.paper.StrategySimRunService;
import com.guidinglight.nexusquant.strategy.domain.SpotTargetStrategy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 只引用正式研究、发布与SIM事实；不同市场时期的绩效不产生收益差或交易指令。 */
@Service
public class ResearchSimComparisonService {
    private final StrategyExperimentService experiments;
    private final ExperimentValidationService validations;
    private final BacktestRunService backtests;
    private final BacktestPublishService publications;
    private final BacktestEvaluationService evaluations;
    private final StrategyVersionSnapshotQueryPort versions;
    private final PaperTradingRunService paperRuns;
    private final ObjectProvider<StrategySimRunService> sims;
    private final ObjectProvider<StrategySimAnalysisQuery> analysis;
    private final ObjectProvider<ContinuousSimQueryService> continuous;
    private final ResearchSimDecisionComparisonQuery decisionQuery;
    private final ResearchFeeReadScope scope;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public ResearchSimComparisonService(StrategyExperimentService experiments, ExperimentValidationService validations,
            BacktestRunService backtests, BacktestPublishService publications, BacktestEvaluationService evaluations,
            StrategyVersionSnapshotQueryPort versions, PaperTradingRunService paperRuns,
            ObjectProvider<StrategySimRunService> sims, ObjectProvider<StrategySimAnalysisQuery> analysis,
            ObjectProvider<ContinuousSimQueryService> continuous, ResearchSimDecisionComparisonQuery decisionQuery,
            ResearchFeeReadScope scope, JdbcTemplate jdbc, ObjectMapper mapper) {
        this.experiments=experiments; this.validations=validations; this.backtests=backtests;
        this.publications=publications; this.evaluations=evaluations; this.versions=versions; this.paperRuns=paperRuns;
        this.sims=sims; this.analysis=analysis; this.continuous=continuous; this.decisionQuery=decisionQuery;
        this.scope=scope; this.jdbc=jdbc; this.mapper=mapper;
    }

    /** 所有权限、快照和经济读取共用一个有界只读快照；刷新不能补写观察或创建工作。 */
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ, timeout=20)
    public Comparison compare(UUID validationId, UUID selectedTrialId, String strategyVersionId,
            String publishId, String paperRunId, long owner) {
        requireId(strategyVersionId);
        if (publishId!=null) requireId(publishId);
        if (paperRunId!=null) requireId(paperRunId);
        var validation=validations.get(validationId,owner);
        var source=experiments.get(validation.sourceExperimentId(),owner);
        var finalTraining=validation.steps().stream().filter(s->s.number()==12).findFirst().orElse(null);
        JsonNode selected=finalTraining==null || finalTraining.selection()==null ? mapper.createObjectNode()
                : finalTraining.selection().path("selected");
        require("SUCCEEDED".equals(validation.status()) && selectedTrialId!=null
                && selectedTrialId.toString().equals(selected.path("trialId").asText())
                && strategyVersionId.equals(selected.path("versionId").asText()), "FINAL_SELECTION_MISMATCH");
        var trial=source.trials().stream().filter(t->t.trialId().equals(selectedTrialId))
                .max(Comparator.comparingInt(StrategyExperimentService.Trial::attempt)).orElseThrow(()->conflict("TRIAL_NOT_IN_EXPERIMENT"));
        require(trial.strategyVersionId().equals(strategyVersionId) && trial.comparable()
                && "SUCCEEDED".equals(trial.status()) && "SUCCEEDED".equals(trial.runStatus()), "SOURCE_TRIAL_NOT_ELIGIBLE");
        JsonNode candidate=null;
        for (JsonNode c:validation.frozenPolicy().path("candidates"))
            if (selectedTrialId.toString().equals(c.path("sourceTrialId").asText())) candidate=c;
        require(candidate!=null && strategyVersionId.equals(candidate.path("strategyVersionId").asText())
                && trial.parameters().equals(candidate.path("parameters")), "VALIDATION_CANDIDATE_IDENTITY_MISMATCH");
        var sourceRun=backtests.getByBacktestRunId(trial.backtestRunId());
        var strategy=SpotTargetStrategy.fromSnapshot(sourceRun.strategyVersionSnapshotJson(),mapper);
        var version=versions.findById(strategyVersionId).orElseThrow(()->conflict("STRATEGY_VERSION_UNAVAILABLE"));
        require(strategyVersionId.equals(sourceRun.strategyVersionId())
                && strategyVersionId.equals(strategy.strategyVersionId())
                && strategy.checksum().matches("[0-9a-f]{64}") && strategy.checksum().equals(version.checksum())
                && trial.parameters().equals(read(sourceRun.paramSnapshotJson()))
                && trial.parameters().equals(read(version.paramSnapshotJson())), "STRATEGY_VERSION_IDENTITY_MISMATCH");
        require(child(read(sourceRun.strategyVersionSnapshotJson()),"paramSnapshot").equals(trial.parameters())
                && child(read(sourceRun.strategyVersionSnapshotJson()),"configSnapshot").equals(read(version.configSnapshotJson()))
                && child(read(sourceRun.strategyVersionSnapshotJson()),"sourceSnapshot").equals(read(version.sourceSnapshotJson())),
                "STRATEGY_SNAPSHOT_MISMATCH");
        var periods=researchPeriods(validation);
        String configId=sourceRun.backtestConfigId(), runId=sourceRun.backtestRunId();
        if (publishId==null) {
            require(paperRunId==null,"PUBLISH_REQUIRED_FOR_SIM");
            return result(validationId,selectedTrialId,strategy,publishId,null,configId,runId,periods,
                    unavailableObservation("NO_PUBLISH_SELECTED"), ResearchSimDecisionComparisonQuery.unavailable("NO_SIM_SELECTED"));
        }
        // 所有者来自服务端认证；私有费用来源不能因绕开HTTP仓储上下文而泄漏。
        require(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM backtest_publish_records p WHERE p.publish_record_id=? AND "
                +scope.backtest("p.backtest_run_id",owner)+")",Boolean.class,publishId)),"PUBLISH_NOT_ACCESSIBLE");
        var publish=publications.getByPublishRecordId(publishId);
        require(publish.publishStatus()==PublishStatus.SUCCEEDED && strategyVersionId.equals(publish.strategyVersionId()),"PUBLISH_VERSION_MISMATCH");
        var sim=sims.getIfAvailable();
        if (sim==null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"SIM_QUERY_UNAVAILABLE");
        BacktestRun published=sim.readPublishedInput(publishId);
        JsonNode execution=read(published.configSnapshotJson()).path("executionSpec");
        require(published.status()==BacktestRunStatus.SUCCEEDED && !execution.has("windowExecution")
                && !execution.has("costStress"),"FULL_WINDOW_BACKTEST_REQUIRED");
        requireSnapshot(sourceRun.strategyVersionSnapshotJson(),published.strategyVersionSnapshotJson(),strategy);
        requireSnapshot(sourceRun.strategyVersionSnapshotJson(),publish.versionSnapshotJson(),strategy);
        require(trial.parameters().equals(read(published.paramSnapshotJson())),"PUBLISH_PARAMETERS_MISMATCH");
        var evaluation=evaluations.getByEvalReportId(publish.evalReportId()).orElseThrow(()->conflict("PUBLISH_EVALUATION_UNAVAILABLE"));
        require(published.backtestRunId().equals(evaluation.backtestRunId()) && evaluation.evaluationStatus()==EvaluationStatus.SUCCEEDED,
                "PUBLISH_EVALUATION_MISMATCH");
        JsonNode summary=read(published.summaryJson());
        require("OKX".equals(summary.path("exchangeCode").asText()) && "BTC-USDT".equals(summary.path("symbol").asText())
                && "1h".equals(summary.path("interval").asText()),"CONTINUOUS_SIM_SCOPE_INVALID");
        // 数据库种子仅保留既有注册Demo运行例外；普通研究窗口不能凭完成WF获得准入。
        boolean publicInput="public-capture".equals(summary.path("datasetProvider").asText());
        boolean demo=TenantContext.requireCurrent().kind()==TenantKind.DEMO;
        require(publicInput || demo && paperRunId!=null,"CONTINUOUS_SIM_SOURCE_NOT_ELIGIBLE");
        configId=published.backtestConfigId(); runId=published.backtestRunId();
        if (paperRunId==null) return result(validationId,selectedTrialId,strategy,publishId,null,configId,runId,periods,
                unavailableObservation("NO_SIM_SELECTED"), ResearchSimDecisionComparisonQuery.unavailable("NO_SIM_SELECTED"));
        var query=analysis.getIfAvailable(); var cursorQuery=continuous.getIfAvailable();
        if (query==null || cursorQuery==null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"SIM_QUERY_UNAVAILABLE");
        var identity=query.readIdentity(owner,paperRunId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"SIM_RUN_NOT_FOUND"));
        require(publishId.equals(identity.publishId()) && strategyVersionId.equals(identity.strategyVersionId()),"SIM_PUBLISH_IDENTITY_MISMATCH");
        var paper=paperRuns.getById(paperRunId);
        require(paper.canonicalAccountId()!=null && paper.canonicalAccountId()==identity.canonicalAccountId()
                && "SIM".equals(paper.tradeEnv()) && "OKX".equals(paper.exchangeCode()) && "SPOT".equals(paper.marketType())
                && "BTC-USDT".equals(paper.symbol()) && "1h".equals(paper.intervalCode()),"SIM_ACCOUNT_OR_SCOPE_MISMATCH");
        requireSnapshot(sourceRun.strategyVersionSnapshotJson(),paper.strategyVersionSnapshotJson(),strategy);
        require(trial.parameters().equals(read(paper.paramSnapshotJson()))
                && read(published.datasetSnapshotJson()).equals(read(paper.datasetSnapshotJson())),"SIM_FROZEN_INPUT_MISMATCH");
        var cursor=cursorQuery.find(paperRunId);
        require(cursor!=null,"CONTINUOUS_SIM_REQUIRED");
        // 原SIM只读准入复核包括来源、注册账户与冻结成本；不调用start/advance/facts。
        sim.continuousSeed(paperRunId);
        var performance=query.readPerformance(owner,paperRunId,null,null).orElseThrow();
        var metrics=query.readMetrics(owner,paperRunId).orElseThrow();
        Period forward=forwardPeriod(strategyVersionId,performance,metrics);
        periods.add(forward);
        boolean complete="AVAILABLE".equals(forward.status()) && performance.tradesAfterCoverage()==0 && !"STALLED".equals(cursor.status())
                && cursor.gapStartBar()==null && cursor.consecutivePollFailures()==0;
        var observation=new Observation("STOPPED".equals(cursor.status())?"SIM_STOPPED":"SIM_RUNNING",
                complete?"OBSERVATION_AVAILABLE":"OBSERVATION_INCOMPLETE",
                complete?null:Objects.toString(cursor.blockReason(),performance.status()),performance.coverageStart(),performance.coverageEnd(),cursor);
        var decisions=decisionQuery.read(paperRunId,identity.canonicalAccountId(),strategy,metrics.decisionCount());
        return result(validationId,selectedTrialId,strategy,publishId,paperRunId,configId,runId,periods,observation,decisions);
    }

    private List<Period> researchPeriods(ExperimentValidationService.View validation) {
        var periods=new ArrayList<Period>();
        for (var step:validation.steps()) {
            String phase=switch(step.role()) {case "IN_SAMPLE"->"TRAINING";case "OUT_OF_SAMPLE"->"WALK_FORWARD_OOS";
                case "FINAL_HOLDOUT"->"FINAL_HOLDOUT";default->null;};
            if (phase==null) continue;
            var results=validation.results().stream().filter(r->r.step()==step.number()).toList();
            JsonNode window=validation.frozenPolicy().path("steps").get(step.number());
            Instant from=window==null?null:instant(window.path("startTime"));
            Instant to=window==null?null:instant(window.path("endTimeExclusive"));
            if (results.isEmpty()) periods.add(new Period(phase,step.fold(),null,"UNAVAILABLE","EVALUATION_NOT_AVAILABLE",from,to,
                    null,null,null,null,"USDT","UNAVAILABLE",null,null,null,null));
            for (var r:results) {
                boolean hidden="FINAL_HOLDOUT".equals(phase) && validation.holdoutViews().isEmpty();
                boolean available=!hidden && "SUCCEEDED".equals(r.status()) && r.evaluationId()!=null
                        && r.netPnl()!=null && r.totalReturn()!=null && r.maxDrawdown()!=null;
                JsonNode full=r.researchValidity()==null?mapper.createObjectNode():r.researchValidity().path("full");
                String fee=full.path("fee").isNull() || full.path("fee").isMissingNode()?null:full.path("fee").asText();
                periods.add(new Period(phase,step.fold(),r.strategyVersionId(),available?"AVAILABLE":"UNAVAILABLE",
                        available?null:hidden?"HOLDOUT_NOT_EXPLICITLY_VIEWED":Objects.toString(r.failureReason(),"EVALUATION_NOT_AVAILABLE"),from,to,
                        available?r.netPnl():null,available?r.totalReturn():null,available?r.maxDrawdown():null,
                        available?fee:null,"USDT",available && fee!=null?"AVAILABLE":"UNAVAILABLE",
                        available && r.tradeCount()!=null?r.tradeCount().longValue():null,r.backtestConfigId(),r.backtestRunId(),r.evaluationId()));
            }
        }
        return periods;
    }

    static Period forwardPeriod(String version,StrategySimPerformanceQuery.View performance,StrategySimAnalysisQuery.Metrics metrics) {
        boolean available="AVAILABLE".equals(performance.status());
        boolean sameCoverage=available && performance.tradesAfterCoverage()==0;
        // 费用只在每笔成交都有同一计价币费用事实时汇总；混币或缺失不补零。
        String fee=null;
        if (sameCoverage && metrics.tradeCount()==0) fee="0";
        else if (sameCoverage && metrics.fees().size()==1) {
            var f=metrics.fees().getFirst();
            if ("USDT".equals(f.currency()) && f.tradeCount()==f.measuredFeeCount()) fee=f.totalFee();
        }
        return new Period("CONTINUOUS_SIM_FORWARD",null,version,available?(sameCoverage?"AVAILABLE":"NOT_COMPARABLE"):"UNAVAILABLE",
                available?(sameCoverage?null:"EXECUTIONS_OUTSIDE_VALUATION_COVERAGE"):performance.status(),performance.coverageStart(),performance.coverageEnd(),
                available?decimal(performance.pnl()):null,available?decimal(performance.returnRatio()):null,
                available?decimal(performance.maxDrawdownRatio()):null,fee,"USDT",
                fee==null?"UNAVAILABLE":"AVAILABLE",sameCoverage?metrics.tradeCount():null,null,null,null);
    }

    private void requireSnapshot(String expected,String actual,SpotTargetStrategy strategy) {
        var a=read(expected); var b=read(actual);
        require(strategy.sameFrozenStrategy(SpotTargetStrategy.fromSnapshot(actual,mapper))
                && child(a,"paramSnapshot").equals(child(b,"paramSnapshot"))
                && child(a,"configSnapshot").equals(child(b,"configSnapshot"))
                && child(a,"sourceSnapshot").equals(child(b,"sourceSnapshot")),"STRATEGY_SNAPSHOT_MISMATCH");
    }
    private JsonNode child(JsonNode n,String field) {
        JsonNode value=n.has(field)?n.path(field):n.path(field+"Json");
        return value.isTextual()?read(value.asText()):value;
    }
    private JsonNode read(String json) {try {return mapper.readTree(json);} catch(Exception e) {throw conflict("FROZEN_JSON_INVALID");}}
    private static Instant instant(JsonNode n) {return n.isTextual()?Instant.parse(n.asText()):null;}
    private static String decimal(BigDecimal n) {return n==null?null:n.toPlainString();}
    private static void requireId(String id) {if(id==null || id.isBlank() || id.length()>128) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_COMPARISON_IDENTITY");}
    private static void require(boolean ok,String reason) {if(!ok) throw conflict(reason);}
    private static ResponseStatusException conflict(String reason) {return new ResponseStatusException(HttpStatus.CONFLICT,reason);}
    private static Observation unavailableObservation(String reason) {return new Observation("SIM_NOT_STARTED","OBSERVATION_INCOMPLETE",reason,null,null,null);}
    private static Comparison result(UUID validation,UUID trial,SpotTargetStrategy strategy,String publish,String paper,String config,String run,
            List<Period> periods,Observation observation,ResearchSimDecisionComparisonQuery.Decisions decisions) {
        if (periods.stream().noneMatch(p->"CONTINUOUS_SIM_FORWARD".equals(p.phase())))
            periods.add(new Period("CONTINUOUS_SIM_FORWARD",null,strategy.strategyVersionId(),"UNAVAILABLE",observation.reason(),
                    null,null,null,null,null,null,"USDT","UNAVAILABLE",null,null,null,null));
        return new Comparison(validation,trial,strategy.strategyVersionId(),strategy.checksum(),publish,paper,config,run,"NOT_COMPARABLE",
                "DIFFERENT_MARKET_PERIODS_AND_METRIC_BASES",periods,observation,decisions);
    }
    public record Period(String phase,Integer fold,String strategyVersionId,String status,String reason,Instant from,Instant to,
            String netPnl,String totalReturn,String maxDrawdown,String fee,String feeCurrency,String feeStatus,Long tradeCount,
            String backtestConfigId,String backtestRunId,String evaluationId) {}
    public record Observation(String state,String status,String reason,Instant from,Instant to,ContinuousSimRunService.Status cursor) {}
    public record Comparison(UUID validationId,UUID selectedTrialId,String strategyVersionId,String strategyChecksum,String publishId,String paperRunId,
            String backtestConfigId,String backtestRunId,String status,String reason,List<Period> performance,Observation observation,
            ResearchSimDecisionComparisonQuery.Decisions decisions) {}
}
