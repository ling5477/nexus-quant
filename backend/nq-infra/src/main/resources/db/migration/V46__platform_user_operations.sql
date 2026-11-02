-- 平台命令只改变认证与执行控制；不写入任何经济事实。
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '120s';

CREATE TABLE platform_user_operations (
    operation_id uuid PRIMARY KEY,
    actor_user_id bigint NOT NULL REFERENCES users(id),
    target_user_id bigint NOT NULL REFERENCES users(id),
    target_tenant_id uuid NOT NULL REFERENCES tenants(tenant_id),
    action varchar(8) NOT NULL CHECK(action IN ('ENABLE','DISABLE')),
    request_hash varchar(64) NOT NULL CHECK(request_hash ~ '^[0-9a-f]{64}$'),
    reason varchar(512) NOT NULL CHECK(length(btrim(reason)) BETWEEN 1 AND 512),
    before_enabled boolean NOT NULL,
    after_enabled boolean NOT NULL,
    before_auth_version bigint NOT NULL,
    after_auth_version bigint NOT NULL,
    before_execution_epoch bigint NOT NULL,
    after_execution_epoch bigint NOT NULL,
    before_suspended boolean NOT NULL,
    resume_permitted boolean NOT NULL,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    CHECK(after_auth_version>=before_auth_version AND after_execution_epoch>=before_execution_epoch),
    CHECK((action='ENABLE' AND after_enabled) OR (action='DISABLE' AND NOT after_enabled))
);
CREATE INDEX idx_platform_operations_target ON platform_user_operations(target_user_id,created_at DESC,operation_id);
REVOKE ALL ON platform_user_operations FROM PUBLIC;
COMMENT ON TABLE platform_user_operations IS '不可变平台启停回执及审计，不承载业务经济事实';

CREATE TABLE platform_identity_initializations (
    user_id bigint PRIMARY KEY REFERENCES users(id),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    installed_by name NOT NULL DEFAULT session_user
);
REVOKE ALL ON platform_identity_initializations FROM PUBLIC;

CREATE FUNCTION nq_preserve_platform_audit() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'platform audit is immutable' USING ERRCODE='23514';
END;
$$;
CREATE TRIGGER trg_platform_operation_immutable BEFORE UPDATE OR DELETE ON platform_user_operations
    FOR EACH ROW EXECUTE FUNCTION nq_preserve_platform_audit();
CREATE TRIGGER trg_platform_identity_immutable BEFORE UPDATE OR DELETE ON platform_identity_initializations
    FOR EACH ROW EXECUTE FUNCTION nq_preserve_platform_audit();

CREATE FUNCTION nq_require_platform_session(p_actor bigint,p_session text,p_version bigint) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public,pg_temp AS $$
BEGIN
    IF NOT EXISTS(SELECT 1 FROM users u JOIN auth_sessions s ON s.user_id=u.id
        WHERE u.id=p_actor AND u.enabled AND NOT u.must_change_password AND u.auth_version=p_version
            AND s.session_id=p_session AND s.auth_version=u.auth_version AND s.revoked_at IS NULL
            AND s.expires_at>clock_timestamp() AND s.last_active_at>clock_timestamp()-interval '30 minutes'
            AND EXISTS(SELECT 1 FROM user_roles ur JOIN roles r ON r.id=ur.role_id
                WHERE ur.user_id=u.id AND r.role_code='PLATFORM_ADMIN')
            AND NOT EXISTS(SELECT 1 FROM user_roles ur JOIN roles r ON r.id=ur.role_id
                WHERE ur.user_id=u.id AND r.role_code<>'PLATFORM_ADMIN')
            AND NOT EXISTS(SELECT 1 FROM tenant_memberships m WHERE m.user_id=u.id)) THEN
        RAISE EXCEPTION 'PLATFORM_ACCESS_DENIED' USING ERRCODE='42501';
    END IF;
END;
$$;
REVOKE ALL ON FUNCTION nq_require_platform_session(bigint,text,bigint) FROM PUBLIC;

CREATE FUNCTION nq_platform_user_operation(p_operation uuid,p_actor bigint,p_session text,p_version bigint,
    p_target bigint,p_action text,p_hash text,p_reason text) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public,pg_temp AS $$
DECLARE original platform_user_operations%ROWTYPE; target users%ROWTYPE; control tenant_execution_controls%ROWTYPE;
    identity uuid; after_version bigint; after_epoch bigint; inherited_pause boolean;
BEGIN
    PERFORM nq_require_platform_session(p_actor,p_session,p_version);
    IF p_action NOT IN ('ENABLE','DISABLE') OR p_hash !~ '^[0-9a-f]{64}$'
        OR length(btrim(p_reason)) NOT BETWEEN 1 AND 512 OR p_operation IS NULL THEN
        RAISE EXCEPTION 'PLATFORM_INPUT_INVALID' USING ERRCODE='23514';
    END IF;
    PERFORM pg_advisory_xact_lock(hashtextextended('platform-user-operation:'||p_operation::text,341));
    SELECT * INTO original FROM platform_user_operations WHERE operation_id=p_operation;
    IF FOUND THEN
        IF (original.actor_user_id,original.target_user_id,original.action,original.request_hash,original.reason)
            IS DISTINCT FROM (p_actor,p_target,p_action,p_hash,p_reason) THEN
            RAISE EXCEPTION 'PLATFORM_OPERATION_CONFLICT' USING ERRCODE='23514';
        END IF;
        RETURN to_jsonb(original);
    END IF;
    -- 与登录、续期和改密使用同一用户行锁；控制锁与经济提交的 SHARE 锁冲突并保持至提交。
    SELECT * INTO target FROM users WHERE id=p_target FOR UPDATE;
    IF NOT FOUND OR EXISTS(SELECT 1 FROM user_roles ur JOIN roles r ON r.id=ur.role_id
        WHERE ur.user_id=p_target AND r.role_code<>'DEMO_USER') THEN
        RAISE EXCEPTION 'PLATFORM_TARGET_NOT_ALLOWED' USING ERRCODE='42501';
    END IF;
    SELECT m.tenant_id INTO identity FROM tenant_memberships m JOIN tenants t USING(tenant_id)
        JOIN demo_registrations d ON d.user_id=m.user_id AND d.tenant_id=m.tenant_id AND d.status='READY'
        WHERE m.user_id=p_target AND m.membership_kind='DEMO' AND m.membership_role='DEMO_USER'
            AND t.tenant_kind='DEMO';
    IF identity IS NULL THEN RAISE EXCEPTION 'PLATFORM_TARGET_NOT_READY' USING ERRCODE='23514'; END IF;
    SELECT * INTO STRICT control FROM tenant_execution_controls WHERE tenant_id=identity FOR UPDATE;
    -- 多次启停仍可继承同一个平台暂停；其他暂停导致代际变化后不能继承解除权限。
    inherited_pause:=NOT control.suspended OR EXISTS(SELECT 1 FROM platform_user_operations d
        WHERE d.target_tenant_id=identity AND d.action='DISABLE' AND d.resume_permitted
            AND d.after_execution_epoch=control.execution_epoch);
    IF p_action='DISABLE' AND target.enabled THEN
        UPDATE users SET enabled=false,auth_version=auth_version+1,updated_at=clock_timestamp() WHERE id=p_target;
        UPDATE auth_sessions SET revoked_at=clock_timestamp() WHERE user_id=p_target AND revoked_at IS NULL;
        -- 用户触发器通常已完成一次代际推进；已暂停状态也必须使旧许可失效一次。
        UPDATE tenant_execution_controls SET suspended=true,requires_manual_resume=true,
            execution_epoch=CASE WHEN execution_epoch=control.execution_epoch THEN execution_epoch+1 ELSE execution_epoch END
            WHERE tenant_id=identity;
    ELSIF p_action='ENABLE' AND NOT target.enabled THEN
        UPDATE users SET enabled=true,updated_at=clock_timestamp() WHERE id=p_target;
        -- 启用只恢复认证资格，暂停标志和人工恢复要求均保留。
    END IF;
    SELECT auth_version INTO after_version FROM users WHERE id=p_target;
    SELECT execution_epoch INTO after_epoch FROM tenant_execution_controls WHERE tenant_id=identity;
    INSERT INTO platform_user_operations(operation_id,actor_user_id,target_user_id,target_tenant_id,action,request_hash,
        reason,before_enabled,after_enabled,before_auth_version,after_auth_version,before_execution_epoch,
        after_execution_epoch,before_suspended,resume_permitted)
    VALUES(p_operation,p_actor,p_target,identity,p_action,p_hash,p_reason,target.enabled,p_action='ENABLE',
        target.auth_version,after_version,control.execution_epoch,after_epoch,control.suspended,inherited_pause)
    RETURNING * INTO original;
    RETURN to_jsonb(original);
END;
$$;
REVOKE ALL ON FUNCTION nq_platform_user_operation(uuid,bigint,text,bigint,bigint,text,text,text) FROM PUBLIC;

-- 只有原有显式 Resume 路径可解除由平台禁用造成的暂停；其他原因暂停仍 fail closed。
CREATE OR REPLACE FUNCTION nq_resume_tenant_execution(p_run text) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public,pg_temp AS $$
DECLARE identity uuid:=nq_entity_tenant('paper',p_run); control tenant_execution_controls%ROWTYPE;
BEGIN
    IF NOT COALESCE(nq_tenant_is_current(identity),false) THEN RAISE EXCEPTION 'RUN_NOT_FOUND' USING ERRCODE='42501'; END IF;
    SELECT * INTO STRICT control FROM tenant_execution_controls WHERE tenant_id=identity FOR UPDATE;
    IF NULLIF(current_setting('nq.execution_epoch',true),'')::bigint IS DISTINCT FROM control.execution_epoch THEN
        RAISE EXCEPTION 'TENANT_EXECUTION_EPOCH_STALE' USING ERRCODE='42501';
    END IF;
    IF control.suspended THEN
        IF NOT EXISTS(SELECT 1 FROM platform_user_operations d JOIN tenant_memberships m ON m.user_id=d.target_user_id
            JOIN users u ON u.id=m.user_id JOIN tenants t ON t.tenant_id=m.tenant_id
            WHERE d.target_tenant_id=identity AND d.action='DISABLE' AND d.before_enabled AND d.resume_permitted
                AND d.after_execution_epoch=control.execution_epoch AND u.enabled AND m.status='ACTIVE' AND t.status='ACTIVE'
                AND EXISTS(SELECT 1 FROM platform_user_operations e WHERE e.target_user_id=u.id AND e.action='ENABLE'
                    AND e.created_at>d.created_at AND e.after_execution_epoch=control.execution_epoch)) THEN
            RAISE EXCEPTION 'TENANT_SUSPENDED' USING ERRCODE='42501';
        END IF;
        UPDATE tenant_execution_controls SET suspended=false WHERE tenant_id=identity;
    END IF;
    UPDATE tenant_execution_controls SET requires_manual_resume=false WHERE tenant_id=identity
        RETURNING execution_epoch INTO control.execution_epoch;
    PERFORM set_config('nq.execution_epoch',control.execution_epoch::text,true);
END;
$$;

-- 已有注册函数也遵循同一解析规则；仅正向修复，不改写历史迁移校验和。
ALTER FUNCTION nq_create_demo_pending(uuid,text,text,text,text) SET search_path TO pg_catalog,public,pg_temp;
ALTER FUNCTION nq_initialize_demo_identity(uuid,text) SET search_path TO pg_catalog,public,pg_temp;
-- 已经暂停时叠加另一种停用原因也使旧平台恢复凭据失效，不能把布尔值未变化当作来源未变化。
CREATE OR REPLACE FUNCTION nq_suspend_disabled_tenant() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public,pg_temp AS $$
BEGIN
    CASE TG_TABLE_NAME
        WHEN 'tenants' THEN
            IF NEW.status IS DISTINCT FROM OLD.status THEN
                UPDATE tenant_execution_controls c SET suspended=(NEW.status<>'ACTIVE'),requires_manual_resume=true,
                    execution_epoch=CASE WHEN NEW.status<>'ACTIVE' AND c.suspended THEN c.execution_epoch+1 ELSE c.execution_epoch END
                    WHERE c.tenant_id=NEW.tenant_id;
            END IF;
        WHEN 'users' THEN
            IF NOT NEW.enabled AND OLD.enabled THEN
                UPDATE tenant_execution_controls c SET suspended=true,requires_manual_resume=true,
                    execution_epoch=CASE WHEN c.suspended THEN c.execution_epoch+1 ELSE c.execution_epoch END
                    FROM tenant_memberships m WHERE m.user_id=NEW.id AND m.membership_kind='DEMO' AND c.tenant_id=m.tenant_id;
            END IF;
        WHEN 'tenant_memberships' THEN
            IF NEW.status='DISABLED' AND OLD.status<>'DISABLED' THEN
                UPDATE tenant_execution_controls c SET suspended=true,requires_manual_resume=true,
                    execution_epoch=CASE WHEN c.suspended THEN c.execution_epoch+1 ELSE c.execution_epoch END
                    WHERE c.tenant_id=NEW.tenant_id AND NEW.membership_kind='DEMO';
            END IF;
    END CASE;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION nq_suspend_mixed_tenant_role() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public,pg_temp AS $$
BEGIN
    UPDATE tenant_execution_controls c SET suspended=true,requires_manual_resume=true,
        execution_epoch=CASE WHEN c.suspended THEN c.execution_epoch+1 ELSE c.execution_epoch END
        FROM tenant_memberships m JOIN roles r ON r.id=NEW.role_id
        WHERE m.user_id=NEW.user_id AND m.membership_kind='DEMO' AND c.tenant_id=m.tenant_id AND r.role_code<>'DEMO_USER';
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION nq_suspend_removed_membership() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public,pg_temp AS $$
BEGIN
    IF OLD.membership_kind='DEMO' THEN
        UPDATE tenant_execution_controls c SET suspended=true,requires_manual_resume=true,
            execution_epoch=CASE WHEN c.suspended THEN c.execution_epoch+1 ELSE c.execution_epoch END
            WHERE c.tenant_id=OLD.tenant_id;
    END IF;
    RETURN OLD;
END;
$$;

CREATE OR REPLACE FUNCTION nq_suspend_changed_demo_role() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public,pg_temp AS $$
BEGIN
    UPDATE tenant_execution_controls c SET suspended=true,requires_manual_resume=true,
        execution_epoch=CASE WHEN c.suspended THEN c.execution_epoch+1 ELSE c.execution_epoch END
        FROM tenant_memberships m WHERE m.user_id=OLD.user_id AND m.membership_kind='DEMO' AND c.tenant_id=m.tenant_id;
    IF TG_OP='DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;
