# L6 Shadow Runs Freshness Delivery Acceptance

2026-09-13（Asia/Shanghai）。PASS / L6_FRESHNESS_CI_TEST_CONTRACT_REMEDIATED / PRODUCTION_CANDIDATE_UNCHANGED / INDEPENDENT_CORRECTNESS_REVIEW_REUSED / EXACT_HEAD_CI_GREEN。P0=0、P1=0（本闭环范围）；L6=NOT_ACCEPTED / SOAK=NOT_RUN / READY_TO_START_L6_A_60MIN。

## 不可变交付身份

| 项目 | 已核验结果 |
| --- | --- |
| 原 precise delivery | `35f6bbdadacbfb2b6097a9fcb5a0e84bcbd924a8` |
| 原 CI | `34736516078`，永久保留 FAILURE；7 success / 1 failure / 1 skipped |
| Remediation commit / 当前 remote SHA | `23a7135ad8e0574373c1b818dfed010dc7479a71` |
| Remediation tree | `e2aadb0665c8af50138860746a7922dface25d8a` |
| Parent | `35f6bbdadacbfb2b6097a9fcb5a0e84bcbd924a8` |
| Branch | `audit/post-gatey-agent-baseline` |
| 提交范围 | 2 files：ShadowRunReadOnlyQueryServiceTest.java + L6_FRESHNESS_CI_TEST_CONTRACT_REMEDIATION.md |
| Missing / unexpected / production delta | 0 / 0 / 0 |
| Exact-head workflow | NQ CI Baseline / workflow_dispatch |
| Exact-head run | [34738227591](https://github.com/ling5477/nexus-quant/actions/runs/34738227591) |
| Run headSha | `23a7135ad8e0574373c1b818dfed010dc7479a71` |
| Run status / conclusion | completed / success |

原审查 3,951 文件在 precise delivery 时全部原字节匹配；唯一额外文件是独立审查报告。原 delivery inventory SHA-256=`237d1293d60f812d6ee76b9a91c00f1fc6bc37b27975e3315b9fee8e650049d2`；canonical staged inventory SHA-256=`4b6da9c5a5938ab2acdfec01f9016d17c513acba31453f67d3643d08d18bfbe9`，允许差异仅 Git CRLF→LF 正规化。该身份属于原交付，不冒充本次测试修复后的全仓身份。

本次仅测试与 remediation evidence delta；生产 owner 原始 SHA-256=`2984c7a63a3f59ae939a0e8f8a1f4f16311071779cb398dc4ae0a54e35b9e435`；独立审查报告原始 SHA-256=`d18c89689634631ee56e56d1f979477902059a776cb7fa97a6a640f6f69921f0`。CI 后重新核验全部入口 tracked files，除授权测试类外全部原字节一致；修复测试仍等于已测试候选。生产与 review 结论不变，无需新增 independent review。

## CI 与回归

| Job | Conclusion |
| --- | --- |
| Backend regression | SUCCESS |
| Delivery SBOM and provenance | SUCCESS |
| Frontend build and critical E2E | SUCCESS |
| Repository hygiene and governance | SUCCESS |
| Java architecture guard | SUCCESS |
| Research quality | SUCCESS |
| Secret scanning | SUCCESS |
| PostgreSQL and Flyway | SUCCESS |
| Runtime safety and no-outbound | SUCCESS |

9/9 jobs SUCCESS；failed=0 / cancelled=0 / skipped=0。Linux Backend regression 原始日志确认目标测试 10 tests / 0 failures / 0 errors / 0 skips，nq-core 576 tests / 0 failures / 0 errors / 0 skips。Windows 本地模块结果的 13 个平台条件 skips 原样保留于[remediation 记录](L6_FRESHNESS_CI_TEST_CONTRACT_REMEDIATION.md)，Linux 此次实际覆盖通过。没有在本地重跑 Full Maven、L4/L5、L6 readiness 或 60/180min。

最终 run JSON、CI backend 原始日志、baseline inventory、staged identity 和本地测试 XML 保存在 `C:\Users\Lingyu\AppData\Local\Temp\nq-l6-ci-remediation-kv5hi25v`。远程 refs/heads/audit/post-gatey-agent-baseline 通过 git ls-remote 确认等于 remediation commit。

## 接受边界

[既有独立审查](L6_SHADOW_RUNS_FRESHNESS_CORRECTNESS_REVIEW.md)继续有效：SHADOW_RUNS 七天 freshness policy、Validation aggregate fail-closed、mandatory measurements、formal 10/40/10 contract 已接受。本次只同步旧测试契约，未修改 threshold、aggregate、measurement 或 duration；没有新增 review。

历史 L6_MANDATORY_MEASUREMENT_NOT_READY、L6_PRODUCTION_OBSERVABILITY_CHANGE_REQUIRED、VALIDATION_UNEXPECTED_DEGRADATION 及 failed CI 34736516078 全部保留原失败/阻断事实。本次只接受当前 technical commit/run pair，不追认历史失败，也不表示正式稳定性资格已执行。

唯一下一动作：`NQ-GATEAUDIT-PHASE6-L6-ACTIVE-STABILITY-60MIN-QUALIFICATION`，10min warmup / 40min active / 10min drain。本轮没有开始该运行，不追加 test remediation、delivery、readiness 或 freshness review。

本文件是 CI GREEN 后新增的接受记录，未进入上述技术提交，当前不暂存、不再提交；不会用后续文档提交替换已验证的 technical commit/run pair。
