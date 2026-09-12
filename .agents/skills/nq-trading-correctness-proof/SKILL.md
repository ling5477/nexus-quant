---
name: nq-trading-correctness-proof
description: 用于 NQ 订单、成交、对账、账务、恢复或交易状态并发的正确性变更与证明。
---
# 交易正确性证明

围绕变更可能破坏的不变量和实际可达路径设计证明，保留 SIM/LIVE、租户/账户隔离、风控、状态机、幂等、账务和审计边界。证据不能只由方法存在或 mock 交互推断。

按问题选择成功、拒绝、部分失败、重复/并发以及超时后恢复场景。SQL 与持久化语义使用隔离 PostgreSQL；重启恢复要求跨进程/重启证据；外部系统使用受控 fixture，禁止真实交易副作用。

审查、候选/证据身份与验证范围由[统一合同](references/regression-delivery.md)定义。未知外部事实只阻塞依赖其最终性的操作；正确性缺口保留失败证据，不通过弱化断言完成验收。

完成标准是受影响不变量有可复验证据，或清楚定位尚未满足的验收条件。按需参考 [证明选择](references/proof-selection.md)。

按命中主题读取 [工程/安全边界](references/engineering-boundaries.md) 或 [回归与交付](references/regression-delivery.md)，无需加载无关标准。
