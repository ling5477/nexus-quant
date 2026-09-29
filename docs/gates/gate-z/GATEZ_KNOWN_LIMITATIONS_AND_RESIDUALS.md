# GateZ known limitations and residuals

以下均为 `NON_BLOCKING_FOR_GATEZ`：本次不修改 schema、生产事实或交易代码。历史兼容字段与未来产品规划不能被“冻结”解释为已经清零。

| Residual / current state | Canonical owner | Why non-blocking | Future trigger / recommended disposition |
| --- | --- | --- | --- |
| `accounts` ↔ `exchange_accounts` 保留兼容 bridge；V58 为 ACTIVE SIM 建立确定性 legacy 身份 | Account identity / exchange account | 已接受 SIM 创建链使用约束内桥接，未见 GateZ scope P0/P1 | V1 canonical convergence 时核对调用方、数据迁移与退役条件 |
| `orders.venue` ↔ `exchange_code` 双列兼容 | canonical Order / JDBC repository | V5 同义约束/映射仍服务既存代码；本次无交易写侧变更 | 新 canonical schema work 先证明所有读写者迁移，再 forward-only 收敛 |
| `trades.exchange` ↔ `exchange_code` 双列兼容 | canonical Trade / JDBC repository | V5 兼容语义不改变已接受 SIM 事实 | Trade owner 收敛时审查索引、外键和 replay |
| `external_order_id` ↔ `exchange_order_id` 兼容 | Order/Trade exchange identity | V5 同步及历史引用仍存在，SIM 去重证据独立成立 | 外部订单身份统一时保留历史回填与负例证明 |
| `strategy_definitions` 有重复 INSERT owner 路径 | Strategy definition persistence | GateZ 单策略运行链已有确定性身份及接受测试 | 新策略创建入口或 owner 合并时统一写侧并补并发测试 |
| `strategy_definitions.version` 与 `strategy_versions.version` 语义重叠 | Strategy registry / frozen version | 前者是配置快照审计，后者是冻结执行版本；GateZ 固定版本有独立校验 | V1 策略版本模型收敛时明确迁移/兼容合同 |
| canonical Position 行无独立 realized/unrealized PnL 字段 | Position projection / economics query | `/facts` summary 与 Ledger/PnL 来源可用；PR #56 的 Position 列仅表示已实现字段 | 需要逐仓 PnL UI 时设计 canonical projection，禁止前端臆造 |

若未来入口改变可达性、并发或财务语义，须用新候选验证；这些残余不授权当下修复，也不把历史 UNKNOWN 改为 MATCH。
