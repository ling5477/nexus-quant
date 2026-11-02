-- 默认账户改为每用户唯一。旧版本的跨市场多默认身份有歧义，要求用户重新选择，不能任意回退。
-- 仅修改可变偏好，不改变账户、环境、凭据、交易或账务事实；升级前备份可恢复旧偏好。
UPDATE exchange_accounts
SET is_default = FALSE, updated_at = CURRENT_TIMESTAMP
WHERE is_default = TRUE
  AND (status <> 'ACTIVE' OR owner_user_id IN (
      SELECT owner_user_id FROM exchange_accounts WHERE is_default = TRUE
      GROUP BY owner_user_id HAVING COUNT(*) > 1
  ));

CREATE UNIQUE INDEX uq_exchange_accounts_user_default
    ON exchange_accounts (owner_user_id) WHERE is_default = TRUE;
COMMENT ON INDEX uq_exchange_accounts_user_default IS '每用户至多一个明确默认账户；临时查看不修改该关系';
