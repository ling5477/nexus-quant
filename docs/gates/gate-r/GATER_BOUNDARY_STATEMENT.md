# GateR Boundary Statement

## Closed state vs runtime capability

本归档明确：GateR 冻结仅代表 local fact / read-only diagnostics 的基线收口，不代表以下能力：

- LIVE 运行（`LIVE: DISABLED`）。
- AI runtime（`AI: NOT STARTED`）。
- DH runtime 集成（`DH runtime: NOT INTEGRATED`）。
- RealClient / real provider / private trading adapter / real permission probe 实现。
- order / cancel / transfer / withdraw。
- 真实订单授权（trading authorization）。

## Allowed runtime behavior at closeout

- Shadow Run 仍为 read-only local fact / diagnostic only。
- no-side-effect；结果可读、不可执行。
- 禁止 scheduler 与后台 runner；禁止 runner side-effect endpoint。
- 禁止 credential/material 输出。

## No false positive declarations

- 本状态不表示 GateR `FROZEN / ACCEPTED / TAGGED` 可等价于 live execution ready。
- 本状态不表示可下单、可修改账户、可变更账本、可回测资金、可启动真实 trading 流程。
