# NQ Documentation Governance — G2 Freeze Review

任务：`NQ-DOCS-GOVERNANCE-G2-FREEZE-REVIEW`

日期：2026-06-19

任务类型：DOCUMENTATION_GOVERNANCE_FREEZE_REVIEW + CURRENT_CONTROL_SEMANTIC_BASELINE_REVIEW + NAVIGATION_AND_LINK_HYGIENE_AUDIT

> 本轮为**只读冻结复核**。**没有移动、删除、重命名、归档任何文档**，未改 G1 五份冻结对象、`docs/gates/**`/`docs/archive/**`/`.agents/**`/`templates/**`、workflow、代码、测试、migration、依赖。仅新增本文并更新 `docs/current/README.md`/`STATUS.md`/`TESTING.md`/`WORKLOG.md`。

---

## 审查结论

**结论：`NQ-DOCS-GOVERNANCE-G2-FREEZE-REVIEW：PASS / ACCEPTED / FROZEN`**

- **G2 current-control drift repair = FROZEN / ACCEPTED**。
- P0 = 0；P1 = 0；P2 = 0；P3 = 3（信息性，不阻塞）。

## 关键定义：G2 是 **semantic baseline freeze**，不是 blob lock

G2 freeze 冻结的是**语义修复断言 + 导航模型 + 规则优先级 + current-control link hygiene**，**不是**把持续更新的 current-control 文档锁成不可修改 blob。

- `STATUS.md` / `WORKLOG.md` / `TESTING.md` / `ROADMAP.md` / `docs/current/README.md` 等**仍可正常追加带日期的真实状态记录与导航**（见 §7 允许维护）。
- 只有**恢复已修复缺陷**或**越过冻结证据边界**才使 G2 freeze 失效（见 §6）。
- 锚点：G2 repair commit `3c1f5ec0`、accept commit `7de61114`；G1 五份冻结对象自 `7eb7ae53` 起零 drift；`docs/gates/**`/`docs/archive/**`/`.agents/**`/`templates/**`/code/workflow 跨 G2 零 drift。

---

## 逐项核验

### 1. GateJ / GateK 导航语义（PASS）

- `PLAN_GATEJ.md` / `GATEJ_WORK_ORDER.md` 仍可导航，仅作历史/冻结证据入口（权威指向 `docs/gates/gate-j/`）。
- GateJ 明确为 **completed historical gate**；GateJ 权威冻结卷宗仍指向 `docs/gates/gate-j/`。
- `docs/current/` 17 份 GateJ 副本继续保持 **NON_AUTHORITATIVE / FUTURE_SUPERSEDE_CANDIDATE / G3**。
- GateK CI mainline 明确为 **COMPLETED / ACCEPTED**。
- current-control 入口可导航至 README / STATUS / ROADMAP / Authority Index / Evidence Index。

### 2. Current state semantic baseline（PASS）

- Batch 5A = **FROZEN / ACCEPTED**，且 `docs/README.md:43` 与 `ROADMAP.md:38` 显式声明 **“仅 4 个 no-backend smoke spec，不是 authenticated / backend E2E / 交易链路 / 真实 provider 覆盖”**。
- 5B-ENV = **P1 SECURITY ENHANCEMENT / NOT STARTED**；5B-SMOKE = **BLOCKED BY 5B-ENV**；4F-B~4F-F = **OPTIONAL BACKLOG / NOT STARTED**；static assertion = **OPTIONAL FUTURE HARDENING / NOT IMPLEMENTED**（top 口径无 completed 误标）。
- G1 = **FROZEN / ACCEPTED**。
- G2 当前 = **ACCEPTED / READY FOR FREEZE REVIEW**；全仓**无** “G2 = FROZEN” 误写（仅在否定语境出现该串）。本 freeze review 结论通过后，G2 标记为 **FROZEN / ACCEPTED**。

### 3. Rule 16 freeze assertion（PASS）

`docs/DOC_RULES.md` Rule 16 五级优先级完整、顺序正确、无矛盾：① 冻结证据保留优先 → ② current control 单一权威优先 → ③ 迁移前先建 index/redirect/compatibility mapping → ④ 复制仅过渡导航/必要快照且明确 authority → ⑤ 禁止为减少文件数删除或改写历史证据。“不重复” = current 层不出现并列权威，**不等于**删除历史 Gate 文档或冻结证据。

### 4. Link hygiene freeze assertion（PASS）

- `docs/current/API.md` → `../gates/gate-i/GATEI_API_PLAN.md`；`docs/current/DB_SCHEMA.md` → `../gates/gate-i/GATEI_DB_PLAN.md`；两目标存在、相对路径可解析。
- 当前控制文档 leading-slash malformed link = **0**。
- GateH/GateJ 冻结快照内 `./GATEI_*` 历史链接（4 处）**仍未被修改**（`docs/gates` 跨 G2 零 drift）。
- `docs/README.md` 冻结快照兼容入口仅提供导航，**未宣称已改写**历史快照。

### 5. Evidence navigation boundary（PASS，附 P3）

- `docs/README.md` 的 Documentation Governance Evidence 导航存在，显式枚举至 **G2 implementation**：governance plan + plan review、G1 implementation + G1 review + G1 freeze review、G2 implementation；并以分节标题将整个 review/freeze 类标注为 **HISTORICAL_EVIDENCE / RETAIN_IN_PLACE，不计入 G1 的 278 / 283**。
- `NQ_DOCS_G2_REVIEW.md`（G2 review）与本 `NQ_DOCS_G2_FREEZE_REVIEW.md`（G2 freeze review）**晚于**该导航段创建，由 standing class rule 治理并经 `docs/current/README.md` fact-file 列表可导航（见 P3-2）。
- **未修改** `NQ_DOCS_EVIDENCE_INDEX.md`（冻结对象，跨 G2 零 drift）。
- review/freeze 文档**未回写** G1 的 278 / 283。

---

## 6. G2 freeze 失效条件（任一触发须重新审查 G2 semantic baseline）

1. GateJ 历史文档重新被标记为 current authority。
2. GateK 或 Batch 5A 冻结状态被错误降级、遗漏或错误表述。
3. 将 5A 误写成 authenticated / backend E2E coverage。
4. 将 5B-ENV / 5B-SMOKE / 4F backlog / static assertion 误写为 completed。
5. Rule 16 五级优先级被删除、降级或矛盾化。
6. `API.md` / `DB_SCHEMA.md` 两条已修复链接恢复为 malformed leading-slash 路径。
7. 直接修改 `docs/gates/**` / `docs/archive/**` 的冻结快照或历史链接。
8. 修改 G1 五份冻结对象。

## 7. 允许的后续维护（**不**自动使 G2 freeze 失效）

- 在 `STATUS.md` / `WORKLOG.md` / `TESTING.md` 追加带日期的真实状态记录。
- 在 `ROADMAP.md` 新增未来规划，但不改写已冻结事实。
- 在 `docs/current/README.md` 新增导航链接，但不创造第二个 current authority。
- 为新增文档建立历史 evidence 导航。
- 后续 G3~G6 的受控文档治理变更。

---

## Findings

### P0 / P1 / P2

- 无。

### P3（信息性 / 受控后续处理，不阻塞冻结）

- **P3-1（carried-over）**：G1 冻结的 `NQ_DOCS_EVIDENCE_INDEX.md` 尚无物理 “Governance Evidence” section；review/freeze/G2 文档按 standing class rule（HISTORICAL_EVIDENCE / RETAIN_IN_PLACE，不计入 278/283）治理，物理进入 evidence index 须走**单独受控基线修订**（本轮做会触发 G1 冻结失效）。
- **P3-2（导航枚举，anti-recursion by-design）**：`docs/README.md` 的 Documentation Governance Evidence 导航显式枚举至 G2 implementation；`NQ_DOCS_G2_REVIEW.md` / 本 freeze review 晚于该段，经 standing class rule + `docs/current/README.md` fact-file 列表可导航。物理在 `docs/README.md` 逐一枚举每份 review/freeze 文档会重新引入无限递归，故按类治理；后续可在受控的 `docs/README.md` / evidence-index 修订中补充。本轮不允许改 `docs/README.md`，故记为 P3。
- **P3-3（历史日志，非 drift）**：`docs/current/STATUS.md` 早期里程碑条目按 as-of-time 记 `mainline IN PROGRESS`，属追加式时间序日志，已被顶部条目（`COMPLETED / ACCEPTED`）取代；改写将篡改历史。保留正确，无需动作。

---

## 检查 / 修改 / 验证 / 风险 / 回滚

- **检查文件（只读）**：G2 实施/评审文档、current-control 文档、G1 五份冻结对象、`docs/gates/gate-i|gate-h|gate-j` 链接目标；`git diff 7eb7ae53..HEAD`、`git log`、相对路径解析、语义断言 grep。
- **修改文件（本轮）**：新增 `docs/current/NQ_DOCS_G2_FREEZE_REVIEW.md`；更新 `docs/current/README.md`/`STATUS.md`/`TESTING.md`/`WORKLOG.md`（仅追加冻结记录）。
- **验证**：docs-only；G1 五对象 + gates/archive/.agents/templates/code/workflow 跨 G2 零 drift；malformed link = 0；frozen `./GATEI_*` 4 处未改；Rule 16 五级完整；无 G2-as-frozen 误写；`git diff --check` clean。
- **风险**：零迁移、零代码、零不可逆操作。
- **回滚**：删除本文并 revert 4 份 current 文档本轮追加段即可完全回滚。

---

## 状态结论（原样）

```text
NQ Docs Governance Plan = FROZEN FOR G1 BASELINE
G1 authority/evidence index = FROZEN / ACCEPTED
G2 current-control drift repair = FROZEN / ACCEPTED
G3 = READY FOR IMPLEMENTATION
G4～G6 = NOT STARTED
NQ GateK CI mainline = COMPLETED / ACCEPTED
Batch 5A = FROZEN / ACCEPTED
Batch 5B-ENV = P1 SECURITY ENHANCEMENT / NOT STARTED
Batch 5B-SMOKE = BLOCKED BY 5B-ENV
LIVE / AI / DH runtime / RealClient / real provider = 未开启、未接入、未实现
```
