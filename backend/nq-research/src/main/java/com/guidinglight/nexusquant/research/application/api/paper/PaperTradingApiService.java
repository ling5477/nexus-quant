package com.guidinglight.nexusquant.research.application.api.paper;

import com.guidinglight.nexusquant.research.application.paper.PaperTradingRunCreateCommand;
import com.guidinglight.nexusquant.research.application.paper.PaperTradingRunService;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingOrder;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingPosition;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRun;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingTrade;

import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaperTradingApiService {

    private final PaperTradingRunService runService;

    public PaperTradingApiService(PaperTradingRunService runService) {
        this.runService = Objects.requireNonNull(runService, "runService must not be null");
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
}
