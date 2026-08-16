# NQ-GATEY-6E-MINIMUM-ORDER-VALUE-SEMANTIC-FORWARD-REMEDIATION-IMPLEMENTATION — attempt-01

## Task classification

- ownership：NQ-only。
- type：`CODE_CHANGE / FORWARD_MIGRATION / CONTRACT_REMEDIATION / DATA_INTEGRITY`。
- level：L级高风险migration；是GateY-6E subordinate blocker remediation，不重新规划或实现GateY-6E真实交易能力。
- result：`PASS / GATEY_6E_MINIMUM_ORDER_VALUE_SEMANTIC_REMEDIATED / V41_IMPLEMENTED / VENUE_NOT_PUBLISHED_MODELED_EXPLICITLY / NO_FABRICATED_PREREQUISITE_FACT / INSTRUMENT_OBSERVATION_V2 / JAVA_POSTGRES_PARITY_PASS / V40_TO_V41_PASS / V1_TO_V41_PASS / OKX_CALL_0 / EXCHANGE_MUTATION_0 / FIRST_REAL_ORDER_NOT_AUTHORIZED / LIVE_DISABLED / PENDING_INDEPENDENT_MIGRATION_SECURITY_REVIEW`。

## Starting baseline

- branch=`dev`；起始worktree clean、staged=`0`。
- `HEAD == origin/dev == 4fa39a7c5700cabd6b041d6457cb4640eabb4feb`。
- `NQ CI Baseline` run `31947934533`：`completed / success`，head SHA精确匹配。
- 起始最高migration为V40；`V1～V40`不允许修改。
- authority before/after均为accepted=`GateY-6D / ACCEPTED|CI_GREEN`，work=`GateY-6E / NOT_STARTED / NONE / NOT_RUN`；machine next action仍为`NQ-GATEY-6E-FIRST-REAL-ORDER-PREREQUISITE-IMPLEMENTATION`；LIVE=`DISABLED`，kill switch=`ENGAGED`。

## Exact V40 semantic defect

V40把每个instrument item的独立`minimum_order_value > 0`及`minimum_order_value_currency='USDT'`定义为mandatory venue-authored fact，并纳入instrument canonical digest。GateY-6E blocked attempt-01已证明当前OKX typed instrument contract只正式提供`state/tickSz/lotSz/minSz`，不能证明独立minimum order notional。固定USDT常量、历史公告、`minSz × ticker`、UI抓取、fixture或经验推导都会伪造不存在的venue fact，因此不能继续使用V40语义。

正确语义是：`minimumOrderSize`仍为mandatory venue fact；`minimumOrderValue`仅在venue正式发布时使用`VENUE_PUBLISHED`并强制value/currency，venue API未发布时使用`VENUE_NOT_PUBLISHED`且不得携带人工value/currency。未来真实`PLACE`若返回未预先公开的额外notional规则明确拒绝，必须为`DEFINITIVELY_REJECTED / NO_RETRY`；本任务未实现或调用该路径。

## V41 forward-only remediation

- 唯一新增migration为`V41__gate_y6e_minimum_order_value_semantic_remediation.sql`；V1～V40未修改。V41 SHA-256=`e97f97f2ef79b9628e952a310170f10bba96899e82781656e5dedfac4c95cbc4`。
- `pilot_instrument_observation_items`新增非空`minimum_order_value_evidence_class`，允许：
  - `VENUE_PUBLISHED`：value必须`>0`，currency必须非空；
  - `VENUE_NOT_PUBLISHED`：value/currency必须同时为`NULL`；
  - `LEGACY_V40_REQUIRED`：仅为历史v1/V40行保留原`>0 / USDT`形态。
- 历史行使用`ADD COLUMN ... DEFAULT 'LEGACY_V40_REQUIRED'`完成metadata-safe标记，并立即`DROP DEFAULT`。migration没有`UPDATE`，不修改历史value/currency，不创建新observation/item，不把历史值重新解释为`VENUE_PUBLISHED`。
- V41以`5s lock_timeout / 60s statement_timeout`运行；production lock window/target scale未测量，production migration未授权。

## Versioned observation and canonical contract

- 新production instrument observation只能使用`instrument-metadata-observation.v2`；数据库insert guard拒绝migration后新v1 observation。
- 历史`instrument-metadata-observation.v1`保持可回读；v1 item必须标为`LEGACY_V40_REQUIRED`，其canonical field顺序、bytes与digest不变，不重写、不重算、不fake backfill。
- v2 canonical item固定编码`symbol/tradingStatus/tickSize/lotSize/minimumOrderSize/minimumOrderValueEvidenceClass`；只有`VENUE_PUBLISHED`才继续编码`minimumOrderValue/minimumOrderValueCurrency`。`VENUE_NOT_PUBLISHED`不编码两个nullable字段，因此只有一个确定性representation。
- `PilotPrerequisiteObservation.InstrumentItem`、`PilotObservationCanonicalEncoder`、`PilotObservationSet`、JDBC insert/row mapper和PostgreSQL digest/payload reconstruction均按observation schema version处理。
- 外层`pilot-scope.v1`字段未变，未升级schema；instrument metadata digest变化自然进入exact scope hash。

## Database enforcement and compatibility

- 新v2空`VENUE_PUBLISHED`、携带人工value的`VENUE_NOT_PUBLISHED`以及v2使用legacy class均由Java与数据库约束拒绝。
- v1/v2允许在同一数据库中共存；历史v1可读，新production insert不能继续创建v1。
- V41替换instrument digest与observation payload reconstruction函数以支持versioned canonical bytes；Java/PostgreSQL v2 payload/hash byte-for-byte parity通过。
- V40既有append-only/immutable trigger、exact instrument set与complete observation set deferred trigger未删除；same identity same payload保持幂等，same identity different payload保持conflict。

## Validation evidence

| Command / suite | Result |
| --- | --- |
| `mvn -f backend/pom.xml -pl nq-core,nq-infra -am -DskipTests compile` | PASS（通过），exit=`0` |
| focused core/infra Java与migration contract | 16 tests，failures/errors=`0/0` |
| required PostgreSQL 17.7 suite | 4 tests，failures/errors/skipped=`0/0/0`；repository Compose PostgreSQL，`127.0.0.1:5432/nexus_quant`，只使用并清理随机schema |
| populated V40→V41 | PASS；legacy行标记`LEGACY_V40_REQUIRED`，historical value/currency/fingerprint/canonical bytes不变，no fake backfill |
| fresh V1→V41 + Flyway validate/checksum | PASS；latest=`41`，v1/v2 coexistence通过 |
| semantics/parity | v2 NOT_PUBLISHED有效；fake value、空PUBLISHED、new v1拒绝；Java/PostgreSQL parity通过 |
| persistence regressions | append-only、complete set、identity/idempotency/conflict、lock-timeout通过 |
| `mvn -f backend/pom.xml test` | 23/23 reactor modules `SUCCESS`，`BUILD SUCCESS`，exit=`0`，total=`65s`；reactor failures/errors=`0/0`；`nq-app` 289 tests / 0 failures / 0 errors / 30 existing conditional skips |

RCA记录：两次focused Maven命令因PowerShell `-D`参数引号解析在测试启动前失败，修正命令后相同suite通过；PostgreSQL首次rerun因第二个测试`RiskLimitSet`复用全局version=`100`而失败，fixture最小改为`101`后4/4通过。Mockito dynamic-agent、SLF4J NOP、deprecation/unchecked、expected error-path stack trace与LF→CRLF提示为非阻断warning。frontend/Python/E2E未运行，因为对应diff为0。

## Diff and boundary counters

- migration：新增V41=`1`；V1～V40 diff=`0`。
- production/test：仅`nq-core`、`nq-infra`、`nq-app`；frontend/research/scripts/deploy/`.github` diff=`0`。
- governance：`STATUS.md` / `ROADMAP.md` diff=`0`；authority不变。
- credential read=`0`；OKX API call=`0`；PilotScope real materialization=`0`；OperatorApproval real creation=`0`；ExecutionIntent/Receipt=`0/0`。
- PLACE/CANCEL/TRANSFER/WITHDRAW=`0/0/0/0`；worker start=`0`；provider runtime binding=`0`；exchange mutation=`0`；LIVE enable=`0`；kill disengage=`0`。
- 唯一网络访问是只读GitHub Actions baseline查询；生产数据库、真实用户数据、stage/commit/push/tag/deploy=`0`。

## Findings and residuals

- P0：0。
- P1：0；`MINIMUM_ORDER_VALUE_SOURCE_UNRESOLVED`已通过不伪造venue fact的versioned evidence语义关闭。
- P2：1；`PRODUCTION_LOCK_WINDOW_NOT_MEASURED`。本地PostgreSQL随机schema验证不能外推生产锁窗口或数据规模；独立migration security review必须复核DDL lock、constraint/trigger完整性、canonical parity与rollback。
- P3：0。
- residual：`IMPLEMENTED / PENDING_INDEPENDENT_MIGRATION_SECURITY_REVIEW`；尚未commit，exact-head implementation CI尚未运行，不授权真实pilot、provider、第一笔真实订单、micro-live或LIVE。

## Authority and decision

`STATUS.md`与`ROADMAP.md`未修改；machine authority继续：

```text
accepted_batch=GateY-6D
accepted_batch_status=ACCEPTED|CI_GREEN

work_batch=GateY-6E
work_batch_status=NOT_STARTED
work_batch_commit=NONE
work_batch_ci_run=NOT_RUN
next_action=NQ-GATEY-6E-FIRST-REAL-ORDER-PREREQUISITE-IMPLEMENTATION

live=DISABLED
kill_switch=ENGAGED
```

Final decision：`PASS / GATEY_6E_MINIMUM_ORDER_VALUE_SEMANTIC_REMEDIATED / V41_IMPLEMENTED / VENUE_NOT_PUBLISHED_MODELED_EXPLICITLY / NO_FABRICATED_PREREQUISITE_FACT / INSTRUMENT_OBSERVATION_V2 / JAVA_POSTGRES_PARITY_PASS / V40_TO_V41_PASS / V1_TO_V41_PASS / OKX_CALL_0 / EXCHANGE_MUTATION_0 / FIRST_REAL_ORDER_NOT_AUTHORIZED / LIVE_DISABLED / PENDING_INDEPENDENT_MIGRATION_SECURITY_REVIEW`。

唯一下一任务：`NQ-GATEY-6E-MINIMUM-ORDER-VALUE-SEMANTIC-FORWARD-REMEDIATION-SECURITY-REVIEW`。

建议review接受后的commit message：`fix(gatey): align OKX minimum order constraint semantics`。
