# 凭据版本与稳定操作合同

账户域持有凭据版本与生命周期事实。新入口委托原 ExchangeAccountCredentialCommandService 与结构校验服务，不新增交易、订单、成交或账务权威。旧凭据接口保持兼容，正式账户页改用新稳定操作入口。

| 接口 | 输入、结果与边界 |
| --- | --- |
| GET /api/exchange-accounts/{accountId}/credentials | 当前owner的全部生命周期脱敏摘要；limit默认20、最大50；before为排他的版本ID游标，按ID降序，返回items、hasMore、nextBefore。事务与查询最多5秒，无逐版本解密或外部调用。 |
| POST /api/exchange-accounts/{accountId}/credentials/operations/{operationId} | UUID稳定身份；type、credentialType和明确credentialId（CREATE除外）；只有CREATE/ROTATE接受材料，ROTATE/ENABLE必填原因。返回SUCCEEDED、提交时刻和脱敏历史摘要。 |
| GET /api/exchange-accounts/{accountId}/credentials/operations/{operationId} | 当前owner、账户和原UUID下的成功回执。404仅代表当前未记录成功结果，不证明原请求失败、停止或未发送。 |

未认证401；无写角色403；账户或版本不可见404；非法身份/范围/字段400；生命周期冲突409。CREDENTIAL_OPERATION_IDENTITY_CONFLICT表示已成功身份的输入或指纹密钥不匹配，应查询原结果，不能清除原身份当作失败。读取沿用现有VIEWER权限；写入沿用ADMIN/OPERATOR权限和owner约束。

CREATE只允许同类型不存在使用中版本，避免隐式替换。ROTATE锁定指定使用中版本，保留旧版本及审计；DISABLE、ENABLE、EXPIRE、REVOKE完全委托既有状态合同，REVOKED不可重新启用。VERIFY锁定明确版本，校验并回写该版本；并发轮换先完成时，旧版本校验被拒绝，不把新版本替换成校验目标。结构校验不调用真实交易所，不证明连接、权限、真实交易或LIVE授权。权限探活仍由原独立权限合同拥有，新操作入口不触发它。

同owner、账户、UUID先取得事务锁，再锁账户串行新入口的不同操作。完整有序长度前缀输入使用现有masterKey分域派生HMAC，包含材料、类型、目标及原因；主密钥与keyVersion变化时POST拒绝重放，GET仍可查询已有结果。成功回执与版本、校验和审计同一最多10秒事务；任一步失败全部回滚。不储存原请求、原材料、原因或解密payload到回执。V6增量增加不可变回执、owner/account/version外键、JSON字段白名单和有界历史索引；V1—V5不改写。

前端仅在sessionStorage按用户保留账户、UUID、操作类型、凭据类型、目标版本和待确认阶段。密钥和原因只在当前表单及单次请求内存中使用，提交结束、失败、关闭及换身份时清空；不进入mutation缓存。刷新后的SUBMITTING转UNKNOWN，无自动重放。恢复先查询；404后用户显式重新输入原材料和原因，再确认同一身份提交。成功记录存在时不同输入拒绝；无回执时仍不能把查询当作失败证明。跨浏览器/session丢失恢复不在本合同范围，不能把本地身份当作业务事实。

文本输入在计算指纹前拒绝未配对UTF-16代理字符，返回400且不产生回执或业务写入；正常中文、重音字符、完整补充字符和合法U+FFFD保持原值，避免有损UTF-8编码把不同材料绑定为同一成功输入。

操作回执是提交时历史摘要，不代表当前使用状态。确认后重新读取版本列表；停用后查询早期CREATE回执中的ACTIVE不会重新启用凭据。读、恢复或写失败保持页面错误和原身份；用户切换后拒绝把旧响应用于新身份。
