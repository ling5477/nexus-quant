package com.guidinglight.nexusquant.research.application.api.paper;

import com.guidinglight.nexusquant.research.application.paper.PaperRunAlertCreateCommand;
import com.guidinglight.nexusquant.research.application.paper.PaperRunDailyReportGenerateCommand;
import com.guidinglight.nexusquant.research.application.paper.PaperRunMonitorRunService;
import com.guidinglight.nexusquant.research.application.paper.PaperRunMonitorService;
import com.guidinglight.nexusquant.research.application.paper.PaperRunRecoverCommand;
import com.guidinglight.nexusquant.research.application.paper.PaperRunRecoveryService;
import com.guidinglight.nexusquant.research.application.paper.PaperRunRetryFailedStepCommand;
import com.guidinglight.nexusquant.research.application.paper.PaperRunScheduleCreateCommand;
import com.guidinglight.nexusquant.research.application.paper.PaperRunScheduleService;
import com.guidinglight.nexusquant.research.application.paper.PaperRunStabilityCheckGenerateCommand;
import com.guidinglight.nexusquant.research.application.paper.PaperRunStabilityCheckService;
import com.guidinglight.nexusquant.research.application.paper.PaperTradingMonitorService;
import com.guidinglight.nexusquant.research.application.paper.PaperTradingRunCreateCommand;
import com.guidinglight.nexusquant.research.application.paper.PaperTradingRunService;
import com.guidinglight.nexusquant.research.domain.paper.EmergencyStopEvent;
import com.guidinglight.nexusquant.research.domain.paper.EquityCurveSnapshot;
import com.guidinglight.nexusquant.research.domain.paper.PaperRiskCheckResult;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunAlert;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunDailyReport;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunHeartbeat;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunRecoveryEvent;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunSchedule;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunScheduleFire;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunStabilityCheck;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingOrder;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingPosition;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRun;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingTrade;
import com.guidinglight.nexusquant.research.domain.paper.PositionCurveSnapshot;
import com.guidinglight.nexusquant.research.domain.paper.TradeReplayRecord;

import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaperTradingApiService {

    private final PaperTradingRunService runService;
    private final PaperTradingMonitorService monitorService;
    private final PaperRunScheduleService scheduleService;
    private final PaperRunMonitorService paperRunMonitorService;
    private final PaperRunRecoveryService recoveryService;
    private final PaperRunStabilityCheckService stabilityCheckService;
    private final PaperRunMonitorRunService monitorRunService;

    public PaperTradingApiService(
            PaperTradingRunService runService,
            PaperTradingMonitorService monitorService,
            PaperRunScheduleService scheduleService,
            PaperRunMonitorService paperRunMonitorService,
            PaperRunRecoveryService recoveryService,
            PaperRunStabilityCheckService stabilityCheckService,
            PaperRunMonitorRunService monitorRunService
    ) {
        this.runService = Objects.requireNonNull(runService, "runService must not be null");
        this.monitorService = Objects.requireNonNull(monitorService, "monitorService must not be null");
        this.scheduleService = Objects.requireNonNull(scheduleService, "scheduleService must not be null");
        this.paperRunMonitorService = Objects.requireNonNull(paperRunMonitorService, "paperRunMonitorService must not be null");
        this.recoveryService = Objects.requireNonNull(recoveryService, "recoveryService must not be null");
        this.stabilityCheckService = Objects.requireNonNull(stabilityCheckService, "stabilityCheckService must not be null");
        this.monitorRunService = Objects.requireNonNull(monitorRunService, "monitorRunService must not be null");
    }

    public PaperTradingRun create(PaperTradingRunCreateCommand command) {
        try {
            return runService.create(command);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    public PaperTradingRun start(String paperRunId) {
        try {
            return runService.start(paperRunId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
        }
    }

    public PaperTradingRun stop(String paperRunId) {
        try {
            return runService.stop(paperRunId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
        }
    }

    public PaperTradingRun getById(String paperRunId) {
        try {
            return runService.getById(paperRunId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    public List<PaperTradingRun> list(String publishId, String status) {
        return runService.list(publishId, status);
    }

    public List<PaperTradingOrder> listOrders(String paperRunId) {
        getById(paperRunId);
        return runService.listOrders(paperRunId);
    }

    public List<PaperTradingTrade> listTrades(String paperRunId) {
        getById(paperRunId);
        return runService.listTrades(paperRunId);
    }

    public List<PaperTradingPosition> listPositions(String paperRunId) {
        getById(paperRunId);
        return runService.listPositions(paperRunId);
    }

    public PaperRiskCheckResult runRiskCheckOnce(String paperRunId) {
        try {
            return monitorService.runRiskCheckOnce(paperRunId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    public List<PaperRiskCheckResult> listRiskResults(String paperRunId) {
        try {
            return monitorService.listRiskResults(paperRunId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    public List<EquityCurveSnapshot> listEquityCurve(String paperRunId) {
        try {
            return monitorService.listEquityCurve(paperRunId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    public List<PositionCurveSnapshot> listPositionCurve(String paperRunId) {
        try {
            return monitorService.listPositionCurve(paperRunId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    public List<TradeReplayRecord> listReplayRecords(String paperRunId) {
        try {
            return monitorService.listReplayRecords(paperRunId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    public EmergencyStopEvent triggerEmergencyStop(String paperRunId, String triggerType, String reason, String triggeredBy) {
        try {
            return monitorService.triggerEmergencyStop(paperRunId, triggerType, reason, triggeredBy);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    public List<EmergencyStopEvent> listEmergencyStops(String paperRunId) {
        try {
            return monitorService.listEmergencyStops(paperRunId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    public PaperRunSchedule createSchedule(PaperRunScheduleCreateCommand command) {
        try {
            return scheduleService.createSchedule(command);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    public PaperRunSchedule getScheduleById(String scheduleId) {
        try {
            return scheduleService.getScheduleById(scheduleId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    public List<PaperRunSchedule> listSchedules(String paperRunId, String status) {
        return scheduleService.listSchedules(paperRunId, status);
    }

    public PaperRunSchedule updateScheduleStatus(String scheduleId, String status) {
        try {
            return scheduleService.updateScheduleStatus(scheduleId, status);
        } catch (IllegalArgumentException ex) {
            String msg = ex.getMessage() != null ? ex.getMessage() : "";
            HttpStatus httpStatus = msg.startsWith("invalid schedule status")
                    ? HttpStatus.BAD_REQUEST : HttpStatus.NOT_FOUND;
            throw new ResponseStatusException(httpStatus, ex.getMessage(), ex);
        }
    }

    public PaperRunScheduleFire runScheduleOnce(String scheduleId) {
        try {
            return scheduleService.runScheduleOnce(scheduleId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
        }
    }

    public List<PaperRunScheduleFire> listFires(String scheduleId) {
        try {
            return scheduleService.listFires(scheduleId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    public PaperRunHeartbeat runHeartbeatOnce(String paperRunId) {
        try {
            return scheduleService.runHeartbeatOnce(paperRunId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    public List<PaperRunHeartbeat> listHeartbeats(String paperRunId) {
        try {
            return scheduleService.listHeartbeats(paperRunId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    public PaperRunDailyReport generateDailyReport(PaperRunDailyReportGenerateCommand command) {
        try {
            return paperRunMonitorService.generateDailyReport(command);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    public PaperRunDailyReport getDailyReportById(String reportId) {
        try {
            return paperRunMonitorService.getDailyReportById(reportId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    public List<PaperRunDailyReport> listDailyReports(String paperRunId) {
        try {
            return paperRunMonitorService.listDailyReports(paperRunId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    public PaperRunAlert createAlert(PaperRunAlertCreateCommand command) {
        try {
            return paperRunMonitorService.createAlert(command);
        } catch (IllegalArgumentException ex) {
            String msg = ex.getMessage() != null ? ex.getMessage() : "";
            HttpStatus httpStatus = msg.startsWith("invalid alert severity")
                    ? HttpStatus.BAD_REQUEST : HttpStatus.NOT_FOUND;
            throw new ResponseStatusException(httpStatus, ex.getMessage(), ex);
        }
    }

    public List<PaperRunAlert> listAlerts(String paperRunId, String status, String severity) {
        try {
            return paperRunMonitorService.listAlerts(paperRunId, status, severity);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    public PaperRunAlert ackAlert(String alertId, String acknowledgedBy) {
        try {
            return paperRunMonitorService.ackAlert(alertId, acknowledgedBy);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
        }
    }

    public PaperRunAlert resolveAlert(String alertId, String resolvedBy) {
        try {
            return paperRunMonitorService.resolveAlert(alertId, resolvedBy);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    public PaperRunRecoveryEvent recover(PaperRunRecoverCommand command) {
        try {
            return recoveryService.recover(command);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    public PaperRunRecoveryEvent retryFailedStep(PaperRunRetryFailedStepCommand command) {
        try {
            return recoveryService.retryFailedStep(command);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    public List<PaperRunRecoveryEvent> listRecoveryEvents(String paperRunId, String recoveryType, String status) {
        try {
            return recoveryService.listRecoveryEvents(paperRunId, recoveryType, status);
        } catch (IllegalArgumentException ex) {
            String msg = ex.getMessage() != null ? ex.getMessage() : "";
            HttpStatus httpStatus = (msg.startsWith("invalid recovery") )
                    ? HttpStatus.BAD_REQUEST : HttpStatus.NOT_FOUND;
            throw new ResponseStatusException(httpStatus, ex.getMessage(), ex);
        }
    }

    public PaperRunStabilityCheck generateStabilityCheck(PaperRunStabilityCheckGenerateCommand command) {
        try {
            return stabilityCheckService.generate(command);
        } catch (IllegalArgumentException ex) {
            String msg = ex.getMessage() != null ? ex.getMessage() : "";
            HttpStatus httpStatus = (msg.startsWith("invalid stability") || msg.startsWith("checkWindow"))
                    ? HttpStatus.BAD_REQUEST : HttpStatus.NOT_FOUND;
            throw new ResponseStatusException(httpStatus, ex.getMessage(), ex);
        }
    }

    public PaperRunStabilityCheck getStabilityCheckById(String stabilityCheckId) {
        try {
            return stabilityCheckService.getById(stabilityCheckId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    public List<PaperRunStabilityCheck> listStabilityChecks(String paperRunId, String status) {
        try {
            return stabilityCheckService.list(paperRunId, status);
        } catch (IllegalArgumentException ex) {
            String msg = ex.getMessage() != null ? ex.getMessage() : "";
            HttpStatus httpStatus = msg.startsWith("invalid stability") ? HttpStatus.BAD_REQUEST : HttpStatus.NOT_FOUND;
            throw new ResponseStatusException(httpStatus, ex.getMessage(), ex);
        }
    }

    public PaperRunMonitorRunService.MonitorRunOnceResult runMonitorOnce(String paperRunId) {
        try {
            return monitorRunService.runOnce(paperRunId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }
}
