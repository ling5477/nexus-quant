SET LOCAL lock_timeout='5s';
SET LOCAL statement_timeout='30s';

CREATE INDEX idx_ledger_events_posted_entry ON ledger_events(entry_id,ledger_event_id)
    WHERE event_type='POSTED';

CREATE TABLE sim_account_projection_operations (
    operation_id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants(tenant_id),
    account_id bigint NOT NULL REFERENCES accounts(account_id),
    paper_run_id varchar(128) NOT NULL REFERENCES continuous_sim_runs(paper_run_id),
    owner_user_id bigint NOT NULL,
    actor varchar(128) NOT NULL CHECK (btrim(actor)<>''),
    trace_id varchar(128) NOT NULL CHECK (btrim(trace_id)<>''),
    end_operation_id uuid NOT NULL REFERENCES continuous_sim_operations(operation_id),
    recovery_operation_id uuid REFERENCES sim_account_projection_operations(operation_id),
    request_sha256 char(64) NOT NULL CHECK (request_sha256 ~ '^[0-9a-f]{64}$'),
    ledger_watermark char(64) NOT NULL CHECK (ledger_watermark ~ '^[0-9a-f]{64}$'),
    result_json jsonb NOT NULL CHECK (jsonb_typeof(result_json)='object' AND octet_length(result_json::text)<=8192),
    recorded_at timestamptz NOT NULL CHECK (isfinite(recorded_at)),
    FOREIGN KEY (tenant_id,owner_user_id) REFERENCES tenant_memberships(tenant_id,user_id),
    CHECK (operation_id<>end_operation_id AND operation_id IS DISTINCT FROM recovery_operation_id)
);

CREATE INDEX idx_sim_projection_operations_run
    ON sim_account_projection_operations(paper_run_id,recorded_at DESC,operation_id);
CREATE UNIQUE INDEX uq_sim_projection_finalization
    ON sim_account_projection_operations(recovery_operation_id) WHERE recovery_operation_id IS NOT NULL;

ALTER TABLE sim_account_projection_operations ENABLE ROW LEVEL SECURITY;
ALTER TABLE sim_account_projection_operations FORCE ROW LEVEL SECURITY;
CREATE POLICY sim_projection_operations_read ON sim_account_projection_operations FOR SELECT
    USING (nq_tenant_is_current(tenant_id));
CREATE POLICY sim_projection_operations_insert ON sim_account_projection_operations FOR INSERT
    WITH CHECK (nq_tenant_is_current(tenant_id)
        AND owner_user_id=NULLIF(current_setting('nq.user_id',true),'')::bigint
        AND EXISTS (SELECT 1 FROM paper_trading_runs p JOIN accounts a ON a.account_id=p.canonical_account_id
            JOIN continuous_sim_operations e ON e.paper_run_id=p.paper_run_id
            WHERE p.paper_run_id=sim_account_projection_operations.paper_run_id
              AND a.account_id=sim_account_projection_operations.account_id
              AND p.tenant_id=sim_account_projection_operations.tenant_id AND a.tenant_id=p.tenant_id
              AND p.trade_env='SIM' AND p.created_by=sim_account_projection_operations.actor
              AND e.operation_id=sim_account_projection_operations.end_operation_id
              AND e.operation_type='END' AND e.owner_user_id=sim_account_projection_operations.owner_user_id)
        AND (recovery_operation_id IS NULL OR EXISTS (
            SELECT 1 FROM sim_account_projection_operations r
            WHERE r.operation_id=sim_account_projection_operations.recovery_operation_id
              AND r.recovery_operation_id IS NULL AND r.account_id=sim_account_projection_operations.account_id
              AND r.end_operation_id=sim_account_projection_operations.end_operation_id
              AND r.tenant_id=sim_account_projection_operations.tenant_id
              AND r.owner_user_id=sim_account_projection_operations.owner_user_id)));

CREATE TRIGGER trg_sim_projection_operation_immutable
    BEFORE UPDATE OR DELETE ON sim_account_projection_operations
    FOR EACH ROW EXECUTE FUNCTION guard_continuous_sim_operation();

COMMENT ON TABLE sim_account_projection_operations IS '账户投影恢复与原人工结束复核的不可变回执；与对应状态修改和审计原子提交';
COMMENT ON COLUMN sim_account_projection_operations.end_operation_id IS '原人工结束操作回执，复核不能覆盖原操作';
COMMENT ON COLUMN sim_account_projection_operations.recovery_operation_id IS '复核关联的原恢复编号；恢复回执本身为空';
COMMENT ON COLUMN sim_account_projection_operations.ledger_watermark IS '核验过的同账户经济事实和入账顺序摘要';
