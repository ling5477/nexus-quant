-- 只回填根对象的归属元数据；经济事实、冻结快照与既有迁移不变。
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '120s';

CREATE FUNCTION nq_current_tenant_id() RETURNS uuid
LANGUAGE plpgsql STABLE SET search_path TO pg_catalog, public AS $$
DECLARE value text := current_setting('nq.tenant_id', true);
BEGIN
    IF value IS NULL OR value = '' THEN RETURN NULL; END IF;
    IF value !~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'
        OR value::uuid = '00000000-0000-0000-0000-000000000000'::uuid THEN
        RAISE EXCEPTION 'TENANT_ACCESS_DENIED' USING ERRCODE = '42501';
    END IF;
    RETURN value::uuid;
END;
$$;

-- 原准入修订与公开采集不可变规则继续保护所有业务字段，只豁免首次归属回填。
CREATE OR REPLACE FUNCTION bump_admission_for_paper_mutation() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE' AND OLD.tenant_id IS NULL AND NEW.tenant_id IS NOT NULL
        AND (to_jsonb(NEW) - 'tenant_id') = (to_jsonb(OLD) - 'tenant_id') THEN
        RETURN NEW;
    END IF;
    PERFORM bump_strategy_release_admission_revisions(ARRAY[
        CASE WHEN TG_OP <> 'INSERT' THEN OLD.publish_id ELSE NULL END,
        CASE WHEN TG_OP <> 'DELETE' THEN NEW.publish_id ELSE NULL END
    ]);
    RETURN COALESCE(NEW, OLD);
END;
$$;

CREATE OR REPLACE FUNCTION reject_public_capture_dataset_mutation() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE' AND OLD.tenant_id IS NULL AND NEW.tenant_id IS NOT NULL
        AND (to_jsonb(NEW) - 'tenant_id') = (to_jsonb(OLD) - 'tenant_id') THEN
        RETURN NEW;
    END IF;
    IF OLD.source = 'OKX_PUBLIC_CAPTURE' THEN
        RAISE EXCEPTION 'public capture dataset is immutable';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;

ALTER TABLE accounts ADD COLUMN tenant_id uuid;
ALTER TABLE exchange_accounts ADD COLUMN tenant_id uuid;
ALTER TABLE strategy_definitions ADD COLUMN tenant_id uuid;
ALTER TABLE strategy_versions ADD COLUMN tenant_id uuid;
ALTER TABLE research_configs ADD COLUMN tenant_id uuid;
ALTER TABLE backtest_configs ADD COLUMN tenant_id uuid;
ALTER TABLE paper_trading_runs ADD COLUMN tenant_id uuid;
ALTER TABLE marketdata_datasets ADD COLUMN tenant_id uuid;
ALTER TABLE marketdata_datasets ADD COLUMN visibility varchar(24) NOT NULL DEFAULT 'TENANT_PRIVATE';

-- ALTER 已持有表锁；捕获有限主键上界并按千行回填，不根据作者字符串拆分历史归属。
DO $$
DECLARE item text[]; upper_id text; last_id text; batch_max text;
BEGIN
    FOREACH item SLICE 1 IN ARRAY ARRAY[
        ['accounts','account_id'], ['exchange_accounts','exchange_account_id'],
        ['strategy_definitions','strategy_id'], ['strategy_versions','strategy_version_id'],
        ['research_configs','research_config_id'], ['backtest_configs','backtest_config_id'],
        ['paper_trading_runs','paper_run_id'], ['marketdata_datasets','dataset_id']
    ] LOOP
        EXECUTE format('SELECT max(%I::text) FROM %I',item[2],item[1]) INTO upper_id;
        last_id := '';
        LOOP
            EXECUTE format($query$
                WITH batch AS MATERIALIZED (
                    SELECT %1$I::text AS id FROM %2$I
                    WHERE %1$I::text > $1 AND %1$I::text <= $2 AND tenant_id IS NULL
                    ORDER BY %1$I::text LIMIT 1000
                ), changed AS (
                    UPDATE %2$I t SET tenant_id = '00000000-0000-4000-8000-000000000001'::uuid
                    FROM batch b WHERE t.%1$I::text = b.id RETURNING b.id
                ) SELECT max(id) FROM changed
            $query$,item[2],item[1]) INTO batch_max USING last_id,upper_id;
            EXIT WHEN batch_max IS NULL;
            last_id := batch_max;
        END LOOP;
        EXECUTE format('ALTER TABLE %I ALTER COLUMN tenant_id SET DEFAULT nq_current_tenant_id()',item[1]);
        IF item[1] <> 'marketdata_datasets' THEN
            EXECUTE format('ALTER TABLE %I ALTER COLUMN tenant_id SET NOT NULL',item[1]);
        END IF;
        EXECUTE format('ALTER TABLE %I ADD CONSTRAINT %I FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id) NOT VALID',
            item[1],'fk_'||item[1]||'_tenant');
        EXECUTE format('ALTER TABLE %I VALIDATE CONSTRAINT %I',item[1],'fk_'||item[1]||'_tenant');
        EXECUTE format('ALTER TABLE %I ADD CONSTRAINT %I UNIQUE (tenant_id,%I)',
            item[1],'uq_'||item[1]||'_tenant_identity',item[2]);
        EXECUTE format('CREATE INDEX %I ON %I (tenant_id,created_at,%I)',
            'idx_'||item[1]||'_tenant_created',item[1],item[2]);
        EXECUTE format('COMMENT ON COLUMN %I.tenant_id IS %L',item[1],
            '服务端确认且不可重绑定的租户归属；历史数据固定 NQ_LOCAL');
    END LOOP;
END;
$$;

ALTER TABLE marketdata_datasets ADD CONSTRAINT ck_dataset_tenant_visibility CHECK (
    (visibility = 'TENANT_PRIVATE' AND tenant_id IS NOT NULL)
    OR (visibility = 'SHARED_READONLY' AND tenant_id IS NULL));
COMMENT ON COLUMN marketdata_datasets.visibility IS '私有描述符或受控准入的共享只读描述符；不复制共享行情';

CREATE FUNCTION nq_preserve_business_tenant() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.tenant_id IS DISTINCT FROM OLD.tenant_id
        OR (TG_TABLE_NAME = 'marketdata_datasets'
            AND to_jsonb(NEW)->'visibility' IS DISTINCT FROM to_jsonb(OLD)->'visibility') THEN
        RAISE EXCEPTION 'tenant ownership is immutable' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
DO $$
DECLARE name text;
BEGIN
    FOREACH name IN ARRAY ARRAY['accounts','exchange_accounts','strategy_definitions','strategy_versions',
        'research_configs','backtest_configs','paper_trading_runs','marketdata_datasets'] LOOP
        EXECUTE format('CREATE TRIGGER trg_preserve_business_tenant BEFORE UPDATE ON %I FOR EACH ROW EXECUTE FUNCTION nq_preserve_business_tenant()',name);
    END LOOP;
END;
$$;

-- 全局主键保持不变；仅策略代码及版本序号变成租户内部身份。
ALTER TABLE strategy_versions DROP CONSTRAINT fk_strategy_versions_strategy_code;
ALTER TABLE strategy_definitions DROP CONSTRAINT uq_strategy_definitions_strategy_code;
ALTER TABLE strategy_versions DROP CONSTRAINT uq_strategy_versions_strategy_code_version;
ALTER TABLE strategy_definitions ADD CONSTRAINT uq_strategy_definitions_tenant_code UNIQUE (tenant_id,strategy_code);
ALTER TABLE strategy_versions ADD CONSTRAINT uq_strategy_versions_tenant_code_version UNIQUE (tenant_id,strategy_code,version);
ALTER TABLE strategy_versions ADD CONSTRAINT fk_strategy_versions_tenant_code FOREIGN KEY (tenant_id,strategy_code)
    REFERENCES strategy_definitions(tenant_id,strategy_code);
ALTER TABLE strategy_definitions ADD CONSTRAINT fk_strategy_definition_tenant_account FOREIGN KEY (tenant_id,account_id)
    REFERENCES accounts(tenant_id,account_id);
ALTER TABLE exchange_accounts ADD CONSTRAINT fk_exchange_account_tenant_owner FOREIGN KEY (tenant_id,owner_user_id)
    REFERENCES tenant_memberships(tenant_id,user_id);
ALTER TABLE exchange_accounts ADD CONSTRAINT fk_exchange_account_tenant_legacy FOREIGN KEY (tenant_id,legacy_account_id)
    REFERENCES accounts(tenant_id,account_id);
ALTER TABLE research_configs ADD CONSTRAINT fk_research_config_tenant_strategy FOREIGN KEY (tenant_id,source_strategy_id)
    REFERENCES strategy_definitions(tenant_id,strategy_id);
ALTER TABLE backtest_configs ADD CONSTRAINT fk_backtest_config_tenant_research FOREIGN KEY (tenant_id,research_config_id)
    REFERENCES research_configs(tenant_id,research_config_id);
ALTER TABLE backtest_configs ADD CONSTRAINT fk_backtest_config_tenant_version FOREIGN KEY (tenant_id,strategy_version_id)
    REFERENCES strategy_versions(tenant_id,strategy_version_id);
ALTER TABLE paper_trading_runs ADD CONSTRAINT fk_paper_run_tenant_account FOREIGN KEY (tenant_id,canonical_account_id)
    REFERENCES accounts(tenant_id,account_id);
ALTER TABLE paper_trading_runs ADD CONSTRAINT fk_paper_run_tenant_version FOREIGN KEY (tenant_id,strategy_version_id)
    REFERENCES strategy_versions(tenant_id,strategy_version_id);

CREATE TABLE marketdata_dataset_publications (
    dataset_id uuid PRIMARY KEY REFERENCES marketdata_datasets(dataset_id) ON DELETE RESTRICT,
    source_reference varchar(512) NOT NULL CHECK (btrim(source_reference) <> ''),
    license_reference varchar(512) NOT NULL CHECK (btrim(license_reference) <> ''),
    permitted_from timestamptz NOT NULL,
    permitted_until timestamptz NOT NULL CHECK (permitted_until > permitted_from),
    expires_at timestamptz NOT NULL CHECK (isfinite(expires_at)),
    status varchar(16) NOT NULL CHECK (status IN ('APPROVED','REVOKED')),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE marketdata_dataset_publications IS '受控共享数据展示许可；无记录、已撤销或过期均不对 Demo 准入';
