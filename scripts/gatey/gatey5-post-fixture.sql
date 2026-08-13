\set ON_ERROR_STOP on
SET TIME ZONE 'UTC';
SET client_min_messages = warning;

-- Bind only the release rows consumed by sessions. The V38 immutable-identity
-- trigger performs the first binding and advances revision exactly once.
UPDATE strategy_release_admission_state
SET release_artifact_digest = encode(digest('gatey-production-like-scale-v1:release:' || publish_record_id, 'sha256'), 'hex'),
    manifest_fingerprint = encode(digest('gatey-production-like-scale-v1:manifest:' || publish_record_id, 'sha256'), 'hex'),
    manifest_schema_version = 'strategy-release-manifest.v1',
    identity_bound_at = TIMESTAMPTZ '2025-01-03 00:00:00+00'
WHERE publish_record_id IN (
    SELECT 'gy5-publish-' || n FROM generate_series(1, :live_sessions) n
);

INSERT INTO risk_limit_sets (
    risk_limit_set_id, digest_schema_version, version, effective_scope, quote_currency,
    capital_cap, max_order_notional, max_symbol_position_notional,
    max_daily_realized_loss, max_daily_total_loss, max_open_orders, max_intraday_orders,
    symbol_allowlist, order_type_allowlist, max_session_duration_seconds,
    spread_limit_bps, slippage_limit_bps, max_market_data_age_ms, min_data_coverage_bps,
    required_data_source, data_quality_action, canonical_digest, created_by, created_at)
SELECT (substr(h,1,8)||'-'||substr(h,9,4)||'-'||substr(h,13,4)||'-'||substr(h,17,4)||'-'||substr(h,21,12))::uuid,
       'risk-limit-set.v1', n, 'LIVE_SESSION_OKX_SPOT', 'USDT', 10000, 1000, 5000,
       500, 1000, 20, 200, ARRAY['BTC-USDT'], ARRAY['LIMIT'], 14400,
       100, 100, 5000, 10000, 'OKX_PRIMARY', 'BLOCK',
       encode(digest('gatey-production-like-scale-v1:risk-digest:' || n, 'sha256'), 'hex'),
       ((n-1) % :users) + 1,
       TIMESTAMPTZ '2025-01-03 00:00:00+00' + (n % 86400) * INTERVAL '1 second'
FROM generate_series(1, :risk_limit_sets) n
CROSS JOIN LATERAL (SELECT encode(digest('gatey-production-like-scale-v1:risk_limit_sets:' || n, 'sha256'), 'hex') h) d;

-- Insert at most one non-terminal session per exchange account, then transition
-- that batch to terminal KILLED before the account slot is reused.
CREATE TEMP TABLE gatey5_fixture_params (
    live_sessions INTEGER NOT NULL,
    exchange_accounts INTEGER NOT NULL,
    users INTEGER NOT NULL,
    risk_limit_sets INTEGER NOT NULL
);
INSERT INTO gatey5_fixture_params VALUES (
    :live_sessions, :exchange_accounts, :users, :risk_limit_sets
);

DO $$
DECLARE
    batch_start INTEGER := 1;
    batch_end INTEGER;
    session_target INTEGER;
    account_target INTEGER;
    user_target INTEGER;
    risk_target INTEGER;
BEGIN
    SELECT live_sessions, exchange_accounts, users, risk_limit_sets
    INTO session_target, account_target, user_target, risk_target
    FROM gatey5_fixture_params;
    WHILE batch_start <= session_target LOOP
        batch_end := LEAST(batch_start + account_target - 1, session_target);
        INSERT INTO live_sessions (
            session_id, owner_id, exchange_account_id, venue, strategy_release_id,
            release_digest, release_admission_revision, risk_limit_set_id,
            risk_limit_set_digest, credential_reference, symbol_allowlist, capital_cap,
            execution_window_start, execution_window_end, state, version,
            approval_scope_hash, approval_scope_schema_version, next_event_sequence,
            created_by, created_at, updated_at)
        SELECT session_id,
               ((n-1) % user_target) + 1, ((n-1) % account_target) + 1, 'OKX_SPOT',
               'gy5-publish-' || n,
               encode(digest('gatey-production-like-scale-v1:release:gy5-publish-' || n, 'sha256'), 'hex'),
               1, risk_id,
               encode(digest('gatey-production-like-scale-v1:risk-digest:' || (((n-1) % risk_target)+1), 'sha256'), 'hex'),
               n, ARRAY['BTC-USDT'], 10000,
               TIMESTAMPTZ '2025-01-04 00:00:00+00' + (n % 86400) * INTERVAL '1 second',
               TIMESTAMPTZ '2025-01-04 04:00:00+00' + (n % 86400) * INTERVAL '1 second',
               'APPROVAL_PENDING', 1,
               encode(digest('gatey-production-like-scale-v1:approval-scope:' || n, 'sha256'), 'hex'),
               'approval-scope.v1', 1, ((n-1) % user_target) + 1,
               TIMESTAMPTZ '2025-01-03 00:00:00+00' + (n % 86400) * INTERVAL '1 second',
               TIMESTAMPTZ '2025-01-03 00:00:00+00' + (n % 86400) * INTERVAL '1 second'
        FROM generate_series(batch_start, batch_end) n
        CROSS JOIN LATERAL (
            SELECT encode(digest('gatey-production-like-scale-v1:live_sessions:' || n, 'sha256'), 'hex') h
        ) digest_source
        CROSS JOIN LATERAL (
            SELECT (substr(h,1,8)||'-'||substr(h,9,4)||'-'||substr(h,13,4)||'-'||substr(h,17,4)||'-'||substr(h,21,12))::uuid session_id
        ) session_source
        CROSS JOIN LATERAL (
            SELECT encode(digest('gatey-production-like-scale-v1:risk_limit_sets:' || (((n-1) % risk_target)+1), 'sha256'), 'hex') rh
        ) risk_digest_source
        CROSS JOIN LATERAL (
            SELECT (substr(rh,1,8)||'-'||substr(rh,9,4)||'-'||substr(rh,13,4)||'-'||substr(rh,17,4)||'-'||substr(rh,21,12))::uuid risk_id
        ) risk_source;

        UPDATE live_sessions
        SET state='KILLED', version=version+1, updated_at=updated_at + INTERVAL '1 second'
        WHERE session_id IN (
            SELECT (substr(h,1,8)||'-'||substr(h,9,4)||'-'||substr(h,13,4)||'-'||substr(h,17,4)||'-'||substr(h,21,12))::uuid
            FROM generate_series(batch_start, batch_end) n
            CROSS JOIN LATERAL (SELECT encode(digest('gatey-production-like-scale-v1:live_sessions:' || n, 'sha256'), 'hex') h) d
        );
        batch_start := batch_end + 1;
    END LOOP;
END $$;

INSERT INTO live_session_events (
    event_id, session_id, sequence_no, from_state, to_state, command, actor_id,
    request_id, trace_id, reason_code, idempotency_key, command_payload_hash,
    command_payload_schema_version, metadata, created_at)
SELECT event_id, session_id, ((n-1) % :events_per_session) + 1,
       'APPROVAL_PENDING', 'KILLED', 'FIXTURE_TERMINAL', ((session_no-1) % :users)+1,
       'gy5-event-request-' || n, 'gy5-event-trace-' || n, 'FIXTURE_ONLY',
       'gy5-event-idempotency-' || n,
       encode(digest('gatey-production-like-scale-v1:event-payload:' || n, 'sha256'), 'hex'),
       'live-session-command.v1', '{}',
       TIMESTAMPTZ '2025-01-05 00:00:00+00' + (n % 86400) * INTERVAL '1 second'
FROM generate_series(1, :live_session_events) n
CROSS JOIN LATERAL (SELECT ((n-1) / :events_per_session) + 1 session_no) s
CROSS JOIN LATERAL (SELECT encode(digest('gatey-production-like-scale-v1:live_session_events:' || n, 'sha256'), 'hex') eh) ed
CROSS JOIN LATERAL (SELECT (substr(eh,1,8)||'-'||substr(eh,9,4)||'-'||substr(eh,13,4)||'-'||substr(eh,17,4)||'-'||substr(eh,21,12))::uuid event_id) ei
CROSS JOIN LATERAL (SELECT encode(digest('gatey-production-like-scale-v1:live_sessions:' || session_no, 'sha256'), 'hex') sh) sd
CROSS JOIN LATERAL (SELECT (substr(sh,1,8)||'-'||substr(sh,9,4)||'-'||substr(sh,13,4)||'-'||substr(sh,17,4)||'-'||substr(sh,21,12))::uuid session_id) si;

INSERT INTO operator_approvals (
    approval_id, session_id, scope_hash, release_digest, risk_limit_set_digest,
    approver_id, approver_role, decision, reason, approved_at, expires_at)
SELECT approval_id, session_id,
       encode(digest('gatey-production-like-scale-v1:approval-scope:' || session_no, 'sha256'), 'hex'),
       encode(digest('gatey-production-like-scale-v1:release:gy5-publish-' || session_no, 'sha256'), 'hex'),
       encode(digest('gatey-production-like-scale-v1:risk-digest:' || (((session_no-1) % :risk_limit_sets)+1), 'sha256'), 'hex'),
       (session_no % :users) + 1, 'LIVE_APPROVER',
       CASE WHEN n % 3 = 0 THEN 'REJECTED' ELSE 'APPROVED' END, 'synthetic fixture decision',
       TIMESTAMPTZ '2025-01-03 01:00:00+00' + (n % 3600) * INTERVAL '1 second',
       TIMESTAMPTZ '2025-01-03 02:00:00+00' + (n % 3600) * INTERVAL '1 second'
FROM generate_series(1, :operator_approvals) n
CROSS JOIN LATERAL (SELECT ((n-1) / :approvals_per_session) + 1 session_no) s
CROSS JOIN LATERAL (SELECT encode(digest('gatey-production-like-scale-v1:operator_approvals:' || n, 'sha256'), 'hex') ah) ad
CROSS JOIN LATERAL (SELECT (substr(ah,1,8)||'-'||substr(ah,9,4)||'-'||substr(ah,13,4)||'-'||substr(ah,17,4)||'-'||substr(ah,21,12))::uuid approval_id) ai
CROSS JOIN LATERAL (SELECT encode(digest('gatey-production-like-scale-v1:live_sessions:' || session_no, 'sha256'), 'hex') sh) sd
CROSS JOIN LATERAL (SELECT (substr(sh,1,8)||'-'||substr(sh,9,4)||'-'||substr(sh,13,4)||'-'||substr(sh,17,4)||'-'||substr(sh,21,12))::uuid session_id) si;

INSERT INTO execution_intents (
    intent_id, session_id, sequence, action, symbol, side, order_type, quantity, limit_price,
    payload_hash_schema_version, payload_hash, client_order_id, local_order_id,
    state, version, created_at)
SELECT intent_id, session_id, ((n-1) % :intents_per_session) + 1,
       'PLACE', 'BTC-USDT', CASE n % 2 WHEN 0 THEN 'BUY' ELSE 'SELL' END,
       'LIMIT', 1, 100, 'execution-intent-payload.v1',
       encode(digest('gatey-production-like-scale-v1:intent-payload:' || n, 'sha256'), 'hex'),
       'gy5-client-' || n, 'gy5-order-' || (((n-1) % :orders)+1), 'CREATED', 1,
       TIMESTAMPTZ '2025-01-06 00:00:00+00' + (n % 86400) * INTERVAL '1 second'
FROM generate_series(1, :execution_intents) n
CROSS JOIN LATERAL (SELECT ((n-1) / :intents_per_session) + 1 session_no) s
CROSS JOIN LATERAL (SELECT encode(digest('gatey-production-like-scale-v1:execution_intents:' || n, 'sha256'), 'hex') ih) id
CROSS JOIN LATERAL (SELECT (substr(ih,1,8)||'-'||substr(ih,9,4)||'-'||substr(ih,13,4)||'-'||substr(ih,17,4)||'-'||substr(ih,21,12))::uuid intent_id) ii
CROSS JOIN LATERAL (SELECT encode(digest('gatey-production-like-scale-v1:live_sessions:' || session_no, 'sha256'), 'hex') sh) sd
CROSS JOIN LATERAL (SELECT (substr(sh,1,8)||'-'||substr(sh,9,4)||'-'||substr(sh,13,4)||'-'||substr(sh,17,4)||'-'||substr(sh,21,12))::uuid session_id) si;

INSERT INTO execution_receipts (
    receipt_id, intent_id, attempt_no, outcome, exchange_request_id, exchange_order_id,
    error_category, error_code, received_at, payload_digest, payload_digest_schema_version)
SELECT receipt_id, intent_id, ((n-1) % :receipts_per_intent)+1,
       CASE (n-1) % 3 WHEN 0 THEN 'ACKNOWLEDGED' WHEN 1 THEN 'QUERY_CONFIRMED' ELSE 'QUERY_NOT_FOUND' END,
       'gy5-request-' || n,
       CASE WHEN n % 3 = 0 THEN NULL ELSE 'gy5-exchange-order-' || intent_no END,
       'FAKE_FIXTURE', 'SYNTHETIC',
       TIMESTAMPTZ '2025-01-07 00:00:00+00' + (n % 86400) * INTERVAL '1 second',
       encode(digest('gatey-production-like-scale-v1:receipt-payload:' || n, 'sha256'), 'hex'),
       'execution-receipt-envelope.v1'
FROM generate_series(1, :execution_receipts) n
CROSS JOIN LATERAL (SELECT ((n-1) / :receipts_per_intent) + 1 intent_no) s
CROSS JOIN LATERAL (SELECT encode(digest('gatey-production-like-scale-v1:execution_receipts:' || n, 'sha256'), 'hex') rh) rd
CROSS JOIN LATERAL (SELECT (substr(rh,1,8)||'-'||substr(rh,9,4)||'-'||substr(rh,13,4)||'-'||substr(rh,17,4)||'-'||substr(rh,21,12))::uuid receipt_id) ri
CROSS JOIN LATERAL (SELECT encode(digest('gatey-production-like-scale-v1:execution_intents:' || intent_no, 'sha256'), 'hex') ih) id
CROSS JOIN LATERAL (SELECT (substr(ih,1,8)||'-'||substr(ih,9,4)||'-'||substr(ih,13,4)||'-'||substr(ih,17,4)||'-'||substr(ih,21,12))::uuid intent_id) ii;
