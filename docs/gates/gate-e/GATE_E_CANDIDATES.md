# GateE Candidates

> 来源：GateD 冻结收尾后剩余的非阻塞治理项。  
> 原则：不把这些项重新定义成 GateD 主阻塞。

---

## 1. 高优先级（建议 GateE Top 1）

### 1.1 Binance background reconcile 噪音治理
- 范围：统一 Binance scheduler / reconcile 的 credential 与 timestamp 口径，清理 `BINANCE_RECONCILE_ORDER_FAILED(credentials missing / -1021)` 这类伪噪音。
- 为什么排第一：
  - 它直接作用于当前唯一仍高频出现的执行域噪音点。
  - 影响面集中在 `nq-scheduler + nq-adapter-binance`，不需要重新打开 GateD 主线。
  - 产出可验证：审计日志收敛、reconcile 行为更可解释。
- 第一批不要混入：
  - account sync 扩展
  - snapshot 拉取增强
  - observability 指标体系
  - 大规模兼容债务清理

---

## 2. 中优先级（建议 GateE Top 2 / Top 3）

### 2.1 返回模型一致性收尾
- 范围：继续收紧 `Paper / OKX / Binance` 在未成交、成交、恢复、对账场景下的响应口径与文档描述。
- 价值：降低查询接口与验收脚本的特判分支，减少后续测试矩阵复杂度。

### 2.2 account / position snapshot 拉取增强
- 范围：增强真实 venue 的 snapshot 拉取、映射与查询视图一致性。
- 价值：为后续 account sync 扩展和查询面稳定化打基础。

### 2.3 深层兼容债务收口
- 范围：清理历史 alias、遗留命名、局部兼容构造器和现行脚本中的冗余兼容层。
- 价值：减少长期维护成本，但不应与 GateE 第一批混做。

---

## 3. 可后置治理项

### 3.1 account sync 扩展
- 说明：是能力扩边，不是 GateD 冻结后必须立刻处理的噪音治理项。

### 3.2 observability / 指标完善
- 说明：重要，但适合作为独立治理批，不要和执行域行为修复混在一起。

### 3.3 schema / metadata 收口
- 说明：仅在出现真实 schema 差异需求时再立项，不为迎合历史占位文案制造空 migration。

### 3.4 Binance 深度齐平
- 说明：属于扩边和体验增强，明确顺延，不进入 GateE 第一批。

---

## 4. 推荐排序

- Top 1：Binance background reconcile 噪音治理
- Top 2：返回模型一致性收尾
- Top 3：account / position snapshot 拉取增强

排序理由：
- Top 1 最贴近当前残余高噪音点，边界最窄、验证最直接。
- Top 2 能降低后续 query / acceptance / docs 的分叉成本。
- Top 3 对后续 account sync 与查询视图增强有承上启下作用，但比 Top 1 更容易扩散，因此排在后面。
