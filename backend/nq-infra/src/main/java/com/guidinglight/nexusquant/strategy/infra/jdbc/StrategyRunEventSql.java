package com.guidinglight.nexusquant.strategy.infra.jdbc;

/** 事件归属只认正式合同及canonical身份；各分支类型/作用域互斥，避免UNION去重掩盖身份冲突。 */
final class StrategyRunEventSql {
    private StrategyRunEventSql() {}

    /** 所有者条件复用已有运行关系读取边界，存在性与以下查询由同次RR事务保护。 */
    static String withLinkedEvents(String visibility) {
        return """
WITH visible_run AS MATERIALIZED (
 SELECT r.* FROM strategy_runs r WHERE r.strategy_run_id=?
            """
                + visibility
                + """

), related_orders AS NOT MATERIALIZED (
 SELECT o.* FROM orders o
 JOIN visible_run r ON o.strategy_run_id=r.strategy_run_id

 AND o.account_id=r.account_id
 AND o.trade_env=r.trade_env

 AND upper(btrim(o.exchange_code))=upper(btrim(r.exchange_code))
), related_trades AS NOT MATERIALIZED (
 SELECT t.* FROM trades t
 JOIN related_orders o ON t.order_id=o.order_id

 AND t.strategy_run_id=o.strategy_run_id
 AND t.account_id=o.account_id
 AND t.trade_env=o.trade_env

 AND upper(btrim(t.exchange_code))=upper(btrim(o.exchange_code))
 AND t.symbol=o.symbol
), linked_events AS (
SELECT e.*,o.order_id AS matched_order_id,NULL::varchar AS matched_trade_id,o.account_id AS matched_account_id,'ORDER_ID'::text AS association
 FROM related_orders o
 JOIN event_store e ON e.payload_json#>>'{payload,order_id}'=o.order_id
 WHERE e.event_type IN ('PlaceOrderCommand','CancelOrderCommand','OrderCreated','OrderSubmitted','OrderStatusChangedPayload','RiskPassed','RiskRejected')
 AND jsonb_typeof(e.payload_json#>'{payload,order_id}')='string'
 AND char_length(e.payload_json#>>'{payload,order_id}') BETWEEN 1
 AND 128
 AND e.schema_version=1
 AND jsonb_typeof(e.payload_json)='object'

 AND e.payload_json->>'event_id'=e.event_id
 AND e.payload_json->>'type'=e.event_type

 AND jsonb_typeof(e.payload_json->'version')='number'
 AND e.payload_json->>'version'='1'

 AND e.payload_json->>'trace_id'=e.trace_id
 AND e.payload_json->>'key'=e.key_value

 AND jsonb_typeof(e.payload_json->'payload')='object'
 AND ((e.event_type IN ('PlaceOrderCommand','CancelOrderCommand')
 AND e.topic='order.command.v1')

 OR (e.event_type IN ('OrderCreated','OrderSubmitted','OrderStatusChangedPayload','RiskPassed','RiskRejected')
 AND e.topic='order.event.v1'))

 AND e.key_value=o.client_order_id
 AND e.payload_json#>>'{payload,client_order_id}'=o.client_order_id

 AND (e.event_type NOT IN ('OrderCreated','OrderStatusChangedPayload','PlaceOrderCommand','CancelOrderCommand')
 OR (jsonb_typeof(e.payload_json#>'{payload,account_id}')='number'
 AND e.payload_json#>>'{payload,account_id}'=o.account_id::text))

 AND (e.event_type<>'OrderCreated'
 OR e.payload_json#>>'{payload,strategy_run_id}'=o.strategy_run_id)

 AND (e.payload_json#>'{payload,account_id}' IS NULL
 OR e.payload_json#>'{payload,account_id}'='null'::jsonb
 OR (jsonb_typeof(e.payload_json#>'{payload,account_id}')='number'
 AND e.payload_json#>>'{payload,account_id}'=o.account_id::text))
 AND (e.payload_json#>'{payload,trade_env}' IS NULL
 OR e.payload_json#>'{payload,trade_env}'='null'::jsonb
 OR (jsonb_typeof(e.payload_json#>'{payload,trade_env}')='string'
 AND e.payload_json#>>'{payload,trade_env}'=o.trade_env))
 AND (e.payload_json#>'{payload,strategy_run_id}' IS NULL
 OR e.payload_json#>'{payload,strategy_run_id}'='null'::jsonb
 OR (jsonb_typeof(e.payload_json#>'{payload,strategy_run_id}')='string'
 AND e.payload_json#>>'{payload,strategy_run_id}'=o.strategy_run_id))
 AND (e.payload_json#>'{payload,venue}' IS NULL
 OR e.payload_json#>'{payload,venue}'='null'::jsonb
 OR (jsonb_typeof(e.payload_json#>'{payload,venue}')='string'
 AND e.payload_json#>>'{payload,venue}'=o.venue))
 AND (e.payload_json#>'{payload,symbol}' IS NULL
 OR e.payload_json#>'{payload,symbol}'='null'::jsonb
 OR (jsonb_typeof(e.payload_json#>'{payload,symbol}')='string'
 AND e.payload_json#>>'{payload,symbol}'=o.symbol))
 UNION ALL
SELECT e.*,o.order_id AS matched_order_id,t.trade_id AS matched_trade_id,o.account_id AS matched_account_id,'TRADE_ID'::text AS association
 FROM related_trades t
 JOIN related_orders o ON o.order_id=t.order_id
 JOIN event_store e ON e.payload_json#>>'{payload,trade_id}'=t.trade_id
 WHERE e.event_type IN ('TradeExecuted','LedgerPosted','LedgerPostFailed')
 AND jsonb_typeof(e.payload_json#>'{payload,trade_id}')='string'
 AND char_length(e.payload_json#>>'{payload,trade_id}') BETWEEN 1
 AND 128
 AND e.schema_version=1
 AND jsonb_typeof(e.payload_json)='object'

 AND e.payload_json->>'event_id'=e.event_id
 AND e.payload_json->>'type'=e.event_type

 AND jsonb_typeof(e.payload_json->'version')='number'
 AND e.payload_json->>'version'='1'

 AND e.payload_json->>'trace_id'=e.trace_id
 AND e.payload_json->>'key'=e.key_value

 AND jsonb_typeof(e.payload_json->'payload')='object'
 AND ((e.event_type='TradeExecuted'
 AND e.topic='trade.event.v1'

 AND e.key_value=o.client_order_id
 AND e.payload_json#>>'{payload,order_id}'=o.order_id

 AND e.payload_json#>>'{payload,client_order_id}'=o.client_order_id

 AND e.payload_json#>>'{payload,venue}'=o.venue
 AND e.payload_json#>>'{payload,exchange}'=t.exchange)

 OR (e.event_type IN ('LedgerPosted','LedgerPostFailed')
 AND e.topic='ledger.event.v1'
 AND e.key_value=t.trade_id))

 AND jsonb_typeof(e.payload_json#>'{payload,account_id}')='number'
 AND e.payload_json#>>'{payload,account_id}'=o.account_id::text
 AND (e.payload_json#>'{payload,account_id}' IS NULL
 OR e.payload_json#>'{payload,account_id}'='null'::jsonb
 OR (jsonb_typeof(e.payload_json#>'{payload,account_id}')='number'
 AND e.payload_json#>>'{payload,account_id}'=o.account_id::text))
 AND (e.payload_json#>'{payload,trade_env}' IS NULL
 OR e.payload_json#>'{payload,trade_env}'='null'::jsonb
 OR (jsonb_typeof(e.payload_json#>'{payload,trade_env}')='string'
 AND e.payload_json#>>'{payload,trade_env}'=o.trade_env))
 AND (e.payload_json#>'{payload,strategy_run_id}' IS NULL
 OR e.payload_json#>'{payload,strategy_run_id}'='null'::jsonb
 OR (jsonb_typeof(e.payload_json#>'{payload,strategy_run_id}')='string'
 AND e.payload_json#>>'{payload,strategy_run_id}'=o.strategy_run_id))
 AND (e.payload_json#>'{payload,venue}' IS NULL
 OR e.payload_json#>'{payload,venue}'='null'::jsonb
 OR (jsonb_typeof(e.payload_json#>'{payload,venue}')='string'
 AND e.payload_json#>>'{payload,venue}'=o.venue))
 AND (e.payload_json#>'{payload,symbol}' IS NULL
 OR e.payload_json#>'{payload,symbol}'='null'::jsonb
 OR (jsonb_typeof(e.payload_json#>'{payload,symbol}')='string'
 AND e.payload_json#>>'{payload,symbol}'=o.symbol))
 UNION ALL
SELECT e.*,o.order_id AS matched_order_id,NULL::varchar AS matched_trade_id,o.account_id AS matched_account_id,'ORDER_RISK'::text AS association
 FROM related_orders o
 JOIN event_store e ON e.payload_json#>>'{payload,scope_id}'=o.order_id
 WHERE e.event_type='RiskEventRaised'
 AND jsonb_typeof(e.payload_json#>'{payload,scope_id}')='string'
 AND char_length(e.payload_json#>>'{payload,scope_id}') BETWEEN 1
 AND 128
 AND e.payload_json#>>'{payload,scope}' IN ('ORDER','LEDGER')
 AND e.schema_version=1
 AND jsonb_typeof(e.payload_json)='object'

 AND e.payload_json->>'event_id'=e.event_id
 AND e.payload_json->>'type'=e.event_type

 AND jsonb_typeof(e.payload_json->'version')='number'
 AND e.payload_json->>'version'='1'

 AND e.payload_json->>'trace_id'=e.trace_id
 AND e.payload_json->>'key'=e.key_value

 AND jsonb_typeof(e.payload_json->'payload')='object'
 AND e.topic='risk.event.v1'
 AND (e.payload_json#>'{payload,account_id}' IS NULL
 OR e.payload_json#>'{payload,account_id}'='null'::jsonb
 OR (jsonb_typeof(e.payload_json#>'{payload,account_id}')='number'
 AND e.payload_json#>>'{payload,account_id}'=o.account_id::text))
 AND (e.payload_json#>'{payload,trade_env}' IS NULL
 OR e.payload_json#>'{payload,trade_env}'='null'::jsonb
 OR (jsonb_typeof(e.payload_json#>'{payload,trade_env}')='string'
 AND e.payload_json#>>'{payload,trade_env}'=o.trade_env))
 AND (e.payload_json#>'{payload,strategy_run_id}' IS NULL
 OR e.payload_json#>'{payload,strategy_run_id}'='null'::jsonb
 OR (jsonb_typeof(e.payload_json#>'{payload,strategy_run_id}')='string'
 AND e.payload_json#>>'{payload,strategy_run_id}'=o.strategy_run_id))
 AND (e.payload_json#>'{payload,venue}' IS NULL
 OR e.payload_json#>'{payload,venue}'='null'::jsonb
 OR (jsonb_typeof(e.payload_json#>'{payload,venue}')='string'
 AND e.payload_json#>>'{payload,venue}'=o.venue))
 AND (e.payload_json#>'{payload,symbol}' IS NULL
 OR e.payload_json#>'{payload,symbol}'='null'::jsonb
 OR (jsonb_typeof(e.payload_json#>'{payload,symbol}')='string'
 AND e.payload_json#>>'{payload,symbol}'=o.symbol))
 AND e.payload_json#>>'{payload,scope}'='ORDER'
 AND e.key_value=o.client_order_id
 UNION ALL
SELECT e.*,o.order_id AS matched_order_id,t.trade_id AS matched_trade_id,o.account_id AS matched_account_id,'TRADE_RISK'::text AS association
 FROM related_trades t
 JOIN related_orders o ON o.order_id=t.order_id
 JOIN event_store e ON e.payload_json#>>'{payload,scope_id}'=t.trade_id
 WHERE e.event_type='RiskEventRaised'
 AND jsonb_typeof(e.payload_json#>'{payload,scope_id}')='string'
 AND char_length(e.payload_json#>>'{payload,scope_id}') BETWEEN 1
 AND 128
 AND e.payload_json#>>'{payload,scope}' IN ('ORDER','LEDGER')
 AND e.schema_version=1
 AND jsonb_typeof(e.payload_json)='object'

 AND e.payload_json->>'event_id'=e.event_id
 AND e.payload_json->>'type'=e.event_type

 AND jsonb_typeof(e.payload_json->'version')='number'
 AND e.payload_json->>'version'='1'

 AND e.payload_json->>'trace_id'=e.trace_id
 AND e.payload_json->>'key'=e.key_value

 AND jsonb_typeof(e.payload_json->'payload')='object'
 AND e.topic='risk.event.v1'
 AND (e.payload_json#>'{payload,account_id}' IS NULL
 OR e.payload_json#>'{payload,account_id}'='null'::jsonb
 OR (jsonb_typeof(e.payload_json#>'{payload,account_id}')='number'
 AND e.payload_json#>>'{payload,account_id}'=o.account_id::text))
 AND (e.payload_json#>'{payload,trade_env}' IS NULL
 OR e.payload_json#>'{payload,trade_env}'='null'::jsonb
 OR (jsonb_typeof(e.payload_json#>'{payload,trade_env}')='string'
 AND e.payload_json#>>'{payload,trade_env}'=o.trade_env))
 AND (e.payload_json#>'{payload,strategy_run_id}' IS NULL
 OR e.payload_json#>'{payload,strategy_run_id}'='null'::jsonb
 OR (jsonb_typeof(e.payload_json#>'{payload,strategy_run_id}')='string'
 AND e.payload_json#>>'{payload,strategy_run_id}'=o.strategy_run_id))
 AND (e.payload_json#>'{payload,venue}' IS NULL
 OR e.payload_json#>'{payload,venue}'='null'::jsonb
 OR (jsonb_typeof(e.payload_json#>'{payload,venue}')='string'
 AND e.payload_json#>>'{payload,venue}'=o.venue))
 AND (e.payload_json#>'{payload,symbol}' IS NULL
 OR e.payload_json#>'{payload,symbol}'='null'::jsonb
 OR (jsonb_typeof(e.payload_json#>'{payload,symbol}')='string'
 AND e.payload_json#>>'{payload,symbol}'=o.symbol))
 AND e.payload_json#>>'{payload,scope}'='LEDGER'
 AND e.key_value=t.trade_id
 UNION ALL
SELECT e.*,o.order_id AS matched_order_id,NULL::varchar AS matched_trade_id,o.account_id AS matched_account_id,'ACCOUNT_CLIENT_ORDER'::text AS association
 FROM related_orders o
 JOIN event_store e ON e.payload_json#>>'{payload,account_id}'=o.account_id::text
 AND upper(btrim(e.payload_json#>>'{payload,venue}'))=upper(btrim(o.venue))
 AND (CASE WHEN nullif(e.payload_json#>>'{payload,client_order_id}','') IS NOT NULL THEN 'C:'||(e.payload_json#>>'{payload,client_order_id}') ELSE 'E:'||(e.payload_json#>>'{payload,external_order_id}') END)='C:'||o.client_order_id
 WHERE e.event_type IN ('OrderAck','OrderReject','CancelAck','CancelReject')
 AND jsonb_typeof(e.payload_json#>'{payload,account_id}')='number'
 AND char_length(e.payload_json#>>'{payload,account_id}') BETWEEN 1
 AND 19
 AND jsonb_typeof(e.payload_json#>'{payload,venue}')='string'
 AND char_length(e.payload_json#>>'{payload,venue}') BETWEEN 1
 AND 32
 AND ((jsonb_typeof(e.payload_json#>'{payload,client_order_id}')='string'
 AND char_length(e.payload_json#>>'{payload,client_order_id}') BETWEEN 1
 AND 128)
 OR ((e.payload_json#>>'{payload,client_order_id}') IS NULL
 OR e.payload_json#>>'{payload,client_order_id}'='')
 AND jsonb_typeof(e.payload_json#>'{payload,external_order_id}')='string'
 AND char_length(e.payload_json#>>'{payload,external_order_id}') BETWEEN 1
 AND 128)
 AND e.schema_version=1
 AND jsonb_typeof(e.payload_json)='object'

 AND e.payload_json->>'event_id'=e.event_id
 AND e.payload_json->>'type'=e.event_type

 AND jsonb_typeof(e.payload_json->'version')='number'
 AND e.payload_json->>'version'='1'

 AND e.payload_json->>'trace_id'=e.trace_id
 AND e.payload_json->>'key'=e.key_value

 AND jsonb_typeof(e.payload_json->'payload')='object'
 AND e.topic='order.event.v1'
 AND jsonb_typeof(e.payload_json#>'{payload,account_id}')='number'
 AND e.payload_json#>>'{payload,account_id}'=o.account_id::text
 AND (e.payload_json#>'{payload,trade_env}' IS NULL
 OR e.payload_json#>'{payload,trade_env}'='null'::jsonb
 OR (jsonb_typeof(e.payload_json#>'{payload,trade_env}')='string'
 AND e.payload_json#>>'{payload,trade_env}'=o.trade_env))
 AND upper(btrim(e.payload_json#>>'{payload,venue}'))=upper(btrim(o.venue))
 AND (e.payload_json#>'{payload,strategy_run_id}' IS NULL
 OR e.payload_json#>'{payload,strategy_run_id}'='null'::jsonb
 OR (jsonb_typeof(e.payload_json#>'{payload,strategy_run_id}')='string'
 AND e.payload_json#>>'{payload,strategy_run_id}'=o.strategy_run_id))
 AND e.key_value=o.client_order_id
 AND nullif(e.payload_json#>>'{payload,client_order_id}','') IS NOT NULL
 AND (e.payload_json#>'{payload,external_order_id}' IS NULL
 OR e.payload_json#>'{payload,external_order_id}'='null'::jsonb
 OR (jsonb_typeof(e.payload_json#>'{payload,external_order_id}')='string'
 AND e.payload_json#>>'{payload,external_order_id}'=coalesce(o.external_order_id,e.payload_json#>>'{payload,external_order_id}')))
 UNION ALL
SELECT e.*,o.order_id AS matched_order_id,NULL::varchar AS matched_trade_id,o.account_id AS matched_account_id,'ACCOUNT_EXTERNAL_ORDER'::text AS association
 FROM related_orders o
 JOIN event_store e ON e.payload_json#>>'{payload,account_id}'=o.account_id::text
 AND upper(btrim(e.payload_json#>>'{payload,venue}'))=upper(btrim(o.venue))
 AND (CASE WHEN nullif(e.payload_json#>>'{payload,client_order_id}','') IS NOT NULL THEN 'C:'||(e.payload_json#>>'{payload,client_order_id}') ELSE 'E:'||(e.payload_json#>>'{payload,external_order_id}') END)='E:'||o.external_order_id
 WHERE e.event_type IN ('OrderAck','OrderReject','CancelAck','CancelReject')
 AND jsonb_typeof(e.payload_json#>'{payload,account_id}')='number'
 AND char_length(e.payload_json#>>'{payload,account_id}') BETWEEN 1
 AND 19
 AND jsonb_typeof(e.payload_json#>'{payload,venue}')='string'
 AND char_length(e.payload_json#>>'{payload,venue}') BETWEEN 1
 AND 32
 AND ((jsonb_typeof(e.payload_json#>'{payload,client_order_id}')='string'
 AND char_length(e.payload_json#>>'{payload,client_order_id}') BETWEEN 1
 AND 128)
 OR ((e.payload_json#>>'{payload,client_order_id}') IS NULL
 OR e.payload_json#>>'{payload,client_order_id}'='')
 AND jsonb_typeof(e.payload_json#>'{payload,external_order_id}')='string'
 AND char_length(e.payload_json#>>'{payload,external_order_id}') BETWEEN 1
 AND 128)
 AND e.schema_version=1
 AND jsonb_typeof(e.payload_json)='object'

 AND e.payload_json->>'event_id'=e.event_id
 AND e.payload_json->>'type'=e.event_type

 AND jsonb_typeof(e.payload_json->'version')='number'
 AND e.payload_json->>'version'='1'

 AND e.payload_json->>'trace_id'=e.trace_id
 AND e.payload_json->>'key'=e.key_value

 AND jsonb_typeof(e.payload_json->'payload')='object'
 AND e.topic='order.event.v1'
 AND jsonb_typeof(e.payload_json#>'{payload,account_id}')='number'
 AND e.payload_json#>>'{payload,account_id}'=o.account_id::text
 AND (e.payload_json#>'{payload,trade_env}' IS NULL
 OR e.payload_json#>'{payload,trade_env}'='null'::jsonb
 OR (jsonb_typeof(e.payload_json#>'{payload,trade_env}')='string'
 AND e.payload_json#>>'{payload,trade_env}'=o.trade_env))
 AND upper(btrim(e.payload_json#>>'{payload,venue}'))=upper(btrim(o.venue))
 AND (e.payload_json#>'{payload,strategy_run_id}' IS NULL
 OR e.payload_json#>'{payload,strategy_run_id}'='null'::jsonb
 OR (jsonb_typeof(e.payload_json#>'{payload,strategy_run_id}')='string'
 AND e.payload_json#>>'{payload,strategy_run_id}'=o.strategy_run_id))
 AND (e.payload_json#>>'{payload,client_order_id}' IS NULL
 OR e.payload_json#>>'{payload,client_order_id}'='')
 AND e.key_value=o.external_order_id
 AND NOT EXISTS(SELECT 1 FROM orders other WHERE other.account_id=o.account_id
 AND other.venue=o.venue
 AND other.external_order_id=o.external_order_id
 AND other.order_id<>o.order_id)
 UNION ALL
SELECT e.*,o.order_id AS matched_order_id,NULL::varchar AS matched_trade_id,o.account_id AS matched_account_id,'ORDER_TRIGGERED_POSITION'::text AS association
 FROM related_orders o
 JOIN event_store e ON e.key_value=o.order_id
 WHERE e.event_type='PositionUpdated'
 AND e.topic='position.event.v1'
 AND e.payload_json->>'source'='nq-ledger.trade-posting'
 AND e.schema_version=1
 AND jsonb_typeof(e.payload_json)='object'

 AND e.payload_json->>'event_id'=e.event_id
 AND e.payload_json->>'type'=e.event_type

 AND jsonb_typeof(e.payload_json->'version')='number'
 AND e.payload_json->>'version'='1'

 AND e.payload_json->>'trace_id'=e.trace_id
 AND e.payload_json->>'key'=e.key_value

 AND jsonb_typeof(e.payload_json->'payload')='object'
 AND jsonb_typeof(e.payload_json#>'{payload,account_id}')='number'
 AND e.payload_json#>>'{payload,account_id}'=o.account_id::text
 AND e.payload_json#>>'{payload,symbol}'=o.symbol
 AND e.payload_json#>>'{payload,reason}'='TRADE'
 AND (e.payload_json#>'{payload,trade_env}' IS NULL
 OR e.payload_json#>'{payload,trade_env}'='null'::jsonb
 OR (jsonb_typeof(e.payload_json#>'{payload,trade_env}')='string'
 AND e.payload_json#>>'{payload,trade_env}'=o.trade_env))
)
            """;
    }
}
