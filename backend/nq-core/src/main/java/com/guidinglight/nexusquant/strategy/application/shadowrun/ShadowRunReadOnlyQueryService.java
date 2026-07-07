package com.guidinglight.nexusquant.strategy.application.shadowrun;

import com.guidinglight.nexusquant.strategy.domain.port.ShadowRunFactRepository;
import com.guidinglight.nexusquant.strategy.domain.port.ShadowRunListQuery;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowConsistencyReport;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRun;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunEvent;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunSnapshot;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shadow Run read-only query service。
 *
 * <p>职责：为 GateR-6 HTTP detail / events / snapshots / latest consistency report 提供只读查询。
 * 该 service 只依赖 {@link ShadowRunFactRepository} 的查询方法；不会创建 Shadow Run、不会追加
 * event/snapshot/report、不会调用 runner、adapter、credential store、真实交易所，也不会修改真实
 * account / ledger / order。
 *
 * <p>线程安全：无共享可变状态；事务边界为 read-only 查询事务。幂等：同一查询只返回当前本地事实，
 * 不产生副作用。
 */
@Service
public class ShadowRunReadOnlyQueryService {

    private final ShadowRunFactRepository repository;

    /**
     * 生产构造器。
     *
     * @param repository Shadow Run 本地事实 repository；本 service 只调用 read-only 方法
     */
    @Autowired
    public ShadowRunReadOnlyQueryService(ShadowRunFactRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    /**
     * 查询 Shadow Run detail。
     *
     * @param shadowRunId 本地 Shadow Run id
     * @return 本地 Shadow Run 主事实
     * @throws ShadowRunReadOnlyNotFoundException 目标 run 不存在时抛出，供 API 映射 404
     */
    @Transactional(readOnly = true)
    public ShadowRun getDetail(UUID shadowRunId) {
        return requireRun(shadowRunId);
    }

    /**
     * 查询 Shadow Run 主事实列表。
     *
     * <p>只做本地 bounded read-only 查询。limit/offset 已由 {@link ShadowRunListQuery}
     * 约束，不会无界读取，也不会创建 run、触发 runner 或访问外部 adapter。
     *
     * @param query 列表筛选与分页参数
     * @return 当前筛选条件下的列表和 total
     */
    @Transactional(readOnly = true)
    public ShadowRunListResult list(ShadowRunListQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        return new ShadowRunListResult(
                repository.listRuns(query),
                query.limit(),
                query.offset(),
                repository.countRuns(query)
        );
    }

    /**
     * 查询 Shadow Run 生命周期事件。
     *
     * <p>先确认 run 存在，避免不存在 id 被误解为空事件列表。
     *
     * @param shadowRunId 本地 Shadow Run id
     * @return append-only 事件列表
     */
    @Transactional(readOnly = true)
    public List<ShadowRunEvent> listEvents(UUID shadowRunId) {
        requireRun(shadowRunId);
        return repository.listEvents(shadowRunId);
    }

    /**
     * 查询 Shadow Run 本地快照。
     *
     * <p>先确认 run 存在，避免不存在 id 被误解为空 replay 数据。
     *
     * @param shadowRunId 本地 Shadow Run id
     * @return 脱敏 snapshot 列表
     */
    @Transactional(readOnly = true)
    public List<ShadowRunSnapshot> listSnapshots(UUID shadowRunId) {
        requireRun(shadowRunId);
        return repository.listSnapshots(shadowRunId);
    }

    /**
     * 查询最近一次 consistency report。
     *
     * @param shadowRunId 本地 Shadow Run id
     * @return 最新 report；report 只代表诊断对照，不代表 approval 或 LIVE readiness
     * @throws ShadowRunReadOnlyNotFoundException run 不存在或尚无 report 时抛出，供 API 明确 404 语义
     */
    @Transactional(readOnly = true)
    public ShadowConsistencyReport getLatestConsistencyReport(UUID shadowRunId) {
        requireRun(shadowRunId);
        return repository.findLatestReport(shadowRunId)
                .orElseThrow(() -> new ShadowRunReadOnlyNotFoundException(
                        "shadow consistency report not found: " + shadowRunId
                ));
    }

    private ShadowRun requireRun(UUID shadowRunId) {
        Objects.requireNonNull(shadowRunId, "shadowRunId must not be null");
        return repository.findById(shadowRunId)
                .orElseThrow(() -> new ShadowRunReadOnlyNotFoundException(
                        "shadow run not found: " + shadowRunId
                ));
    }
}
