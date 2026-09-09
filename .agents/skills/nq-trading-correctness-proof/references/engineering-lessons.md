# 重复问题根因治理与工程经验

本文件是项目统一的 engineering lessons / troubleshooting reference，按共同机制追加短条目，相关 Skill 按需读取。具体任务的原始证据保留在其既有位置，以链接引用；不为每个小问题创建独立长文档，不把历史 Gate、commit、CI run 清单加入默认 Agent 上下文。

## 触发条件与排除项

满足下列任一条件，必须启动根因排查，不得继续只修表面症状：

- 同类问题跨任务、Gate、CI run 或模块出现两次及以上。
- 修复后复发，或相同 workaround、allowlist、compatibility patch 连续增加。
- 不同报错指向同一底层机制，或修一个问题会稳定触发下一个机械性问题。
- 同类人工操作已经形成重复排错流程。

“同类”按根因类别判断，不要求报错文本相同。必须有共同机制的证据或强迹象；一次性 typo、单次环境故障、已知外部服务偶发失败，以及 owner / mechanism 明确不同的相似报错，不自动升级为系统性根因任务。

## 必须执行的路径

```text
STOP PATCHING SYMPTOMS
→ REPRODUCE
→ TRACE HISTORY
→ IDENTIFY ROOT CAUSE
→ FIX ROOT CAUSE
→ ADD REGRESSION
→ CAPTURE PROJECT LESSON
```

停止连续追加表面补丁，先保留原失败并追踪历史，再在授权范围内修复源头。不得沿用“出现一次加一个例外，再出现扩大 allowlist”的循环。只有生产恢复确需临时止血时才允许例外，并且必须登记根因整改 owner、后续动作和退出条件；该例外不授予生产操作权限。

每次排查至少回答以下八项；未知项标明缺少什么证据，不补造历史：

1. 第一次何时出现？区分最早已确认记录与推断。
2. 之前如何修复？定位实际补丁和验证证据。
3. 为什么上次修复未阻止复发？指出其覆盖边界。
4. 多次故障共享哪个机制？给出复现、数据流或调用链证据。
5. canonical owner 在哪里：数据生成、domain model、状态机、transaction/concurrency、persistence、configuration、CI/checker、test harness、evidence、deployment、instruction/workflow，或明确的其他 owner？
6. 现有 workaround 应 DELETE、CONSOLIDATE 还是 KEEP TEMPORARILY？保留项须有原因及退出条件。
7. 哪项修改能使未来同类问题自然消失，而非仅消除当前报错？
8. 哪些自动化 regression 能阻止复发，如何运行及判定？

## 修复顺序与永久回归

优先定位和修复：源头数据 / 事实模型 → canonical owner → 核心实现 → 生成器 / adapter → validator / checker → consumer → 最后才是 allowlist / exception。不能为了让错误输入通过而削弱正确的检查器。

根因修复必须包含原失败场景的 permanent regression、至少一个相邻变体，以及有效正常场景的 positive control。断言必须证明共同生成机制受到约束；“本次不再报错”不足以证明未来同类问题不会再次产生。记录这些回归的固定入口、运行命令及成功/拒绝标准。

## 经验条目与可复用排查路径

每种共同机制在本文件维护一个短条目，至少包含：Problem pattern、Symptoms、Root cause、Why previous fix was insufficient、Canonical solution、Detection method、Debugging sequence、Regression tests、Do / Don't、Applicable modules。后续重复事件扩充该条目，以链接保留任务细节，不复制长日志。

稳定的排查路径按以下顺序记录；后续同类故障优先复用，再针对差异补证：

```text
现象 → 第一检查项 → 判断标准 → 第二检查项
→ 最小复现 → 根因分类 → 修复路径 → 验证命令
```

## Review、提交与完成定义

根因修复涉及 trading correctness、accounting、concurrency、migration/schema、credential/security、CI/release trust 或 production deployment 时，在验收或发布前保留一次真正独立的候选审查。普通工程根因修复采用 root-cause fix + regression + self-review + relevant tests，再按已有 Git 授权提交；不因“根因分析”自动增加多轮治理 review，也不扩大 commit/push/PR/merge 授权。

重复问题只有下列条件全部满足才可标记为系统性关闭：

```text
ROOT_CAUSE_IDENTIFIED
ROOT_CAUSE_FIXED
WORKAROUND_REDUCED_OR_REMOVED
ORIGINAL_REPRO_PASS
ADJACENT_REGRESSION_PASS
PROJECT_LESSON_CAPTURED
DEBUG_METHOD_REUSABLE
```

仅 CURRENT_SYMPTOM_FIXED 不得标记系统性关闭。KEEP TEMPORARILY 是处置状态，不能代替 WORKAROUND_REDUCED_OR_REMOVED 的实际证据。单批交付验收与系统性关闭分开记录，不改写既有验收或失败历史。

## 经验：随机 synthetic identity 进入 tracked evidence

| 项目 | 可复用结论 |
| --- | --- |
| Problem pattern / Symptoms | 不同 qualification 批次将随机 synthetic identity 原样写入 Git，secret scanner 在 client/event key 等字段重复报告 generic-api-key。 |
| 最早已确认记录 / 历史修复 | 已确认最早的本轮追踪记录为[2026-09-08：前一批交付及精确例外补丁](https://github.com/ling5477/nexus-quant/commit/e0d4a0276b2ee9c9a302556277ad605e57b109b5)。随后[另一批交付的失败 CI](https://github.com/ling5477/nexus-quant/actions/runs/34318927962)再次命中；不据此断言更早历史没有同类问题。 |
| Root cause / owner | test harness 的 evidence-export owner 缺少 runtime raw identity 与 tracked representation 的边界。随机值生成适合隔离运行，但直接发布到 evidence 会持续产生新的高熵字符串。 |
| Why previous fix was insufficient | 精确 allowlist 只覆盖已见到的固定值，未约束下一次运行的导出行为。改 workflow 后若忽略已有内容 hash 绑定，还会触发第二个机械性失败；应核查当前 validator 与实际绑定，而非盲目更新所有 hash。 |
| Canonical solution | 运行身份保持随机；在导出层按已知字段建立 batch/run/type 内的稳定双射，默认 proof 文件输出 canonical references，raw-* 留在 ephemeral artifacts。引用采用短字段分隔，避免规范化后的拼接字符串再次触发 scanner。规范见[身份策略](regression-delivery.md#synthetic-test-identity-policy)。 |
| Workaround disposition | KEEP TEMPORARILY：历史精确兼容例外保持原范围，不扩充。退出条件是相关历史 evidence 已获授权迁移、身份关系及必要原始字节证明仍可验证，并在去除例外的配置上通过扫描及负例；未满足前不自动删除。 |
| Detection method | 对每条命中核对 path、line、field/context、身份类型及 fixture provenance。UNKNOWN、真实秘密候选或非 synthetic 高熵值必须停止相关 canonicalization，不能借导出掩盖。 |
| Debugging sequence | 命中现象 → 查当前 scanner report → 判断是否同一生成机制 → 查旧补丁与导出入口 → 用新随机身份复现最小字段样本 → 定位 harness/export owner → 修字段映射和默认文件选择 → 执行下列验证。 |
| Regression tests | 原始完整证据结构注入新随机身份后必须零泄漏；相邻变体覆盖不同身份、类型/run 隔离、前向引用与复合键；正常导出保持状态/数量且 raw 文件不变。apiKey/token/Authorization 哨兵必须保持原值并被实际 pinned scanner 拒绝。入口与具体证据见[整改记录](../../../../docs/audit/evidence/l4-b2-synthetic-identity-remediation/README.md)。 |
| Do / Don't | Do：核验来源、双射和完整逆映射；运行当前 scanner、相关负例与 stage-assets。Don't：按长字符串通用清洗、统一 redacted、发布 raw identity 的 hash/base64/hex 替身、按批次或目录扩充 allowlist、修改运行 ID 为固定值。 |
| Applicable modules | L4 test harness、evidence exporter、打包/交付流程；不改变 production trading、账务或 scanner policy。 |
| 关闭边界 | 本条记录已交付的源头整改和可复用方法；历史 workaround 仍保留，尚不能据此声称满足本规则的全部系统性关闭条件。未来批次还须执行自身回归，不能借旧 CI 自动覆盖。 |

验证命令从仓库根目录运行：

```text
python backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/test_synthetic_evidence.py
mvn -f backend/pom.xml -pl nq-app -am test -Dtest=SyntheticEvidenceExportTest -Dsurefire.failIfNoSpecifiedTests=false
python backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/verify_synthetic_secrets.py <pinned-linux-archive> <ephemeral-output>
python scripts/docs/check-stage-assets.py
git diff --check
```

scanner 验证程序使用 Linux pinned archive，先校验当前 supply-chain lock，再原样读取当前 CI 配置。Windows worktree 从 WSL 调用时可通过程序的 `--git` 参数指定实际 Windows Git。判定为映射/正例通过、原身份残留为零、凭证负例全部 REJECT、tracked scan 零 findings、stage-assets errors=0；只有实际 changed bound assets 才机械同步对应 hash。发布验收另须 canonical exact-head CI。

## 经验：Durable Source Fan-out Rule

- 症状：Trade 已提交，但进程在后续事件或账务调用前死亡，restart 只能恢复部分必需事实。此前 F004 为 Ledger 增加从 durable Trade 重放；B4 证明同一 source 的 TradeExecuted 仍依赖首次插入后的单次内存调用，因此 F004 的单分支修复未覆盖完整 fan-out。
- 共同根因：将数据库 commit 后的函数调用误当成可靠持久化传播。进程退出会丢掉未执行调用，重试只检查 source 存在又可能永久跳过派生事实。
- 规则：每个 REQUIRED durable derived fact 必须与 source 同事务原子提交，或能在 restart/replay 中从 source 幂等重建。同一数据库/事务管理器、ownership 合理时优先原子提交；独立事务应保留可重建路径及持久唯一性。不要因此合并原本独立的 Ledger 事务。
- 排查：先列 source、全部 required fan-out 与各 commit；逐个核对真实 writer、恢复候选、durable identity/约束、旧事件兼容与冲突处理，再在真实提交边界 kill/rollback。不能只给当前报错位置补 append，也不能用无锁 SELECT-then-INSERT 声称并发唯一。
- 修复 owner：Trade persistence / required event recovery。当前普通 OKX 将新 Trade+TradeExecuted 原子写入；旧缺口以 source 行锁串行化恢复，稳定 event_id 主键兜底，保留且验证已有随机 ID 事件，冲突拒绝。该写入协议要求参与者遵守同一 source 锁；不据此授权旧新 writer 混跑、历史生产数据清洗或扩大到其他事件。
- Permanent regression：正常正例；原缺口旧进程→新PID恢复；event durable/ledger未提交；ledger durable/event缺失；反复重启；两个真实PG连接同时竞争source锁；event与ledger各自commit失败；SIM/LIVE与Kill下恢复。检查每个派生事实恰好一次、业务payload来自durable source、源事实及另一分支不被重写。入口：`B4TradeEventRemediationTest`（`nq.b4.remediation=true`）、`B4TradeEventPostgresTest`（`nq.b4.pg=true`）；结果见[B4整改证据](../../../../docs/audit/evidence/GATEAUDIT_PHASE6_L4_B4_TRADE_EVENT_DURABILITY_REMEDIATION_ATTEMPT01.md)。实现证明、独立审查、交付与B4资格验收分别记录，不相互替代。
