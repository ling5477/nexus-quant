-- 仅增加注册与账户身份；保留所有既存用户、历史迁移及经济事实。
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '120s';

ALTER TABLE users ADD COLUMN email varchar(254);
ALTER TABLE users ADD COLUMN email_verified_at timestamptz;
ALTER TABLE users ADD CONSTRAINT ck_user_email_normalized CHECK (
    email IS NULL OR (email=lower(btrim(email)) AND length(email)>3));
-- 既存大小写冲突必须使升级失败，不能静默改名或合并身份。
CREATE UNIQUE INDEX uq_users_normalized_username ON users(lower(btrim(username)));
CREATE UNIQUE INDEX uq_users_normalized_email ON users(email) WHERE email IS NOT NULL;

-- 复合外键在 RLS 隐藏凭据时仍然有效，不能把“不可见”误判为“没有凭据”。
ALTER TABLE exchange_accounts ADD COLUMN accepts_credentials boolean
    GENERATED ALWAYS AS (exchange_code<>'PAPER') STORED;
ALTER TABLE exchange_accounts ADD CONSTRAINT uq_exchange_credential_capability UNIQUE(exchange_account_id,accepts_credentials);
ALTER TABLE exchange_account_credentials ADD COLUMN credential_capability_required boolean NOT NULL DEFAULT true
    CHECK(credential_capability_required);
ALTER TABLE exchange_account_credentials ADD CONSTRAINT fk_credential_account_capability
    FOREIGN KEY(exchange_account_id,credential_capability_required) REFERENCES exchange_accounts(exchange_account_id,accepts_credentials);
COMMENT ON COLUMN exchange_accounts.accepts_credentials IS '由venue派生的凭据能力；PAPER账户绝不持有交易所凭据';
COMMENT ON COLUMN exchange_account_credentials.credential_capability_required IS '恒为true的外键判别，防止RLS不可见时绕过PAPER零凭据约束';

-- 同一触发器服务三种行类型，先按表分支再解析字段，用户启用不能访问不存在的 status。
CREATE OR REPLACE FUNCTION nq_suspend_disabled_tenant() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public AS $$
BEGIN
    CASE TG_TABLE_NAME
        WHEN 'tenants' THEN
            IF NEW.status IS DISTINCT FROM OLD.status THEN
                UPDATE tenant_execution_controls SET suspended=(NEW.status<>'ACTIVE'),requires_manual_resume=true WHERE tenant_id=NEW.tenant_id;
            END IF;
        WHEN 'users' THEN
            IF NOT NEW.enabled AND OLD.enabled THEN
                UPDATE tenant_execution_controls c SET suspended=true,requires_manual_resume=true
                    FROM tenant_memberships m WHERE m.user_id=NEW.id AND m.membership_kind='DEMO' AND c.tenant_id=m.tenant_id;
            END IF;
        WHEN 'tenant_memberships' THEN
            IF NEW.status='DISABLED' AND OLD.status<>'DISABLED' THEN
                UPDATE tenant_execution_controls SET suspended=true,requires_manual_resume=true
                    WHERE tenant_id=NEW.tenant_id AND NEW.membership_kind='DEMO';
            END IF;
    END CASE;
    RETURN NEW;
END;
$$;

CREATE TABLE demo_registrations (
    registration_id uuid PRIMARY KEY,
    user_id bigint NOT NULL UNIQUE REFERENCES users(id),
    request_hash varchar(64) NOT NULL CHECK(request_hash ~ '^[0-9a-f]{64}$'),
    tenant_id uuid UNIQUE REFERENCES tenants(tenant_id),
    status varchar(16) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','INITIALIZING','READY')),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ready_at timestamptz,
    cash_entry_id varchar(64) REFERENCES ledger_entries(entry_id),
    contra_entry_id varchar(64) REFERENCES ledger_entries(entry_id),
    CHECK((status='PENDING' AND tenant_id IS NULL AND ready_at IS NULL)
        OR (status='INITIALIZING' AND tenant_id IS NOT NULL AND ready_at IS NULL)
        OR (status='READY' AND tenant_id IS NOT NULL AND ready_at IS NOT NULL)),
    CHECK((status='READY')=(cash_entry_id IS NOT NULL AND contra_entry_id IS NOT NULL))
);
CREATE TABLE demo_email_verifications (
    token_id uuid PRIMARY KEY,
    registration_id uuid NOT NULL REFERENCES demo_registrations(registration_id),
    token_hash varchar(64) NOT NULL UNIQUE CHECK(token_hash ~ '^[0-9a-f]{64}$'),
    expires_at timestamptz NOT NULL,
    consumed_at timestamptz,
    invalidated_at timestamptz,
    attempts integer NOT NULL DEFAULT 0 CHECK(attempts BETWEEN 0 AND 5),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK(expires_at>created_at)
);
CREATE INDEX idx_demo_verification_registration ON demo_email_verifications(registration_id,created_at DESC);
CREATE TABLE demo_email_outbox (
    token_id uuid PRIMARY KEY REFERENCES demo_email_verifications(token_id),
    status varchar(16) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','SENT','FAILED','CANCELLED')),
    attempts integer NOT NULL DEFAULT 0 CHECK(attempts BETWEEN 0 AND 6),
    next_attempt_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK((status='SENT')=(sent_at IS NOT NULL))
);
CREATE INDEX idx_demo_outbox_pending ON demo_email_outbox(next_attempt_at,token_id) WHERE status='PENDING';
CREATE TABLE demo_request_limits (
    bucket_hash varchar(64) PRIMARY KEY CHECK(bucket_hash ~ '^[0-9a-f]{64}$'),
    window_start timestamptz NOT NULL,
    attempts integer NOT NULL CHECK(attempts>0)
);
CREATE INDEX idx_demo_limit_expiry ON demo_request_limits(window_start);

ALTER TABLE paper_trading_runs ADD CONSTRAINT uq_paper_tenant_account_identity UNIQUE(tenant_id,paper_run_id,canonical_account_id);
CREATE TABLE tenant_sim_accounts (
    tenant_id uuid PRIMARY KEY REFERENCES tenants(tenant_id),
    exchange_account_id bigint NOT NULL UNIQUE,
    account_id bigint NOT NULL UNIQUE,
    reserved_paper_run_id varchar(64) NOT NULL UNIQUE CHECK(reserved_paper_run_id ~ '^ptr-[0-9a-f-]{36}$'),
    bound_paper_run_id varchar(64) UNIQUE,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY(tenant_id,exchange_account_id) REFERENCES exchange_accounts(tenant_id,exchange_account_id),
    FOREIGN KEY(tenant_id,account_id) REFERENCES accounts(tenant_id,account_id),
    FOREIGN KEY(tenant_id,bound_paper_run_id,account_id) REFERENCES paper_trading_runs(tenant_id,paper_run_id,canonical_account_id),
    CHECK(bound_paper_run_id IS NULL OR bound_paper_run_id=reserved_paper_run_id)
);
ALTER TABLE tenant_sim_accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_sim_accounts FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_scope ON tenant_sim_accounts USING(nq_tenant_is_current(tenant_id)) WITH CHECK(nq_tenant_is_current(tenant_id));

CREATE FUNCTION nq_preserve_demo_sim_identity() RETURNS trigger
LANGUAGE plpgsql SET search_path TO pg_catalog,public AS $$
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'demo SIM identity must be retained' USING ERRCODE='23514'; END IF;
    IF TG_OP='UPDATE' AND ((NEW.tenant_id,NEW.exchange_account_id,NEW.account_id,NEW.reserved_paper_run_id,NEW.created_at)
        IS DISTINCT FROM (OLD.tenant_id,OLD.exchange_account_id,OLD.account_id,OLD.reserved_paper_run_id,OLD.created_at)
        OR (OLD.bound_paper_run_id IS NOT NULL AND NEW.bound_paper_run_id IS DISTINCT FROM OLD.bound_paper_run_id)) THEN
        RAISE EXCEPTION 'demo SIM identity cannot be rebound' USING ERRCODE='23514';
    END IF;
    IF NOT EXISTS(SELECT 1 FROM exchange_accounts e JOIN accounts a ON a.account_id=e.legacy_account_id
        JOIN tenants t ON t.tenant_id=e.tenant_id JOIN tenant_memberships m ON m.tenant_id=t.tenant_id
        WHERE t.tenant_kind='DEMO' AND e.tenant_id=NEW.tenant_id AND e.exchange_account_id=NEW.exchange_account_id
            AND a.account_id=NEW.account_id AND a.tenant_id=t.tenant_id AND m.user_id=e.owner_user_id
            AND e.exchange_code='PAPER' AND e.trade_env='SIM'
            AND NOT EXISTS(SELECT 1 FROM exchange_account_credentials c WHERE c.exchange_account_id=e.exchange_account_id)) THEN
        RAISE EXCEPTION 'invalid demo SIM account identity' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_preserve_demo_sim BEFORE INSERT OR UPDATE OR DELETE ON tenant_sim_accounts
    FOR EACH ROW EXECUTE FUNCTION nq_preserve_demo_sim_identity();

-- 专用受限入口只授予 DEMO_USER；不能借注册写入本地或平台权限。
CREATE FUNCTION nq_create_demo_pending(p_registration uuid,p_username text,p_email text,p_password_hash text,p_request_hash text)
RETURNS bigint LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public AS $$
DECLARE found_user bigint; original_hash text;
BEGIN
    PERFORM pg_advisory_xact_lock(hashtextextended('demo-registration:'||p_registration::text,341));
    SELECT user_id,request_hash INTO found_user,original_hash FROM demo_registrations WHERE registration_id=p_registration;
    IF FOUND THEN
        IF original_hash<>p_request_hash THEN RAISE EXCEPTION 'REGISTRATION_ID_CONFLICT' USING ERRCODE='23514'; END IF;
        RETURN found_user;
    END IF;
    IF p_username<>lower(btrim(p_username)) OR p_username !~ '^[a-z0-9][a-z0-9_.-]{2,63}$'
        OR p_email<>lower(btrim(p_email)) OR length(p_email)>254 OR p_password_hash !~ '^\$2[aby]\$' THEN
        RAISE EXCEPTION 'invalid demo registration' USING ERRCODE='23514';
    END IF;
    INSERT INTO users(username,password_hash,email,enabled) VALUES(p_username,p_password_hash,p_email,false)
        ON CONFLICT DO NOTHING RETURNING id INTO found_user;
    IF found_user IS NULL THEN RETURN NULL; END IF;
    INSERT INTO user_roles(user_id,role_id) SELECT found_user,id FROM roles WHERE role_code='DEMO_USER';
    INSERT INTO demo_registrations(registration_id,user_id,request_hash) VALUES(p_registration,found_user,p_request_hash);
    RETURN found_user;
END;
$$;
REVOKE ALL ON FUNCTION nq_create_demo_pending(uuid,text,text,text,text) FROM PUBLIC;

CREATE FUNCTION nq_initialize_demo_identity(p_registration uuid,p_digest text) RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public AS $$
DECLARE registration demo_registrations%ROWTYPE; identity uuid; code text;
BEGIN
    SELECT * INTO STRICT registration FROM demo_registrations WHERE registration_id=p_registration FOR UPDATE;
    IF NOT EXISTS(SELECT 1 FROM demo_email_verifications v WHERE v.registration_id=p_registration AND v.token_hash=p_digest
        AND v.consumed_at IS NOT NULL AND v.invalidated_at IS NULL AND v.attempts<5) THEN
        RAISE EXCEPTION 'VERIFICATION_INVALID' USING ERRCODE='23514';
    END IF;
    IF registration.status='READY' THEN RETURN registration.tenant_id; END IF;
    IF registration.status<>'PENDING' OR EXISTS(SELECT 1 FROM tenant_memberships WHERE user_id=registration.user_id)
        OR NOT EXISTS(SELECT 1 FROM users WHERE id=registration.user_id AND NOT enabled AND email IS NOT NULL) THEN
        RAISE EXCEPTION 'invalid pending registration identity' USING ERRCODE='23514';
    END IF;
    identity:=gen_random_uuid(); code:='DEMO_'||upper(replace(identity::text,'-',''));
    INSERT INTO tenants(tenant_id,tenant_code,tenant_name,tenant_kind) VALUES(identity,code,'Demo','DEMO');
    INSERT INTO tenant_memberships(tenant_id,user_id,membership_kind,membership_role)
        VALUES(identity,registration.user_id,'DEMO','DEMO_USER');
    UPDATE demo_registrations SET tenant_id=identity,status='INITIALIZING' WHERE registration_id=p_registration;
    RETURN identity;
END;
$$;
REVOKE ALL ON FUNCTION nq_initialize_demo_identity(uuid,text) FROM PUBLIC;

COMMENT ON TABLE tenant_sim_accounts IS '每演示租户唯一账户和预留运行身份，不存余额或收益';
COMMENT ON TABLE demo_email_outbox IS '仅存验证邮件身份和投递状态；令牌由独立服务密钥重建，数据库仅有摘要';
COMMENT ON TABLE demo_registrations IS '稳定注册回执；READY与canonical注资在同一事务提交';

-- PAPER 只在服务器建立的 Demo 成员关系内桥接，不加入普通交易所白名单。
CREATE OR REPLACE FUNCTION guard_canonical_account_compatibility_bridge() RETURNS trigger
LANGUAGE plpgsql SET search_path TO pg_catalog,public AS $$
DECLARE v_account accounts%ROWTYPE; v_expected_code text;
BEGIN
    IF (TG_OP='INSERT' AND NEW.legacy_account_id IS NOT NULL)
        OR (TG_OP='UPDATE' AND OLD.legacy_account_id IS NULL AND NEW.legacy_account_id IS NOT NULL) THEN
        SELECT * INTO STRICT v_account FROM accounts WHERE account_id=NEW.legacy_account_id FOR KEY SHARE;
        IF NEW.trade_env='LIVE' AND NEW.exchange_code='OKX' THEN
            v_expected_code:=canonical_legacy_account_code(NEW.exchange_account_id);
        ELSIF NEW.trade_env='SIM' AND NEW.exchange_code IN ('OKX','BINANCE') THEN
            v_expected_code:='nq-'||lower(NEW.exchange_code)||'-sim-'||NEW.exchange_account_id::text;
        ELSIF NEW.trade_env='SIM' AND NEW.exchange_code='PAPER' AND NEW.tenant_id=nq_current_tenant_id()
            AND NEW.owner_user_id=NULLIF(current_setting('nq.user_id',true),'')::bigint
            AND v_account.tenant_id=NEW.tenant_id
            AND EXISTS(SELECT 1 FROM tenants t JOIN tenant_memberships m USING(tenant_id)
                WHERE t.tenant_id=NEW.tenant_id AND t.tenant_kind='DEMO' AND t.status='ACTIVE'
                    AND m.user_id=NEW.owner_user_id AND m.membership_kind='DEMO' AND m.status='ACTIVE')
            AND NOT EXISTS(SELECT 1 FROM exchange_account_credentials c WHERE c.exchange_account_id=NEW.exchange_account_id) THEN
            v_expected_code:='nq-paper-sim-'||NEW.exchange_account_id::text;
        ELSE RAISE EXCEPTION 'canonical legacy account bridge is invalid' USING ERRCODE='23514';
        END IF;
        IF NEW.status<>'ACTIVE' OR v_account.account_code<>v_expected_code OR v_account.venue<>NEW.exchange_code OR v_account.status<>'ACTIVE' THEN
            RAISE EXCEPTION 'canonical legacy account bridge is invalid' USING ERRCODE='23514';
        END IF;
    ELSIF TG_OP='UPDATE' AND OLD.legacy_account_id IS NOT NULL AND NEW.legacy_account_id IS DISTINCT FROM OLD.legacy_account_id THEN
        RAISE EXCEPTION 'canonical legacy account bridge is immutable' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE OR REPLACE FUNCTION nq_check_additional_tenant_links() RETURNS trigger
LANGUAGE plpgsql SET search_path TO pg_catalog, public AS $$
DECLARE data jsonb:=to_jsonb(NEW); identity uuid; parent_data jsonb;
BEGIN
    CASE TG_TABLE_NAME
        WHEN 'backtest_configs' THEN
            IF NEW.strategy_version_id IS NOT NULL AND NOT EXISTS (
                SELECT 1 FROM research_configs r JOIN strategy_definitions s ON s.strategy_id=r.source_strategy_id
                    JOIN strategy_versions v ON v.strategy_code=s.strategy_code AND v.tenant_id=s.tenant_id
                WHERE r.research_config_id=NEW.research_config_id AND v.strategy_version_id=NEW.strategy_version_id) THEN
                RAISE EXCEPTION 'TENANT_ACCESS_DENIED: version source mismatch' USING ERRCODE='23514';
            END IF;
        WHEN 'backtest_publish_records' THEN
            IF NOT EXISTS (SELECT 1 FROM backtest_runs r WHERE r.backtest_run_id=NEW.backtest_run_id
                AND r.backtest_config_id=NEW.backtest_config_id AND r.research_config_id=NEW.research_config_id
                AND r.source_strategy_id=NEW.source_strategy_id
                AND (NEW.strategy_version_id IS NULL OR r.strategy_version_id=NEW.strategy_version_id)) THEN
                RAISE EXCEPTION 'TENANT_ACCESS_DENIED: publication parent mismatch' USING ERRCODE='23514';
            END IF;
        WHEN 'paper_trading_runs' THEN
            IF NEW.strategy_version_id IS NOT NULL AND NOT EXISTS (
                SELECT 1 FROM backtest_publish_records p WHERE p.publish_record_id=NEW.publish_id
                    AND p.strategy_version_id=NEW.strategy_version_id) THEN
                RAISE EXCEPTION 'TENANT_ACCESS_DENIED: paper version mismatch' USING ERRCODE='23514';
            END IF;
        WHEN 'paper_run_schedule_fires' THEN
            IF NOT EXISTS(SELECT 1 FROM paper_run_schedules s WHERE s.schedule_id=NEW.schedule_id AND s.paper_run_id=NEW.paper_run_id) THEN
                RAISE EXCEPTION 'TENANT_ACCESS_DENIED: schedule run mismatch' USING ERRCODE='23514';
            END IF;
        WHEN 'ledger_entries' THEN
            IF NEW.ref_type='TRADE' AND NOT EXISTS(SELECT 1 FROM trades t WHERE t.trade_id=NEW.ref_id AND t.account_id=NEW.account_id) THEN
                RAISE EXCEPTION 'TENANT_ACCESS_DENIED: ledger trade mismatch' USING ERRCODE='23514';
            ELSIF NEW.ref_type IN ('SIM_FUNDING_CASH','SIM_FUNDING_CONTRA') AND NOT EXISTS(
                SELECT 1 FROM paper_trading_runs p WHERE p.paper_run_id=NEW.ref_id AND p.canonical_account_id=NEW.account_id) AND NOT EXISTS(SELECT 1 FROM tenant_sim_accounts d WHERE d.account_id=NEW.account_id AND d.reserved_paper_run_id=NEW.ref_id AND d.tenant_id=nq_current_tenant_id()) THEN
                RAISE EXCEPTION 'TENANT_ACCESS_DENIED: ledger funding mismatch' USING ERRCODE='23514';
            END IF;
        WHEN 'sim_asset_funding_receipts' THEN
            IF NOT EXISTS(SELECT 1 FROM paper_trading_runs p WHERE p.paper_run_id=NEW.paper_run_id AND p.canonical_account_id=NEW.account_id)
                OR (SELECT count(*) FROM ledger_entries e WHERE e.entry_id IN (NEW.cash_entry_id,NEW.cash_contra_entry_id,NEW.asset_entry_id,NEW.asset_contra_entry_id)
                    AND e.account_id=NEW.account_id AND e.ref_id=NEW.paper_run_id AND e.ref_type IN ('SIM_FUNDING_CASH','SIM_FUNDING_CONTRA'))<>4 THEN
                RAISE EXCEPTION 'TENANT_ACCESS_DENIED: funding receipt mismatch' USING ERRCODE='23514';
            END IF;
    END CASE;
    RETURN NEW;
END;
$$;
