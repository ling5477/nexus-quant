# NQ-V102-05-BACKTEST-SIM-COMPARISON

日期：2026-10-27。结果：`IMPLEMENTED / TARGETED_VALIDATION_PASS`。

## 基线与实现

- 执行前重新 fetch `origin/dev`，从 `5f93d3f0bfb6e47f35a6fa71ad51c364c239f8e7` 创建 `codex/nq-v102-backtest-sim-comparison`。起始工作区和 index 均为空。
- 起始 tree：`e95401c2ce98425f2845ed02ca9ec43bbeb0d31b`；隔离数据库实际迁移、validate 到 V49，无新增或修改 migration。
- 最终 staged tree 包含本报告，由最终回复记录 `git write-tree` 的实际结果，避免报告内容引用自身 tree 的循环。
- 新增只读 `GET /api/strategy-experiments/validations/{validationId}/sim-comparison`；参数为 `selectedTrialId`、`strategyVersionId`，以及可选的 `publishId`、`paperRunId`。
- 服务端验证本人实验、成功验证的最终重训选择、源候选、正式版本、Checksum、参数/配置/源码快照、成功全窗口回测及评价、发布血缘和真实 Continuous SIM 的租户/账户/范围。复用既有发布输入验证、SIM 归属、游标和绩效服务；原 SIM 创建前验证仅抽取复用，准入语义不变。
- 比较选择存于 URL，刷新只读恢复，不增加比较表或复制任何经济事实；不激活版本、不发布、不创建/启动 SIM、不创建订单或执行任务。
- 实验当前使用冻结 DB 历史输入，普通本地 Continuous SIM 要求公开捕获输入。因此允许选中版本的正式发布使用不同的合格全窗口回测；不把实验窗口冒充 SIM 种子，也不要求两段历史数据相同。

| 对照部分 | 实际行为 |
| --- | --- |
| 研究绩效 | `TRAINING`、`WALK_FORWARD_OOS`、`FINAL_HOLDOUT` 分列各自窗口、版本、原 Run/Evaluation 和正式评价数值，不串接独立重置窗口收益。Holdout 未显式查看时返回 `UNAVAILABLE / HOLDOUT_NOT_EXPLICITLY_VIEWED`。 |
| Forward 绩效 | `CONTINUOUS_SIM_FORWARD` 复用正式 SIM 历史绩效。缺价、缺费用或资料不足保留 null/`UNAVAILABLE`；混币费用不相加。估值覆盖外有成交时标记 `NOT_COMPARABLE / EXECUTIONS_OUTSIDE_VALUATION_COVERAGE`，不把全运行费用/次数贴进该期间。 |
| 信号 | 最近 50 个保存窗口，核对原连续行情、内容摘要、可用时间、策略版本及相邻 durable 策略状态，用原 `SpotTargetStrategy` 重算；证据不足返回 `NOT_COMPARABLE`。总数来自正式完整统计，并明确窗口截断。 |
| 执行归因 | 展示原决策、订单/成交状态、实际费用与报价相对滑点；区分数据、信号时间、动作、执行事件、风险/执行失败、成本假设和事实不足。单笔已知 USDT 标量费用可比较；多笔舍入或未重建原币/优惠 charge 合同时仅标记事实不足。 |
| 期间解释 | 全局 `NOT_COMPARABLE / DIFFERENT_MARKET_PERIODS_AND_METRIC_BASES`。历史研究与后续 SIM 的收益差不能直接归因为执行错误，不预测收益或给出 LIVE 建议。 |
| 前端 | 现有实验/Walk-Forward 内增加 Research → Publish → SIM Comparison；复用已有发布/SIM分页，支持原实验、验证、评价、正式回测、发布、SIM详情导航，中英、窄屏、错误身份拒绝与只读恢复。 |

## 实际验证

使用任务专属 `postgres:16.15` 容器 `nq-v102-comparison-pg16-20261027`，地址 `127.0.0.1:55445`，各测试/浏览器使用独立随机数据库和 runtime role。镜像 ID：`sha256:ca0bd484cb98bf4b24eb1010e73fb3fcbd6714d240fbc1a10eea5b7dbecb641d`。完整 Servlet/AppContext、正式服务、RLS 和真实 canonical 成交/账务均实际运行；禁止 outbound/LIVE。

比较夹具通过正式实验、Walk-Forward、回测、评价、发布、Continuous SIM、撮合服务建立事实。历史实验复用仓库已有保存样本；公开捕获使用本地受控 HTTP 合成行情。实验生成的 DRAFT 版本在隔离夹具中预置 ACTIVE，作为既有发布前提；生产比较代码不提供或执行激活。负例故障注入仅作用于隔离数据库，恢复原值，不将它当作真实供应方或真实交易执行证明。

| 验收 | 结果与证据 |
| --- | --- |
| A 版本关联 | 正确身份成功；错误版本/候选、参数、Checksum，以及另一个合法 Publish 与原 SIM 的错误配对均拒绝。 |
| B 准入 | 窗口 Holdout Run 的发布不能成为比较或 Continuous SIM 的合法种子，失败前后经济事实不变；保持 OKX/BTC-USDT/SPOT/1h 和既有来源边界。 |
| C 同源决策 | SMA 正式保存输入重算 MATCH；时间、动作、执行事件、数据摘要、风险/失败及费用差异负例均识别。Channel 验证 stale ENTER_LONG 未成交但保存 target=1 后，下一窗口仍恢复 LONG 并 HOLD/MATCH；错误前态 Checksum 返回 NOT_COMPARABLE。 |
| D 期间 | 4 TRAINING、3 OOS、1 Holdout、1 Forward 分期，明确不同时期不可直接比较，不隐式披露 Holdout。 |
| E 绩效 | 来自原评价或 SIM 分析；缺价和费用返回不可用。真实 PG 中模拟延迟成交超出估值覆盖后，正式查询返回该覆盖事实，比较行 NOT_COMPARABLE、费用/次数为空、观察不完整。另有 3 项投影边界单测。 |
| F 隔离 | 跨用户 404、跨 Demo 租户由现有路由 guard 返回 403；错误发布/SIM/快照均拒绝，未认证 401。 |
| G 只读 | 连续两次比较 GET 的 15 张研究/执行/账务/游标/披露表全行内容指纹不变；浏览器比较及导航业务写请求为 0。 |
| H 前端 | 真实选择发布/SIM、四阶段、决策、原详情导航、刷新恢复、390px、中英、错误身份拒绝/恢复；最终重新启动最新候选进程后再次完成选择 → SIM详情 → 返回/刷新，业务写 0。 |

| 验证批次 | 真实结果 | 本地证据 |
| --- | --- | --- |
| 后端编译 | PASS，11.48s | `artifacts/nq-v102-comparison-main-compile.log` |
| 10 类定向回归 | 47 run，0 failure/error，1 既存条件 skip，6m19s | `artifacts/nq-v102-comparison-regression.log` |
| 最终比较 PG + 投影测试 | 5/5 PASS，0 skip，1m04s；含 SMA/Channel 两项 PG 场景和 3 项边界测试 | `artifacts/nq-v102-comparison-final-stateful-targeted.log` |
| 前端 build | `tsc -b` + Vite PASS | `artifacts/nq-v102-comparison-frontend-build.log` |
| 前端单测 | 3 文件 10/10 PASS | `artifacts/nq-v102-comparison-frontend-unit.log` |
| 原实验 Playwright | 3/3 PASS，49.8s | `artifacts/nq-v102-comparison-experiment-regression-playwright.log` |
| 最终候选浏览器闭环 | PASS，4/3/1/1 分期、1 MATCH、业务写 0 | `artifacts/nq-v102-comparison-ui-final-{selected-assertions,navigation-assertions,reload-assertions}.log` |
| 数据事实 | SMA 原绩效/决策与只读指纹；Channel 前态事实 | `artifacts/nq-v102-comparison-postgres-facts.json`、`artifacts/nq-v102-comparison-channel-facts.json` |

原有 skip 为 `StrategySimPostgresIntegrationTest.productUxRealApiAndBrowserUseOneCanonicalRun`，原因是未设置 `nq.c3.browser.required`。不宣称该原 C3 浏览器用例通过。其余原策略实验、Walk-Forward、SIM、发布和绩效相关所选测试均成功；未运行全仓测试或远程 CI。既有 Vite/AntD 提示保留。

最终浏览器身份：experiment `e015c645-865d-47ae-992b-b41955a3ac90`，validation `322b0d46-1c9a-4761-885b-00c4319175e4`，publish `pub-299bbeab-d979-4e89-8470-545373bce987`，SIM `ptr-852fbc7a-351d-4d5f-99b2-2bf32ffee4cb`。截图在 `artifacts/output/playwright/nq-v102-comparison-{desktop,mobile,English}.png`。

复现最终后端测试（从仓库根目录；PG 测试口令由本地环境传入，报告不保存口令）：

```powershell
$env:JAVA_HOME='D:\Tool\Java\Java21'
& 'D:\Tool\Maven\apache-maven-3.9.12\bin\mvn.cmd' -f backend/pom.xml -pl nq-app -am test `
  -Dsurefire.failIfNoSpecifiedTests=false `
  -Dtest=ResearchSimComparisonPostgresIntegrationTest,ResearchSimPerformanceComparisonTest `
  -Dnq.tenant.pg.required=true `
  -Dnq.tenant.pg.url=jdbc:postgresql://127.0.0.1:55445/postgres `
  -Dnq.tenant.pg.user=postgres "-Dnq.tenant.pg.password=$env:NQ_TEST_PG_PASSWORD"
```

## 独立审查与限制

完成一次定向独立候选审查及实质变更后受影响费用块补审，无未闭合 P1/P2。审查者自行调用专属夹具验证正常 GET、错误候选 409、未认证 401；其早期进程证据仅覆盖未变的身份路径。审查发现的费用/次数与估值期间混用 P2 已修复，并以最终真实 PG 延迟成交负例及投影测试关闭。

最终审查生产文件 SHA256：

```text
ResearchSimComparisonController.java f448212a739ab295a79d9041449acaee9638d6afdffac16bf5c778a42caa5d08
ResearchSimComparisonService.java a0800bac666fc9c0f8a2089a05bb5a83a2fb43a44078c104f1ab6bc1adfae533
ResearchSimDecisionComparisonQuery.java f265e47af5fda3b0352a71fb69da1b7639b6aada5f032c6f36b27c7b8bed36a3
StrategySimRunService.java e60af2413a953391ea3dfdb0a7714dae21021801be7ddbe43cde0c3c529ab68d
```

早期成本夹具、快照对象/文本兼容及 Demo 拒绝状态断言失败分别保留于 `nq-v102-comparison-postgres-first/second/third.log`；修复后以新运行通过，不把历史失败改成 PASS。最终生产代码之后只补充 Channel 测试，未改变已审查生产候选。

范围限制：本次证明只读关联与差异解释，不证明策略盈利、真实长期前向表现、发布资格提升或 LIVE 授权。无完整原币/优惠费用合同或可信来源的窗口保持事实不足/NOT_COMPARABLE。

## 文件与 Git

精确变更 17 个文件：3 个新比较生产类、既有 SIM 验证复用抽取、4 个测试/夹具文件、8 个前端文件及本报告。无依赖、schema、Canonical Order/Trade/Ledger 实现或环境配置变更。

`Commit: NOT_COMMITTED`；`Push: NOT_PUSHED`；`PR: NOT_CREATED`；`Release: NOT_CREATED`；`LIVE: DISABLED`。最终精确 staged allowlist/tree 和检查结果以本次最终回复为准。

任务浏览器、Vite 和两轮比较夹具已正常停止；随机测试数据库及临时 role 均已回收（剩余 0）。保留任务专属 PG 容器、构建结果和关键证据；未删除其他任务资源。本轮未进入 PR 合并后清理，不报告 `CLEANUP_COMPLETE`。
