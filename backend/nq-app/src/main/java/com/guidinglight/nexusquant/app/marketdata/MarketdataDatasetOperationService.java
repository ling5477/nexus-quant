package com.guidinglight.nexusquant.app.marketdata;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.marketdata.api.dto.MarketdataDatasetResponse;
import com.guidinglight.nexusquant.marketdata.application.command.CreateMarketdataDatasetCommand;
import com.guidinglight.nexusquant.marketdata.application.result.MarketdataDatasetRefresh;
import com.guidinglight.nexusquant.marketdata.application.service.MarketdataDatasetService;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataDatasetCoverage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Semaphore;

/** 数据集创建和质量刷新的稳定原请求；业务事实仍由原数据集服务、覆盖表和准入锁拥有。 */
@Service
public final class MarketdataDatasetOperationService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final MarketdataDatasetService datasets;
    private final Clock clock;
    private final TransactionTemplate registration;
    private final TransactionTemplate execution;
    private final TransactionTemplate reading;
    private final Semaphore capacity = new Semaphore(4);

    @Autowired
    public MarketdataDatasetOperationService(
            JdbcTemplate jdbc,
            ObjectMapper mapper,
            MarketdataDatasetService datasets,
            PlatformTransactionManager manager) {
        this(jdbc, mapper, datasets, manager, Clock.systemUTC());
    }

    MarketdataDatasetOperationService(
            JdbcTemplate jdbc,
            ObjectMapper mapper,
            MarketdataDatasetService datasets,
            PlatformTransactionManager manager,
            Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.mapper = Objects.requireNonNull(mapper);
        this.datasets = Objects.requireNonNull(datasets);
        this.clock = Objects.requireNonNull(clock);
        registration = template(manager, 10, false);
        registration.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        execution = template(manager, 30, false);
        reading = template(manager, 5, true);
    }

    public OperationView create(
            UUID id, CreateMarketdataDatasetCommand raw, long owner, String actor) {
        var command = datasets.normalizeCreateCommand(raw);
        ObjectNode input = mapper.createObjectNode();
        input.put("datasetName", command.datasetName());
        input.put("exchangeCode", command.exchangeCode());
        input.put("marketType", command.marketType());
        input.put("symbol", command.symbol());
        input.put("interval", command.interval());
        input.put("startTime", command.startTime().toString());
        input.put("endTime", command.endTime().toString());
        return submit(id, "CREATE", id, input.toString(), owner, actor);
    }

    public OperationView refresh(UUID id, UUID datasetId, long owner, String actor) {
        return submit(id, "REFRESH", datasetId, "{}", owner, actor);
    }

    public Optional<OperationView> find(UUID id, long owner) {
        identity(id, owner);
        return reading.execute(
                status ->
                        jdbc
                                .query(
                                        "SELECT * FROM marketdata_dataset_operations WHERE"
                                                + " operation_id=? AND owner_user_id=?",
                                        (row, index) -> stored(row).view(),
                                        id,
                                        owner)
                                .stream()
                                .findFirst());
    }

    private OperationView submit(
            UUID id, String kind, UUID datasetId, String input, long owner, String actor) {
        identity(id, owner);
        identity(datasetId, owner);
        if (actor == null || actor.isBlank() || actor.length() > 128)
            throw new IllegalArgumentException("DATASET_OPERATION_ACTOR_INVALID");
        OperationView original =
                registration.execute(
                        status -> {
                            lockTimeout();
                            Instant now = now();
                            jdbc.update(
                                    """
INSERT INTO marketdata_dataset_operations(operation_id,owner_user_id,actor,kind,dataset_id,request_json,state,created_at,updated_at)
VALUES(?,?,?,?,?,?::jsonb,'PENDING',?,?) ON CONFLICT(operation_id) DO NOTHING
""",
                                    id,
                                    owner,
                                    actor,
                                    kind,
                                    datasetId,
                                    input,
                                    Timestamp.from(now),
                                    Timestamp.from(now));
                            Stored saved = lookup(id, false).orElseThrow();
                            if (saved.owner() != owner
                                    || !saved.view().kind().equals(kind)
                                    || !saved.view().datasetId().equals(datasetId)
                                    || !tree(saved.request()).equals(tree(input)))
                                throw new IllegalStateException(
                                        "DATASET_OPERATION_IDENTITY_CONFLICT");
                            return saved.view();
                        });
        if (!original.state().equals("PENDING")) return original;
        if (!capacity.tryAcquire())
            throw new IllegalStateException("DATASET_OPERATION_CAPACITY_BUSY");
        try {
            return execution.execute(status -> executeLocked(id, owner));
        } finally {
            capacity.release();
        }
    }

    private OperationView executeLocked(UUID id, long owner) {
        lockTimeout();
        Stored saved = lookup(id, true).orElseThrow();
        if (saved.owner() != owner)
            throw new IllegalStateException("DATASET_OPERATION_IDENTITY_CONFLICT");
        OperationView original = saved.view();
        if (!original.state().equals("PENDING")) return original;
        MarketdataDatasetRefresh refreshed;
        if (original.kind().equals("CREATE")) {
            // 不把另一来源或旧入口创建的同名UUID据为本次请求结果。
            if (jdbc.queryForObject(
                    "SELECT EXISTS(SELECT 1 FROM marketdata_datasets WHERE dataset_id=?)",
                    Boolean.class,
                    original.datasetId())) {
                return fail(saved, "DATASET_ID_OCCUPIED");
            }
            var request = tree(saved.request());
            var command =
                    new CreateMarketdataDatasetCommand(
                            request.path("datasetName").asText(),
                            request.path("exchangeCode").asText(),
                            request.path("marketType").asText(),
                            request.path("symbol").asText(),
                            request.path("interval").asText(),
                            Instant.parse(request.path("startTime").asText()),
                            Instant.parse(request.path("endTime").asText()),
                            saved.actor());
            refreshed = datasets.createDataset(original.datasetId(), command);
        } else {
            // 确定业务拒绝在进入事务代理前判定；未知SQL/并发错误须回滚并保持PENDING。
            var existing =
                    jdbc.queryForList(
                            "SELECT source FROM marketdata_datasets WHERE dataset_id=?",
                            String.class,
                            original.datasetId());
            if (existing.isEmpty()) return fail(saved, "DATASET_NOT_FOUND");
            if ("OKX_PUBLIC_CAPTURE".equals(existing.getFirst()))
                return fail(saved, "PUBLIC_CAPTURE_DATASET_IMMUTABLE");
            refreshed = datasets.refreshQualityWithCoverage(original.datasetId(), id);
        }
        Result result =
                new Result(
                        MarketdataDatasetResponse.from(refreshed.dataset()), refreshed.coverage());
        Instant finished = now();
        // 与数据集、覆盖历史及主状态共用执行事务；完成阶段失败不能留下伪成功或半成品。
        jdbc.update(
                """
UPDATE marketdata_dataset_operations SET state='SUCCEEDED',result_dataset_id=?,result_coverage_id=?,
    result_json=?::jsonb,updated_at=?,completed_at=? WHERE operation_id=? AND state='PENDING'
""",
                original.datasetId(),
                id,
                json(result),
                Timestamp.from(finished),
                Timestamp.from(finished),
                id);
        return lookup(id, false).orElseThrow().view();
    }

    private OperationView fail(Stored saved, String code) {
        Instant finished = now();
        jdbc.update(
                """
UPDATE marketdata_dataset_operations SET state='FAILED',failure_code=?,updated_at=?,completed_at=?
WHERE operation_id=? AND state='PENDING'
""",
                code,
                Timestamp.from(finished),
                Timestamp.from(finished),
                saved.view().operationId());
        return lookup(saved.view().operationId(), false).orElseThrow().view();
    }

    private Optional<Stored> lookup(UUID id, boolean lock) {
        return jdbc
                .query(
                        "SELECT * FROM marketdata_dataset_operations WHERE operation_id=?"
                                + (lock ? " FOR UPDATE" : ""),
                        (row, index) -> stored(row),
                        id)
                .stream()
                .findFirst();
    }

    private Stored stored(ResultSet row) throws SQLException {
        String result = row.getString("result_json");
        Timestamp completed = row.getTimestamp("completed_at");
        try {
            return new Stored(
                    row.getLong("owner_user_id"),
                    row.getString("actor"),
                    row.getString("request_json"),
                    new OperationView(
                            row.getObject("operation_id", UUID.class),
                            row.getString("kind"),
                            row.getObject("dataset_id", UUID.class),
                            row.getString("state"),
                            tree(row.getString("request_json")),
                            row.getTimestamp("created_at").toInstant(),
                            completed == null ? null : completed.toInstant(),
                            result == null ? null : mapper.readValue(result, Result.class),
                            row.getString("failure_code")));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("DATASET_OPERATION_RECEIPT_INVALID", error);
        }
    }

    private void lockTimeout() {
        jdbc.queryForObject("SELECT set_config('lock_timeout','3s',true)", String.class);
    }

    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private static void identity(UUID id, long owner) {
        if (id == null || id.equals(new UUID(0, 0)) || owner <= 0)
            throw new IllegalArgumentException("DATASET_OPERATION_IDENTITY_INVALID");
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("DATASET_OPERATION_SERIALIZATION_FAILED", error);
        }
    }

    private ObjectNode tree(String value) {
        try {
            var result = mapper.readTree(value);
            if (result instanceof ObjectNode object) return object;
            throw new IllegalStateException("DATASET_OPERATION_REQUEST_INVALID");
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("DATASET_OPERATION_REQUEST_INVALID", error);
        }
    }

    private static TransactionTemplate template(
            PlatformTransactionManager manager, int seconds, boolean readOnly) {
        var result = new TransactionTemplate(manager);
        result.setTimeout(seconds);
        result.setReadOnly(readOnly);
        return result;
    }

    private record Stored(long owner, String actor, String request, OperationView view) {}

    public record Result(MarketdataDatasetResponse dataset, MarketdataDatasetCoverage coverage) {}

    public record OperationView(
            UUID operationId,
            String kind,
            UUID datasetId,
            String state,
            ObjectNode input,
            Instant createdAt,
            Instant completedAt,
            Result result,
            String failureCode) {}
}
