\set ON_ERROR_STOP on
-- 使用安装身份运行；runtime_role 仅获得这一受限命令，不获得平台审计表写权限。
BEGIN;
SELECT format('REVOKE ALL ON platform_user_operations,platform_identity_initializations FROM %I', :'runtime_role') \gexec
SELECT format('GRANT EXECUTE ON FUNCTION nq_platform_user_operation(uuid,bigint,text,bigint,bigint,text,text,text) TO %I', :'runtime_role') \gexec
COMMIT;
