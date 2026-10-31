-- 仅为正式业务身份读取增加有界索引，不修改事件、交易或已执行V1—V23。
SET LOCAL lock_timeout='5s';
SET LOCAL statement_timeout='60s';

CREATE INDEX idx_event_store_order_business_identity ON event_store ((payload_json#>>'{payload,order_id}'), created_at ASC, event_id ASC)
WHERE schema_version=1
    AND event_type IN ('PlaceOrderCommand','CancelOrderCommand','OrderCreated','OrderSubmitted','OrderStatusChangedPayload','RiskPassed','RiskRejected')
    AND jsonb_typeof(payload_json#>'{payload,order_id}')='string'
    AND char_length(payload_json#>>'{payload,order_id}') BETWEEN 1
    AND 128;
COMMENT ON INDEX idx_event_store_order_business_identity IS '按正式order事件身份定位关联记录；不把共享trace或分区键单独当作运行归属。';

CREATE INDEX idx_event_store_trade_business_identity ON event_store ((payload_json#>>'{payload,trade_id}'), created_at ASC, event_id ASC)
WHERE schema_version=1
    AND event_type IN ('TradeExecuted','LedgerPosted','LedgerPostFailed')
    AND jsonb_typeof(payload_json#>'{payload,trade_id}')='string'
    AND char_length(payload_json#>>'{payload,trade_id}') BETWEEN 1
    AND 128;
COMMENT ON INDEX idx_event_store_trade_business_identity IS '按正式trade事件身份定位关联记录；不把共享trace或分区键单独当作运行归属。';

CREATE INDEX idx_event_store_risk_business_identity ON event_store ((payload_json#>>'{payload,scope}'), (payload_json#>>'{payload,scope_id}'), created_at ASC, event_id ASC)
WHERE schema_version=1
    AND event_type='RiskEventRaised'
    AND jsonb_typeof(payload_json#>'{payload,scope_id}')='string'
    AND char_length(payload_json#>>'{payload,scope_id}') BETWEEN 1
    AND 128
    AND payload_json#>>'{payload,scope}' IN ('ORDER','LEDGER');
COMMENT ON INDEX idx_event_store_risk_business_identity IS '按正式risk事件身份定位关联记录；不把共享trace或分区键单独当作运行归属。';

CREATE INDEX idx_event_store_ack_business_identity ON event_store ((payload_json#>>'{payload,account_id}'), upper(btrim(payload_json#>>'{payload,venue}')), (CASE WHEN nullif(payload_json#>>'{payload,client_order_id}','') IS NOT NULL
        THEN 'C:'||(payload_json#>>'{payload,client_order_id}')
        ELSE 'E:'||(payload_json#>>'{payload,external_order_id}') END), created_at ASC, event_id ASC)
WHERE schema_version=1
    AND event_type IN ('OrderAck','OrderReject','CancelAck','CancelReject')
    AND jsonb_typeof(payload_json#>'{payload,account_id}')='number'
    AND char_length(payload_json#>>'{payload,account_id}') BETWEEN 1
    AND 19
    AND jsonb_typeof(payload_json#>'{payload,venue}')='string'
    AND char_length(payload_json#>>'{payload,venue}') BETWEEN 1
    AND 32
    AND ((jsonb_typeof(payload_json#>'{payload,client_order_id}')='string'
    AND char_length(payload_json#>>'{payload,client_order_id}') BETWEEN 1
    AND 128)
        OR ((payload_json#>>'{payload,client_order_id}') IS NULL
        OR payload_json#>>'{payload,client_order_id}'='')
    AND jsonb_typeof(payload_json#>'{payload,external_order_id}')='string'
    AND char_length(payload_json#>>'{payload,external_order_id}') BETWEEN 1
    AND 128);
COMMENT ON INDEX idx_event_store_ack_business_identity IS '按正式ack事件身份定位关联记录；不把共享trace或分区键单独当作运行归属。';

CREATE INDEX idx_event_store_position_business_identity ON event_store (key_value, created_at ASC, event_id ASC)
WHERE schema_version=1
    AND event_type='PositionUpdated'
    AND topic='position.event.v1'
    AND payload_json->>'source'='nq-ledger.trade-posting';
COMMENT ON INDEX idx_event_store_position_business_identity IS '按正式position事件身份定位关联记录；不把共享trace或分区键单独当作运行归属。';

