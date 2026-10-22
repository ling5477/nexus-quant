# 平台身份与启停安装

此能力只存在于 `-Ppublic-demo` 构建的 `NQ_PUBLIC_DEMO` 制品。默认 Self-Hosted 不包含注册、平台 Controller 或前端模块。先用独立安装身份完成当前正向 Flyway migration，再执行：

```powershell
psql <安全连接配置> -v runtime_role=<受限业务运行角色> -f scripts/demo/Install-PlatformAdmin.sql
```

此脚本仅授予受控启停函数的 EXECUTE，并撤销运行角色对平台审计表的直接权限；不授予经济事实 DML 或跨租户读取能力。[专用读取角色安装](README-platform-read.md) 是独立步骤。角色、口令与连接配置均由安装环境安全提供，不写入 Git、镜像或日志。

平台账号由独立 CLI 初始化，不装配成 Spring Runner 或公共 HTTP。将实际 Public Demo Boot JAR 解压到新建的受控安装目录，再从包含 `BOOT-INF` 的目录执行（Windows classpath 分隔符为 `;`，Linux/macOS 为 `:`）：

```powershell
$env:NQ_PLATFORM_INSTALL_JDBC_URL = '<明确的安装数据库地址>'
$env:NQ_PLATFORM_INSTALL_DB_USER = '<安装身份>'
java -cp '.;BOOT-INF/classes;BOOT-INF/lib/*' com.guidinglight.nexusquant.demo.platform.PlatformAdminInitializer <规范化平台用户名>
```

程序验证制品身份后，从交互终端隐藏读取数据库口令及确认两次的平台初始口令（20–72 UTF-8 字节，请使用密码管理器生成）。口令不接受命令行参数或公共 HTTP 输入。数据库已存在任意 PLATFORM_ADMIN 或平台初始化审计时拒绝再次创建；不会覆盖用户、补充 Membership、授予 ADMIN/OPERATOR、验证邮箱、建模拟账户或注资。首次登录强制通过既有改密流程，改密后重新登录。

启停请求为 `POST /api/platform/users/{userId}/disable|enable`，JSON 仅接受 `operationId` UUID 和非空 `reason`（最多 512 字符）。未知网络结果应以原 operationId、目标、动作和原因重试，返回原审计回执；同编号不同内容返回 409。待验证注册者与平台身份均不能被此命令启停。

禁用与登录/续期使用用户行锁，与经济提交使用同一执行控制行的冲突锁，事务提交前等待已获许可的有限经济事务。成功后旧 JWT 和 Session 不能继续，后台恢复和新经济提交保持拒绝；操作不取消订单、不回滚历史成交、不改余额或收益。启用保留 `suspended` 与 `requires_manual_resume`、旧 Session 撤销事实。用户重新登录后走原显式 Resume；仅属于平台禁用且执行代际仍一致的暂停可解除，其他原因仍拒绝。

数据库结构仅正向升级。关闭平台管理入口时保留审计、禁用状态和人工恢复要求，不恢复会话或自动继续 SIM；不能回退到不具备当前租户隔离能力的旧包。本轮脚本不授予真实交易、公网部署或 v1.0.1 发布权限。
