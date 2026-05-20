package com.guidinglight.nexusquant.research.application.paper;

import com.guidinglight.nexusquant.research.domain.BacktestPublishRecord;
import com.guidinglight.nexusquant.research.domain.BacktestRun;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingOrder;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingPosition;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRun;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRunStatus;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingTrade;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperTradingOrderRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperTradingPositionRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperTradingRunRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperTradingTradeRepository;
import com.guidinglight.nexusquant.research.domain.port.BacktestPublishRecordRepository;
import com.guidinglight.nexusquant.research.domain.port.BacktestRunRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PaperTradingRunService {

    private final PaperTradingRunRepository runRepository;
    private final PaperTradingOrderRepository orderRepository;
    private final PaperTradingTradeRepository tradeRepository;
    private final PaperTradingPositionRepository positionRepository;
    private final BacktestPublishRecordRepository publishRecordRepository;
    private final BacktestRunRepository backtestRunRepository;
    private final Clock clock;

    @Autowired
    public PaperTradingRunService(
            PaperTradingRunRepository runRepository,
            PaperTradingOrderRepository orderRepository,
            PaperTradingTradeRepository tradeRepository,
            PaperTradingPositionRepository positionRepository,
            BacktestPublishRecordRepository publishRecordRepository,
            BacktestRunRepository backtestRunRepository
    ) {
        this(runRepository, orderRepository, tradeRepository, positionRepository,
                publishRecordRepository, backtestRunRepository, Clock.systemUTC());
    }

    public PaperTradingRunService(
            PaperTradingRunRepository runRepository,
            PaperTradingOrderRepository orderRepository,
            PaperTradingTradeRepository tradeRepository,
            PaperTradingPositionRepository positionRepository,
            BacktestPublishRecordRepository publishRecordRepository,
            BacktestRunRepository backtestRunRepository,
            Clock clock
    ) {
        this.runRepository = Objects.requireNonNull(runRepository);
        this.orderRepository = Objects.requireNonNull(orderRepository);
        this.tradeRepository = Objects.requireNonNull(tradeRepository);
        this.positionRepository = Objects.requireNonNull(positionRepository);
        this.publishRecordRepository = Objects.requireNonNull(publishRecordRepository);
        this.backtestRunRepository = Objects.requireNonNull(backtestRunRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    public PaperTradingRun create(PaperTradingRunCreateCommand command) {
        BacktestPublishRecord publish = publishRecordRepository.findByPublishRecordId(command.publishId())
                .orElseThrow(() -> new IllegalArgumentException("publish record not found: " + command.publishId()));

        String strategyVersionId = publish.strategyVersionId();
        String publishSnapshotJson = publish.publishSnapshotJson() != null ? publish.publishSnapshotJson() : "{}";
        String versionSnapshotJson = publish.versionSnapshotJson() != null ? publish.versionSnapshotJson() : "{}";

        String datasetSnapshotJson = "{}";
        String paramSnapshotJson = "{}";
        String configSnapshotJson = command.configSnapshotJson() != null ? command.configSnapshotJson() : "{}";

        BacktestRun backtestRun = backtestRunRepository.findByBacktestRunId(publish.backtestRunId()).orElse(null);
        if (backtestRun != null) {
            datasetSnapshotJson = backtestRun.datasetSnapshotJson() != null ? backtestRun.datasetSnapshotJson() : "{}";
            paramSnapshotJson = backtestRun.paramSnapshotJson() != null ? backtestRun.paramSnapshotJson() : "{}";
        }

        Instant now = clock.instant();
        PaperTradingRun run = new PaperTradingRun(
                "ptr-" + UUID.randomUUID(),
                command.publishId(),
                strategyVersionId,
                PaperTradingRunStatus.CREATED,
                command.tradeEnv(),
                command.exchangeCode(),
                command.marketType(),
                command.symbol(),
                command.intervalCode(),
                null,
                null,
                publishSnapshotJson,
                versionSnapshotJson,
                datasetSnapshotJson,
                paramSnapshotJson,
                configSnapshotJson,
                command.createdBy(),
                now,
                now
        );
        runRepository.insert(run);
        return run;
    }

    public PaperTradingRun start(String paperRunId) {
        PaperTradingRun run = getById(paperRunId);
        if (run.status() != PaperTradingRunStatus.CREATED) {
            throw new IllegalStateException("paper run can only start from CREATED, current: " + run.status());
        }
        Instant now = clock.instant();
        runRepository.updateStatus(paperRunId, PaperTradingRunStatus.RUNNING, now, null, now);
        return runRepository.findById(paperRunId).orElseThrow();
    }

    public PaperTradingRun stop(String paperRunId) {
        PaperTradingRun run = getById(paperRunId);
        if (run.status() != PaperTradingRunStatus.RUNNING) {
            throw new IllegalStateException("paper run can only stop from RUNNING, current: " + run.status());
        }
        Instant now = clock.instant();
        runRepository.updateStatus(paperRunId, PaperTradingRunStatus.STOPPED, null, now, now);
        return runRepository.findById(paperRunId).orElseThrow();
    }

    public PaperTradingRun getById(String paperRunId) {
        return runRepository.findById(paperRunId)
                .orElseThrow(() -> new IllegalArgumentException("paper run not found: " + paperRunId));
    }

    public List<PaperTradingRun> list(String publishId, String status) {
        return runRepository.list(publishId, status);
    }

    public List<PaperTradingOrder> listOrders(String paperRunId) {
        return orderRepository.listByRunId(paperRunId);
    }

    public List<PaperTradingTrade> listTrades(String paperRunId) {
        return tradeRepository.listByRunId(paperRunId);
    }

    public List<PaperTradingPosition> listPositions(String paperRunId) {
        return positionRepository.listByRunId(paperRunId);
    }
}
