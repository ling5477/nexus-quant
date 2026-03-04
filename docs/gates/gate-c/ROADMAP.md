# docs/gates/gate-c/ROADMAP.md
# Gate C ROADMAP

- GateC-0：执行链路前置改造（adapter 三分法 + AdapterRouter + external_order_id + 回执事件化）【必须】
- GateC-1：OKX Spot 接入（REST + 轮询同步）-> 门禁通过【必须】
- GateC-1.1：OKX 私有 WS（可选优化）+ REST reconcile 兜底
- GateC-2：Binance Spot 接入（复用框架）-> 门禁通过
- GateD：研究/回测/产物发布（AlphaCFG / RD-Agent / Alphalens 等放这里）
- GateE：运营生产化（告警、权限、SLO、回滚、对账增强）