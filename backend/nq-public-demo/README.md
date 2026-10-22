# Public Demo 注册模块

模块只进入显式 `public-demo` Maven 构建。默认 Self-Hosted 不依赖此模块，无法通过 Spring profile 或 `nq.demo.registration-enabled` 加载注册 Controller。

```powershell
mvn -f backend/pom.xml -Ppublic-demo -pl nq-app -am package '-DskipTests'
```

前端对应构建使用 `VITE_DISTRIBUTION=PUBLIC_DEMO`；默认 `SELF_HOSTED` 不包含注册与验证页面 chunk。Demo 业务入口仍须服务器开启 `nq.demo.tenant-business-enabled`，不是前端授予权限。

## 运行要求

使用独立、已迁移到 V45 的 PostgreSQL 数据库和现有受限运行角色安装方式。除原有权限外，仅授予运行角色这两个函数的 EXECUTE，不授予直接写 Tenant、Membership 或 execution control 的权限：

```sql
GRANT EXECUTE ON FUNCTION nq_create_demo_pending(uuid,text,text,text,text) TO <runtime_role>;
GRANT EXECUTE ON FUNCTION nq_initialize_demo_identity(uuid,text) TO <runtime_role>;
GRANT SELECT,INSERT,UPDATE ON demo_registrations,demo_email_verifications,demo_email_outbox,
    demo_request_limits,tenant_sim_accounts TO <runtime_role>;
GRANT DELETE ON demo_request_limits TO <runtime_role>;
```

运行时需要显式选择安全配置（本地隔离测试使用 `ci`），禁用默认开发 bootstrap、私有 WebSocket、OKX recovery、instrument catalog sync 和所有 LIVE/真实 provider/client/exchange 开关。Demo 模块的启动守卫拒绝危险配置、旧 local/test 默认身份和真实交易执行配置。

注册与本地邮件测试所需属性：

| 属性 | 含义 |
| --- | --- |
| `nq.demo.registration-enabled=true` | 开启注册、验证消费及重发；关闭时保留回执和已有业务数据 |
| `nq.demo.tenant-business-enabled=true` | 开启已隔离的 Demo 研究与 SIM 白名单 |
| `nq.demo.token-secret` | 安全配置中提供的独立随机秘密，至少 32 字符；禁止入库、入日志或提交 |
| `nq.demo.verification-base-url` | 验证页面地址；生产格式要求 HTTPS，本地允许 loopback HTTP |
| `nq.demo.mail-dispatch-enabled=true` | 开启本地 Outbox 投递；默认关闭 |
| `spring.mail.host` / `spring.mail.port` | 本轮仅允许 loopback SMTP 测试设施 |
| `nq.demo.mail-poll-ms` | Outbox 轮询间隔，默认 1000 毫秒 |

Outbox 只保存随机 token 身份和令牌摘要，使用独立秘密重建同一邮件令牌。必须在服务重启和多个实例间保持该秘密一致；变更秘密会使尚未发送的旧邮件无法重建正确令牌，应受控关闭投递并通过重发建立新验证身份。令牌有效期 30 分钟、最多 5 次错误验证；邮件最多尝试 6 次，间隔有上限。重发有邮箱、来源地址限流及 60 秒冷却，未知邮箱与已有账户均返回相同的 `ACCEPTED`。来源地址使用直接连接地址，不信任任意代理头。

**`PUBLIC_EMAIL_DELIVERY_NOT_QUALIFIED`**：本轮只支持本地邮件验收，尚未接入或验收公共 SMTP；不是公网注册或部署批准。

## 经济与恢复合同

验证成功在一个事务中创建唯一 Demo Tenant/Membership/control/PAPER-SIM 账户，调用原 `SimCashFundingPort.fundOnce` 发放 `10000.00000000 USDT`。注册回执引用原 CASH/CONTRA 分录，`tenant_sim_accounts` 只保存账户与运行身份。任何失败回滚本事务的用户启用、Tenant、账户及资金；成功数据不通过删除用户或分录回滚。

注册时只预留 `ptr-UUID`，不建立虚假 PaperRun。首次 SIM 必须通过有 operationId 的 Continuous SIM CREATE 使用原账户和预留身份；原 funding ref_id 与正式运行一致。其他发布记录不能再次绑定，停止、登录、重启均不补发资金。收益继续由 canonical 分析计算，不把初始注资当利润。

关闭注册不删除待验证注册、Outbox、回执或资金；关闭 `nq.demo.tenant-business-enabled` 停止新 Demo HTTP 业务准入。已运行任务的暂停仍使用既有租户执行控制及 Continuous SIM stop 合同，不能把关闭注册误当成已停止全部后台工作。
