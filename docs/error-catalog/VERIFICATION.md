# 本地化与错误契约验证

工作包：`NQ-GATEAUDIT-FRONTEND-LOCALIZATION-ERROR-UX-CATALOG-IMPLEMENTATION`。基线为 `4122cba1c6697fd1e5322510af028e184c7588ef`，基线 CI `35058863828` 为 9/9 SUCCESS。本报告记录本批证据，不替代 [STATUS](../current/STATUS.md) 的 current authority。

## 候选与边界

- 技术候选：96 文件，SHA256 集合指纹 `61F7B99AF2F3740595D443685D97E5DFF79D4BF26DB5541F4CAAFD3677BF0DE4`；精确路径、字节 SHA256 和规范 Git blob 见 [候选清单](technical-candidate.json)。指纹算法为按路径排序，以 LF 拼接 `path + 空格 + sha256` 后取 UTF-8 SHA256。
- 页面迁移包含 39 个业务 TSX 和双语 pages 资源，2273 个 pages key；此外迁移 App Shell、登录、异常页、共享组件与错误入口。完整范围及 deferred 分类见 [目录说明](README.md)。
- `errorId → errorKey → code → HTTP → unknown` 确定性解析；原诊断与字段值保留在归一化对象，主展示不直接使用 backend message。
- HTTP 409 的旧 `code=STATE_CONFLICT` 保留，只对真实订单 CAS 冲突附加稳定 key/ID。其它错误、交易状态机、幂等、账务、Risk、数据库和 Phase6 qualification 语义不变。
- 工作区既有 Phase6 证据、storage analyzer、用户的 AGENTS 和 agent-workflow-policy 修改均不属于本批。精确 allowlist 交付，禁止整个目录暂存。

## 当前可复核验证

2026-09-22 的原始日志、XML、Playwright 失败上下文和候选散列保存在仓库外持久目录 `E:\Project\nexus-quant-gateaudit-localization-evidence\20260922`。

| 验证 | 结果与证据 |
| --- | --- |
| `npm run build`，含 `test:unit`、TypeScript 和 Vite | PASS；22 unit/static tests，覆盖双语 key/插值一致、直接资源查找、机器身份属性不翻译、目录和兼容解析、未知错误、HTTP类别、网络/字段/trace、locale storage、禁止自动 mutation retry。`build-01.log`。 |
| 本地化及共享组件 E2E | 13/13 PASS，0 skip；`e2e-core-01.log`。包含新 7 项本地化/error tests、登录、adapter、chart、live-query、runtime boundary。 |
| 策略验证／审查工作台 E2E | 24/24 PASS，0 skip，测试文件 start/end SHA256 一致；`e2e-alignment/attempt02` 证据及 `SUMMARY.md`。 |
| 后端 API/module regression | 42 XML / 135 tests / 0 failure / 0 error / 0 skip，exit 0；`backend/regression-01.log`、XML、summary 和 6 文件候选 SHA256。 |
| 文档与候选自查 | current-authority、相关文档链接、stage-assets（2031 scanned / 175 reviewed exceptions / 0 errors）、目标 diff check 通过。 |

后端命令：`mvn -f backend/pom.xml -pl nq-app -am -Dtest=<全部 nq-api *Test,OrderCommandServiceTest,AuthSecurityWebMvcTest> -Dsurefire.failIfNoSpecifiedTests=false test`；精确类清单见 `backend/regression-01-metadata.json`。

新增订单冲突 E2E 使用隔离 HTTP fixture：中英文提示、errorKey/errorId/旧 code/traceId、相同撤单 payload、无自动重放写请求、两种语言的手动查询一致。后端契约测试经过真实 controller/application 和既有状态机，在仓储边界模拟 CAS 竞争，证明一次 CAS、无 venue 调用；它不是 PostgreSQL 并发资格证明。本批未重跑 Phase6 qualification。

## 已整改问题与证据限制

- 前期将旧 code 替换为 ORDER_VERSION_CONFLICT 的方案未交付。独立审查指出兼容性风险后改为 additive errorKey/errorId；保留旧 code 与未编号 JSON 形状。
- 修复 useLiveQuery 独立中文错误映射和 trace 丢失，统一目录并订阅语言；NqFilterBar 默认标题接入语言资源。新增浏览器反例证明回测实时查询错误切换语言后 trace 保留、raw message 不透出。
- 构建曾抓到两处行情 capability 被误翻译，已恢复原 machine union，并增加静态身份属性保护检查；语言变化导致纯数据 memo 重新计算的依赖已移除，避免表单被回填。
- 2026-09-22 E2E alignment attempt01 为 20/24，四处旧显示断言失配。失败保留；attempt02 修正显示选择器后 24/24，不削弱请求数、expectedVersion、幂等或禁止端点断言。
- 2026-09-16 未完成候选阶段有 HMR、选择器和查询对比口径失败。其 TEMP 原始文件在会话恢复时已不存在，不以那些局部结果作最终 PASS 依据；本报告以本轮持久证据为准。
- 前端既有 AntD/React 兼容与弃用提示、Vite 大 chunk 提示仍可见；未因本批扩展为 UI 框架升级或性能重构。
- 全仓历史副本断链与本批隔离；既存未跟踪 Phase6 evidence 的断链不通过删除历史记录消除。相关当前文档链接已单独验证，远端 CI 以精确提交内容为准。

## 独立审查与交付

独立审查 `PASS / P0_0 / P1_0`。审查者未参与实现，起止 96 文件指纹均为上述指纹，HEAD 均为基线，stage=0；独立复现 22 个前端测试，在仓库外重新编译六个候选 Java 源并执行 7 个合同测试，全部通过；核验后端 135 项和 E2E 37 项证据。报告位于持久证据目录 `independent-review/REVIEW.md`。技术审查不包含后续 authority 文档和远端交付 CI。

本地 PASS 和独立审查不等于 exact-head CI 或最终接受。真实后端的两个关键浏览器用例由 canonical CI 在隔离环境中执行，本地未对未知既存后端发请求。

Phase6 保持 ACCEPTED / COMPLETE；Phase7 保持 NOT_STARTED。最终接受后仅将下一动作推进至 Phase7 planning/final baseline entry，不执行 freeze。
