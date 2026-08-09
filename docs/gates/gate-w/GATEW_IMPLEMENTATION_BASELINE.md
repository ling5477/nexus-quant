# GateW Reconstructed Implementation Baseline

GateW 的目标是在 LIVE 继续关闭时，建立 OKX Spot 单 venue 的 typed read-only capability、default-deny endpoint
guard、bounded diagnostic、durable operational safety 与可审计 168h read-only soak。它不是交易授权 Gate。

## 已接受能力

1. GateW-1：typed capability matrix 与 default-deny endpoint guard，阻断 mutating/funds movement endpoint。
2. GateW-2：两个 typed private read-only diagnostic operation；默认不装配，未把 CI/mock 写成 real permission。
3. GateW-3：venue-rule facts、LIMIT-only local preview、bounded read-only reconciliation 与 pure diagnostic risk preflight；
   `executionReadiness=BLOCKED`。
4. GateW-4：durable kill switch、恢复/事件 drill、persistence/retention、human-review evidence binding 与 10,000 次 local
   no-egress soak。
5. GateW runtime：immutable release、root/POSIX 安装与校验、systemd fail-close、sanitized prerequisite、canonical
   control/seal 与 168h OKX read-only soak evidence。

## 数据与运行边界

GateW 新增的 schema 仅承载 venue-rule facts 与 durable kill-switch state；不得保存 credential、token、cookie、signature 或
raw provider response。正式 soak 只调用 allowlisted private read-only capability，order/cancel/transfer/withdraw/LIVE 计数均为
0。

`168h read-only soak ≠ 真实资金交易授权`。GateX 只能进入 `PLAN / NOT_STARTED`，真实小资金交易仍需独立
policy、审批、rollout、rollback 与新的显式授权。
