package com.guidinglight.nexusquant.app.research;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.common.tenant.TenantContext;
import com.guidinglight.nexusquant.marketdata.application.service.MarketdataDatasetService;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.research.application.BacktestRunService;
import com.guidinglight.nexusquant.research.application.ResearchConfigService;
import com.guidinglight.nexusquant.research.application.backtest.command.BacktestConfigCreateRequest;
import com.guidinglight.nexusquant.research.application.backtest.command.BacktestRunStartRequest;
import com.guidinglight.nexusquant.research.application.config.BacktestConfigService;
import com.guidinglight.nexusquant.strategy.application.StrategyVersionService;
import com.guidinglight.nexusquant.strategy.application.command.StrategyVersionCreateRequest;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** 实验只拥有编排身份；版本、配置、Run 和评价仍由原正式服务创建。 */
@Service
public class StrategyExperimentService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final ResearchCreationOperationService operations;
    private final BacktestConfigService configs;
    private final BacktestRunService runs;
    private final ResearchConfigService research;
    private final StrategyVersionService versions;
    private final MarketdataDatasetService datasets;
    private final TransactionTemplate transaction;

    public StrategyExperimentService(JdbcTemplate jdbc, ObjectMapper mapper,
            ResearchCreationOperationService operations, BacktestConfigService configs,
            BacktestRunService runs, ResearchConfigService research, StrategyVersionService versions,
            MarketdataDatasetService datasets, PlatformTransactionManager manager) {
        this.jdbc=jdbc; this.mapper=mapper; this.operations=operations; this.configs=configs;
        this.runs=runs; this.research=research; this.versions=versions; this.datasets=datasets;
        transaction=new TransactionTemplate(manager); transaction.setTimeout(15);
    }

    public List<JsonNode> preview(Request request) {
        var base=configs.getByBacktestConfigId(request.templateConfigId());
        var version=versions.findById(base.strategyVersionId());
        JsonNode source=read(version.sourceSnapshotJson());
        var expanded=ExperimentParameters.expand(source.path("executable").asText(), source,
                request.candidates(), request.grid(), mapper);
        requireCandidateBudget(read(base.datasetSnapshotJson()),expanded.size());
        return expanded;
    }

    public View create(UUID operationId, Request request, long owner) {
        if (request==null || request.templateConfigId()==null || request.templateConfigId().length()>64)
            throw new IllegalArgumentException("template configuration required");
        // 原请求在同事务创建回执内比较；重放不能因后来目录或模板变化而改变原实验。
        var receipt=operations.execute(operationId,owner,"EXPERIMENT","",mapper.valueToTree(request),()-> {
            var base=configs.getByBacktestConfigId(request.templateConfigId());
            var parent=research.getByResearchConfigId(base.researchConfigId());
            var version=versions.findById(base.strategyVersionId());
            JsonNode source=read(version.sourceSnapshotJson());
            String type=source.path("executable").asText();
            List<JsonNode> parameters=ExperimentParameters.expand(type,source,request.candidates(),request.grid(),mapper);
            if (!base.isActive() || !parent.isActive() || base.datasetId()==null)
                throw new IllegalArgumentException("active bound configuration required");
            // 参数实验继承模板已经绑定的冻结版本，不能因后来共享行情变化重新捕获输入。
            JsonNode dataset=read(base.datasetSnapshotJson());
            datasets.readFrozenInput(dataset);
            requireCandidateBudget(dataset,parameters.size());
            if (!base.startTime().equals(Instant.parse(dataset.path("startTime").asText()))
                    || !base.endTime().equals(Instant.parse(dataset.path("endTime").asText())))
                throw new IllegalArgumentException("experiment must use complete frozen dataset window");
            ObjectNode frozen=mapper.createObjectNode();
            frozen.put("templateConfigId",base.backtestConfigId());
            frozen.put("researchConfigId",base.researchConfigId());
            frozen.put("strategyCode",version.strategyCode());
            frozen.put("startTime",base.startTime().toString());
            frozen.put("endTime",base.endTime().toString());
            frozen.put("initialCapital",base.initialCapital().stripTrailingZeros().toPlainString());
            frozen.set("executionSpec",read(base.executionSpec()));
            frozen.set("requestedExecutionSpec",read(base.executionSpec()));
            frozen.set("evaluationSpec",read(base.evaluationSpec()));
            frozen.set("datasetSnapshot",dataset);
            frozen.set("sourceSnapshot",source);
            frozen.set("configSnapshot",read(version.configSnapshotJson()));
            frozen.set("parameterCandidates",mapper.valueToTree(parameters));
            frozen.put("maxTrials",dataset.path("barCount").asInt()>720?4:16);
            frozen.put("maxConcurrentRuns",1); frozen.put("maxAttempts",3);
            UUID id=UUID.randomUUID();
            jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,"experiment-version:"+version.strategyCode());
            int ordinal=0;
            var seeds=new ArrayList<TrialSeed>();
            for (JsonNode parameter:parameters) {
                var created=versions.create(new StrategyVersionCreateRequest(version.strategyCode(),
                        "Experiment "+id+" / "+(++ordinal),"DRAFT",parameter.toString(),
                        version.configSnapshotJson(),version.sourceSnapshotJson(),"experiment:"+owner));
                UUID trial=UUID.randomUUID();
                var attempt=prepareAttempt(trial,1,created.strategyVersionId(),frozen);
                if(!frozen.has("referenceRunId")) {
                    frozen.put("referenceRunId",attempt.run());
                    frozen.set("executionSpec",read(runs.getByBacktestRunId(attempt.run()).configSnapshotJson()).path("executionSpec"));
                }
                seeds.add(new TrialSeed(trial,ordinal,parameter,created.strategyVersionId(),attempt));
            }
            jdbc.update("INSERT INTO strategy_experiments(experiment_id,owner_user_id,strategy_type,frozen_input,trial_count) VALUES(?,?,?,?::jsonb,?)",
                    id,owner,type,frozen.toString(),parameters.size());
            for(var seed:seeds) {
                jdbc.update("INSERT INTO strategy_experiment_trials(trial_id,experiment_id,ordinal,parameter_identity,parameters,strategy_version_id) VALUES(?,?,?,?,?::jsonb,?)",
                        seed.id(),id,seed.ordinal(),seed.parameter().toString(),seed.parameter().toString(),seed.version());
                saveAttempt(seed.id(),1,UUID.randomUUID(),seed.attempt());
            }
            return new ResearchCreationOperationService.Result(id.toString(),id.toString());
        });
        return get(UUID.fromString(receipt.objectId()),owner);
    }

    void createAttempt(UUID trial, int attempt, UUID attemptId, String version, JsonNode frozen) {
        saveAttempt(trial,attempt,attemptId,prepareAttempt(trial,attempt,version,frozen));
    }

    private AttemptSeed prepareAttempt(UUID trial,int attempt,String version,JsonNode frozen) {
        var config=configs.copyForParameterTrial(frozen.path("templateConfigId").asText(),"Experiment "+trial+" / "+attempt);
        configs.bindStrategyVersion(config.backtestConfigId(),version);
        configs.bindDataset(config.backtestConfigId(),frozen.path("datasetSnapshot").path("datasetId").asText(),frozen.path("datasetSnapshot").toString());
        var request=new BacktestRunStartRequest(config.backtestConfigId());
        var run=frozen.has("referenceRunId") ? runs.createWithFrozenAssumptions(request,frozen.path("referenceRunId").asText()) : runs.create(request);
        // Run 在排队之前已经冻结；后台不能重新读模板、费率或行情目录生成另一份输入。
        if (frozen.has("referenceRunId") && !read(run.configSnapshotJson()).path("executionSpec").equals(frozen.path("executionSpec")))
            throw new IllegalArgumentException("experiment requires stable explicit execution assumptions");
        return new AttemptSeed(config.backtestConfigId(),run.backtestRunId());
    }
    private void saveAttempt(UUID trial,int attempt,UUID attemptId,AttemptSeed seed) {
        jdbc.update("INSERT INTO strategy_experiment_attempts(attempt_id,trial_id,attempt_number,backtest_config_id,backtest_run_id) VALUES(?,?,?,?,?)",
                attemptId,trial,attempt,seed.config(),seed.run());
    }

    /** 验证窗口只派生编排组，复用源Trial冻结版本以及原Attempt/Run执行事实。调用方持有事务。 */
    UUID createValidationExperiment(View source, List<Trial> candidates, JsonNode executionSpec, UUID validation) {
        ObjectNode frozen=source.frozenInput().deepCopy();
        frozen.set("executionSpec",executionSpec);
        frozen.set("requestedExecutionSpec",executionSpec);
        frozen.put("validationId",validation.toString());
        frozen.set("parameterCandidates",mapper.valueToTree(candidates.stream().map(Trial::parameters).toList()));
        UUID id=UUID.randomUUID();
        long owner=jdbc.queryForObject("SELECT owner_user_id FROM strategy_experiments WHERE experiment_id=?",Long.class,source.experimentId());
        jdbc.update("INSERT INTO strategy_experiments(experiment_id,owner_user_id,strategy_type,frozen_input,trial_count) VALUES(?,?,?,?::jsonb,?)",
                id,owner,source.strategyType(),frozen.toString(),candidates.size());
        int ordinal=0;
        for (Trial candidate:candidates) {
            UUID trial=UUID.randomUUID();
            var config=configs.copyForValidation(frozen.path("templateConfigId").asText(),"Validation "+validation+" / "+id,executionSpec.toString());
            configs.bindStrategyVersion(config.backtestConfigId(),candidate.strategyVersionId());
            configs.bindDataset(config.backtestConfigId(),frozen.path("datasetSnapshot").path("datasetId").asText(),frozen.path("datasetSnapshot").toString());
            var run=runs.createForValidation(new BacktestRunStartRequest(config.backtestConfigId()),source.frozenInput().path("referenceRunId").asText());
            jdbc.update("INSERT INTO strategy_experiment_trials(trial_id,experiment_id,ordinal,parameter_identity,parameters,strategy_version_id) VALUES(?,?,?,?,?::jsonb,?)",
                    trial,id,++ordinal,candidate.parameterIdentity(),candidate.parameters().toString(),candidate.strategyVersionId());
            saveAttempt(trial,1,UUID.randomUUID(),new AttemptSeed(config.backtestConfigId(),run.backtestRunId()));
        }
        return id;
    }

    public List<Summary> list(long owner, int limit, int offset) {
        if(limit<1 || limit>50 || offset<0 || offset>10000) throw new IllegalArgumentException("invalid experiment page");
        return jdbc.query("SELECT experiment_id,strategy_type,trial_count,cancel_requested,active,created_at FROM strategy_experiments WHERE owner_user_id=? AND NOT jsonb_exists(frozen_input,'validationId') ORDER BY created_at DESC,experiment_id DESC LIMIT ? OFFSET ?",
                (r,n)->new Summary(r.getObject(1,UUID.class),r.getString(2),r.getInt(3),r.getBoolean(4),r.getBoolean(5),r.getTimestamp(6).toInstant()),owner,limit,offset);
    }

    public View get(UUID id,long owner) {
        var input=jdbc.query("SELECT frozen_input::text,cancel_requested,created_at,strategy_type FROM strategy_experiments WHERE experiment_id=? AND owner_user_id=?",
                (r,n)->new Header(read(r.getString(1)),r.getBoolean(2),r.getTimestamp(3).toInstant(),r.getString(4)),id,owner)
                .stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"experiment not found"));
        // 有界连接一次返回至多48个attempt；不以页面逐条查询复制Evaluation事实。
        var trials=jdbc.query("""
            SELECT t.trial_id,t.ordinal,t.parameter_identity,t.parameters::text,t.strategy_version_id,
              a.attempt_id,a.attempt_number AS attempt,a.backtest_config_id,a.backtest_run_id,a.status,a.failure_reason,
              r.status AS run_status,r.failure_code,e.eval_report_id,e.evaluation_status,
              jsonb_build_object('netPnl',e.net_pnl::text,'totalReturn',e.total_return::text,
                'maxDrawdown',e.max_drawdown_rate::text,'sharpe',e.sharpe_ratio::text,
                'tradeCount',e.trade_count,'fee',e.total_fee::text,'slippage',e.total_slippage::text,
                'metrics',e.metrics_json || jsonb_build_object('researchValidity',e.report_json->'researchValidity')) AS metrics,
              (r.dataset_snapshot_json=?::jsonb AND r.config_snapshot_json->'executionSpec'=?::jsonb
                AND r.config_snapshot_json->>'initialCapital'=? AND r.param_snapshot_json=t.parameters) AS comparable
            FROM strategy_experiment_trials t JOIN strategy_experiment_attempts a USING(trial_id)
            JOIN backtest_runs r ON r.backtest_run_id=a.backtest_run_id
            LEFT JOIN LATERAL (SELECT report.* FROM backtest_eval_reports report WHERE report.backtest_run_id=r.backtest_run_id
                ORDER BY report.evaluated_at DESC,report.eval_report_id DESC LIMIT 1) e ON true
            WHERE t.experiment_id=? ORDER BY t.ordinal,a.attempt_number LIMIT 48
            """,(r,n)-> new Trial(r.getObject("trial_id",UUID.class),r.getInt("ordinal"),r.getString("parameter_identity"),
                read(r.getString("parameters")),r.getString("strategy_version_id"),r.getObject("attempt_id",UUID.class),r.getInt("attempt"),
                r.getString("backtest_config_id"),r.getString("backtest_run_id"),r.getString("status"),r.getString("failure_reason"),
                r.getString("run_status"),r.getString("eval_report_id"),r.getString("evaluation_status"),read(r.getString("metrics")),r.getBoolean("comparable")),
                input.frozen().path("datasetSnapshot").toString(),input.frozen().path("executionSpec").toString(),
                input.frozen().path("initialCapital").asText(),id);
        return new View(id,input.type(),status(trials,input.cancelled()),input.cancelled(),input.createdAt(),input.frozen(),trials);
    }

    public View cancel(UUID id,long owner) {
        transaction.executeWithoutResult(s->{var owned=get(id,owner); rejectManaged(owned);
            if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM experiment_validations WHERE source_experiment_id=? AND status='RUNNING')",Boolean.class,id)))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"cancel the active validation run explicitly");
            jdbc.update("UPDATE strategy_experiments SET cancel_requested=true WHERE experiment_id=?",id);
            jdbc.update("UPDATE strategy_experiment_attempts a SET status='CANCELLED' FROM strategy_experiment_trials t WHERE t.trial_id=a.trial_id AND t.experiment_id=? AND a.status='QUEUED'",id);});
        return get(id,owner);
    }

    public View retry(UUID id,UUID trial,UUID attemptId,long owner) {
        transaction.executeWithoutResult(s->{
            var owned=jdbc.queryForList("SELECT experiment_id FROM strategy_experiments WHERE experiment_id=? AND owner_user_id=? FOR UPDATE",UUID.class,id,owner);
            if(owned.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"experiment not found");
            View view=get(id,owner);
            rejectManaged(view);
            if(view.cancelRequested()) throw new ResponseStatusException(HttpStatus.CONFLICT,"experiment cancelled");
            var existing=jdbc.queryForList("SELECT trial_id FROM strategy_experiment_attempts WHERE attempt_id=?",UUID.class,attemptId);
            if(!existing.isEmpty()) {
                if(!existing.getFirst().equals(trial)) throw new ResponseStatusException(HttpStatus.CONFLICT,"attempt identity conflict");
                return;
            }
            Trial prior=view.trials().stream().filter(t->t.trialId().equals(trial)).max(Comparator.comparingInt(Trial::attempt))
                    .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"trial not found"));
            if(!prior.status().equals("FAILED") || prior.attempt()>=3)
                throw new ResponseStatusException(HttpStatus.CONFLICT,"only terminal failure may be retried, maximum three attempts");
            createAttempt(trial,prior.attempt()+1,attemptId,prior.strategyVersionId(),view.frozenInput());
            jdbc.update("UPDATE strategy_experiments SET active=true WHERE experiment_id=?",id);
        });
        return get(id,owner);
    }

    private void rejectManaged(View view) {
        if(view.frozenInput().has("validationId")) throw new ResponseStatusException(HttpStatus.CONFLICT,"validation experiment is managed by its validation run");
    }

    static String status(List<Trial> trials,boolean cancelled) {
        Map<UUID,Trial> latest=new HashMap<>();
        trials.forEach(t->latest.merge(t.trialId(),t,(a,b)->a.attempt()>b.attempt()?a:b));
        if(latest.values().stream().anyMatch(t->t.status().equals("OUTCOME_UNKNOWN"))) return "OUTCOME_UNKNOWN";
        if(latest.values().stream().anyMatch(t->t.status().equals("RUNNING"))) return "RUNNING";
        if(latest.values().stream().anyMatch(t->t.status().equals("QUEUED"))) return "QUEUED";
        long succeeded=latest.values().stream().filter(t->t.status().equals("SUCCEEDED")).count();
        if(succeeded==latest.size() && !latest.isEmpty()) return "SUCCEEDED";
        if(cancelled) return "CANCELLED";
        return succeeded>0 ? "PARTIAL_FAILURE" : "FAILED";
    }

    JsonNode read(String json) {
        try { return mapper.reader().with(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(json); }
        catch(java.io.IOException e) {throw new IllegalArgumentException("invalid experiment JSON",e);}
    }

    List<HistoricalBar> readFrozenInput(JsonNode dataset) {
        return datasets.readFrozenInput(dataset);
    }

    private static void requireCandidateBudget(JsonNode dataset,int count) {
        if(dataset.path("barCount").asInt()>720 && count>4)
            throw new IllegalArgumentException("LONG_RESEARCH_MAXIMUM_FOUR_CANDIDATES");
    }
    public record Request(String templateConfigId,JsonNode candidates,JsonNode grid) {}
    private record AttemptSeed(String config,String run) {}
    private record TrialSeed(UUID id,int ordinal,JsonNode parameter,String version,AttemptSeed attempt) {}
    private record Header(JsonNode frozen,boolean cancelled,Instant createdAt,String type) {}
    public record Summary(UUID experimentId,String strategyType,int trialCount,boolean cancelRequested,boolean active,Instant createdAt) {}
    public record View(UUID experimentId,String strategyType,String status,boolean cancelRequested,Instant createdAt,JsonNode frozenInput,List<Trial> trials) {}
    public record Trial(UUID trialId,int ordinal,String parameterIdentity,JsonNode parameters,String strategyVersionId,
            UUID attemptId,int attempt,String backtestConfigId,String backtestRunId,String status,String failureReason,
            String runStatus,String evaluationId,String evaluationStatus,JsonNode metrics,boolean comparable) {}
}
