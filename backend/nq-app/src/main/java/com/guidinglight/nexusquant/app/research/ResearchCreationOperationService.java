package com.guidinglight.nexusquant.app.research;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** 创建回执只拥有稳定请求身份；对象、校验及生命周期仍由各正式服务拥有。 */
@Service
public class ResearchCreationOperationService {
  private final JdbcTemplate jdbc;
  private final TransactionTemplate transaction;
  private final TransactionTemplate reading;
  private final TransactionTemplate simCreation;

  public ResearchCreationOperationService(JdbcTemplate jdbc, PlatformTransactionManager manager) {
    this.jdbc = jdbc;
    transaction = new TransactionTemplate(manager);
    transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    transaction.setTimeout(15);
    // canonical资金投影明确要求READ_COMMITTED；不能把研究配置的RR合同套到SIM注资。
    simCreation = new TransactionTemplate(manager);
    simCreation.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    simCreation.setTimeout(30);
    reading = new TransactionTemplate(manager);
    reading.setReadOnly(true);
    reading.setTimeout(5);
  }

  /** 唯一键等待原事务；同请求重放只返回原对象。失败回滚时不遗留半个业务对象。 */
  public Receipt execute(
      UUID id, long owner, String kind, String parent, JsonNode input, Supplier<Result> create) {
    if (id == null
        || owner <= 0
        || input == null
        || !input.isObject()
        || input.toString().length() > 262144)
      throw new IllegalArgumentException("invalid research creation identity or input");
    // 维持原配置/运行创建的可重复读边界。并发同UUID的序列化回滚只重读一次新事务，
    // 研究与隔离SIM命令均为本地数据库创建，失败事务没有外部副作用，也不改变稳定操作身份。
    for (int attempt = 0; ; attempt++) {
      try {
        return executeOnce(id, owner, kind, parent, input, create);
      } catch (DataAccessException conflict) {
        // Spring可能将PG40001映射为CannotAcquireLockException；只重试已完整回滚的序列化失败。
        if (attempt >= 1
            || !(conflict.getMostSpecificCause() instanceof SQLException sql)
            || !"40001".equals(sql.getSQLState())) throw conflict;
      }
    }
  }

  private Receipt executeOnce(
      UUID id, long owner, String kind, String parent, JsonNode input, Supplier<Result> create) {
    return ("SIM_RUN".equals(kind) ? simCreation : transaction)
        .execute(
            status -> {
              jdbc.execute("SET LOCAL lock_timeout = '5s'");
              jdbc.execute("SET LOCAL statement_timeout = '12s'");
              jdbc.update(
                  """
INSERT INTO research_creation_operations(operation_id,owner_user_id,kind,parent_key,request_json)
VALUES(?,?,?,?,?::jsonb) ON CONFLICT(operation_id) DO NOTHING
""",
                  id,
                  owner,
                  kind,
                  parent,
                  input.toString());
              Stored saved =
                  jdbc.query(
                          "SELECT *, request_json = ?::jsonb AS input_matches"
                              + " FROM research_creation_operations WHERE"
                              + " operation_id=? FOR UPDATE",
                          (row, index) -> stored(row),
                          input.toString(),
                          id)
                      .getFirst();
              // 使用数据库JSONB语义比较，数值指数展开和小数尾零不应导致同输入误判冲突。
              if (saved.owner() != owner
                  || !saved.receipt().kind().equals(kind)
                  || !saved.receipt().parentKey().equals(parent)
                  || !saved.inputMatches())
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "research creation operation identity conflict");
              if (saved.receipt().objectId() != null) return saved.receipt();
              Result result = create.get();
              if (result == null || result.objectId() == null || result.lookupKey() == null)
                throw new IllegalStateException("creation did not return a canonical identity");
              jdbc.update(
                  "UPDATE research_creation_operations SET object_id=?,lookup_key=? WHERE"
                      + " operation_id=?",
                  result.objectId(),
                  result.lookupKey(),
                  id);
              return new Receipt(id, kind, parent, result.objectId(), result.lookupKey());
            });
  }

  /** 未提交的原事务不可见，404不能被解释为允许生成新操作身份。 */
  public Optional<Receipt> find(UUID id, long owner) {
    return reading.execute(
        status ->
            jdbc
                .query(
                    """
SELECT *, TRUE AS input_matches FROM research_creation_operations
WHERE operation_id=? AND owner_user_id=? AND object_id IS NOT NULL
""",
                    (row, index) -> stored(row).receipt(),
                    id,
                    owner)
                .stream()
                .findFirst());
  }

  private Stored stored(ResultSet row) throws SQLException {
    return new Stored(
        row.getLong("owner_user_id"),
        row.getBoolean("input_matches"),
        new Receipt(
            row.getObject("operation_id", UUID.class),
            row.getString("kind"),
            row.getString("parent_key"),
            row.getString("object_id"),
            row.getString("lookup_key")));
  }

  public record Result(String objectId, String lookupKey) {}

  public record Receipt(
      UUID operationId, String kind, String parentKey, String objectId, String lookupKey) {}

  private record Stored(long owner, boolean inputMatches, Receipt receipt) {}
}
