-- 只增加认证归属，不改变既有用户凭据、角色、会话或任何经济事实。
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';

CREATE TABLE tenants (
    tenant_id uuid PRIMARY KEY CHECK (tenant_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    tenant_code varchar(64) NOT NULL UNIQUE CHECK (tenant_code ~ '^[A-Z][A-Z0-9_]{0,63}$'),
    tenant_name varchar(128) NOT NULL CHECK (length(btrim(tenant_name)) > 0),
    tenant_kind varchar(8) NOT NULL CHECK (tenant_kind IN ('LOCAL','DEMO')),
    status varchar(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','DISABLED')),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_tenant_identity_kind UNIQUE (tenant_id,tenant_kind),
    CONSTRAINT ck_tenant_local_identity CHECK (
        (tenant_kind='LOCAL' AND tenant_code='NQ_LOCAL'
            AND tenant_id='00000000-0000-4000-8000-000000000001'::uuid)
        OR (tenant_kind='DEMO' AND tenant_code<>'NQ_LOCAL'
            AND tenant_id<>'00000000-0000-4000-8000-000000000001'::uuid)),
    CONSTRAINT ck_tenant_times CHECK (isfinite(created_at) AND isfinite(updated_at) AND updated_at>=created_at)
);

CREATE TABLE tenant_memberships (
    tenant_id uuid NOT NULL,
    user_id bigint NOT NULL REFERENCES users(id) ON DELETE RESTRICT CHECK (user_id>0),
    membership_kind varchar(8) NOT NULL,
    membership_role varchar(32) NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','DISABLED')),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id,user_id),
    CONSTRAINT uq_membership_current_user UNIQUE (user_id),
    CONSTRAINT fk_membership_tenant_kind FOREIGN KEY (tenant_id,membership_kind)
        REFERENCES tenants(tenant_id,tenant_kind) ON DELETE RESTRICT,
    CONSTRAINT ck_membership_role CHECK (
        (membership_kind='LOCAL' AND membership_role='LOCAL_MEMBER')
        OR (membership_kind='DEMO' AND membership_role='DEMO_USER')),
    CONSTRAINT ck_membership_times CHECK (isfinite(created_at) AND isfinite(updated_at) AND updated_at>=created_at)
);
CREATE UNIQUE INDEX uq_demo_tenant_member ON tenant_memberships(tenant_id) WHERE membership_kind='DEMO';
CREATE INDEX idx_tenant_membership_status ON tenant_memberships(tenant_id,status,user_id);

INSERT INTO tenants(tenant_id,tenant_code,tenant_name,tenant_kind)
VALUES ('00000000-0000-4000-8000-000000000001','NQ_LOCAL','本地租户','LOCAL');

INSERT INTO roles(role_code,description)
VALUES ('DEMO_USER','演示租户成员，不授予本地交易权限'),
       ('PLATFORM_ADMIN','平台专用管理身份，不授予交易经济事实写权限')
ON CONFLICT(role_code) DO NOTHING;

-- 后续空库 bootstrap/local seed 仍由原角色授予路径建立本地关系；不自动建立演示成员。
CREATE FUNCTION enroll_local_role_membership() RETURNS trigger
LANGUAGE plpgsql SET search_path TO 'pg_catalog','public' AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM public.roles WHERE id=NEW.role_id AND role_code IN ('ADMIN','OPERATOR','VIEWER')) THEN
        INSERT INTO public.tenant_memberships(tenant_id,user_id,membership_kind,membership_role)
        VALUES ('00000000-0000-4000-8000-000000000001',NEW.user_id,'LOCAL','LOCAL_MEMBER')
        ON CONFLICT(user_id) DO NOTHING;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_enroll_local_role_membership AFTER INSERT ON user_roles
FOR EACH ROW EXECUTE FUNCTION enroll_local_role_membership();

-- 捕获有限上界，按用户主键分批；失败由 Flyway 整体回滚，重新执行不会重建用户。
DO $$
DECLARE upper_id bigint; last_id bigint := 0; batch_max bigint;
BEGIN
    SELECT max(id) INTO upper_id FROM users;
    LOOP
        WITH batch AS (
            SELECT id FROM users WHERE id>last_id AND id<=upper_id ORDER BY id LIMIT 1000
        ), inserted AS (
            INSERT INTO tenant_memberships(tenant_id,user_id,membership_kind,membership_role)
            SELECT '00000000-0000-4000-8000-000000000001',id,'LOCAL','LOCAL_MEMBER' FROM batch
            ON CONFLICT(user_id) DO NOTHING RETURNING user_id
        )
        SELECT max(id) INTO batch_max FROM batch;
        EXIT WHEN batch_max IS NULL;
        last_id := batch_max;
    END LOOP;
END;
$$;

CREATE FUNCTION preserve_tenant_identity() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF (TG_TABLE_NAME='tenants' AND
        (to_jsonb(NEW)->'tenant_id',to_jsonb(NEW)->'tenant_code',to_jsonb(NEW)->'tenant_kind')
        IS DISTINCT FROM (to_jsonb(OLD)->'tenant_id',to_jsonb(OLD)->'tenant_code',to_jsonb(OLD)->'tenant_kind'))
        OR (TG_TABLE_NAME='tenant_memberships' AND
        (to_jsonb(NEW)->'tenant_id',to_jsonb(NEW)->'user_id',to_jsonb(NEW)->'membership_kind')
        IS DISTINCT FROM (to_jsonb(OLD)->'tenant_id',to_jsonb(OLD)->'user_id',to_jsonb(OLD)->'membership_kind')) THEN
        RAISE EXCEPTION 'tenant identity cannot be rebound' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_preserve_tenant_identity BEFORE UPDATE ON tenants
FOR EACH ROW EXECUTE FUNCTION preserve_tenant_identity();
CREATE TRIGGER trg_preserve_membership_identity BEFORE UPDATE ON tenant_memberships
FOR EACH ROW EXECUTE FUNCTION preserve_tenant_identity();

COMMENT ON TABLE tenants IS '独立租户身份；本地固定NQ_LOCAL，演示环境不因此获得注册或交易权限';
COMMENT ON COLUMN tenants.tenant_id IS '服务端生成的独立UUID，不能用用户编号代替';
COMMENT ON COLUMN tenants.tenant_kind IS 'LOCAL仅允许固定本地身份；DEMO为后续演示成员关系保留';
COMMENT ON TABLE tenant_memberships IS '服务端验证的成员归属；当前每用户一个租户，本地租户可有多用户';
COMMENT ON COLUMN tenant_memberships.membership_role IS 'LOCAL_MEMBER保留原用户角色，DEMO_USER不能取得ADMIN或OPERATOR';
COMMENT ON INDEX uq_demo_tenant_member IS '首版每个演示租户至多一个成员，不限制本地多用户';
COMMENT ON FUNCTION enroll_local_role_membership() IS '仅原本地角色授予时补充缺失成员；不重建用户、不迁移已有成员归属';
