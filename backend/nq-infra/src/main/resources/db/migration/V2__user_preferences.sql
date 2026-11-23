CREATE TABLE user_preferences (
    user_id bigint PRIMARY KEY REFERENCES users(id),
    version bigint NOT NULL CHECK (version > 0),
    locale varchar(8) NOT NULL CHECK (locale IN ('zh-CN', 'en-US')),
    hide_login_disclosures boolean NOT NULL DEFAULT false,
    default_market jsonb,
    watchlist jsonb NOT NULL DEFAULT '[]'::jsonb,
    notifications_read_at timestamptz,
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT user_preferences_default_market_object CHECK (
        default_market IS NULL OR jsonb_typeof(default_market) = 'object'
    ),
    CONSTRAINT user_preferences_watchlist_bound CHECK (
        CASE WHEN jsonb_typeof(watchlist) = 'array' THEN jsonb_array_length(watchlist) <= 20 ELSE false END
    )
);

COMMENT ON TABLE user_preferences IS '认证用户的界面偏好，不包含账户默认权威或交易授权';
COMMENT ON COLUMN user_preferences.version IS '并发保存版本；旧版本不得覆盖新偏好';
COMMENT ON COLUMN user_preferences.hide_login_disclosures IS '只控制登录后的声明提示，法律原文始终可以主动查看';
COMMENT ON COLUMN user_preferences.default_market IS '默认查看公开行情的交易所、市场和标的；空值表示尚未选择';
COMMENT ON COLUMN user_preferences.watchlist IS '按用户排序的自选公开标的，最多二十项，不代表交易能力';
COMMENT ON COLUMN user_preferences.notifications_read_at IS '用户读过通知的时点，不等同于业务告警确认或解决';
