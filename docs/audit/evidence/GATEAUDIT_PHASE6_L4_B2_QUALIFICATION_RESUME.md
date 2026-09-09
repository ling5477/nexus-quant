# Phase6 L4 B2 qualification 续跑

日期：2026-09-09。任务：`NQ-GATEAUDIT-PHASE6-L4-B2-QUALIFICATION-RESUME`。

**PASS / L4_B2_QUALIFICATION_COMPLETE / CANCEL_FILL_RACE_PROVEN / PARTIAL_CANCEL_PROVEN / FULL_FILL_CONVERGENCE_PROVEN / PER_FILL_ACCOUNTING_PROVEN / RESTART_REPLAY_PROVEN / C1_C2_INVARIANTS_PRESERVED / P0_0 / P1_0 / READY_FOR_PRECISE_DELIVERY**

**B2 = QUALIFIED / READY_FOR_DELIVERY**。不是 CI_GREEN 或 ACCEPTED；本轮 stage=0、commit=NONE、push=NONE，未触发 CI。

## 候选与历史链

Task classification：HIGH_RISK / L4_QUALIFICATION / REAL_PROCESS_CORRECTNESS_PROOF / NQ-only。

Starting HEAD 与 `origin/audit/post-gatey-agent-baseline` 均为 `d1cedb6debfaa3dbeacb0e95ef8599c69e5f9da3`，branch=`audit/post-gatey-agent-baseline`。开始及结束执行指定 Git status/branch/ref/diff 检查；已有 B2 组合候选为未提交工作区，不冒充 clean checkout 或已发布提交。

Production candidate fingerprint：`7982a8d51e21332e7474be15b24e468249ba0e19a647d651bdd8a5020305910a`。开始、结束均与上轮 reviewer 的原始字节清单逐项比对，七个已审 production 文件 byte-for-byte 不变。算法和每个文件 raw SHA-256/Git blob 见 [production identity](l4-b2-qualification-resume-attempt01/production-identity.json) 与 [原始清单](l4-b2-qualification-resume-attempt01/production-start.txt)。另外对起始清单内全部 production/migration及原证据做结束核验，未变化。

本轮最终15个相关 Java 源码 Git canonical fingerprint=`3bb05345c1675e42698bb0b58dfa9e13f7af72480f5d2cb4c1935ffd019e6eb4`；算法及逐项 raw/Git hashes见 [manifest](l4-b2-qualification-resume-attempt01/manifest.json)。它包含本轮 harness 变化，不能与上一轮全部源码 fingerprint混用；production 指纹保持独立固定。

以下历史文件均原样保留，本文件作为后续 qualification 结论，不覆盖失败：

1. [首次 B2 FAIL](GATEAUDIT_PHASE6_L4_B2_CANCEL_FILL_RACE_AND_PER_FILL_ACCOUNTING.md)。
2. [terminal convergence remediation](GATEAUDIT_PHASE6_L4_B2_CANCEL_FILL_TERMINAL_CONVERGENCE_REMEDIATION_ATTEMPT01.md)。
3. [此前失败 Independent Review 原文](l4-b2-live-env-remediation-attempt01/previous-independent-review.txt)。
4. [LIVE Trade environment remediation](GATEAUDIT_PHASE6_L4_B2_LIVE_TRADE_ENVIRONMENT_CONSISTENCY_REMEDIATION_ATTEMPT01.md)。
5. [上一轮组合候选 Independent Review 原文副本](l4-b2-qualification-resume-attempt01/combined-independent-review.md)；[原始审查日志、Full Maven与指纹归档](l4-b2-qualification-resume-attempt01/combined-review-raw.zip)。该审查是上一轮独立执行的既成事实，本轮 harness 工作不是新增第三轮 review。

STATUS/ROADMAP 仍保留 pre-B0 旧机器状态；本轮依据用户明确隔离 qualification 授权运行，不改 current authority，不将本轮结论扩展为真实 LIVE/发布许可。

## 真实时序矩阵

Matrix executed：8种场景 × SIM/LIVE × 3次 = **48/48 PASS**。每次使用新数据库、独立新 Synthetic Venue JVM、新真实 Spring NQ JVM；restart 场景再创建新 NQ PID。各次使用显式 HTTP ACK 屏障，顺序由 venue 自有递增 sequence验证，不以睡眠或同一个 exception fixture代替race。

| 场景 | 实际顺序及断言 | SIM | LIVE |
|---|---|---|---|
| ZERO_CANCEL | 0 fill → cancel request → ACK → cancel effect；CANCELLED，executed0，remaining10，Trade/Ledger0 | 3/3 | 3/3 |
| PARTIAL_BEFORE_CANCEL | fill4/fee0 → durable partial → cancel request → ACK → cancel effect；CANCELLED，remaining6 | 3/3 | 3/3 |
| FILL_DURING_CANCEL | cancel request已收到、本地CANCEL_REQUESTED → fill4/fee0.01 → 释放ACK → cancel effect → recovery；CANCELLED，remaining6 | 3/3 | 3/3 |
| LATE_FULL | fill4 durable → cancel ACK/本地CANCELLED → fills3+3 → venue FILLED → recovery；FILLED，remaining0，version恰好+1 | 3/3 | 3/3 |
| STALE_CANCEL | fill4 durable → hold cancel ACK → fills3+3 → ordinary recovery写FILLED → 释放旧cancel ACK；业务快照完全不变 | 3/3 | 3/3 |
| STALE_PLACE | PLACE已被venue接受但ACK未返回 → fills4+3+3 → ordinary recovery按client identity查询并写FILLED → 释放旧PLACE ACK；业务快照完全不变 | 3/3 | 3/3 |
| RESTART_FULL | fill4与CANCELLED durable → kill旧NQ并确认退出 → venue fills3+3及FILLED → 新NQ ordinary recovery；remaining0，PLACE仍1 | 3/3 | 3/3 |
| MULTI_PARTIAL | fills2+3+1 / fees0+0.01+0.02 → durable6 → cancel剩余4；CANCELLED，三笔独立Trade | 3/3 | 3/3 |

完整48行 PID、DB、状态/version、executed/remaining、Trade/Ledger观察值见 [runs](l4-b2-qualification-resume-attempt01/runs.md) 和 [machine summary](l4-b2-qualification-resume-attempt01/matrix-summary.json)。每行同目录的 `环境-场景-重复.json` 保存全部订单、fills、Trades、Ledger、fees、投影、replay与venue事件。每个场景的NQ/Venue原始日志见 [qualification logs](l4-b2-qualification-resume-attempt01/qualification-logs.zip)。

Real-process composition：Java21.0.9 / Spring Boot3.5.10，真实 PreTradeRiskService、OrderCommandService、Spring write proxy、AdapterBackedTradingVenueGateway、OkxExchangeAdapter、HTTP/JSON、JDBC与事务。PG16.15 / V48；canonical锁定镜像`postgres:16@sha256:f1c3376c26f2609ab9f29f71f824103fe2fcd8ee0346485cb6122a4f93df6f94`，`--pull=never`，仅loopback。

Controller使用`nq_b0_reader`和REPEATABLE_READ业务快照；SQL不制造Order/Trade/Ledger通过结果。既有bootstrap仅在disposable DB启动前设定TEST_PRECONDITION并封存角色，运行中没有kill switch写权限。LIVE仅为fixture持久化的Order环境字段；真实LIVE capability、provider、credential、生产PLACE/CANCEL/transfer/withdraw均未启用。

## 正确性与相关回归

- Partial cancel / full-fill convergence：均PASS。部分成交保留CANCELLED；足量durable事实通过reconciliation-only完整证明+OCC纠正到FILLED。重复恢复不持续增加version。
- Remaining quantity：在0、partial、multi-fill、full、duplicate reports情况下直接核对`original - sum(unique canonical fills)`。full场景4+3+3保留两个等量独立fill，不用`SUM(DISTINCT qty)`。
- Three-fill accounting / fees：每个fill对应唯一Trade；逐笔核对成交金额、DEBIT/CREDIT、fee asset与fee amount。费用包含0、正数、三笔不同费用；重复venue报告和replay不产生重复Trade/Ledger。
- Net accounting：按照现有canonical本金/fee成对分录逐key/金额验证，USDT净分录为0，BUY的BTC持仓及账户快照净数量为0/4/6/10。逐fill正负本金、费用分录以及账户balance/available/frozen另经[独立原始快照检查](l4-b2-qualification-resume-attempt01/accounting-verification.json)验证，48/48。不是把固定分录数当oracle，也不宣称当前成对模型证明真实钱包扣款。
- SIM/LIVE：所有Trade环境等于已持久化父Order。B2 PG双方向误绑定fixture在Ledger replay前被拒绝；原错误环境与Order/version不改写。
- Stale ACK / OCC：真实进程late PLACE、late CANCEL各SIM/LIVE三次，FILLED后的状态/version/external identity与全部业务快照不变。相关Spring/PG回归继续覆盖ABA、pre-cancel及CAS conflict；事件失败回滚、并发终态只纠正一次。
- Overfill：B2 PG验证`10.00000001`不clamp；并发新fill超量拒绝；锁等待/并发提交后SQL重新核查完整数量证明。Overfill是隔离PG对抗证明，不冒充48行中的正常venue成交场景。
- Restart/replay：6次明确kill旧NQ后venue继续变化，新PID不同，ordinary recovery之后无duplicate PLACE/Trade/Ledger或status regression，remaining/environment正确。
- C1 interaction：persistent OCC/version、stale PLACE/CANCEL、ABA、pre-cancel相关断言PASS。
- C2 interaction：共享总candidate limit、venue-before-limit、durable cursor、CANCELLED missing-fill discovery、venue isolation、Trade/Ledger replay相关28项PG回归PASS。未重新展开完整C1/C2审查。
- Migration：V48与上一轮/HEAD原始字节一致，SHA-256=`5147c5b6dddc8b6bd05ce9f10620b2a2f9c9bd1b338d120d1df8da57c983b742`；全部migration未变。

## 验证、文件范围与环境

Targeted tests：**108 tests，0 failures，0 errors，0 skips，exit0，6:19**。其中B2 JUnit1承载48次真实进程，B2 PG13，C1/C2 PG28，F004/F002相关恢复10，其余为相关unit与B0 fixture safety；[逐类摘要](l4-b2-qualification-resume-attempt01/targeted-summary.json)。本轮定向构建前`test-compile`通过；没有production代码修复或测试失败后重跑矩阵。

[实际运行脚本](l4-b2-qualification-resume-attempt01/run-targeted.py)记录`-pl nq-app -am`及B2/B2-PG/L4-blockers显式开关、测试选择。环境使用白名单，未继承SPRING_PROFILES_ACTIVE/NQ overrides/Java options；测试PG为本轮专属新库`nq_l4_blocker`。[环境身份](l4-b2-qualification-resume-attempt01/environment.json)。

Full Maven rerun：**NO**。复用上一轮最终production候选上的独立Full Maven：1836 tests、0 failures/errors、84条件性skips。生产字节未变化，用户明确禁止再次Full Maven。本轮新harness及mandatory B2/C1/C2证明由本轮定向运行覆盖；最终整个候选由后续exact-head canonical CI承担交付验证。

Files changed：仅3个harness源码（B0NqProcessMain、B2SyntheticVenueMain、B2RealProcessProofTest）及本qualification evidence/附件。B0新增单个受控异步任务，主线程可执行普通RECOVER；venue ACK等待释放时让出monitor，超时20s失败；共享测试HTTP transport预算由2s扩到25s以容纳屏障，不改变production timeout。本轮没有重跑B0/B1完整qualification，既有接受记录未重写。

Production files changed：0。Flyway / .github / AGENTS / Skills / current authority / frozen历史：0。CI：NOT_RUN。stage=0、commit=NONE、push=NONE。

运行前遇到Docker环境故障：Linux engine pipe不存在，启动日志报告`sailor-ingest.sock`及`engine.sock`为不可访问的stale socket。仅停止本轮启动的失败Docker进程，把socket-only目录改名备份并重建空目录后启动成功（server29.7.2）；未factory reset、删除镜像或修改数据库/凭证。本次启动失败发生在任何Maven业务测试之前，不计为production correctness失败。

本轮新增保留备份：`Docker/run.b2-resume-backup-20260909-124722`、`Docker/run.b2-resume-backup-20260909-125007`、`docker-secrets-engine.b2-socket-backup-20260909-124842`、`docker-secrets-engine.b2-resume-backup-20260909-125007`（均位于LOCALAPPDATA；仅runtime socket内容）。未删除此前备份。独占PG容器`nq-b2-resume-124134` / `c0288919170552a9201f7f8e57f33cd7280021a685a7ffcd2807894dca0fbdfb`经label/ID核对后删除；B0容器remaining0，全部102个NQ/Venue进程（48+48+6）退出，无残留。

## 最终结论

P0：0。P1：0。

Limitations：这是隔离Windows/PG16进程正确性qualification，未执行真实provider、生产交易、Linux容量/stress或完整B0/B1重验；现有canonical成对账务模型不等同真实钱包结算。没有新production P0/P1，无需第三轮Independent Review。

Final decision：**B2 QUALIFIED / READY_FOR_DELIVERY**。

Commit recommendation：可进入下一项精确交付任务；本轮不stage/commit/push，不把未来操作写成已执行。

Next action：`NQ-GATEAUDIT-PHASE6-L4-B2-PRECISE-DELIVERY`。按该任务授权对整个B2候选精确stage → commit → push → exact-head canonical CI，成功后再进行相应接受记录；当前仍非CI_GREEN/ACCEPTED。
