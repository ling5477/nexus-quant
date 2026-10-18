package com.guidinglight.nexusquant.research.application.paper.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.research.application.paper.command.PaperTradingRunCreateCommand;
import com.guidinglight.nexusquant.research.domain.BacktestPublishRecord;
import com.guidinglight.nexusquant.research.domain.BacktestRun;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingOrder;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingPosition;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRun;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRunStatus;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingTrade;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRunCanonicalFactsRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperTradingOrderRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperTradingPositionRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperTradingRunRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperTradingTradeRepository;
import com.guidinglight.nexusquant.research.domain.port.BacktestPublishRecordRepository;
import com.guidinglight.nexusquant.research.domain.port.BacktestRunRepository;
import com.guidinglight.nexusquant.research.domain.port.ResearchFeeSourcePort;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** SIM及旧Paper读取先校验运行范围；来源冻结从回测继承，不接受客户端替换。 */
@Service
public class PaperTradingRunService {

    private final PaperTradingRunRepository runRepository;
    private final PaperTradingOrderRepository orderRepository;
    private final PaperTradingTradeRepository tradeRepository;
    private final PaperTradingPositionRepository positionRepository;
    private final BacktestPublishRecordRepository publishRecordRepository;
    private final BacktestRunRepository backtestRunRepository;
    private final Clock clock;
    private final ObjectMapper feeMapper = new ObjectMapper();
    private ResearchFeeSourcePort feeSource = ResearchFeeSourcePort.disabled();

    /** 独立构造默认不接受私有来源；正式容器复核从原回测继承的模型。 */
    @Autowired
    private void bindFeeSource(ObjectProvider<ResearchFeeSourcePort> provider) {
        feeSource = provider.getIfAvailable(ResearchFeeSourcePort::disabled);
    }

    private final PaperRunCanonicalFactsRepository canonicalFacts;

    @Autowired
    public PaperTradingRunService(
            PaperTradingRunRepository runRepository,
            PaperTradingOrderRepository orderRepository,
            PaperTradingTradeRepository tradeRepository,
            PaperTradingPositionRepository positionRepository,
            BacktestPublishRecordRepository publishRecordRepository,
            BacktestRunRepository backtestRunRepository,
            PaperRunCanonicalFactsRepository canonicalFacts) {
        this(
                runRepository,
                orderRepository,
                tradeRepository,
                positionRepository,
                publishRecordRepository,
                backtestRunRepository,
                Clock.systemUTC(),
                canonicalFacts);
    }

    public PaperTradingRunService(
            PaperTradingRunRepository runRepository,
            PaperTradingOrderRepository orderRepository,
            PaperTradingTradeRepository tradeRepository,
            PaperTradingPositionRepository positionRepository,
            BacktestPublishRecordRepository publishRecordRepository,
            BacktestRunRepository backtestRunRepository,
            Clock clock) {
        this(
                runRepository,
                orderRepository,
                tradeRepository,
                positionRepository,
                publishRecordRepository,
                backtestRunRepository,
                clock,
                null);
    }

    public PaperTradingRunService(
            PaperTradingRunRepository runRepository,
            PaperTradingOrderRepository orderRepository,
            PaperTradingTradeRepository tradeRepository,
            PaperTradingPositionRepository positionRepository,
            BacktestPublishRecordRepository publishRecordRepository,
            BacktestRunRepository backtestRunRepository,
            Clock clock,
            PaperRunCanonicalFactsRepository canonicalFacts) {
        this.runRepository = Objects.requireNonNull(runRepository);
        this.orderRepository = Objects.requireNonNull(orderRepository);
        this.tradeRepository = Objects.requireNonNull(tradeRepository);
        this.positionRepository = Objects.requireNonNull(positionRepository);
        this.publishRecordRepository = Objects.requireNonNull(publishRecordRepository);
        this.backtestRunRepository = Objects.requireNonNull(backtestRunRepository);
        this.clock = Objects.requireNonNull(clock);
        this.canonicalFacts = canonicalFacts;
    }

    public PaperTradingRun create(PaperTradingRunCreateCommand command) {
        BacktestPublishRecord publish =
                publishRecordRepository
                        .findByPublishRecordId(command.publishId())
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "publish record not found: "
                                                        + command.publishId()));

        String strategyVersionId = publish.strategyVersionId();
        String publishSnapshotJson =
                publish.publishSnapshotJson() != null ? publish.publishSnapshotJson() : "{}";
        String versionSnapshotJson =
                publish.versionSnapshotJson() != null ? publish.versionSnapshotJson() : "{}";

        String datasetSnapshotJson = "{}";
        String paramSnapshotJson = "{}";
        String configSnapshotJson =
                command.configSnapshotJson() != null ? command.configSnapshotJson() : "{}";

        BacktestRun backtestRun =
                backtestRunRepository.findByBacktestRunId(publish.backtestRunId()).orElse(null);
        if (backtestRun != null) {
            datasetSnapshotJson =
                    backtestRun.datasetSnapshotJson() != null
                            ? backtestRun.datasetSnapshotJson()
                            : "{}";
            paramSnapshotJson =
                    backtestRun.paramSnapshotJson() != null
                            ? backtestRun.paramSnapshotJson()
                            : "{}";
            configSnapshotJson = inheritFeeSource(backtestRun, configSnapshotJson);
        }

        Instant now = clock.instant();
        PaperTradingRun run =
                new PaperTradingRun(
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
                        now);
        runRepository.insert(run);
        return run;
    }

    /** 运行配置可保留预算等字段，但费用模型只能来自原回测，不能删除或选择另一来源。 */
    private String inheritFeeSource(BacktestRun source, String runtimeConfiguration) {
        try {
            JsonNode execution =
                    feeMapper.readTree(source.configSnapshotJson()).path("executionSpec");
            JsonNode parsed = feeMapper.readTree(runtimeConfiguration);
            if (!parsed.isObject())
                throw new IllegalArgumentException("PAPER_CONFIGURATION_OBJECT_REQUIRED");
            if (parsed.has("executionSpec") && !execution.equals(parsed.path("executionSpec"))) {
                throw new IllegalArgumentException("PAPER_FEE_MODEL_OVERRIDE_FORBIDDEN");
            }
            if (!"CURRENT_ACCOUNT_OBSERVATION".equals(execution.path("costSource").asText()))
                return runtimeConfiguration;
            feeSource.validateFrozenExecution(execution.toString(), source.datasetSnapshotJson());
            ObjectNode result = ((ObjectNode) parsed).deepCopy();
            result.set("executionSpec", execution);
            return result.toString();
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("PAPER_FEE_MODEL_JSON_INVALID", ex);
        }
    }

    public PaperTradingRun start(String paperRunId) {
        PaperTradingRun run = getById(paperRunId);
        if (run.status() != PaperTradingRunStatus.CREATED) {
            throw new IllegalStateException(
                    "paper run can only start from CREATED, current: " + run.status());
        }
        Instant now = clock.instant();
        runRepository.updateStatus(paperRunId, PaperTradingRunStatus.RUNNING, now, null, now);
        return runRepository.findById(paperRunId).orElseThrow();
    }

    public PaperTradingRun stop(String paperRunId) {
        PaperTradingRun run = getById(paperRunId);
        if (run.status() != PaperTradingRunStatus.RUNNING) {
            throw new IllegalStateException(
                    "paper run can only stop from RUNNING, current: " + run.status());
        }
        Instant now = clock.instant();
        runRepository.updateStatus(paperRunId, PaperTradingRunStatus.STOPPED, null, now, now);
        return runRepository.findById(paperRunId).orElseThrow();
    }

    public PaperTradingRun getById(String paperRunId) {
        return runRepository
                .findById(paperRunId)
                .orElseThrow(
                        () -> new IllegalArgumentException("paper run not found: " + paperRunId));
    }

    public List<PaperTradingRun> list(String publishId, String status) {
        return runRepository.list(publishId, status);
    }

    public boolean isStrategySim(String paperRunId) {
        getById(paperRunId);
        return canonicalFacts != null && canonicalFacts.isStrategySim(paperRunId);
    }

    public List<PaperTradingOrder> listOrders(String paperRunId) {
        if (isStrategySim(paperRunId)) {
            return canonicalFacts.orders(paperRunId);
        }
        return orderRepository.listByRunId(paperRunId);
    }

    public List<PaperTradingTrade> listTrades(String paperRunId) {
        if (isStrategySim(paperRunId)) {
            return canonicalFacts.trades(paperRunId);
        }
        return tradeRepository.listByRunId(paperRunId);
    }

    public List<PaperTradingPosition> listPositions(String paperRunId) {
        if (isStrategySim(paperRunId)) {
            return canonicalFacts.positions(paperRunId);
        }
        return positionRepository.listByRunId(paperRunId);
    }
}
