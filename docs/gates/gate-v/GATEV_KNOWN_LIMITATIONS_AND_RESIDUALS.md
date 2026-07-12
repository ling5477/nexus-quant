# GateV Known Limitations and Residuals

1. Python manifest preview：`No-file residual / NOT IMPLEMENTED`。Java 保持 no-file、`UNAVAILABLE / UNKNOWN` 基线；该 residual 不阻断 GateV pre-tag closeout，但不得写成已实现或 trading-ready。
2. Scheduler 默认关闭；GateV 证明的是受控实现与测试边界，不证明生产启用、长期运行稳定性或自动 remediation。
3. PostgreSQL advisory transaction timeout 不能强制终止任意不响应 interrupt 的非 JDBC 无限阻塞 callback；当前 callback 受 bounded local read-only contract 约束。
4. tenant model 仍为固定 `NQ_LOCAL` 单租户边界，不是通用 multi-tenant platform；跨 owner 仅 ADMIN 且仍受 tenant scope 限制。
5. case retention 只记录 `retention_until` 政策；GateV 不实现自动 hard-delete/archive job。
6. 前端 build 有既有 large-chunk warning；Playwright 有 Ant Design v5 / React 19 compatibility warning。两者未造成 build/test failure，但应在独立前端治理任务处理。
7. 长期本地 PostgreSQL 的 V33 checksum 存在历史 drift；本轮未执行 repair。当前代码以 disposable PostgreSQL 16.14 从 V1 到 V33 重新验证通过。
8. pre-tag archive 没有 tag object 或 tagged-commit CI；必须等待本任务 review、提交、exact-HEAD CI 与独立授权后再创建 `nq-gatev-freeze`。

这些限制不扩大 GateV 范围，也不构成 LIVE、Shadow、real provider、private trading、permission probe、AI、DH、Integration 或 Python runtime 的授权。
