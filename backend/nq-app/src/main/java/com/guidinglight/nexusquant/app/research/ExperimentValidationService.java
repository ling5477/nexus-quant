package com.guidinglight.nexusquant.app.research;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.marketdata.domain.FrozenHistoricalInput;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** 时间顺序验证只编排原实验组；每个步骤仍由既有Worker原子执行与评价。 */
@Service
public class ExperimentValidationService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final StrategyExperimentService experiments;
    private final ResearchCreationOperationService operations;
    private final TransactionTemplate transaction;

    public ExperimentValidationService(JdbcTemplate jdbc,ObjectMapper mapper,StrategyExperimentService experiments,
            ResearchCreationOperationService operations,PlatformTransactionManager manager) {
        this.jdbc=jdbc;this.mapper=mapper;this.experiments=experiments;this.operations=operations;
        transaction=new TransactionTemplate(manager);transaction.setTimeout(20);
    }

    public JsonNode preview(UUID source,long owner) {
        var experiment=experiments.get(source,owner);
        return policy(experiment);
    }

    private ObjectNode policy(StrategyExperimentService.View source) {
        ObjectNode policy=mapper.createObjectNode();
        policy.put("version",WalkForwardPolicy.VERSION);policy.put("selectionPolicy",WalkForwardPolicy.SELECTION);
        policy.put("finalSelectionPolicy","RETRAIN_ALL_CANDIDATES_ON_BARS_0_600_THEN_FREEZE_V1");
        policy.put("windowVersion","FLAT_WINDOW_V1");policy.put("initialPosition","FLAT");
        policy.put("initialCapital",source.frozenInput().path("initialCapital").asText());
        policy.put("warmupPolicy","HISTORY_ONLY_RESET_STATE_AT_START");policy.put("warmupTrading","DISABLED");
        policy.put("executionTiming","NEXT_OPEN_STRICTLY_AFTER_AVAILABLE_AT");
        policy.put("oosAggregation","ARITHMETIC_MEAN_OF_INDEPENDENT_RESET_RETURNS_NOT_PORTFOLIO_RETURN");
        policy.put("limitation","SHORT_SAMPLE_30_DAYS_NOT_LONG_TERM_ROBUSTNESS_OR_LIVE_AUTHORITY");
        policy.put("holdoutExposure","SOURCE_EXPERIMENT_ALREADY_EVALUATED_FULL_DATASET_NOT_PRISTINE");
        policy.put("datasetSha256",source.frozenInput().path("datasetSnapshot").path("frozenInput").path("sha256").asText());
        var bars=FrozenHistoricalInput.read(source.frozenInput().path("datasetSnapshot"),mapper);
        policy.put("barCount",bars.size());
        policy.set("candidates",mapper.valueToTree(latest(source).stream().map(t->Map.of("ordinal",t.ordinal(),
                "sourceTrialId",t.trialId().toString(),"strategyVersionId",t.strategyVersionId(),"parameters",t.parameters())).toList()));
        var windows=policy.putArray("steps");
        for(int step=0;step<16;step++) {
            var window=WalkForwardPolicy.window(step);
            ObjectNode row=mapper.valueToTree(window);row.put("barCount",window.endBar()-window.startBar());
            if(window.endBar()<=bars.size()) {
                row.put("startTime",bars.get(window.startBar()).openTime().toString());
                row.put("endTimeExclusive",bars.get(window.endBar()-1).openTime().plusSeconds(3600).toString());
            } else row.put("status","INSUFFICIENT_DATA");
            windows.add(row);
        }
        var costs=policy.putObject("costHypotheses");
        for(int n:List.of(2,3)) {
            var spec=WalkForwardPolicy.execution(source.frozenInput().path("executionSpec"),WalkForwardPolicy.window(n),mapper);
            if(spec==null) costs.put(WalkForwardPolicy.window(n).role(),"UNAVAILABLE");
            else costs.set(WalkForwardPolicy.window(n).role(),spec.path("costStress"));
        }
        return policy;
    }

    public View create(UUID source,UUID operation,long owner) {
        ObjectNode input=mapper.createObjectNode();input.put("sourceExperimentId",source.toString());input.put("version",WalkForwardPolicy.VERSION);
        var receipt=operations.execute(operation,owner,"EXPERIMENT_VALIDATION",source.toString(),input,()-> {
            experiments.get(source,owner);
            jdbc.queryForObject("SELECT experiment_id FROM strategy_experiments WHERE experiment_id=? AND owner_user_id=? FOR UPDATE",UUID.class,source,owner);
            var experiment=experiments.get(source,owner);
            if(!"SUCCEEDED".equals(experiment.status()) || experiment.frozenInput().has("validationId"))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"completed source experiment required");
            if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM experiment_validations WHERE source_experiment_id=? AND status='RUNNING')",Boolean.class,source)))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"validation already running");
            ObjectNode policy=policy(experiment);
            UUID id=UUID.randomUUID();
            boolean sufficient=policy.path("barCount").asInt()==720;
            jdbc.update("INSERT INTO experiment_validations(validation_id,owner_user_id,source_experiment_id,frozen_policy,status,failure_reason) VALUES(?,?,?,?::jsonb,?,?)",
                    id,owner,source,policy.toString(),sufficient?"RUNNING":"INSUFFICIENT_DATA",sufficient?null:"EXACTLY_720_COMPLETE_HOURLY_BARS_REQUIRED");
            if(sufficient) {
                seed(id,0,experiment,latest(experiment));
                jdbc.update("UPDATE strategy_experiments SET active=true WHERE experiment_id=?",source);
            }
            return new ResearchCreationOperationService.Result(id.toString(),id.toString());
        });
        return get(UUID.fromString(receipt.objectId()),owner);
    }

    private List<StrategyExperimentService.Trial> latest(StrategyExperimentService.View source) {
        return source.trials().stream().filter(t->source.trials().stream().noneMatch(o->o.trialId().equals(t.trialId()) && o.attempt()>t.attempt()))
                .sorted(Comparator.comparingInt(StrategyExperimentService.Trial::ordinal)).toList();
    }

    private void seed(UUID validation,int step,StrategyExperimentService.View source,List<StrategyExperimentService.Trial> candidates) {
        var window=WalkForwardPolicy.window(step);
        var spec=WalkForwardPolicy.execution(source.frozenInput().path("executionSpec"),window,mapper);
        UUID child=spec==null?null:experiments.createValidationExperiment(source,candidates,spec,validation);
        jdbc.update("INSERT INTO experiment_validation_steps(validation_id,step_number,fold_number,role,start_bar,end_bar,experiment_id,unavailable_reason) VALUES(?,?,?,?,?,?,?,?)",
                validation,step,window.fold(),window.role(),window.startBar(),window.endBar(),child,spec==null?"COST_INPUT_NOT_COMPARABLE":null);
    }

    /** 由同一个EXPERIMENT工作身份推动；等待子实验时不占用执行锁或创建第二个调度器。 */
    boolean advanceForSource(UUID source) {
        return Boolean.TRUE.equals(transaction.execute(s->{
            var ids=jdbc.queryForList("SELECT validation_id FROM experiment_validations WHERE source_experiment_id=? AND status='RUNNING' FOR UPDATE",UUID.class,source);
            if(ids.isEmpty()) return false;
            UUID id=ids.getFirst();
            Header h=header(id,null);
            var sourceView=experiments.get(source,h.owner());
            var steps=steps(id);var current=steps.getLast();
            StrategyExperimentService.View child=current.experimentId()==null?null:experiments.get(current.experimentId(),h.owner());
            if(child!=null && List.of("QUEUED","RUNNING").contains(child.status())) return false;
            if(child!=null && "OUTCOME_UNKNOWN".equals(child.status())) {terminal(id,"OUTCOME_UNKNOWN","RUN_STATE_REQUIRES_RECONCILIATION");return true;}
            if(current.number()%4==0) {
                if(current.selection()==null) {
                    var scores=new ArrayList<WalkForwardPolicy.Score>();
                    for(var t:latest(child)) {
                        var origin=latest(sourceView).stream().filter(o->o.strategyVersionId().equals(t.strategyVersionId())).findFirst().orElseThrow();
                        int lookback=origin.parameters().path("window").asInt(origin.parameters().path("entryLookbackBars").asInt())+1;
                        if("SUCCEEDED".equals(t.status()) && t.comparable() && current.endBar()-lookback>=2
                                && t.metrics().hasNonNull("totalReturn") && t.metrics().hasNonNull("maxDrawdown"))
                            scores.add(new WalkForwardPolicy.Score(origin.ordinal(),origin.trialId().toString(),origin.strategyVersionId(),t.backtestRunId(),t.evaluationId(),
                                    new BigDecimal(t.metrics().path("totalReturn").asText()),new BigDecimal(t.metrics().path("maxDrawdown").asText())));
                    }
                    var ranked=WalkForwardPolicy.rank(scores);
                    ObjectNode selection=mapper.createObjectNode();selection.put("policy",WalkForwardPolicy.SELECTION);
                    selection.set("rankings",mapper.valueToTree(ranked));
                    if(!ranked.isEmpty()) selection.set("selected",mapper.valueToTree(ranked.getFirst()));
                    jdbc.update("UPDATE experiment_validation_steps SET selection=?::jsonb,selected_at=clock_timestamp() WHERE validation_id=? AND step_number=? AND selection IS NULL",selection.toString(),id,current.number());
                    if(ranked.isEmpty()) {terminal(id,"NO_ELIGIBLE_CANDIDATE","NO_VALID_TRAINING_RESULT");return true;}
                }
            } else if(current.number()%4==1 && child!=null && !"SUCCEEDED".equals(child.status())) {
                terminal(id,"FAILED","INDEPENDENT_EVALUATION_FAILED");return true;
            }
            if(current.number()==15) {terminal(id,"SUCCEEDED",null);return true;}
            int next=current.number()+1;
            List<StrategyExperimentService.Trial> candidates=latest(sourceView);
            if(next%4!=0) {
                JsonNode selected=steps(id).stream().filter(x->x.number()==next/4*4).findFirst().orElseThrow().selection().path("selected");
                candidates=candidates.stream().filter(t->t.trialId().toString().equals(selected.path("trialId").asText())).toList();
                if(candidates.size()!=1) throw new IllegalStateException("FROZEN_SELECTION_IDENTITY_MISMATCH");
            }
            seed(id,next,sourceView,candidates);
            return true;
        }));
    }

    private void terminal(UUID id,String status,String reason) {
        jdbc.update("UPDATE experiment_validations SET status=?,failure_reason=? WHERE validation_id=?",status,reason,id);
    }

    public View cancel(UUID id,long owner) {
        transaction.executeWithoutResult(s->{
            Header h=header(id,owner);
            // 与编排领取共享源锁；当前执行事务可以完成，取消只影响尚未领取的经济事实。
            jdbc.queryForObject("SELECT experiment_id FROM strategy_experiments WHERE experiment_id=? FOR UPDATE",UUID.class,h.source());
            jdbc.queryForObject("SELECT validation_id FROM experiment_validations WHERE validation_id=? FOR UPDATE",UUID.class,id);
            if(!"RUNNING".equals(header(id,owner).status())) return;
            terminal(id,"CANCELLED","USER_CANCELLED");
            jdbc.update("UPDATE strategy_experiments SET cancel_requested=true WHERE experiment_id IN (SELECT experiment_id FROM experiment_validation_steps WHERE validation_id=?)",id);
            jdbc.update("UPDATE strategy_experiment_attempts a SET status='CANCELLED' FROM strategy_experiment_trials t,experiment_validation_steps s WHERE a.trial_id=t.trial_id AND t.experiment_id=s.experiment_id AND s.validation_id=? AND a.status='QUEUED'",id);
        });
        return get(id,owner);
    }

    public List<Summary> list(UUID source,long owner) {
        experiments.get(source,owner);
        return jdbc.query("SELECT validation_id,status,failure_reason,created_at FROM experiment_validations WHERE source_experiment_id=? AND owner_user_id=? ORDER BY created_at DESC,validation_id DESC LIMIT 20",
                (r,n)->new Summary(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getTimestamp(4).toInstant()),source,owner);
    }

    private Header header(UUID id,Long owner) {
        var values=jdbc.query("SELECT source_experiment_id,owner_user_id,frozen_policy::text,status,failure_reason,created_at FROM experiment_validations WHERE validation_id=?"+(owner==null?"":" AND owner_user_id=?"),
                (r,n)->new Header(r.getObject(1,UUID.class),r.getLong(2),experiments.read(r.getString(3)),r.getString(4),r.getString(5),r.getTimestamp(6).toInstant()),
                owner==null?new Object[]{id}:new Object[]{id,owner});
        return values.stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"validation not found"));
    }
    private List<Step> steps(UUID id) {
        return jdbc.query("SELECT step_number,fold_number,role,start_bar,end_bar,experiment_id,unavailable_reason,selection::text,selected_at FROM experiment_validation_steps WHERE validation_id=? ORDER BY step_number LIMIT 16",
                (r,n)->new Step(r.getInt(1),r.getInt(2),r.getString(3),r.getInt(4),r.getInt(5),r.getObject(6,UUID.class),r.getString(7),r.getString(8)==null?null:experiments.read(r.getString(8)),r.getTimestamp(9)==null?null:r.getTimestamp(9).toInstant()),id);
    }

    public View get(UUID id,long owner) {
        Header h=header(id,owner);
        List<Result> results=jdbc.query("""
            SELECT s.step_number,t.ordinal,t.strategy_version_id,a.backtest_config_id,a.backtest_run_id,a.status,a.failure_reason,
              e.eval_report_id,e.evaluated_at,e.total_return::text,e.net_pnl::text,e.max_drawdown_rate::text,e.trade_count,
              ((e.report_json->'researchValidity')-'identity')::text,(r.summary_json-ARRAY['consumedBars','datasetSnapshot','researchFacts','decisionReasons'])::text,r.config_snapshot_json::text
            FROM experiment_validation_steps s JOIN strategy_experiment_trials t ON t.experiment_id=s.experiment_id
            JOIN strategy_experiment_attempts a USING(trial_id) JOIN backtest_runs r ON r.backtest_run_id=a.backtest_run_id
            LEFT JOIN LATERAL (SELECT * FROM backtest_eval_reports p WHERE p.backtest_run_id=a.backtest_run_id ORDER BY p.evaluated_at DESC,p.eval_report_id DESC LIMIT 1) e ON true
            WHERE s.validation_id=? ORDER BY s.step_number,t.ordinal LIMIT 76
            """,(r,n)->new Result(r.getInt(1),r.getInt(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6),r.getString(7),r.getString(8),
                    r.getTimestamp(9)==null?null:r.getTimestamp(9).toInstant(),r.getString(10),r.getString(11),r.getString(12),(Integer)r.getObject(13),
                    r.getString(14)==null?null:experiments.read(r.getString(14)),experiments.read(r.getString(15)),experiments.read(r.getString(16)).path("executionSpec")),id);
        var views=jdbc.query("SELECT view_id,viewed_at FROM experiment_holdout_views WHERE validation_id=? ORDER BY viewed_at DESC LIMIT 50",(r,n)->new HoldoutView(r.getObject(1,UUID.class),r.getTimestamp(2).toInstant()),id);
        long uses=jdbc.queryForObject("SELECT count(*) FROM experiment_validations WHERE frozen_policy->>'datasetSha256'=? AND owner_user_id=?",Long.class,h.policy().path("datasetSha256").asText(),owner);
        var definitions=steps(id);
        return new View(id,h.source(),h.status(),h.reason(),h.created(),h.policy(),definitions,results,stability(results,definitions,h.policy()),costSensitivity(results,definitions),views,uses);
    }

    private JsonNode costSensitivity(List<Result> results,List<Step> steps) {
        var report=mapper.createArrayNode();
        for(var step:steps) if(step.number()%4>=2) {
            ObjectNode row=report.addObject();row.put("fold",step.fold());row.put("scenario",step.role());
            var base=results.stream().filter(r->r.step()==step.number()/4*4+1).findFirst().orElse(null);
            var stress=results.stream().filter(r->r.step()==step.number()).findFirst().orElse(null);
            if(base==null || stress==null || !"SUCCEEDED".equals(base.status()) || !"SUCCEEDED".equals(stress.status())
                    || base.totalReturn()==null || stress.totalReturn()==null) {
                row.put("status","UNAVAILABLE");row.put("reason",step.unavailableReason()!=null?step.unavailableReason():stress!=null && stress.failureReason()!=null?stress.failureReason():"EVALUATION_NOT_COMPLETE");continue;
            }
            row.put("status","AVAILABLE");row.set("assumption",stress.executionSpec().path("costStress"));
            row.put("baselineRunId",base.backtestRunId());row.put("stressRunId",stress.backtestRunId());
            row.put("baselineNetPnl",base.netPnl());row.put("stressNetPnl",stress.netPnl());
            row.put("returnChange",new BigDecimal(stress.totalReturn()).subtract(new BigDecimal(base.totalReturn())).toPlainString());
            row.put("maxDrawdownChange",new BigDecimal(stress.maxDrawdown()).subtract(new BigDecimal(base.maxDrawdown())).toPlainString());
            row.put("tradeCountChange",stress.tradeCount()-base.tradeCount());
            row.set("benchmarkRelative",stress.researchValidity().path("strategyVsBenchmarkDifference"));
        }
        return report;
    }

    private JsonNode stability(List<Result> results,List<Step> steps,JsonNode policy) {
        ObjectNode report=mapper.createObjectNode();
        var rankings=report.putArray("rankChanges");
        for(JsonNode candidate:policy.path("candidates")) {
            ObjectNode row=rankings.addObject();row.set("candidate",candidate);
            var ranks=row.putArray("foldRanks");
            for(var step:steps) if(step.role().equals("IN_SAMPLE") && step.selection()!=null) {
                int rank=0,position=0;
                for(JsonNode score:step.selection().path("rankings")) {position++;if(score.path("ordinal").asInt()==candidate.path("ordinal").asInt())rank=position;}
                ObjectNode value=ranks.addObject();value.put("fold",step.fold());
                if(rank>0)value.put("rank",rank);else value.put("status","UNAVAILABLE");
            }
        }
        var changes=report.putArray("selectedCandidates");
        var neighbours=report.putArray("adjacentCandidateDifferences");
        for(var step:steps) if(step.selection()!=null) {
            ObjectNode selected=changes.addObject();selected.put("fold",step.fold());selected.set("selected",step.selection().path("selected"));
            for(JsonNode a:policy.path("candidates")) for(JsonNode b:policy.path("candidates")) {
                if(a.path("ordinal").asInt()>=b.path("ordinal").asInt() || !neighbour(a.path("parameters"),b.path("parameters"),policy.path("candidates")))continue;
                JsonNode sa=null,sb=null;
                for(JsonNode score:step.selection().path("rankings")) {
                    if(score.path("ordinal").asInt()==a.path("ordinal").asInt())sa=score;
                    if(score.path("ordinal").asInt()==b.path("ordinal").asInt())sb=score;
                }
                ObjectNode pair=neighbours.addObject();pair.put("fold",step.fold());pair.put("a",a.path("ordinal").asInt());pair.put("b",b.path("ordinal").asInt());
                if(sa!=null && sb!=null)pair.put("trainingReturnDifferenceBMinusA",new BigDecimal(sb.path("totalReturn").asText()).subtract(new BigDecimal(sa.path("totalReturn").asText())).toPlainString());
                else pair.put("status","UNAVAILABLE");
            }
        }
        var oos=results.stream().filter(r->r.step()%4==1 && r.step()<12 && "SUCCEEDED".equals(r.status()) && r.totalReturn()!=null).toList();
        report.put("completedOosFolds",oos.size());
        if(oos.size()==3) {
            BigDecimal mean=oos.stream().map(r->new BigDecimal(r.totalReturn())).reduce(BigDecimal.ZERO,BigDecimal::add).divide(BigDecimal.valueOf(3),18,java.math.RoundingMode.HALF_UP);
            report.put("meanIndependentOosReturn",mean.toPlainString());
            BigDecimal total=oos.stream().map(r->new BigDecimal(r.totalReturn()).abs()).reduce(BigDecimal.ZERO,BigDecimal::add);
            if(total.signum()>0) {
                BigDecimal share=oos.stream().map(r->new BigDecimal(r.totalReturn()).abs()).max(BigDecimal::compareTo).orElseThrow().divide(total,18,java.math.RoundingMode.HALF_UP);
                report.put("largestWindowAbsoluteReturnShare",share.toPlainString());
                report.put("windowDependence",share.compareTo(new BigDecimal("0.8"))>=0?"HIGH_SINGLE_WINDOW_DEPENDENCE":"NO_80_PERCENT_WINDOW_CONCENTRATION");
            }
            else report.put("windowDependence","UNAVAILABLE_ZERO_ABSOLUTE_RETURNS");
        } else report.put("status","UNAVAILABLE_INCOMPLETE_OOS_FOLDS");
        return report;
    }

    private boolean neighbour(JsonNode a,JsonNode b,JsonNode candidates) {
        String axis=null;
        for(var fields=a.fieldNames();fields.hasNext();) {
            String key=fields.next();
            if(new BigDecimal(a.path(key).asText()).compareTo(new BigDecimal(b.path(key).asText()))!=0) {
                if(axis!=null)return false;axis=key;
            }
        }
        if(axis==null)return false;
        BigDecimal low=new BigDecimal(a.path(axis).asText()).min(new BigDecimal(b.path(axis).asText()));
        BigDecimal high=new BigDecimal(a.path(axis).asText()).max(new BigDecimal(b.path(axis).asText()));
        for(JsonNode c:candidates) {BigDecimal v=new BigDecimal(c.path("parameters").path(axis).asText());if(v.compareTo(low)>0&&v.compareTo(high)<0)return false;}
        return true;
    }

    public View revealHoldout(UUID id,UUID view,long owner) {
        transaction.executeWithoutResult(s->{
            header(id,owner);
            if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM experiment_validation_steps WHERE validation_id=? AND role='FINAL_HOLDOUT')",Boolean.class,id)))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"holdout not evaluated");
            var existing=jdbc.queryForList("SELECT validation_id FROM experiment_holdout_views WHERE view_id=?",UUID.class,view);
            if(!existing.isEmpty() && !existing.getFirst().equals(id)) throw new ResponseStatusException(HttpStatus.CONFLICT,"view identity conflict");
            jdbc.update("INSERT INTO experiment_holdout_views(view_id,validation_id) VALUES(?,?) ON CONFLICT(view_id) DO NOTHING",view,id);
        });
        return get(id,owner);
    }

    private record Header(UUID source,long owner,JsonNode policy,String status,String reason,Instant created) {}
    public record Step(int number,int fold,String role,int startBar,int endBar,UUID experimentId,String unavailableReason,JsonNode selection,Instant selectedAt) {}
    public record Result(int step,int ordinal,String strategyVersionId,String backtestConfigId,String backtestRunId,String status,String failureReason,
            String evaluationId,Instant evaluatedAt,String totalReturn,String netPnl,String maxDrawdown,Integer tradeCount,JsonNode researchValidity,JsonNode summary,JsonNode executionSpec) {}
    public record HoldoutView(UUID viewId,Instant viewedAt) {}
    public record Summary(UUID validationId,String status,String failureReason,Instant createdAt) {}
    public record View(UUID validationId,UUID sourceExperimentId,String status,String failureReason,Instant createdAt,JsonNode frozenPolicy,
            List<Step> steps,List<Result> results,JsonNode parameterStability,JsonNode costSensitivity,List<HoldoutView> holdoutViews,long datasetValidationUses) {}
}
