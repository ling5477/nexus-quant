package com.guidinglight.nexusquant.strategy.domain.port;

import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowConsistencyReport;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRun;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunEvent;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunSnapshot;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunStatus;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunStatusUpdateResult;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Shadow Run 本地事实 repository port。
 *
 * <p>该 port 只允许写入 Shadow Run 本地 fact / audit / snapshot / consistency report，
 * 不允许提交订单、读取 credential、调用 private endpoint 或修改真实账户、资金、订单和 ledger。
 */
public interface ShadowRunFactRepository {

    /**
     * 创建 Shadow Run 主事实；实现必须按 idempotency key 去重，重复请求返回既有 run。
     */
    ShadowRun create(ShadowRun run);

    /**
     * 按本地 Shadow Run id 查询主事实；不存在时返回 empty，不触发外部 IO。
     */
    Optional<ShadowRun> findById(UUID shadowRunId);

    /**
     * 按幂等键查询主事实；用于重复创建请求复用既有本地 fact。
     */
    Optional<ShadowRun> findByIdempotencyKey(String idempotencyKey);

    /**
     * 追加审计事件；事件 append-only，不修改真实账户、资金、订单或 ledger。
     */
    void appendEvent(ShadowRunEvent event);

    /**
     * 追加本地快照；payload 必须已通过敏感字段 guard，不允许保存 credential 或 private payload。
     */
    void appendSnapshot(ShadowRunSnapshot snapshot);

    /**
     * 创建一致性复盘报告；report 只表达脱敏比较结果，不表达交易授权。
     */
    ShadowConsistencyReport createConsistencyReport(ShadowConsistencyReport report);

    /**
     * 使用状态机和 expected version 更新状态；非法流转或版本冲突必须失败并给出明确错误。
     */
    ShadowRunStatusUpdateResult updateStatus(
            UUID shadowRunId,
            ShadowRunStatus toStatus,
            long expectedVersion,
            String reasonCode,
            String message,
            String requestId,
            String traceId
    );

    /**
     * 将 run 置为 FAILED，保留失败 reason event；调用方需提供当前 expected version。
     */
    default ShadowRunStatusUpdateResult failWithReason(
            UUID shadowRunId,
            long expectedVersion,
            String reasonCode,
            String message,
            String requestId,
            String traceId
    ) {
        return updateStatus(shadowRunId, ShadowRunStatus.FAILED, expectedVersion, reasonCode, message, requestId, traceId);
    }

    /**
     * 将 run 置为 BLOCKED，保留阻断 reason event；调用方需提供当前 expected version。
     */
    default ShadowRunStatusUpdateResult blockWithReason(
            UUID shadowRunId,
            long expectedVersion,
            String reasonCode,
            String message,
            String requestId,
            String traceId
    ) {
        return updateStatus(shadowRunId, ShadowRunStatus.BLOCKED, expectedVersion, reasonCode, message, requestId, traceId);
    }

    /**
     * 查询 run 的审计事件，按创建时间返回；不读取外部系统。
     */
    List<ShadowRunEvent> listEvents(UUID shadowRunId);

    /**
     * 查询 run 的本地快照，按类型和 sequence 返回；不执行策略或 runner。
     */
    List<ShadowRunSnapshot> listSnapshots(UUID shadowRunId);

    /**
     * 查询最近一次一致性复盘报告；报告不表示 approval 或 live-ready。
     */
    Optional<ShadowConsistencyReport> findLatestReport(UUID shadowRunId);
}
