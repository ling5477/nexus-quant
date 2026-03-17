# 事件契约演进规则（EVOLUTION_RULES）

> Archive Notice
> - 本文件为根级历史留档，不是当前阶段的 Source of Truth。
> - 当前阶段请优先阅读 `docs/current/*` 与 `docs/gates/gate-e/*`。
> - 若需 Gate A 冻结快照，请优先参考 `docs/gates/gate-a/EVOLUTION_RULES.md`。

> 目标：保证事件的长期可读、可回放、可升级。  
> 事件契约是系统的“公共 API”，比内部代码更需要谨慎变更。

---

## 1. 版本字段

事件 Envelope 中必须包含：
- `schema_version`：事件 schema 版本（语义：字段结构版本）
- `event_version`：事件实例版本（可选，用于同 schema 下的轻微调整）

建议：
- `schema_version` 采用整数递增：1,2,3...
- 每次 breaking change 必须升级 `schema_version`

---

## 2. 允许的兼容变更（Backward Compatible）

在不升级 `schema_version` 的前提下，允许：
- 新增 **可选字段**（必须提供默认值语义）
- 扩展枚举（消费者必须有默认分支/unknown 处理）
- 增加 JSON 对象的可选属性（不改变既有字段含义）

禁止：
- 修改既有字段语义
- 更改字段类型（string → number 等）
- 删除字段
- 将可选字段变为必填

---

## 3. Breaking change 规则（必须升级 schema_version）

以下情况必须升级 `schema_version`：
- 字段类型变化、字段语义变化
- 字段删除或重命名（重命名视为删除+新增）
- 必填性变化（optional → required）
- 事件 topic 语义变化（同 topic 表示的业务事件类型发生变化）

---

## 4. 升级策略（推荐）

- **双写期**：生产者在过渡期同时发布 v1/v2（不同 topic 或同 topic 的不同 schema_version）
- **消费者兼容**：消费者先上线兼容读取 v1/v2
- **切换**：生产者切到只发新版本
- **清理**：过渡期后下线旧版本消费

> Gate A：只冻结规则，不要求实现双写。

---

## 5. Unknown 字段与前向兼容

消费者必须遵循：
- 解析时允许未知字段存在（忽略）
- 枚举未知值走 `UNKNOWN` 分支
- 反序列化失败必须可观测（指标 + 死信/隔离队列占位）

---

## 6. 变更流程（强制）

每次事件契约变更必须：
1. 更新 `docs/CONTRACTS.md`
2. 更新本文件（如涉及规则变化）
3. 在 `docs/DECISIONS.md` 增加 ADR（记录动机、影响面、迁移计划）
