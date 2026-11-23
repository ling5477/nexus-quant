package com.guidinglight.nexusquant.app.research;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.research.api.dto.BacktestEvaluationResponse;
import com.guidinglight.nexusquant.research.application.eval.BacktestEvaluationFailedException;
import com.guidinglight.nexusquant.research.application.eval.api.BacktestRunApiService;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** 原请求登记与正式业务结果回执；不调度任务，不对未知请求自动重做。 */
@Service
public final class BacktestEvaluationRequestService {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final BacktestRunApiService delegate;
  private final TransactionTemplate registration, execution, reading;

  public BacktestEvaluationRequestService(
      JdbcTemplate jdbc,
      ObjectMapper mapper,
      BacktestRunApiService delegate,
      PlatformTransactionManager manager) {
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.delegate = delegate;
    registration = transaction(manager, 10, false);
    execution = transaction(manager, 40, false);
    reading = transaction(manager, 5, true);
  }

  public Receipt submit(UUID id, String rawTarget, long owner) {
    identity(id, owner);
    String target = runId(rawTarget);
    delegate.getByBacktestRunId(target);
    boolean inserted =
        Boolean.TRUE.equals(
            registration.execute(
                status -> {
                  limits();
                  var prior = stored(id);
                  if (prior.isPresent()) {
                    match(prior.get(), target, owner);
                    return false;
                  }

                  int count =
                      jdbc.update(
                          "INSERT INTO"
                              + " backtest_evaluation_requests(request_id,owner_user_id,backtest_run_id)"
                              + " VALUES(?,?,?) ON CONFLICT(request_id) DO NOTHING",
                          id,
                          owner,
                          target);
                  match(stored(id).orElseThrow(), target, owner);
                  return count == 1;
                }));
    // 重复或重启遗留PENDING只读原事实，绝不当成没有执行过而重新发起外部读取/评估。
    if (!inserted) return find(id, owner).orElseThrow();
    return execution.execute(
        status -> {
          limits();
          jdbc.queryForObject(
              "SELECT pg_advisory_xact_lock(hashtextextended(?,0))",
              Object.class,
              "backtest_evaluation_requests:" + target);
          var original =
              jdbc.query(
                      "SELECT * FROM backtest_evaluation_requests WHERE request_id=? FOR UPDATE",
                      (row, n) -> stored(row),
                      id)
                  .getFirst();
          match(original, target, owner);
          BacktestEvaluationResponse result = evaluate(target);
          String state = result.evaluationStatus();
          // 与原目录/报告使用相同数据源和事务；提交异常时保留PENDING，不能据异常推断没有生效。
          jdbc.update(
              "UPDATE backtest_evaluation_requests SET"
                  + " state=?,result_json=?::jsonb,completed_at=clock_timestamp() WHERE"
                  + " request_id=? AND state='PENDING'",
              state,
              json(result),
              id);
          return stored(id).orElseThrow().receipt();
        });
  }

  public Optional<Receipt> find(UUID id, long owner) {
    identity(id, owner);
    Optional<Receipt> found =
        reading.execute(
            status ->
                jdbc
                    .query(
                        "SELECT * FROM backtest_evaluation_requests WHERE request_id=? AND"
                            + " owner_user_id=?",
                        (row, n) -> stored(row).receipt(),
                        id,
                        owner)
                    .stream()
                    .findFirst());
    found.ifPresent(value -> delegate.getByBacktestRunId(value.target()));
    return found;
  }

  private Optional<Stored> stored(UUID id) {
    return jdbc
        .query(
            "SELECT * FROM backtest_evaluation_requests WHERE request_id=?",
            (row, n) -> stored(row),
            id)
        .stream()
        .findFirst();
  }

  private Stored stored(ResultSet row) throws SQLException {
    var completed = row.getTimestamp("completed_at");
    String raw = row.getString("result_json");
    try {
      return new Stored(
          row.getLong("owner_user_id"),
          new Receipt(
              row.getObject("request_id", UUID.class),
              row.getString("backtest_run_id"),
              row.getString("state"),
              row.getTimestamp("created_at").toInstant(),
              completed == null ? null : completed.toInstant(),
              raw == null ? null : mapper.readValue(raw, BacktestEvaluationResponse.class)));
    } catch (JsonProcessingException error) {
      throw new IllegalStateException("request receipt is invalid", error);
    }
  }

  private static void match(Stored prior, String target, long owner) {
    if (prior.owner() != owner || !prior.receipt().target().equals(target))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "original request identity conflict");
  }

  private String json(Object result) {
    try {
      return mapper.writeValueAsString(result);
    } catch (JsonProcessingException error) {
      throw new IllegalStateException("receipt serialization failed", error);
    }
  }

  private void limits() {
    jdbc.execute("SET LOCAL lock_timeout='3s'");
    jdbc.execute("SET LOCAL statement_timeout='35s'");
  }

  private static TransactionTemplate transaction(
      PlatformTransactionManager manager, int timeout, boolean readOnly) {
    var value = new TransactionTemplate(manager);
    value.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    value.setTimeout(timeout);
    value.setReadOnly(readOnly);
    return value;
  }

  private static void identity(UUID id, long owner) {
    if (id == null || id.equals(new UUID(0, 0)) || owner <= 0)
      throw new IllegalArgumentException("invalid request identity");
  }

  private BacktestEvaluationResponse evaluate(String target) {
    try {
      return BacktestEvaluationResponse.from(delegate.evaluate(target));
    } catch (BacktestEvaluationFailedException failure) {
      // 仍在同一数据库事务内，原失败报告与回执一起提交；SQL/未知异常不得伪装为终态。
      return BacktestEvaluationResponse.from(failure.report());
    }
  }

  private static String runId(String raw) {
    if (raw == null || !raw.matches("[A-Za-z0-9._:-]{1,128}"))
      throw new IllegalArgumentException("invalid backtest run identity");
    return raw;
  }

  private record Stored(long owner, Receipt receipt) {}

  public record Receipt(
      UUID requestId,
      String target,
      String state,
      Instant createdAt,
      Instant completedAt,
      BacktestEvaluationResponse result) {}
}
