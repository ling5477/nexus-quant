package com.guidinglight.nexusquant.research.infra.paper.jdbc;

import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRun;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRunStatus;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperTradingRunRepository;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.AdmissionMutationCoordinator;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPaperTradingRunRepository implements PaperTradingRunRepository {

    private static final String BASE_SELECT = """
            SELECT paper_run_id, publish_id, strategy_version_id, status, trade_env, exchange_code, market_type,
                   symbol, interval_code, started_at, stopped_at,
                   publish_snapshot_json::text AS publish_snapshot_json,
                   strategy_version_snapshot_json::text AS strategy_version_snapshot_json,
                   dataset_snapshot_json::text AS dataset_snapshot_json,
                   param_snapshot_json::text AS param_snapshot_json,
                   config_snapshot_json::text AS config_snapshot_json,
                   created_by, created_at, updated_at
            FROM paper_trading_runs
            """;

    private static final RowMapper<PaperTradingRun> ROW_MAPPER = JdbcPaperTradingRunRepository::mapRow;

    private final JdbcTemplate jdbcTemplate;
    private final AdmissionMutationCoordinator admissionMutationCoordinator;

    public JdbcPaperTradingRunRepository(
            JdbcTemplate jdbcTemplate,
            AdmissionMutationCoordinator admissionMutationCoordinator
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.admissionMutationCoordinator = admissionMutationCoordinator;
    }

    @Override
    public void insert(PaperTradingRun run) {
        admissionMutationCoordinator.withLockedAdmissionStates(List.of(run.publishId()), () -> jdbcTemplate.update(
                """
                        INSERT INTO paper_trading_runs (
                            paper_run_id, publish_id, strategy_version_id, status, trade_env, exchange_code, market_type,
                            symbol, interval_code, started_at, stopped_at,
                            publish_snapshot_json, strategy_version_snapshot_json, dataset_snapshot_json,
                            param_snapshot_json, config_snapshot_json,
                            created_by, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                                  CAST(? AS JSONB), CAST(? AS JSONB), CAST(? AS JSONB),
                                  CAST(? AS JSONB), CAST(? AS JSONB),
                                  ?, ?, ?)
                        """,
                run.paperRunId(),
                run.publishId(),
                run.strategyVersionId(),
                run.status().name(),
                run.tradeEnv(),
                run.exchangeCode(),
                run.marketType(),
                run.symbol(),
                run.intervalCode(),
                toTimestamp(run.startedAt()),
                toTimestamp(run.stoppedAt()),
                run.publishSnapshotJson(),
                run.strategyVersionSnapshotJson(),
                run.datasetSnapshotJson(),
                run.paramSnapshotJson(),
                run.configSnapshotJson(),
                run.createdBy(),
                Timestamp.from(run.createdAt()),
                Timestamp.from(run.updatedAt())
        ));
    }

    @Override
    public Optional<PaperTradingRun> findById(String paperRunId) {
        List<PaperTradingRun> rows = jdbcTemplate.query(
                BASE_SELECT + " WHERE paper_run_id = ?",
                ROW_MAPPER,
                paperRunId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public List<PaperTradingRun> list(String publishId, String status) {
        StringBuilder sql = new StringBuilder(BASE_SELECT);
        List<Object> args = new ArrayList<>();
        boolean hasWhere = false;
        if (publishId != null && !publishId.isBlank()) {
            sql.append(" WHERE publish_id = ?");
            args.add(publishId);
            hasWhere = true;
        }
        if (status != null && !status.isBlank()) {
            sql.append(hasWhere ? " AND" : " WHERE").append(" status = ?");
            args.add(status);
        }
        sql.append(" ORDER BY created_at DESC, paper_run_id DESC");
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, args.toArray());
    }

    @Override
    public boolean updateStatus(String paperRunId, PaperTradingRunStatus status, Instant startedAt, Instant stoppedAt, Instant updatedAt) {
        List<String> publishIds = jdbcTemplate.query(
                "SELECT publish_id FROM paper_trading_runs WHERE paper_run_id = ?",
                (resultSet, rowNum) -> resultSet.getString("publish_id"),
                paperRunId
        );
        int updated = admissionMutationCoordinator.withLockedAdmissionStates(publishIds, () -> jdbcTemplate.update(
                """
                        UPDATE paper_trading_runs
                        SET status = ?,
                            started_at = COALESCE(?, started_at),
                            stopped_at = COALESCE(?, stopped_at),
                            updated_at = ?
                        WHERE paper_run_id = ?
                        """,
                status.name(),
                toTimestamp(startedAt),
                toTimestamp(stoppedAt),
                Timestamp.from(updatedAt),
                paperRunId
        ));
        return updated > 0;
    }

    private static PaperTradingRun mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        Timestamp startedAt = resultSet.getTimestamp("started_at");
        Timestamp stoppedAt = resultSet.getTimestamp("stopped_at");
        return new PaperTradingRun(
                resultSet.getString("paper_run_id"),
                resultSet.getString("publish_id"),
                resultSet.getString("strategy_version_id"),
                PaperTradingRunStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("trade_env"),
                resultSet.getString("exchange_code"),
                resultSet.getString("market_type"),
                resultSet.getString("symbol"),
                resultSet.getString("interval_code"),
                startedAt == null ? null : startedAt.toInstant(),
                stoppedAt == null ? null : stoppedAt.toInstant(),
                resultSet.getString("publish_snapshot_json"),
                resultSet.getString("strategy_version_snapshot_json"),
                resultSet.getString("dataset_snapshot_json"),
                resultSet.getString("param_snapshot_json"),
                resultSet.getString("config_snapshot_json"),
                resultSet.getString("created_by"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant()
        );
    }

    private static Timestamp toTimestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
