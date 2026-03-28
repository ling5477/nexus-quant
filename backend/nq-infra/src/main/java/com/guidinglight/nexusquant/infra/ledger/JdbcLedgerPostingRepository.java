package com.guidinglight.nexusquant.infra.ledger;

import com.guidinglight.nexusquant.ledger.model.AccountSnapshotProjection;
import com.guidinglight.nexusquant.ledger.model.LedgerPostingEntry;
import com.guidinglight.nexusquant.ledger.model.PositionProjection;
import com.guidinglight.nexusquant.ledger.service.port.LedgerPostingRepository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * JdbcLedgerPostingRepository 是记账模块的 JDBC 实现。
 */
@Repository
public class JdbcLedgerPostingRepository implements LedgerPostingRepository {

    private static final RowMapper<PositionProjection> POSITION_ROW_MAPPER = JdbcLedgerPostingRepository::mapPosition;

    private final JdbcTemplate jdbcTemplate;

    public JdbcLedgerPostingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean existsByIdempotencyKey(String idempotencyKey) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM ledger_entries WHERE idempotency_key = ?",
                Integer.class,
                idempotencyKey
        );
        return count != null && count > 0;
    }

    @Override
    public BigDecimal currentBalance(Long accountId, String currency) {
        BigDecimal balance = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(delta), 0) FROM ledger_entries WHERE account_id = ? AND currency = ?",
                BigDecimal.class,
                accountId,
                currency
        );
        if (balance == null) {
            return BigDecimal.ZERO;
        }
        return balance;
    }

    @Override
    public void insertEntry(LedgerPostingEntry entry) {
        jdbcTemplate.update(
                """
                        INSERT INTO ledger_entries (
                            entry_id, account_id, currency, delta, balance_after, direction, ref_type, ref_id,
                            idempotency_key, trace_id, ts
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                entry.entryId(),
                entry.accountId(),
                entry.currency(),
                entry.delta(),
                entry.balanceAfter(),
                entry.direction().name(),
                entry.refType(),
                entry.refId(),
                entry.idempotencyKey(),
                entry.traceId(),
                Timestamp.from(entry.ts())
        );
    }

    @Override
    public void insertLedgerEvent(String entryId, String eventType, String payloadJson, String traceId) {
        jdbcTemplate.update(
                """
                        INSERT INTO ledger_events (entry_id, event_type, payload_json, trace_id)
                        VALUES (?, ?, CAST(? AS jsonb), ?)
                        """,
                entryId,
                eventType,
                payloadJson,
                traceId
        );
    }

    @Override
    public void insertAccountSnapshot(AccountSnapshotProjection snapshot) {
        jdbcTemplate.update(
                """
                        INSERT INTO account_snapshots (
                            account_id, currency, balance, available, frozen, ts, trace_id
                        ) VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                snapshot.accountId(),
                snapshot.currency(),
                snapshot.balance(),
                snapshot.available(),
                snapshot.frozen(),
                Timestamp.from(snapshot.snapshotTs()),
                snapshot.traceId()
        );
    }

    @Override
    public Optional<PositionProjection> findPosition(Long accountId, String symbol) {
        List<PositionProjection> rows = jdbcTemplate.query(
                """
                        SELECT account_id, symbol, qty, available_qty, avg_price, trace_id
                        FROM positions
                        WHERE account_id = ? AND symbol = ?
                        """,
                POSITION_ROW_MAPPER,
                accountId,
                symbol
        );
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rows.getFirst());
    }

    @Override
    public void upsertPosition(PositionProjection projection, Instant updatedAt) {
        jdbcTemplate.update(
                """
                        INSERT INTO positions (
                            account_id, symbol, qty, available_qty, frozen_qty, avg_price, trace_id, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (account_id, symbol)
                        DO UPDATE SET
                            qty = EXCLUDED.qty,
                            available_qty = EXCLUDED.available_qty,
                            avg_price = EXCLUDED.avg_price,
                            trace_id = EXCLUDED.trace_id,
                            updated_at = EXCLUDED.updated_at
                        """,
                projection.accountId(),
                projection.symbol(),
                projection.qty(),
                projection.availableQty(),
                BigDecimal.ZERO,
                projection.avgPrice(),
                projection.traceId(),
                Timestamp.from(updatedAt)
        );
    }

    private static PositionProjection mapPosition(ResultSet resultSet, int rowNum) throws SQLException {
        return new PositionProjection(
                resultSet.getLong("account_id"),
                resultSet.getString("symbol"),
                resultSet.getBigDecimal("qty"),
                resultSet.getBigDecimal("available_qty"),
                resultSet.getBigDecimal("avg_price"),
                resultSet.getString("trace_id")
        );
    }
}
