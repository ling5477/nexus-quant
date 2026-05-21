package com.guidinglight.nexusquant.research.application.api.paper;

import com.guidinglight.nexusquant.research.application.paper.PaperRunScheduleCreateCommand;
import com.guidinglight.nexusquant.research.application.paper.PaperRunScheduleService;
import com.guidinglight.nexusquant.research.application.paper.PaperTradingMonitorService;
import com.guidinglight.nexusquant.research.application.paper.PaperTradingRunCreateCommand;
import com.guidinglight.nexusquant.research.application.paper.PaperTradingRunService;
import com.guidinglight.nexusquant.research.domain.paper.EmergencyStopEvent;
import com.guidinglight.nexusquant.research.domain.paper.EquityCurveSnapshot;
import com.guidinglight.nexusquant.research.domain.paper.PaperRiskCheckResult;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunHeartbeat;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunSchedule;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunScheduleFire;
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

    public PaperTradingApiService(
            PaperTradingRunService runService,
            PaperTradingMonitorService monitorService,
            PaperRunScheduleService scheduleService
    ) {
        this.runService = Objects.requireNonNull(runService, "runService must not be null");
        this.monitorService = Objects.requireNonNull(monitorService, "monitorService must not be null");
        this.scheduleService = Objects.requireNonNull(scheduleService, "scheduleService must not be null");
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
}
