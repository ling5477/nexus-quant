package com.guidinglight.nexusquant.app.research;

import com.guidinglight.nexusquant.common.tenant.TenantWorkAccess;
import com.guidinglight.nexusquant.research.application.BacktestRunService;
import com.guidinglight.nexusquant.research.application.backtest.BacktestExecutionService;
import com.guidinglight.nexusquant.research.application.eval.BacktestEvaluationFailedException;
import com.guidinglight.nexusquant.research.application.eval.BacktestEvaluationService;
import com.guidinglight.nexusquant.research.domain.BacktestRunStatus;
import com.guidinglight.nexusquant.scheduler.control.ScheduledJobContributor;
import com.guidinglight.nexusquant.scheduler.control.ScheduledJobRegistry;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 单次领取一条持久任务；正式回测和attempt终态原子提交，中断后先查原Run。 */
@Component
public class StrategyExperimentWorker implements ScheduledJobContributor {
    private final JdbcTemplate jdbc;
    private final TenantWorkAccess tenants;
    private final BacktestRunService runs;
    private final BacktestExecutionService execution;
    private final BacktestEvaluationService evaluations;
    private final TransactionTemplate transaction;

    public StrategyExperimentWorker(JdbcTemplate jdbc,TenantWorkAccess tenants,BacktestRunService runs,
            BacktestExecutionService execution,BacktestEvaluationService evaluations,PlatformTransactionManager manager) {
        this.jdbc=jdbc;this.tenants=tenants;this.runs=runs;this.execution=execution;this.evaluations=evaluations;
        transaction=new TransactionTemplate(manager);transaction.setTimeout(40);
    }

    @Override public ScheduledJobRegistry.Job job() {
        return new ScheduledJobRegistry.Job("STRATEGY_EXPERIMENTS",2000,1000,60000,()->true,()->{
            tick();return ScheduledJobRegistry.JobResult.success();},true);
    }

    public void tick() {
        for(String id:tenants.next(TenantWorkAccess.Kind.EXPERIMENT,4)) {
            boolean worked=tenants.execute(TenantWorkAccess.Kind.EXPERIMENT,id,()->advance(UUID.fromString(id)));
            if(worked) break;
        }
    }

    public boolean advance(UUID experiment) {
        Claim claim=transaction.execute(s->{
            limits();
            // 与取消/重试共享父锁；取消提交后不得再领取后续任务。
            var flags=jdbc.queryForList("SELECT cancel_requested FROM strategy_experiments WHERE experiment_id=? AND active FOR UPDATE",Boolean.class,experiment);
            if(flags.isEmpty()) return null;
            if(flags.getFirst()) jdbc.update("UPDATE strategy_experiment_attempts a SET status='CANCELLED' FROM strategy_experiment_trials t WHERE a.trial_id=t.trial_id AND t.experiment_id=? AND a.status='QUEUED'",experiment);
            var rows=jdbc.query("""
                SELECT a.attempt_id,a.backtest_run_id FROM strategy_experiment_attempts a
                JOIN strategy_experiment_trials t USING(trial_id) WHERE t.experiment_id=?
                AND a.status IN ('RUNNING','QUEUED') ORDER BY a.status DESC,t.ordinal,a.attempt LIMIT 1 FOR UPDATE OF a
                """,(r,n)->new Claim(r.getObject(1,UUID.class),r.getString(2)),experiment);
            if(rows.isEmpty()) {finish(experiment);return null;}
            Claim value=rows.getFirst();
            jdbc.update("UPDATE strategy_experiment_attempts SET status='RUNNING' WHERE attempt_id=?",value.id());
            return value;
        });
        if(claim==null) return false;
        return Boolean.TRUE.equals(transaction.execute(s->{
            limits();
            if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(hashtextextended('strategy-experiment-execution',0))",Boolean.class))) return false;
            String state=jdbc.queryForObject("SELECT status FROM strategy_experiment_attempts WHERE attempt_id=? FOR UPDATE",String.class,claim.id());
            if(!"RUNNING".equals(state)) return false;
            jdbc.queryForObject("SELECT backtest_run_id FROM backtest_runs WHERE backtest_run_id=? FOR UPDATE",String.class,claim.run());
            jdbc.queryForObject("SELECT set_config('nq.experiment_attempt',?,true)",String.class,claim.id().toString());
            var run=runs.getByBacktestRunId(claim.run());
            if(run.status()==BacktestRunStatus.CREATED) {
                try {
                    execution.startRun(claim.run());
                } catch (RuntimeException failure) {
                    // 正式执行器先保存业务失败再抛异常；只在真实FAILED已可读时收敛attempt，
                    // SQL回滚/未知异常仍向外传播，不能将异常本身伪装为已保存的失败。
                    if(runs.getByBacktestRunId(claim.run()).status()!=BacktestRunStatus.FAILED) throw failure;
                }
                run=runs.getByBacktestRunId(claim.run());
            }
            String result;
            String reason=null;
            if(run.status()==BacktestRunStatus.SUCCEEDED) {
                var report=evaluations.getByBacktestRunId(claim.run()).orElse(null);
                if(report==null) {
                    try {report=evaluations.evaluate(claim.run());}
                    catch(BacktestEvaluationFailedException failed) {report=failed.report();}
                }
                result=report.evaluationStatus().name();
                if(!result.equals("SUCCEEDED")) reason="EVALUATION_FAILED";
            } else if(run.status()==BacktestRunStatus.FAILED) {
                result="FAILED";reason=run.failureCode()==null?"BACKTEST_FAILED":run.failureCode();
            } else {
                // 既有非终态不证明没有执行过；只保留未知，禁止自动生成新Run。
                result="OUTCOME_UNKNOWN";reason="RUN_STATE_REQUIRES_RECONCILIATION";
            }
            jdbc.update("UPDATE strategy_experiment_attempts SET status=?,failure_reason=? WHERE attempt_id=?",result,reason,claim.id());
            finish(experiment);
            return true;
        }));
    }

    private void finish(UUID experiment) {
        jdbc.update("UPDATE strategy_experiments e SET active=EXISTS(SELECT 1 FROM strategy_experiment_trials t JOIN strategy_experiment_attempts a USING(trial_id) WHERE t.experiment_id=e.experiment_id AND a.status IN ('QUEUED','RUNNING')) WHERE experiment_id=?",experiment);
    }
    private void limits() {jdbc.execute("SET LOCAL lock_timeout='3s'");jdbc.execute("SET LOCAL statement_timeout='35s'");}
    private record Claim(UUID id,String run) {}
}
