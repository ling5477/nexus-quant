\set ON_ERROR_STOP on
SET TIME ZONE 'UTC';
SET client_min_messages = warning;

UPDATE exchange_accounts
SET trade_env='LIVE', updated_at=CURRENT_TIMESTAMP
WHERE exchange_account_id=1;

UPDATE strategy_release_admission_state
SET release_artifact_digest=repeat('a',64), manifest_fingerprint=repeat('b',64),
    manifest_schema_version='strategy-release-manifest.v1', identity_bound_at=CURRENT_TIMESTAMP
WHERE publish_record_id='gy5-publish-1' AND release_artifact_digest IS NULL;

INSERT INTO risk_limit_sets(
    risk_limit_set_id,digest_schema_version,version,effective_scope,quote_currency,capital_cap,
    max_order_notional,max_symbol_position_notional,max_daily_realized_loss,max_daily_total_loss,
    max_open_orders,max_intraday_orders,symbol_allowlist,order_type_allowlist,max_session_duration_seconds,
    spread_limit_bps,slippage_limit_bps,max_market_data_age_ms,min_data_coverage_bps,required_data_source,
    data_quality_action,canonical_digest,created_by,created_at)
VALUES ('50000000-0000-0000-0000-000000000001','risk-limit-set.v1',1,'LIVE_SESSION_OKX_SPOT',
        'USDT',100,10,50,5,10,3,20,ARRAY['BTC-USDT'],ARRAY['LIMIT'],600,15,20,1000,9900,
        'OKX_PRIMARY','BLOCK',repeat('c',64),1,CURRENT_TIMESTAMP);

INSERT INTO live_sessions(
    session_id,owner_id,exchange_account_id,venue,strategy_release_id,release_digest,
    release_admission_revision,risk_limit_set_id,risk_limit_set_digest,credential_reference,
    symbol_allowlist,capital_cap,execution_window_start,execution_window_end,state,version,
    approval_scope_hash,approval_scope_schema_version,next_event_sequence,created_by,created_at,updated_at)
VALUES ('60000000-0000-0000-0000-000000000001',1,1,'OKX_SPOT','gy5-publish-1',repeat('a',64),1,
        '50000000-0000-0000-0000-000000000001',repeat('c',64),1,ARRAY['BTC-USDT'],100,
        CURRENT_TIMESTAMP-INTERVAL '1 hour',CURRENT_TIMESTAMP+INTERVAL '4 hours','APPROVAL_PENDING',1,
        repeat('d',64),'approval-scope.v1',1,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
UPDATE live_sessions SET state='APPROVED',version=version+1,updated_at=CURRENT_TIMESTAMP WHERE session_id='60000000-0000-0000-0000-000000000001';
UPDATE live_sessions SET state='LIVE_WARMUP',version=version+1,updated_at=CURRENT_TIMESTAMP WHERE session_id='60000000-0000-0000-0000-000000000001';
UPDATE live_sessions SET state='LIVE_ACTIVE',version=version+1,updated_at=CURRENT_TIMESTAMP WHERE session_id='60000000-0000-0000-0000-000000000001';

INSERT INTO orders(order_id,account_id,strategy_run_id,symbol,client_order_id,side,type,price,qty,status,
                   reason,trace_id,created_at,updated_at,request_id,dedup_key,exchange_code,trade_env,venue)
SELECT 'fake-worker-order-'||n,1,'gy5-run-1','BTC-USDT',client_id,'BUY','LIMIT',100,1,'CREATED',
       'disposable fake worker fixture','fake-worker-trace-'||n,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,
       'fake-worker-request-'||n,'fake-worker-dedup-'||n,'OKX','LIVE','OKX'
FROM (VALUES
 (1,'crash-before-send'),(2,'crash-after-send'),(3,'crash-after-mutation'),
 (4,'receipt-failure'),(5,'duplicate-worker'),(6,'kill-after-claim'),
 (7,'kill-after-send'),(8,'rollback-release'),(9,'partial-fill'),
 (10,'late-fill'),(11,'cancel-race'),(12,'unknown-observation'),
 (13,'restore-send-started'),(14,'restore-unknown')) AS cases(n,client_id);

INSERT INTO execution_intents(intent_id,session_id,sequence,action,symbol,side,order_type,quantity,
    limit_price,payload_hash_schema_version,payload_hash,client_order_id,local_order_id,state,version)
SELECT ('70000000-0000-0000-0000-'||lpad(n::text,12,'0'))::uuid,
       '60000000-0000-0000-0000-000000000001',n,'PLACE','BTC-USDT','BUY','LIMIT',1,100,
       'execution-intent-payload.v1',encode(digest('fake-worker-payload-'||n,'sha256'),'hex'),
       client_id,'fake-worker-order-'||n,'CREATED',1
FROM (VALUES
 (1,'crash-before-send'),(2,'crash-after-send'),(3,'crash-after-mutation'),
 (4,'receipt-failure'),(5,'duplicate-worker'),(6,'kill-after-claim'),
 (7,'kill-after-send'),(8,'rollback-release'),(9,'partial-fill'),
 (10,'late-fill'),(11,'cancel-race'),(12,'unknown-observation'),
 (13,'restore-send-started'),(14,'restore-unknown')) AS cases(n,client_id);

UPDATE kill_switch_states
SET status='DISENGAGED',version=version+1,reason_code='DISPOSABLE_FAKE_WORKER_FIXTURE',
    source='DISPOSABLE_FIXTURE',updated_at=CURRENT_TIMESTAMP,updated_by='fixture',trace_id='fixture'
WHERE scope='GLOBAL_TRADING';
