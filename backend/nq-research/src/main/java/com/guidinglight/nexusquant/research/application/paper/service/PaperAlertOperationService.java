package com.guidinglight.nexusquant.research.application.paper.service;

import com.guidinglight.nexusquant.research.application.paper.command.PaperAlertOperationCommand;
import com.guidinglight.nexusquant.research.application.paper.command.PaperRunAlertCreateCommand;
import com.guidinglight.nexusquant.research.domain.paper.PaperAlertOperation;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunAlert;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperAlertOperationRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

/** 原编号、告警修改和完成审计原子提交；网络未知先查原回执，不按当前状态猜原结果。 */
@Service
public class PaperAlertOperationService {
    private final PaperAlertOperationRepository operations;
    private final PaperRunMonitorService monitor;
    private final PaperTradingRunService runs;

    public PaperAlertOperationService(
            PaperAlertOperationRepository operations,
            PaperRunMonitorService monitor,
            PaperTradingRunService runs) {
        this.operations = operations;
        this.monitor = monitor;
        this.runs = runs;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 10)
    public Optional<PaperAlertOperation> find(UUID id, long owner, String runId) {
        if (id == null || owner <= 0 || runId == null || runId.isBlank())
            throw new IllegalArgumentException("INVALID_PAPER_ALERT_ACTOR");
        return operations
                .find(id)
                .filter(value -> value.ownerUserId() == owner && value.paperRunId().equals(runId));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 30)
    public PaperAlertOperation submit(
            UUID id,
            long owner,
            String actor,
            String runId,
            PaperAlertOperationCommand command,
            String traceId) {
        if (id == null
                || owner <= 0
                || actor == null
                || actor.isBlank()
                || actor.length() > 128
                || traceId == null
                || traceId.isBlank()
                || traceId.length() > 128
                || command == null) throw new IllegalArgumentException("INVALID_PAPER_ALERT_ACTOR");
        String sha = command.fingerprint(owner, runId);
        operations.lock(id);
        var existing = operations.find(id);
        if (existing.isPresent()) {
            PaperAlertOperation original = existing.get();
            if (original.ownerUserId() != owner || !original.inputSha256().equals(sha))
                throw new IllegalStateException("PAPER_ALERT_OPERATION_ID_CONFLICT");
            return original;
        }
        // 公开来源继续沿原共享运维合同，私有来源必须通过既有父运行owner过滤。
        runs.getById(runId);
        PaperRunAlert changed =
                switch (command.type()) {
                    case CREATE ->
                            monitor.createAlert(
                                    new PaperRunAlertCreateCommand(
                                            runId,
                                            command.alertType(),
                                            command.severity(),
                                            command.title(),
                                            command.message(),
                                            command.source(),
                                            command.eventSnapshotJson()));
                    case ACK -> monitor.ackAlert(runId, command.alertId(), actor);
                    case RESOLVE -> monitor.resolveAlert(runId, command.alertId(), actor);
                };
        // 重新读取已持久化精度的事实，避免把Java纳秒回值当成数据库保存的原时间。
        PaperRunAlert persisted = monitor.getAlert(runId, changed.alertId());
        var completed =
                new PaperAlertOperation(
                        id,
                        owner,
                        actor,
                        runId,
                        command.type().name(),
                        sha,
                        persisted,
                        Instant.now().truncatedTo(ChronoUnit.MICROS));
        operations.complete(completed, traceId);
        return completed;
    }
}
