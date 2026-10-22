# 平台只读安装

先完成当前全部正向 Flyway migration，再由数据库安装管理员在受控终端执行：

```powershell
psql <安全连接配置> -v projection_owner=nq_platform_projection_owner -v reader_role=nq_platform_reader -f scripts/demo/Install-PlatformReadRole.sql
```

角色名必须是本次安装全新身份。脚本创建不可登录的投影函数所有者及独立读取登录角色，最后通过 `psql \password` 安全提示设置随机强口令；禁止将口令放入命令行、源码或提交。管理员连接凭据使用现有安全配置，不在脚本内保存。

投影所有者只有读取最小字段和执行认证检查的权限。读取登录角色只有三个投影函数的 EXECUTE，不继承投影所有者、不持有基础表 SELECT/DML，不可切换业务租户。函数逐次校验有效 PLATFORM_ADMIN 用户、会话和 auth_version，不能以任意 actor 参数获取权限。角色不是平台管理员账号，不能代替受控账号初始化。

Public Demo 安全配置中设置：

| 属性 | 含义 |
| --- | --- |
| `nq.demo.platform-read.jdbc-url` | 与业务使用同一个已隔离数据库的 JDBC 地址 |
| `nq.demo.platform-read.username` | 上述专门读取 LOGIN 角色 |
| `nq.demo.platform-read.password` | 由安全配置注入的独立口令 |

没有配置时平台读取返回 `503 PLATFORM_READ_DATABASE_NOT_CONFIGURED`，不回落到业务连接或安装管理员连接。每次读取在独立只读 REPEATABLE READ 事务执行，并检查角色无写权限、角色继承、基础经济事实读取权限或 schema CREATE 权限。连接池最多三个连接，连接等待五秒，查询五秒，事务八秒。

绩效只读取已有 `CANONICAL_SIM_PROJECTION / VERIFIED / CANONICAL_SIM_LEDGER_AND_FROZEN_MARK_V1` 冻结快照和 canonical 注资身份，使用原账户绩效计算器；不会调用普通租户服务或创建估值。注资是收益基准，不是利润。找不到有效市场估值时金额保持 `null` 与 `UNAVAILABLE`；最新冻结估值超过两小时或其后出现账务推进时标为 `STALE` 并明确历史 `asOf`。快照最多 999 个，超限返回 `HISTORY_WINDOW_LIMITED`。接口不返回原始 Ledger、私有研究 artifact 或凭据。

关闭平台入口不会删除审计、恢复 Session、恢复 SIM 或改写任何经济事实。此安装说明不授予公网部署或 v1.0.1 发布权限。
