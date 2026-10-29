package com.guidinglight.nexusquant.scheduler.infra.jdbc;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 只管理普通 OKX TradeExecuted：锁定 source、验证旧事件、按稳定身份幂等恢复。 */
final class RequiredTradeEventStore {
    private final JdbcTemplate jdbc;

    RequiredTradeEventStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    void ensure(String tradeId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Trade event persistence requires a real transaction");
        }
        // 所有新写入/恢复共用持久 source 行锁；不是无锁 SELECT-then-INSERT。
        String locked = jdbc.queryForObject("SELECT trade_id FROM trades WHERE trade_id=? FOR UPDATE",
                String.class, tradeId);
        if (!tradeId.equals(locked)) throw new IllegalStateException("durable Trade missing");
        var source = jdbc.queryForMap("""
                SELECT o.client_order_id, t.trace_id, t.ts,
                       jsonb_build_object('trade_id',t.trade_id,'order_id',t.order_id,
                         'client_order_id',o.client_order_id,'account_id',t.account_id,
                         'symbol',t.symbol,'venue',o.venue,'exchange',t.exchange,
                         'external_order_id',t.external_order_id,'exchange_trade_id',t.exchange_trade_id,
                         'price',t.price,'qty',t.qty,'fee',t.fee,'fee_currency',t.fee_currency,
                         'ts',t.ts,'trade_env',t.trade_env)::text AS payload
                FROM trades t JOIN orders o ON o.order_id=t.order_id
                WHERE t.trade_id=? AND t.trade_env=o.trade_env AND t.trade_env IN ('SIM','LIVE')
                  AND t.account_id=o.account_id AND t.symbol=o.symbol
                  AND t.exchange='OKX' AND o.venue='OKX'
                  AND t.trace_id=o.trace_id
                  AND t.external_order_id=o.external_order_id
                  AND t.exchange_trade_id IS NOT NULL
                """, tradeId);
        String payload = (String) source.get("payload");
        // 历史随机 event_id 按 durable trade_id 查找；不修改旧 envelope/时间/来源。
        var existing = jdbc.queryForList("""
                SELECT event_id,
                    (((payload_json->'payload')-'trade_env'-'ts') = (CAST(? AS jsonb)-'trade_env'-'ts')
                     AND (NOT jsonb_exists(payload_json->'payload', 'trade_env')
                          OR payload_json->'payload'->>'trade_env'=(CAST(? AS jsonb)->>'trade_env'))
                     AND (payload_json->'payload'->>'ts')::timestamptz=?
                     AND trace_id=? AND key_value=? AND schema_version=1) AS matches
                FROM event_store WHERE topic='trade.event.v1' AND event_type='TradeExecuted'
                  AND payload_json->'payload'->>'trade_id'=? LIMIT 2
                """, payload, payload, source.get("ts"), source.get("trace_id"), source.get("client_order_id"), tradeId);
        if (!existing.isEmpty()) {
            if (existing.size() != 1 || !Boolean.TRUE.equals(existing.getFirst().get("matches"))) {
                throw new IllegalStateException("TRADE_EVENT_IDENTITY_CONFLICT");
            }
            return;
        }
        String eventId = "te-" + UUID.nameUUIDFromBytes(("TradeExecuted:" + tradeId).getBytes(StandardCharsets.UTF_8));
        // V48 event_id 主键提供最后的持久唯一性防线；碰撞/异常绝不吞掉或当成功。
        jdbc.update("""
                INSERT INTO event_store(event_id,topic,schema_version,event_type,payload_json,key_value,trace_id)
                VALUES (?, 'trade.event.v1', 1, 'TradeExecuted',
                    jsonb_build_object('event_id',?,'type','TradeExecuted','version',1,
                      'ts',CAST(? AS timestamptz),'source','nq-scheduler.okx-rest-reconcile',
                      'trace_id',?,'key',?,'payload',CAST(? AS jsonb)),?,?)
                """, eventId, eventId, source.get("ts"), source.get("trace_id"), source.get("client_order_id"),
                payload, source.get("client_order_id"), source.get("trace_id"));
    }
}
