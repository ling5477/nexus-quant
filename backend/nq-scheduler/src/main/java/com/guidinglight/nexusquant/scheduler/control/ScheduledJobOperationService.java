package com.guidinglight.nexusquant.scheduler.control;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** 对原调度入口添加可查询身份；版本写入原子完成，运行领取及终态仍由canonical调度器负责。 */
@Service
@ConditionalOnProperty(
        name = "nq.runtime.provider-observation.enabled",
        havingValue = "false",
        matchIfMissing = true)
public class ScheduledJobOperationService {
    private static final Logger log = LoggerFactory.getLogger(ScheduledJobOperationService.class);
    private final ScheduledJobManagementService management;
    private final ScheduledJobOperationRepository operations;
    private final ScheduledJobRegistry registry;
    private final TransactionTemplate transaction;
    private final Clock clock = Clock.systemUTC();

    public ScheduledJobOperationService(
            ScheduledJobManagementService management,
            ScheduledJobOperationRepository operations,
            ScheduledJobRegistry registry,
            PlatformTransactionManager transactionManager) {
        this.management = management;
        this.operations = operations;
        this.registry = registry;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transaction.setTimeout(20);
    }

    public Optional<ScheduledJobOperation> find(UUID id, long owner) {
        return operations.find(id).filter(value -> value.ownerUserId() == owner);
    }

    public ScheduledJobOperation submit(
            ScheduledJobOperationRequest request, long owner, String actor, String traceId) {
        request.validate();
        if (owner <= 0 || actor == null || actor.isBlank())
            throw new IllegalArgumentException("INVALID_SCHEDULER_ACTOR");
        registry.require(request.jobKey());
        String fingerprint = fingerprint(request, owner);
        if (request.type() == ScheduledJobOperation.Type.UPDATE_CONTROL) {
            return Objects.requireNonNull(
                    transaction.execute(
                            ignored -> {
                                operations.create(
                                        request,
                                        owner,
                                        actor,
                                        fingerprint,
                                        traceId,
                                        clock.instant());
                                ScheduledJobOperation receipt =
                                        requireIdentity(request, owner, fingerprint);
                                if (receipt.status() == ScheduledJobOperation.Status.COMPLETED)
                                    return receipt;
                                if (receipt.status() != ScheduledJobOperation.Status.STARTING) {
                                    throw new IllegalStateException(
                                            "SCHEDULER_OPERATION_STATE_CONFLICT");
                                }
                                ScheduledJobControl result =
                                        management.patch(
                                                request.jobKey(),
                                                request.enabled(),
                                                request.fixedDelayMs(),
                                                request.expectedVersion(),
                                                actor,
                                                receipt.traceId());
                                operations.completeControl(
                                        request.operationId(), result, clock.instant());
                                return operations.find(request.operationId()).orElseThrow();
                            }));
        }

        ScheduledJobOperation receipt;
        try {
            receipt =
                    Objects.requireNonNull(
                            transaction.execute(
                                    ignored -> {
                                        operations.create(
                                                request,
                                                owner,
                                                actor,
                                                fingerprint,
                                                traceId,
                                                clock.instant());
                                        return requireIdentity(request, owner, fingerprint);
                                    }));
        } catch (DuplicateKeyException conflict) {
            // 并发INSERT可能先碰到pending-run唯一索引，再命中同UUID的主键冲突处理。
            // 失败事务已回滚；只能读取并核对已提交的原身份，不能换编号或重做创建。
            if (operations.find(request.operationId()).isEmpty()) {
                throw new ScheduledJobOperationConflictException("SCHEDULER_OPERATION_PENDING");
            }
            receipt = requireIdentity(request, owner, fingerprint);
        }
        if (receipt.status() != ScheduledJobOperation.Status.STARTING) return receipt;
        try {
            SchedulerDispatcher.ExecutionOutcome result =
                    management.runOnce(request.jobKey(), request.operationId());
            // 锁未取得时可能是同一请求的另一调用正在领取，不能抢先写SKIPPED阻止原调用。
            transaction.executeWithoutResult(
                    ignored -> {
                        ScheduledJobOperation current =
                                operations.find(request.operationId()).orElseThrow();
                        if (current.status() != ScheduledJobOperation.Status.COMPLETED
                                && !(current.status() == ScheduledJobOperation.Status.STARTING
                                        && result
                                                == SchedulerDispatcher.ExecutionOutcome.SKIPPED)) {
                            operations.markUnknown(request.operationId());
                        }
                    });
        } catch (RuntimeException failure) {
            log.warn(
                    "scheduler_operation_unconfirmed operationId={} failureType={}",
                    request.operationId(),
                    failure.getClass().getSimpleName());
            transaction.executeWithoutResult(
                    ignored -> {
                        String reason = failure.getMessage();
                        if ("SCHEDULER_CAPABILITY_UNAVAILABLE".equals(reason)
                                || "SCHEDULER_NOT_READY".equals(reason)) {
                            operations.reject(request.operationId(), reason, clock.instant());
                        } else operations.markUnknown(request.operationId());
                    });
        }
        return operations.find(request.operationId()).orElseThrow();
    }

    private ScheduledJobOperation requireIdentity(
            ScheduledJobOperationRequest request, long owner, String hash) {
        ScheduledJobOperation value = operations.find(request.operationId()).orElseThrow();
        if (value.ownerUserId() != owner || !value.inputSha256().equals(hash)) {
            throw new ScheduledJobOperationConflictException("SCHEDULER_OPERATION_ID_CONFLICT");
        }
        return value;
    }

    private static String fingerprint(ScheduledJobOperationRequest request, long owner) {
        String input =
                "SCHEDULER_OPERATION_V1\n"
                        + owner
                        + "\n"
                        + request.type()
                        + "\n"
                        + request.jobKey()
                        + "\n"
                        + request.enabled()
                        + "\n"
                        + request.fixedDelayMs()
                        + "\n"
                        + request.expectedVersion();
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 unavailable", unavailable);
        }
    }
}
