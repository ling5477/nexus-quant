# GateAUDIT SQL Ownership Repository Independent Review

## Candidate and reviewer

2026-09-23，`REVIEW_ONLY`。Reviewer 为独立 subagent `/root/sql_review`（GPT-6 Sol / Medium），未参与 SQL audit 报告编写，未修改候选、index 或 authority。源码 HEAD=`2b565eca6e9e34f6faf856ccd05f49b43f37ed7b`，tree=`82f47d784e76e8f3862a14181758797914f0fad2`；审查前后不变。唯一暂存候选为 [SQL ownership audit](GATEAUDIT_SQL_OWNERSHIP_REPOSITORY_AUDIT.md)，初版 Git blob=`21c18dbf3697a331030d33bedc467729ebc53da3`，修订后 Git blob=`1349d1128fdc52e8860131b8081b9fddf7d6984d`。Reviewer 直接读取 Git index 中的候选原字节并对照源码。

`REVIEWER_TYPE=INDEPENDENT_SUBAGENT`；`CANDIDATE_MODIFIED_BY_REVIEWER=false`。CodeRabbit review=`NOT_RUN`。先前 Windows CLI 查找失败且官方安装端点 TLS 握手失败；本轮核实 WSL 中存在 `/home/lingyu/.local/bin/coderabbit` 0.7.5。WSL 安装事实不改变本次选定的独立 subagent 复核渠道，也不构成 CodeRabbit PASS。

## 独立复核范围

Reviewer 对暂存附录 R001–R444、91 个 owner/file 条目和 D01–D28 作编号与身份反算，将直接 JDBC 调用位置与附录交叉检查，并独立抽样 trading、ledger、risk、scheduler、strategy、marketdata、research/paper、account/auth、livecontrol/recovery 的源码、caller 和事务边界。对 17 组 correctness-critical invariant、28 个重复候选组、7 类 mapper 和 3 个 manual SQL 入口进行定向语义核对。另主动挑战 kill state 多处读取、Trade read projection、ledger balance/diff 三类可能遗漏的语义重复，未找到额外 correctness owner 冲突；`ADDITIONAL_DUPLICATE_GROUPS_FOUND=0`。

`RUNTIME_SQL_INVENTORY_ROWS_CHECKED=444`（初版编号和源码位置反查；修订后为 435）；`OWNER_MATRIX_ROWS_CHECKED=91`；`CORRECTNESS_GROUPS_REVIEWED=17`；`DUPLICATE_GROUPS_REVIEWED=28`；`MAPPER_GROUPS_REVIEWED=7`；`MANUAL_SQL_PATHS_REVIEWED=3`。这些是静态审查覆盖数，不表示逐条重新运行 SQL 或重新证明所有运行时并发条件。生产部署脚本的 SQL 为只读 readback，restore drill 有 disposable 边界，manual seed 未找到普通 runtime caller。

## 初审发现与修订

初审为 `FAIL / REVIEW_FINDINGS`，没有提前签收：

- `SQL-REV-01 / P2 / 审计证据错误`：R038/R039 为 provider transport/callback `execute`，R068/R069/R071/R073/R075 为 `TransactionTemplate.execute`，R316/R318 也是事务包装；这 9 个位置不是报告所定义的 JDBC SQL 执行调用点。原 `444/1314` 不是计数定义差异。原执行 Agent 删除这些行，重排附录 A，修正附录 B 四个 owner 的 site 计数及总数。
- `SQL-REV-02 / P3 / 描述错误`：D07 的 `JdbcShadowRunFactRepository` 可追加动态 WHERE；它与无条件 count 只在无筛选条件时文本重合。原执行 Agent 将其明确为条件性语义候选，不再称全部 28 组均为精确文本重复。

同一 reviewer 仅复核上述修订的 delta，结论为 `PASS / SQL_OWNERSHIP_REPOSITORY_INDEPENDENT_DELTA_REVIEW_ACCEPTED`。修订后 R001–R435 连续且无重复、91 个 owner 行的 sites 合计 435；四个受影响 owner 计数分别为 2/22/1/2；`435+51+527+284+3+3+2=1305`。D07 的条件已说明，28 组仍有处置。原初审发现保留在本记录中，不将初版 FAIL 改写为 PASS。

## 最终判断与边界

在固定 HEAD、报告对象清单及本次静态抽样范围内，未发现未归属的关键写入、第二个独立 correctness owner、CAS/幂等/账务绕行或 canonical mapper 冲突：`UNOWNED_RUNTIME_SQL=0`、`CORRECTNESS_OWNER_DUPLICATION=0`、`CURRENT_CORRECTNESS_BYPASS=0`、`P0=0`、`P1=0`。审查引入的 P2/P3 均已通过上述证据修订关闭；报告自身另保留 2 个 P2 与 1 个 P3 维护项。

最终独立复核=`PASS / SQL_OWNERSHIP_INDEPENDENT_REVIEW_ACCEPTED`，只支持该固定候选的对象级审计判断。正式 S10 接受仍需审计 evidence commit 的 exact-head CI 成功和后续 authority sync；Phase7-E 继续暂停。本审查未运行生产 DB、真实 provider、L4/L5/L6 qualification 或 soak，也不扩张既有动态证明的适用范围。
