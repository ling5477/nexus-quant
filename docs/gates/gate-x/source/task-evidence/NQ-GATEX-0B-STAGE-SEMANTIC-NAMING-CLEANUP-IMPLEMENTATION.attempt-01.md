# NQ-GATEX-0B stage semantic naming cleanup implementation — attempt-01

## 基线与范围

- Starting HEAD：`61d9292b0b77d9c25f36232bee9512b87ac256c6`；`origin/dev` 为同一 commit。
- GateX-0A canonical implementation commit：`49851276cdbbe7daf49506cc3af327e42973788b`。
- GateX-0A exact-head acceptance：`NQ CI Baseline` run `31318868410`，`completed / success`（完成 / 成功）。
- Authority entering 0B：GateW=`FROZEN|ACCEPTED|TAGGED`；GateX-0A=`ACCEPTED|CI_GREEN`；GateX-0B=`NOT_STARTED`；LIVE=`DISABLED`。
- 仅处理 backend 生产 class/package/config 的阶段语义、随 rename 必须调整的测试，以及本任务明确要求的 current facts/evidence；未修改 API、DB schema、migration、SQL、订单状态机、风控规则、ledger、frontend、research、CI、deploy、LIVE、AI 或 DH runtime。

## 生产命名与 package 变更

| 原阶段命名 | 稳定 capability/domain naming |
| --- | --- |
| `GateW3RiskPreflight*` | `DiagnosticOrderRiskPreflight*` |
| `GateW4OperationalSafety*` | `OperationalSafetyAssessment*` |
| `GateWOkxVenueRuleConfiguration` | `OkxVenueRuleSyncConfiguration` |
| `GateWOkxPrivateReadonlyConfiguration` | `OkxPrivateReadOnlyDiagnosticsConfiguration` |
| `GateWOkxPermissionProbeProperties` | `OkxPrivateReadOnlyPermissionProbeProperties` |
| `com.guidinglight.nexusquant.account.infra.gatew` | `com.guidinglight.nexusquant.account.infra.okx.readonly` |

- 风险预检与 operational safety 的 request/result/status/finding/service 以及非历史测试同步 rename，算法、输入字段和封闭状态语义保持不变。
- OKX private read-only executor/probe 的 5 个 production types 与 2 个直接测试迁至稳定 package；调用方只更新 import。
- `PackageBoundaryArchTest` 新增 main source package/import guard，阻止后续生产代码重新依赖 `.gatew` stage package。
- 历史 GateW soak/fail-close/restore drill 测试、migration 文件和 GateW evidence identity 保持原名。

## 配置兼容策略

稳定 key：

```text
nq.okx.venue-rule-sync
nq.okx.private-readonly-diagnostics
```

保留的 legacy alias：

```text
nq.gatew.okx-venue-rules
nq.gatew.okx-private-readonly
```

- `CapabilityPropertyResolver` 集中处理 stable/legacy key，不读取 credential material。
- 普通参数：stable key 优先；只有 legacy key 时继续生效；冲突时选择 stable key 并记录不含值的 warning。
- enable、写侧关闭开关与 permission expected IP：新旧值冲突时 fail closed；缺失或非法布尔值不放行。
- warning 只包含 stable/legacy key 名与 resolution，不包含配置值、secret、credential 或 endpoint payload。
- 新 profile 为 `okx-venue-rule-sync-manual` 与 `okx-private-readonly-diagnostics`；legacy profile 继续兼容。
- 默认/no-egress/private read-only/LIVE-disabled 组合边界保持不变；稳定 private diagnostics profile 已加入 trading adapter 排除表达式。

## GateW 残留扫描分类

命令：

```powershell
rg -n "GateW|gatew|nq\.gatew" backend
```

排除 `target/**` 并用 `gatew` 单词边界复核后的残留分类：

| 分类 | 命中 | 处置 |
| --- | ---: | --- |
| `CONFIG_KEY_LEGACY_ALIAS` | 3 | 保留；两个 legacy prefix 的集中定义/绑定 |
| `LEGACY_PROFILE_COMPATIBILITY` | 9 | 保留；旧 profile 的 Spring 兼容入口与排除条件 |
| `LEGACY_PROFILE_CONFIG` | 10 | 保留；`application-gatew-okx-readonly-soak.yml` 是冻结 soak 运行合同 |
| `HISTORICAL_EVIDENCE_IDENTITY` | 2 | 保留；`GATEW4_OPERATIONAL_SAFETY` / `NQ-GATEW-4` 为既有 evidence binding |
| `COMMENT_HISTORICAL_ORIGIN` | 42 | 保留；仅描述能力来源，不是 class/package/config semantic contract |
| `MIGRATION_HISTORY` | 3 | 保留；V34/V35 历史 migration 不允许重命名或改写 |
| `TEST_OR_HISTORICAL_EVIDENCE` | 405 | 保留；GateW soak/drill 历史测试及 legacy 兼容测试 |
| `REVIEW_REQUIRED` | 0 | 无未分类残留 |

补充精确扫描：旧 production class declaration/import 与 `account.infra.gatew` production dependency 均为 0；普通单词 `gateway` 未被误计为 `gatew`。

## 验证结果

| 验证 | 结果 | 说明 |
| --- | --- | --- |
| changed-test focused Maven | PASS（通过） | risk/safety 47、moved infra 26、nq-app targeted 37；0 failures / 0 errors / 0 skipped |
| `mvn -f backend/pom.xml -pl nq-app -am test` | PASS | 23 个 reactor modules 全部成功；0 failures / 0 errors |
| `mvn -f backend/pom.xml test` | PASS | 全后端 23 个 modules 全部成功；0 failures / 0 errors |
| focused operational safety rerun | PASS | 16 tests；0 failures / 0 errors / 0 skipped |
| Spring assembly | PASS | stable key/profile、legacy key/profile、stable-first、safety conflict fail-close 与唯一 permission probe port 均有覆盖 |
| production package guard | PASS | main source 不依赖 `.gatew` package |
| exact old-name scan | PASS | 旧 production class/package declaration/import=`0` |
| `check-current-authority.ps1` | PASS | `errors=0`；0B completion 到 commit-and-push 映射一致 |
| `check-doc-links.ps1` | PASS | 195 checked / 0 errors / 1 个既有 historical-ledger warning |
| `git diff --check` | PASS | whitespace errors=`0` |

环境 RCA：首次执行必跑命令时 `localhost:5432` 未运行；启动仓库既有、此前停止的 `nexusquant-postgres` 后，唯一失败为既有 `ResearchBacktestHappyPathLocalTest` 缺少 CI workflow 明确准备的 legacy account fixture。插入唯一 PAPER/ACTIVE 本地测试 fixture 后，两条必跑回归通过；fixture 随后精确删除并确认剩余 0 行，容器恢复为停止状态。没有通过修改测试或业务代码掩盖环境问题。

## 行为兼容性与边界

- Spring Bean：默认不装配 outbound/private components；stable 与 legacy 显式 profile/key 均按既有安全开关装配。
- Runtime：risk-preflight、operational safety、venue-rule sync 与 private read-only probe 的关键回归通过；未新增 scheduler、runner、Controller 或启动期网络访问。
- API：无 endpoint、JSON 字段或公开 DTO 变更。
- Database：无 migration/schema/SQL 变更；测试 fixture 已清理。
- Credential/private endpoint：未读取或输出 credential；未扩大 typed private operation 集合；未新增真实调用。
- Trading：LIVE=`DISABLED`；未新增下单、撤单、transfer、withdraw 或 trading authorization 路径。

## 自审与 authority

- P0：0。
- P1：0。
- P2：0。
- P3：0。
- 已知 warning：Mockito dynamic-agent 与部分 module 的 SLF4J no-provider warning；未导致 failure。
- Known protected unstaged diff：`OrderCommandService.java` 当前无 staged/unstaged diff，本任务未触碰。
- Authority after：GateX-0B=`IMPLEMENTED|SELF_REVIEWED`；commit=`UNCOMMITTED`；CI=`NOT_RUN`；唯一下一动作=`NQ-GATEX-0B-COMMIT-AND-PUSH`。

最终结论：`IMPLEMENTED / SELF_REVIEWED / STAGE_SEMANTICS_CLEANED / BACKEND_REGRESSION_GREEN / READY_TO_COMMIT`。
