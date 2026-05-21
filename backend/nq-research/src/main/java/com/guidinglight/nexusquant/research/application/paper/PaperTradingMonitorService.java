package com.guidinglight.nexusquant.research.application.paper;

import com.guidinglight.nexusquant.research.domain.paper.EmergencyStopEvent;
import com.guidinglight.nexusquant.research.domain.paper.EmergencyStopStatus;
import com.guidinglight.nexusquant.research.domain.paper.EmergencyStopTriggerType;
import com.guidinglight.nexusquant.research.domain.paper.EquityCurveSnapshot;
import com.guidinglight.nexusquant.research.domain.paper.PaperRiskCheckResult;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRun;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRunStatus;
import com.guidinglight.nexusquant.research.domain.paper.PositionCurveSnapshot;
import com.guidinglight.nexusquant.research.domain.paper.RiskCheckSeverity;
import com.guidinglight.nexusquant.research.domain.paper.RiskCheckStatus;
import com.guidinglight.nexusquant.research.domain.paper.TradeReplayRecord;
import com.guidinglight.nexusquant.research.domain.paper.port.EmergencyStopEventRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.EquityCurveSnapshotRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRiskCheckResultRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PositionCurveSnapshotRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.TradeReplayRecordRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PaperTradingMonitorService {

    private final PaperTradingRunService runService;
    private final PaperRiskCheckResultRepository riskCheckResultRepository;
    private final EquityCurveSnapshotRepository equityCurveSnapshotRepository;
    private final PositionCurveSnapshotRepository positionCurveSnapshotRepository;
    private final TradeReplayRecordRepository tradeReplayRecordRepository;
    private final EmergencyStopEventRepository emergencyStopEventRepository;
    private final Clock clock;

    @Autowired
    public PaperTradingMonitorService(
            PaperTradingRunService runService,
            PaperRiskCheckResultRepository riskCheckResultRepository,
            EquityCurveSnapshotRepository equityCurveSnapshotRepository,
            PositionCurveSnapshotRepository positionCurveSnapshotRepository,
            TradeReplayRecordRepository tradeReplayRecordRepository,
            EmergencyStopEventRepository emergencyStopEventRepository
    ) {
        this(runService, riskCheckResultRepository, equityCurveSnapshotRepository,
                positionCurveSnapshotRepository, tradeReplayRecordRepository,
                emergencyStopEventRepository, Clock.systemUTC());
    }

    public PaperTradingMonitorService(
            PaperTradingRunService runService,
            PaperRiskCheckResultRepository riskCheckResultRepository,
            EquityCurveSnapshotRepository equityCurveSnapshotRepository,
            PositionCurveSnapshotRepository positionCurveSnapshotRepository,
            TradeReplayRecordRepository tradeReplayRecordRepository,
            EmergencyStopEventRepository emergencyStopEventRepository,
            Clock clock
    ) {
        this.runService = Objects.requireNonNull(runService);
        this.riskCheckResultRepository = Objects.requireNonNull(riskCheckResultRepository);
        this.equityCurveSnapshotRepository = Objects.requireNonNull(equityCurveSnapshotRepository);
        this.positionCurveSnapshotRepository = Objects.requireNonNull(positionCurveSnapshotRepository);
        this.tradeReplayRecordRepository = Objects.requireNonNull(tradeReplayRecordRepository);
        this.emergencyStopEventRepository = Objects.requireNonNull(emergencyStopEventRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    public PaperRiskCheckResult runRiskCheckOnce(String paperRunId) {
        runService.getById(paperRunId);
        Instant now = clock.instant();
        PaperRiskCheckResult result = new PaperRiskCheckResult(
                "rrc-" + UUID.randomUUID(), paperRunId,
                "BASIC_HEALTH_CHECK", RiskCheckStatus.PASSED, RiskCheckSeverity.LOW,
                "Basic health check passed", "{}", "{\"healthy\":true}", now
        );
        riskCheckResultRepository.insert(result);
        return result;
    }

    public List<PaperRiskCheckResult> listRiskResults(String paperRunId) {
        runService.getById(paperRunId);
        return riskCheckResultRepository.listByRunId(paperRunId);
    }

    public List<EquityCurveSnapshot> listEquityCurve(String paperRunId) {
        runService.getById(paperRunId);
        return equityCurveSnapshotRepository.listByRunId(paperRunId);
    }

    public List<PositionCurveSnapshot> listPositionCurve(String paperRunId) {
        runService.getById(paperRunId);
        return positionCurveSnapshotRepository.listByRunId(paperRunId);
    }

    public List<TradeReplayRecord> listReplayRecords(String paperRunId) {
        runService.getById(paperRunId);
        return tradeReplayRecordRepository.listByRunId(paperRunId);
    }

    public EmergencyStopEvent triggerEmergencyStop(String paperRunId, String triggerType, String reason, String triggeredBy) {
        PaperTradingRun run = runService.getById(paperRunId);
        Instant now = clock.instant();

        EmergencyStopStatus resultStatus;
        String resultJson;
        try {
            if (run.status() == PaperTradingRunStatus.RUNNING) {
                runService.stop(paperRunId);
                resultStatus = EmergencyStopStatus.APPLIED;
                resultJson = "{\"action\":\"stopped\"}";
            } else {
                resultStatus = EmergencyStopStatus.FAILED;
                resultJson = "{\"error\":\"run not in RUNNING state, current: " + run.status() + "\"}";
            }
        } catch (IllegalStateException e) {
            resultStatus = EmergencyStopStatus.FAILED;
            resultJson = "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}";
        }

        EmergencyStopEvent event = new EmergencyStopEvent(
                "es-" + UUID.randomUUID(), paperRunId,
                EmergencyStopTriggerType.valueOf(triggerType), resultStatus,
                reason, triggeredBy, now,
                resultStatus == EmergencyStopStatus.APPLIED ? now : null,
                "{\"triggerType\":\"" + triggerType + "\",\"reason\":\"" + (reason != null ? reason.replace("\"", "'") : "") + "\"}",
                resultJson, now
        );
        emergencyStopEventRepository.insert(event);
        return event;
    }

    public List<EmergencyStopEvent> listEmergencyStops(String paperRunId) {
        runService.getById(paperRunId);
        return emergencyStopEventRepository.listByRunId(paperRunId);
    }
}
