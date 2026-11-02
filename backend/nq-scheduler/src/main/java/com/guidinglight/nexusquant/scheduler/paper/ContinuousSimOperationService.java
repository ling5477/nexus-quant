package com.guidinglight.nexusquant.scheduler.paper;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

/** 全部修改均为本地事务；成功回执与业务事实原子落库，网络未知只能查询或显式继续原编号。 */
@Service
@ConditionalOnProperty(prefix = "nq.strategy-sim", name = "enabled", havingValue = "true")
public class ContinuousSimOperationService {
    private final ContinuousSimOperationRepository operations;
    private final ObjectProvider<ContinuousSimRunService> driver;

    public ContinuousSimOperationService(
            ContinuousSimOperationRepository operations,
            ObjectProvider<ContinuousSimRunService> driver) {
        this.operations = operations;
        this.driver = driver;
    }

    /** 原结果读取不重新跑准入或创建；驱动关闭、当前游标变化均不覆盖原已完成命令。 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 10)
    public Optional<ContinuousSimOperation> find(UUID operationId, long owner) {
        if (operationId == null || owner <= 0)
            throw new IllegalArgumentException("INVALID_CONTINUOUS_SIM_ACTOR");
        return operations.find(operationId).filter(value -> value.ownerUserId() == owner);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 30)
    public ContinuousSimOperation submit(
            ContinuousSimOperationRequest request, long owner, String actor, String traceId) {
        if (request == null) throw new IllegalArgumentException("INVALID_CONTINUOUS_SIM_OPERATION");
        request.validate();
        if (owner <= 0
                || actor == null
                || actor.isBlank()
                || actor.length() > 128
                || traceId == null
                || traceId.isBlank()
                || traceId.length() > 128)
            throw new IllegalArgumentException("INVALID_CONTINUOUS_SIM_ACTOR");
        String fingerprint = request.fingerprint(owner);
        operations.lock(request.operationId());
        var existing = operations.find(request.operationId());
        if (existing.isPresent()) {
            var original = existing.get();
            if (original.ownerUserId() != owner || !original.inputSha256().equals(fingerprint))
                throw new IllegalStateException("CONTINUOUS_SIM_OPERATION_ID_CONFLICT");
            return original;
        }
        ContinuousSimRunService available = driver.getIfAvailable();
        if (available == null) throw new IllegalStateException("CONTINUOUS_SIM_DISABLED");
        ContinuousSimRunService.Status result =
                switch (request.type()) {
                    case CREATE -> available.start(request.publishId(), request.budget(), actor);
                    case PAUSE -> available.stop(request.paperRunId());
                    case RESUME -> available.resume(request.paperRunId());
                };
        operations.complete(
                request,
                owner,
                actor,
                fingerprint,
                traceId,
                result,
                Instant.now().truncatedTo(ChronoUnit.MICROS));
        return operations.find(request.operationId()).orElseThrow();
    }
}
