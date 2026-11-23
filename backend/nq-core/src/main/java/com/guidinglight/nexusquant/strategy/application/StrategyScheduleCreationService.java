package com.guidinglight.nexusquant.strategy.application;

import com.guidinglight.nexusquant.strategy.application.command.StrategyScheduleCreateRequest;
import com.guidinglight.nexusquant.strategy.application.exception.StrategyScheduleCreationConflictException;
import com.guidinglight.nexusquant.strategy.domain.StrategyScheduleCreation;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyScheduleCreationRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

/** 计划、原回执和审计原子提交；响应丢失只能查询或显式继续同一编号及输入。 */
@Service
public class StrategyScheduleCreationService {
    private final StrategyScheduleCreationRepository creations;
    private final StrategyScheduleCreationInputs inputs;
    private final StrategyScheduleService schedules;

    public StrategyScheduleCreationService(
            StrategyScheduleCreationRepository creations,
            StrategyScheduleCreationInputs inputs,
            StrategyScheduleService schedules) {
        this.creations = creations;
        this.inputs = inputs;
        this.schedules = schedules;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 5)
    public Optional<StrategyScheduleCreation> find(UUID id, long owner) {
        validateIdentity(id, owner);
        return creations.findOwned(id, owner);
    }

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            isolation = Isolation.READ_COMMITTED,
            timeout = 10)
    public StrategyScheduleCreation submit(
            UUID id,
            long owner,
            String actor,
            StrategyScheduleCreateRequest request,
            String traceId) {
        validateIdentity(id, owner);
        if (actor == null
                || actor.isBlank()
                || actor.length() > 128
                || traceId == null
                || traceId.isBlank()
                || traceId.length() > 64)
            throw new IllegalArgumentException("invalid server creation actor");
        var spec = inputs.normalize(request);
        String sha = spec.fingerprint(owner);
        creations.lock(id);
        var identity = creations.identity(id);
        if (identity.isPresent()) {
            if (identity.get().ownerUserId() != owner || !identity.get().inputSha256().equals(sha))
                throw new StrategyScheduleCreationConflictException(
                        "STRATEGY_SCHEDULE_CREATION_ID_CONFLICT");
            return creations
                    .findOwned(id, owner)
                    .orElseThrow(() -> new IllegalStateException("creation receipt missing"));
        }
        var created =
                schedules.create(
                        new StrategyScheduleCreateRequest(
                                spec.strategyId(),
                                spec.scheduleType(),
                                spec.cronExpr(),
                                spec.timezone(),
                                spec.enabled(),
                                spec.windowConfig(),
                                spec.dedupScope()));
        // 回读正式持久化时间和JSON表示，不能把Java纳秒或当前策略定义伪装成冻结创建结果。
        var persisted = schedules.getByScheduleJobId(created.scheduleJobId());
        var receipt =
                new StrategyScheduleCreation(
                        id,
                        owner,
                        actor,
                        1,
                        sha,
                        spec,
                        persisted,
                        traceId,
                        Instant.now().truncatedTo(ChronoUnit.MICROS));
        creations.complete(receipt);
        return creations.findOwned(id, owner).orElseThrow();
    }

    private static void validateIdentity(UUID id, long owner) {
        if (id == null || id.equals(new UUID(0, 0)) || owner <= 0)
            throw new IllegalArgumentException(
                    "valid creation identity and positive owner required");
    }
}
