package com.guidinglight.nexusquant.ledger.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.guidinglight.nexusquant.contracts.model.OrderSide;
import com.guidinglight.nexusquant.infra.eventstore.EventStoreAppender;
import com.guidinglight.nexusquant.ledger.model.LedgerPostingEntry;
import com.guidinglight.nexusquant.ledger.model.LedgerPostingResult;
import com.guidinglight.nexusquant.ledger.model.PositionProjection;
import com.guidinglight.nexusquant.ledger.model.TradeLedgerRequest;
import com.guidinglight.nexusquant.ledger.service.port.LedgerPostingRepository;
import com.guidinglight.nexusquant.ledger.service.port.LedgerRiskAuditRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * TradeLedgerPostingServiceTest 覆盖记账幂等与平衡校验分支。
 */
class TradeLedgerPostingServiceTest {

    /**
     * 同一 trade 重复调用时不应重复写分录。
     */
    @Test
    void shouldBeIdempotentForRepeatedTradePosting() {
        InMemoryLedgerPostingRepository postingRepository = new InMemoryLedgerPostingRepository();
        RecordingLedgerRiskAuditRepository riskAuditRepository = new RecordingLedgerRiskAuditRepository();
        RecordingJdbcTemplate eventStoreJdbcTemplate = new RecordingJdbcTemplate();
        TradeLedgerPostingService service = new TradeLedgerPostingService(
                postingRepository,
                riskAuditRepository,
                new EventStoreAppender(eventStoreJdbcTemplate, objectMapper()),
                objectMapper()
        );

        TradeLedgerRequest request = baseRequest("trd-401", BigDecimal.ZERO);
        LedgerPostingResult first = service.postTrade(request);
        LedgerPostingResult second = service.postTrade(request);

        assertTrue(first.posted());
        assertFalse(first.idempotentHit());
        assertTrue(second.posted());
        assertTrue(second.idempotentHit());
        assertEquals(2, postingRepository.entryCount());
        assertEquals(2, postingRepository.ledgerEventCount());
        assertEquals(new BigDecimal("0.01000000"), postingRepository.positionQty("BTC-USDT"));
        assertEquals(2, eventStoreJdbcTemplate.updateCount());
    }

    /**
     * fee 非零时触发平衡校验失败，应写风险/审计并返回失败结果。
     */
    @Test
    void shouldRecordRiskAndAuditWhenBalanceCheckFails() {
        InMemoryLedgerPostingRepository postingRepository = new InMemoryLedgerPostingRepository();
        RecordingLedgerRiskAuditRepository riskAuditRepository = new RecordingLedgerRiskAuditRepository();
        RecordingJdbcTemplate eventStoreJdbcTemplate = new RecordingJdbcTemplate();
        TradeLedgerPostingService service = new TradeLedgerPostingService(
                postingRepository,
                riskAuditRepository,
                new EventStoreAppender(eventStoreJdbcTemplate, objectMapper()),
                objectMapper()
        );

        LedgerPostingResult result = service.postTrade(baseRequest("trd-402", new BigDecimal("0.10000000")));

        assertFalse(result.posted());
        assertEquals("LEDGER_NOT_BALANCED", result.reason());
        assertEquals(0, postingRepository.entryCount());
        assertEquals(1, riskAuditRepository.riskCount());
        assertEquals(1, riskAuditRepository.auditCount());
        assertEquals(2, eventStoreJdbcTemplate.updateCount());
    }

    private TradeLedgerRequest baseRequest(String tradeId, BigDecimal fee) {
        return new TradeLedgerRequest(
                tradeId,
                "ord-" + tradeId,
                1001L,
                "BTC-USDT",
                OrderSide.BUY,
                new BigDecimal("100.00000000"),
                new BigDecimal("0.01000000"),
                fee,
                "USDT",
                "trc-" + tradeId,
                Instant.parse("2026-02-25T12:00:00Z")
        );
    }

    private ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    private static final class InMemoryLedgerPostingRepository implements LedgerPostingRepository {

        private final Map<String, LedgerPostingEntry> entriesByIdempotencyKey = new HashMap<>();
        private final Map<String, PositionProjection> positions = new HashMap<>();
        private int ledgerEventCount;

        @Override
        public boolean existsByIdempotencyKey(String idempotencyKey) {
            return entriesByIdempotencyKey.containsKey(idempotencyKey);
        }

        @Override
        public BigDecimal currentBalance(Long accountId, String currency) {
            return entriesByIdempotencyKey.values().stream()
                    .filter(entry -> entry.accountId().equals(accountId) && entry.currency().equals(currency))
                    .map(LedgerPostingEntry::delta)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        @Override
        public void insertEntry(LedgerPostingEntry entry) {
            entriesByIdempotencyKey.put(entry.idempotencyKey(), entry);
        }

        @Override
        public void insertLedgerEvent(String entryId, String eventType, String payloadJson, String traceId) {
            ledgerEventCount++;
        }

        @Override
        public Optional<PositionProjection> findPosition(Long accountId, String symbol) {
            return Optional.ofNullable(positions.get(key(accountId, symbol)));
        }

        @Override
        public void upsertPosition(PositionProjection projection, Instant updatedAt) {
            positions.put(key(projection.accountId(), projection.symbol()), projection);
        }

        int entryCount() {
            return entriesByIdempotencyKey.size();
        }

        int ledgerEventCount() {
            return ledgerEventCount;
        }

        BigDecimal positionQty(String symbol) {
            return positions.get(key(1001L, symbol)).qty();
        }

        private String key(Long accountId, String symbol) {
            return accountId + ":" + symbol;
        }
    }

    private static final class RecordingLedgerRiskAuditRepository implements LedgerRiskAuditRepository {

        private int riskCount;
        private int auditCount;

        @Override
        public void appendRiskEvent(
                String scope,
                String scopeId,
                String decision,
                String reason,
                String severity,
                String traceId
        ) {
            riskCount++;
        }

        @Override
        public void appendAudit(String domain, String action, String actorId, String traceId, Map<String, Object> detail) {
            auditCount++;
        }

        int riskCount() {
            return riskCount;
        }

        int auditCount() {
            return auditCount;
        }
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {

        private int updateCount;

        @Override
        public int update(String sql, Object... args) {
            updateCount++;
            return 1;
        }

        int updateCount() {
            return updateCount;
        }
    }
}
