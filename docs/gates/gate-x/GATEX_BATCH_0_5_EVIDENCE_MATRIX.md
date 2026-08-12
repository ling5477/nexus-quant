# GateX Batch 0–5 Evidence Matrix

| Batch | Status | Commit / acceptance | CI | Capability / evidence | Boundary |
| --- | --- | --- | --- | --- | --- |
| GateX-0A | `ACCEPTED / CI GREEN` | implementation `49851276…`；acceptance `61d9292b…` | `31318868410` | Strategy↔Trading 与 audit port ownership、ArchUnit guardrails | 不改交易状态机/LIVE |
| GateX-0B | `ACCEPTED / CI GREEN` | `108a14d14906d6fa354349c66d35a2ae6967cebf` | `31321821962` | capability/domain naming 与 legacy config alias | 不改公开 API/DB |
| GateX-0C | `ACCEPTED / CI GREEN` | `46392213495652f6a09005148cc160fd2882adb9` | `31325824949` | validation frontend composition decomposition | API/query/RBAC 行为不变 |
| GateX-0D | `ACCEPTED / CI GREEN` | `885ed23375d0d8a58d9d10d2c4768f390322af93` | `31344357225` | canonical StatusTag 与金融涨跌语义 | 不隐藏风险状态 |
| GateX-0E | `AUDITED / IMPLEMENTATION NOT REQUIRED` | task evidence | current accepted baseline | Query/cache/config scoped audit | 无新增代码 |
| GateX-1 | `ACCEPTED / CI GREEN` | `2655f5144ba27cc88c2786de7f76633df3df462d` | `31358676688` | Strategy Release aggregate、manifest、trusted-root verifier、provenance read service | 无 persistence write/admission |
| GateX-2 | `ACCEPTED / CI GREEN` | `894e76bf69dbcf1574be6c993f18ca7913033564` | `31379536899` | provenance migration、JDBC persistence、collision fail-closed | 保留 lock-window P2 |
| GateX-3 | `ACCEPTED / CI GREEN` | `5f4824eecaac5cffbbc314fb8f767bd6ba45c29f` | `31391541813` | fail-closed admission 与 immutable creation plan | 不创建 Shadow Run |
| GateX-4A | `PASS / DESIGN BLOCKER RESOLVED` | schema/security review evidence | review evidence | nullable opaque locator pair、NO FAKE BACKFILL | trusted root 只来自服务端 |
| GateX-4B | `ACCEPTED / CI GREEN` | `92043c37dad96d984d5e55a1e5170c97d335d6d4` | `31403529376` | V37 locator persistence、pair constraint、immutability | producer 当时未接线 |
| GateX-4C | `ACCEPTED / CI GREEN` | `b4e5406fbb9de5432f79f9ef8ef76c95002e0e56` | `31409595743` | server-controlled resolver、containment、identity、strict parser | 不授权运行/交易 |
| GateX-4 | `ACCEPTED / CI GREEN` | `7aaf6027644b2ba6cd7dc588536784be50ff1eff` | `31467397459` | publishRecordId 驱动的只读 admission preview API/UI | GET-only，无 materialization |
| GateX-5A | `REVIEWED / REMEDIATED` | task evidence | included in final baseline | AdmissionGuard migration 与 consistency contract | V38 forward-only |
| GateX-5B | `IMPLEMENTED / REVIEW ACCEPTED` | included in `3336bd81…` | included in `31512467501` | command-time re-admission 与 revision guard | fact tear closed |
| GateX-5 | `ACCEPTED / CI GREEN` | implementation `3336bd8153845d5368a0d65a9c72d3566dc9bd35`；acceptance `a383be750f51d063d429bc25fad80e60dffb7014` | `31512467501` | guarded `CREATED / RELEASE_BOUND` materialization、幂等与原子性 | runner/scheduler/order/network=0 |
| Freeze governance | `ACCEPTED / CI GREEN` | `9848ce24…`、`f255e6b0914c3c6aa39708a269a20a3a17964450` | `31559049270`、`31560815042` | generic closeout action 与 PS5.1 compatibility | semantics unchanged |

所有 task evidence 原文件名保存在 `source/task-evidence/`。历史 BLOCKED、失败 attempt、review rejection 与 remediation 不被覆盖或合并；表中 acceptance 是最终状态，不改写中间历史。

全线 credential/private exchange/order/cancel/transfer/withdraw/LIVE side effect 为 0。GateX freeze 只接受 Research Artifact → Strategy Release → verified binding → guarded admission → `CREATED / RELEASE_BOUND` materialization。
