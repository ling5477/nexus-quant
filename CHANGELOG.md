# 1.0.0

首次产品版本（尚未正式发布）。

- OKX Spot 公开行情、数据集、策略研究、回测与发布。
- Continuous SIM、Paper matching、风险、订单/成交/账务及运行监控。
- Docker / PostgreSQL 16 产品运行时与 Windows、macOS、Linux 安装入口。
- 首次管理员创建、强制改密、持久化认证代际及旧令牌失效。
- Docker 官方发行 bootstrap、daemon 启动等待、host preflight 与确定性 Runtime 资源档位。
- 本地启动、停止、重启、status/doctor、备份、完整数据库恢复与保留数据卸载；purge 需要二次确认。
- 可信 manifest/digest 检查、只提示的 check-update、用户确认更新、失败恢复、已知 receipt 回滚；不执行自动更新。
- LIVE 自动执行、期货/杠杆、Binance 生产、AI 交易、HFT 不在本版本范围内。

许可证已确定为 Apache-2.0，参见 LICENSE；第三方声明和双语风险声明保留。

Windows 11 amd64 / 已运行 Docker Desktop 的隔离全新安装、PG16.15/V34、备份恢复及恢复后重启已验证。升级仍为 `UPGRADE_NOT_VERIFIED`；长时运行观察 已完成72小时观察，原审查因定点 UI 证据缺失未接受；用户已作 `ACCEPT_WITH_EVIDENCE_EXCEPTION` 决定并豁免72小时重跑，原结论保留。历史UI缺口按 `ADMITTED_WITH_DOCUMENTED_EXCEPTION` 单列准入，当前运行差异另经定向验证覆盖；新包使用新源码身份和摘要。公开正式发布尚未完成，其他平台仍未取得正式资格。

保留普通账户收益来源、真实手续费来源、OHLC 实际事件时点和旧 Paper 未知币种权益四项限制。LIVE 仍禁用。
