# 1.0.1 — 候选 / Candidate (unreleased)

Release Date: 尚未发布 / Not released

Release URL: 无（尚未创建 Release） / None (no Release created)

## Features

- Self-Hosted / Public Demo 双发行物；Demo 注册、邮箱验证、Tenant 初始化及幂等 10,000 USDT 模拟资金。
- Platform Admin 只读租户绩效、用户启停、Session 撤销与禁用后执行阻断；管理员无需普通 Tenant Membership。

## Improvements

- Tenant 隔离、注册限流与任务资源配额；本地双发行物容器资格见当前资格报告。

## Bug Fixes

- Canonical SIM 旧日报拒绝返回虚假零值，沿用既有 canonical 分析。
- Demo 管理员读取策略、权益快照和 Docker 运行集成修复。

## Breaking Changes

- 数据库正向迁移；回退旧应用必须沿用更新事务及对应备份恢复，不能只替换镜像。

## Known Issues

- Self-Hosted v1.0.0 → v1.0.1 正式包升级待候选资格验证。
- 版本历史、What's New 与目标更新说明已合并，尚未发布；正式包更新体验仍待资格验证。
- Public Demo 未公网正式上线：真实邮件、HTTPS/TLS、容量与公共行情再展示许可仍待资格证据。本地 SMTP 不代表真实投递资格。
- LIVE 禁用；普通账户权益、真实手续费、OHLC 事件时点与旧 Paper 未知币种权益限制保留。

# 1.0.0 — 已发布 / Released

Release Date: 2026-10-20 (UTC)

Release URL: https://github.com/ling5477/nexus-quant/releases/tag/v1.0.0

首次产品版本，已正式发布（非 draft、非 prerelease）。

## Features

- OKX Spot 公开行情、数据集、策略研究、回测与发布。
- Continuous SIM、Paper matching、风险、订单/成交/账务及运行监控。
- Docker / PostgreSQL 16 产品运行时与 Windows、macOS、Linux 安装入口。
- 首次管理员创建、强制改密、持久化认证代际及旧令牌失效。
- Docker 官方发行 bootstrap、daemon 启动等待、host preflight 与确定性 Runtime 资源档位。
- 本地启动、停止、重启、status/doctor、备份、完整数据库恢复与保留数据卸载；purge 需要二次确认。
- 可信 manifest/digest 检查、只提示的 check-update、用户确认更新、失败恢复、已知 receipt 回滚；不执行自动更新。
- LIVE 自动执行、期货/杠杆、Binance 生产、AI 交易、HFT 不在本版本范围内。

许可证已确定为 Apache-2.0，参见 LICENSE；第三方声明和双语风险声明保留。

Windows 11 amd64 / 已运行 Docker Desktop 的隔离全新安装、PG16.15/V34、备份恢复及恢复后重启已验证。升级仍为 `UPGRADE_NOT_VERIFIED`；长时运行观察 已完成72小时观察，原审查因定点 UI 证据缺失未接受；用户已作 `ACCEPT_WITH_EVIDENCE_EXCEPTION` 决定并豁免72小时重跑，原结论保留。历史UI缺口按 `ADMITTED_WITH_DOCUMENTED_EXCEPTION` 单列准入，当前运行差异另经定向验证覆盖；新包使用新源码身份和摘要。公开正式发布已完成，其他平台仍未取得正式资格。

保留普通账户收益来源、真实手续费来源、OHLC 实际事件时点和旧 Paper 未知币种权益四项限制。LIVE 仍禁用。

## Improvements

- Docker daemon 启动等待、host preflight 和明确的 Runtime 资源档位。

## Bug Fixes

- 首次改密、持久化认证代际与旧令牌失效保护。

## Breaking Changes

- 首次产品版本，无产品版本间升级声明。

## Known Issues

- 其他平台尚未取得正式资格；LIVE 禁用。上述外部数据与历史观察限制保留。
