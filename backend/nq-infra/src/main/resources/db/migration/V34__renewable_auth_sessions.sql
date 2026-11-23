CREATE TABLE auth_sessions (
    session_id VARCHAR(36) PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    auth_version BIGINT NOT NULL CHECK (auth_version >= 1),
    created_at TIMESTAMPTZ NOT NULL,
    last_active_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT auth_sessions_time_order CHECK (
        last_active_at >= created_at AND last_active_at < expires_at
        AND (revoked_at IS NULL OR revoked_at >= created_at)
    )
);
CREATE INDEX auth_sessions_user_created_idx ON auth_sessions(user_id, created_at DESC, session_id);
COMMENT ON TABLE auth_sessions IS '可撤销的登录会话；每用户最多保留十条，不存储原始令牌或密码。';
COMMENT ON COLUMN auth_sessions.session_id IS 'JWT绑定的独立登录身份，本身不能用作认证凭据。';
COMMENT ON COLUMN auth_sessions.auth_version IS '签发时的用户认证代际，改密后旧会话不可续期。';
COMMENT ON COLUMN auth_sessions.last_active_at IS '最近一次显式续期时间；普通业务查询及后台轮询不更新。';
COMMENT ON COLUMN auth_sessions.expires_at IS '首次登录确定的绝对截止时间，续期不得修改。';
