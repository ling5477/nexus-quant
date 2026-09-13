# L6 Active Stability 60min Qualification

2026-09-13（Asia/Shanghai）；attempt=`20260913-preflight-01`。

**BLOCKED / QUALIFICATION_INFRASTRUCTURE_FAILURE / L6_A_MANDATORY_RESOURCE_CADENCE_MISMATCH**。

本轮在正式计时前的入口检查中阻断；没有启动 Maven qualification、PostgreSQL、Synthetic Venue 或 NQ actor。`FORMAL_RUN=NOT_STARTED / SOAK=NOT_RUN / L6_A=NOT_ACCEPTED / L6=NOT_ACCEPTED`。不是运行满60分钟后的FAIL，也没有新的production correctness失败。没有新增独立review或修改既有接受结论。

## 基线与身份

- Branch=`audit/post-gatey-agent-baseline`；HEAD/upstream=`23a7135ad8e0574373c1b818dfed010dc7479a71`；tree=`e2aadb0665c8af50138860746a7922dface25d8a`。
- 本轮再次只读查询 [CI 34738227591](https://github.com/ling5477/nexus-quant/actions/runs/34738227591)：headSha精确匹配，completed/success，9/9 jobs SUCCESS，failed/cancelled/skipped=0。
- [冻结规划](../GATEAUDIT_PHASE6_L5_L6_SCOPE_AND_QUALIFICATION_PLAN.md) SHA-256=`80e339594be7223f07783d30e7f18913a53187ecde59b360ecdbf9223c9e45ac`。
- 初始仅允许的未跟踪文件 `L6_SHADOW_RUNS_FRESHNESS_DELIVERY_ACCEPTANCE.md`；tracked diff=0、stage=0。3954个既有Git可见文件（含该接受证据）前后原始字节相同，inventory SHA-256均为 `ee450bf157f01371616a6f436fec13abd79ca9dc15b2273936f196cad39bd953`。不是 candidate drift。
- Windows 11 build 26200；JDK `21.0.9+7-LTS-338`；本地存在锁定 PG16 image digest `sha256:f1c3376c26f2609ab9f29f71f824103fe2fcd8ee0346485cb6122a4f93df6f94`。本轮PG version/schema实测=`NOT_STARTED`，不以历史16.15/V51冒充运行事实。

## 入口阻断事实

当前用户任务第9节要求“每10秒采样”所列全部mandatory resources。当前committed harness的实际路径为：

| 路径 | 当前行为 | 对本轮合同的影响 |
| --- | --- | --- |
| `L6QualificationControls.java:110–112` | sampler每10秒fixed delay调用resources | JVM heap/GC/threads、Hikari、command queue等具有该采样路径 |
| `L6ActiveStabilityTest.java:55,142–144` | sampleSeconds=30；前次checkpoint完成后至少30秒才再次checkpoint | 不能把resourceSampleSeconds=10解释为所有指标每10秒 |
| `L6ActiveStabilityTest.java:169–170,191–200` | audit/event rows、Venue executor、log/raw files、OS handles仅在checkpoint采集 | 这些mandatory指标不满足本轮10秒采样合同 |

已检查现有L6入口、controls、measurements、相关scripts/test文件与L5/L6证据，未定位能补齐该路径的既有配套10秒采样器。上述行为同时从 `git show 23a7135...:<path>` 保存，绑定committed exact-head，不能由增加运行时长修复。没有将缺失样本插值、复制或填0。

Finding=`P3 / QUALIFICATION_HARNESS_CONTRACT_GAP / QUALIFICATION_BLOCKING`。本轮新增confirmed P0=0、P1=0、P2=0、P3=1；未证明production defect、正确性丢失或资源泄漏。P3是工具合同缺口的严重性，不解除qualification blocking。既有ordinary concurrent INSERT loser P2、wildcard-import P3及历史修复义务保持原处置，未重评或清零。

本轮用户第11节同时要求 `test/harness delta=0 / configuration delta=0`，并规定fixture/environment问题记录 `BLOCKED / QUALIFICATION_INFRASTRUCTURE_FAILURE`。因此没有修改采样器或另建运行采样harness后开始计时。既有freshness、Validation、mandatory字段及10/40/10常量接受事实保留；本记录只说明本轮完整采样频率合同未满足。

尚未完成的入口核对：所搜索L5/L6证据中未定位可直接绑定的冻结数值noise-band manifest；L5 healthy rate的25%与现有两策略分钟窗口的标定关系未验证。这些是未完成项，不另报“已证明不存在”的finding，亦不现场重定阈值。

## 运行与证据边界

正式常量已确认 `600 / 2400 / 600 seconds`，formal mode不读取缩短时长覆盖。正式命令列在machine summary中并标为 `NOT_EXECUTED`；本轮只执行Git、CI、环境及源码只读检查，未重跑readiness、Full Maven、L4/L5或180min。

run ID/PID generations/phase timestamps=`NONE`，正式elapsed=0、样本数=0、active windows=0，含义均为未启动。workload totals、correctness totals、Validation逐源状态、backlog最终值、resource min/max/low-water/high-water和leak结论均为 `NOT_MEASURED / NOT_ASSESSED`，不报告实测零或PASS。Paper/CRITICAL/normal/daily-report controls本轮未执行，不转记历史短时结果。

原始检查输出、exact-head CI JSON、带行号committed source、前后input inventory和只读收集脚本位于 `backend/nq-app/target/l6-a-60min-preflight-20260913/`。全部raw路径、长度与SHA-256见 [machine summary](L6_ACTIVE_STABILITY_60MIN_QUALIFICATION.json)，测量状态见 [metrics summary](L6_ACTIVE_STABILITY_60MIN_QUALIFICATION.metrics.json)。此前失败attempt和历史evidence原字节保留。

未创建任何owned runtime，owned process/container survivors=0，cleanup=`NONE_REQUIRED_NO_RUNTIME_CREATED`；没有清理其他任务的容器，包括docker清单中的既存 `nq-c1-occ-20260907`。

本轮只新增此报告及两个machine summaries，原始检查证据在ignored target。结束Git检查：tracked diff/check/stat/name-only与cached name-only均为空；stage=0、commit=NONE、push=NONE。

下一步为另行处理完整资源采样合同缺口，并绑定资源noise-band与负载标定后再申请正式资格运行；本任务未实施该remediation。60min未PASS，**不得启动180min**。
