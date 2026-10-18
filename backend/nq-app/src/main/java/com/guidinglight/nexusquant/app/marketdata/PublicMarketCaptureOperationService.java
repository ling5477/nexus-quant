package com.guidinglight.nexusquant.app.marketdata;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.app.marketdata.PublicMarketReplayCaptureService.CaptureView;

import org.springframework.beans.factory.ObjectProvider;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/** 稳定请求先登记，来源读取按行锁串行；进程中断只保留待定，完成结果包括失败均原样返回。 */
@Service
public final class PublicMarketCaptureOperationService {
    private static final Set<String> SOURCE_FAILURES =
            Set.of(
                    "PUBLIC_CAPTURE_OUTBOUND_DISABLED",
                    "PUBLIC_CAPTURE_WINDOW_INVALID",
                    "PUBLIC_CAPTURE_GAP_OR_RESPONSE_INVALID",
                    "PUBLIC_CAPTURE_UNCLOSED_OR_SHAPE_INVALID",
                    "PUBLIC_CAPTURE_DUPLICATE_ORDER_OR_GAP",
                    "PUBLIC_CAPTURE_INVALID_OHLC",
                    "PUBLIC_CAPTURE_INVALID_RESPONSE",
                    "PUBLIC_CAPTURE_NON_POSITIVE_VALUE",
                    "PUBLIC_RULE_RESPONSE_INVALID",
                    "PUBLIC_RULE_STALE",
                    "PUBLIC_RULE_NOT_TRADABLE",
                    "PUBLIC_MARKET_RATE_LIMITED",
                    "PUBLIC_MARKET_INTERRUPTED",
                    "PUBLIC_MARKET_TRANSPORT_FAILURE",
                    "PUBLIC_MARKET_RETRY_EXHAUSTED",
                    "PUBLIC_MARKET_RESPONSE_ENCODING",
                    "PUBLIC_MARKET_RESPONSE_TOO_LARGE");
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Supplier<PublicMarketReplayCaptureService> source;
    private final Clock clock;
    private final TransactionTemplate registration;
    private final TransactionTemplate execution;
    private final TransactionTemplate reading;
    private final Semaphore capacity = new Semaphore(2);

    @Autowired
    public PublicMarketCaptureOperationService(
            JdbcTemplate jdbc,
            ObjectMapper mapper,
            PlatformTransactionManager manager,
            ObjectProvider<PublicMarketReplayCaptureService> source) {
        this(jdbc, mapper, manager, source::getIfAvailable, Clock.systemUTC());
    }

    PublicMarketCaptureOperationService(
            JdbcTemplate jdbc,
            ObjectMapper mapper,
            PlatformTransactionManager manager,
            Supplier<PublicMarketReplayCaptureService> source,
            Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.mapper = Objects.requireNonNull(mapper);
        this.source = Objects.requireNonNull(source);
        this.clock = Objects.requireNonNull(clock);
        registration = template(manager, 10, false);
        // 两个来源各最多3次8秒读取和短退避；行锁等待3秒，执行事务有明确的总边界。
        execution = template(manager, 65, false);
        reading = template(manager, 5, true);
    }

    public OperationView submit(UUID id, Instant start, Instant end, long owner, String actor) {
        validateIdentity(id, owner);
        PublicMarketReplayCaptureService.validateWindow(start, end, clock);
        if (actor == null || actor.isBlank() || actor.length() > 128)
            throw new IllegalArgumentException("PUBLIC_CAPTURE_ACTOR_INVALID");
        OperationView original =
                registration.execute(
                        status -> {
                            jdbc.queryForObject(
                                    "SELECT set_config('lock_timeout','3s',true)", String.class);
                            Instant now = now();
                            jdbc.update(
                                    """
                    INSERT INTO public_market_capture_operations(capture_id,owner_user_id,actor,requested_start,requested_end,state,created_at,updated_at)
                    VALUES (?,?,?,?,?,'PENDING',?,?) ON CONFLICT(capture_id) DO NOTHING
                    """,
                                    id,
                                    owner,
                                    actor,
                                    Timestamp.from(start),
                                    Timestamp.from(end),
                                    Timestamp.from(now),
                                    Timestamp.from(now));
                            Stored stored = lookup(id, false).orElseThrow();
                            if (stored.owner() != owner
                                    || !stored.view().start().equals(start)
                                    || !stored.view().end().equals(end)) {
                                throw new IllegalStateException("PUBLIC_CAPTURE_IDENTITY_CONFLICT");
                            }
                            return stored.view();
                        });
        if (!"PENDING".equals(original.state())) return original;
        if (!capacity.tryAcquire()) throw new IllegalStateException("PUBLIC_CAPTURE_CAPACITY_BUSY");
        try {
            return execution.execute(status -> executeLocked(id, owner));
        } finally {
            capacity.release();
        }
    }

    /** 即使公开来源已关闭仍能读取本人原回执；404不证明原请求从未登记或执行。 */
    public Optional<OperationView> find(UUID id, long owner) {
        validateIdentity(id, owner);
        return reading.execute(
                status ->
                        jdbc
                                .query(
                                        "SELECT * FROM public_market_capture_operations WHERE capture_id=? AND owner_user_id=?",
                                        (row, index) -> stored(row).view(),
                                        id,
                                        owner)
                                .stream()
                                .findFirst());
    }

    private OperationView executeLocked(UUID id, long owner) {
        jdbc.queryForObject("SELECT set_config('lock_timeout','3s',true)", String.class);
        Stored stored = lookup(id, true).orElseThrow();
        if (stored.owner() != owner)
            throw new IllegalStateException("PUBLIC_CAPTURE_IDENTITY_CONFLICT");
        OperationView original = stored.view();
        if (!"PENDING".equals(original.state())) return original;
        PublicMarketReplayCaptureService provider = source.get();
        PublicMarketReplayCaptureService.PreparedCapture prepared;
        try {
            if (provider == null)
                throw new IllegalStateException("PUBLIC_CAPTURE_OUTBOUND_DISABLED");
            prepared = provider.prepare(id, original.start(), original.end());
        } catch (IllegalStateException exception) {
            String code = exception.getMessage();
            if (code != null && code.matches("PUBLIC_MARKET_RESPONSE_REJECTED_[1-5][0-9]{2}")) {
                code = "PUBLIC_MARKET_RESPONSE_REJECTED";
            } else if (code == null || !SOURCE_FAILURES.contains(code)) {
                throw exception;
            }
            Instant completed = now();
            jdbc.update(
                    """
                    UPDATE public_market_capture_operations SET state='FAILED',failure_code=?,updated_at=?,completed_at=?
                    WHERE capture_id=? AND state='PENDING'
                    """,
                    code,
                    Timestamp.from(completed),
                    Timestamp.from(completed),
                    id);
            return lookup(id, false).orElseThrow().view();
        }
        // 持久化异常不能变成已确认来源失败；数据集、捕获及成功回执一起回滚，原登记继续为PENDING。
        provider.persist(prepared, stored.actor());
        Instant completed = now();
        jdbc.update(
                """
                UPDATE public_market_capture_operations SET state='SUCCEEDED',result_dataset_id=?,result_json=?::jsonb,
                       updated_at=?,completed_at=? WHERE capture_id=? AND state='PENDING'
                """,
                id,
                json(prepared.view()),
                Timestamp.from(completed),
                Timestamp.from(completed),
                id);
        return lookup(id, false).orElseThrow().view();
    }

    private Optional<Stored> lookup(UUID id, boolean lock) {
        return jdbc
                .query(
                        "SELECT * FROM public_market_capture_operations WHERE capture_id=?"
                                + (lock ? " FOR UPDATE" : ""),
                        (row, index) -> stored(row),
                        id)
                .stream()
                .findFirst();
    }

    private Stored stored(ResultSet row) throws SQLException {
        String value = row.getString("result_json");
        CaptureView result = null;
        if (value != null) {
            try {
                result = mapper.readValue(value, CaptureView.class);
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("PUBLIC_CAPTURE_RECEIPT_INVALID", exception);
            }
        }
        Timestamp completed = row.getTimestamp("completed_at");
        OperationView view =
                new OperationView(
                        row.getObject("capture_id", UUID.class),
                        row.getString("state"),
                        row.getTimestamp("requested_start").toInstant(),
                        row.getTimestamp("requested_end").toInstant(),
                        row.getTimestamp("created_at").toInstant(),
                        completed == null ? null : completed.toInstant(),
                        result,
                        row.getString("failure_code"));
        return new Stored(row.getLong("owner_user_id"), row.getString("actor"), view);
    }

    private String json(CaptureView result) {
        try {
            return mapper.writeValueAsString(result);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("PUBLIC_CAPTURE_RESULT_ENCODING_FAILED", exception);
        }
    }

    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private static void validateIdentity(UUID id, long owner) {
        if (id == null || id.equals(new UUID(0, 0)) || owner <= 0)
            throw new IllegalArgumentException("PUBLIC_CAPTURE_IDENTITY_INVALID");
    }

    private static TransactionTemplate template(
            PlatformTransactionManager manager, int timeout, boolean readOnly) {
        var value = new TransactionTemplate(manager);
        value.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        value.setIsolationLevel(
                readOnly
                        ? TransactionDefinition.ISOLATION_REPEATABLE_READ
                        : TransactionDefinition.ISOLATION_READ_COMMITTED);
        value.setTimeout(timeout);
        value.setReadOnly(readOnly);
        return value;
    }

    private record Stored(long owner, String actor, OperationView view) {}

    public record OperationView(
            UUID captureId,
            String state,
            Instant start,
            Instant end,
            Instant createdAt,
            Instant completedAt,
            CaptureView result,
            String failureCode) {}
}
