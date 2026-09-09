# B2 synthetic identity evidence remediation

本轮只修改 test evidence 导出与身份表示。生产 candidate、两次独立 correctness review、Full Maven `1836 / 0 failures / 0 errors` 和 qualification `48/48 PASS` 的既成事实保持不变。

失败交付 commit=`68fe95b58785a7534b648eb3dc30db67b93aefbd`；[canonical CI 34318927962](https://github.com/ling5477/nexus-quant/actions/runs/34318927962) 为 `8/9 success`，唯一失败是 Secret scanning 的 663 条 generic-api-key 命中。本文件记录本地整改，不提前声明新交付 commit 已被 CI 接受。只有新提交自己的 canonical CI `9/9 completed/success` 后，B2 才能从 `QUALIFIED / DELIVERY_BLOCKED_BY_CI / NOT_ACCEPTED` 转为 `ACCEPTED`。

## 来源与范围

- [inventory](inventory.json) 记录原 commit 中每一条命中的 path、原始行号、字段、身份类型与 synthetic provenance：`663/663 CONFIRMED_SYNTHETIC`、69 files、UNKNOWN=0。清单不保存原身份及其 hash。
- CLIENT 来源为 `TradingRestartRecoveryPostgresIntegrationTest.RestartDatabase.create` 的独立随机数据库名称，经 `B0Fixture.create` 和 `B0NqProcessMain` 产生 client suffix；逐条验证与同一证据中的 database suffix 相等。
- ORDER/TRADE 由真实 OrderCommandService/OkxRestReconcileService 在该隔离 fixture 中生成；每条 event key 与同份快照中的 `order_id` / `trade_id` 主键关联。没有用随机字符串外形单独推断来源。
- 精确证据文件范围来自前次交付的 tracked B2 manifests。历史 FAIL、后续 PASS、PID、金额、费用、状态/version、测试总数、P0/P1、审查结论均保留。

## 导出规则

[共享导出器](../../../../backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/synthetic_evidence.py) 在 `raw-proof.json` / `raw-failed-proof.json` 落盘后生成默认导出文件 `proof.json` / `failed-proof.json`，避免旧打包脚本默认复制 raw。运行时、数据库及独立 JVM 仍使用原随机身份。本轮接入 B2，B3+ 可复用同一入口；B0/B1 已有 runtime 与 ACK 原始字节 hash 契约保持不变，既有 B1 compatibility exception 不改动。

身份格式为 `SYNTH-L4:<BATCH>:R<NN>:<TYPE>:<NNN>`，例如 `SYNTH-L4:B2:R03:CLIENT:001`。这是同一批次/run/type/ordinal 机制。初次全连字符格式仍触发 5 条 pinned generic-api-key；因此改用冒号分隔短字段，不依赖 entropy 阈值或 allowlist。batch/type 长度有界，不把完整 reference 再拼成一个长 token。

主身份先登记，再解析 event key、外键及带 Ledger 后缀的复合幂等键。同一 run 内同类型原身份与 canonical reference 为双射；同 raw 在不同类型使用不同 namespace。批次内调用者提供唯一 run 序号；不同批次/attempt 应提供不同 batch 标签。历史证据统一编号为 R01–R80；metadata 使用独立 B2-EXPORT namespace。

只转换明确 identity 字段及已登记的引用。apiKey/token/Authorization/password/credential/secret/passphrase 等字段与凭证子树不转换；未知字段不按字符串长度或熵改写。历史 Markdown 中 `b2-fill-1/2`、`b2-venue-1` 仍是明确的低熵 runtime fixture 标签；对应完整 JSON 使用各自 run 的 FILL/VENUE 引用。

## 等价与 raw 保存

[等价绑定](equivalence.json) 包含修改文件的 before raw SHA、after raw SHA 与原始归档 disposition；[逆映射验证](equivalence-validation.json) 和 [验证程序](verify-equivalence.py) 校验 1517 个 typed identities 的双射、完整文件逆映射等价和七个 production 文件原始字节不变。每个 JSON 的数组顺序、所有非身份字段、重复/replay 边与重启前后关系均保留；文本只额外调整四个 raw 归档链接和换行表示。

```text
TECHNICAL_CANDIDATE_DELTA = 0
EVIDENCE_SEMANTIC_DELTA = 0
EVIDENCE_IDENTITY_REPRESENTATION = CANONICALIZED
productionFingerprint = 7982a8d51e21332e7474be15b24e468249ba0e19a647d651bdd8a5020305910a
```

`TECHNICAL_CANDIDATE_DELTA` 指七个已审 production files；本轮新增导出 harness 由本轮定向测试覆盖。原 production identity 文档中 BOM 描述的历史笔误不改写：实际 fingerprint 输入为 UTF-8 无 BOM、CRLF、末尾 CRLF。

原始随机 identity、逆映射表、原始 logs/archives 保存在本机 Temp 的 `nq-b2-synthetic-remediation/raw-backup` 及相邻 mappings 文件，不提交 Git。五个 raw 文件从当前 tracked tree 移出：live-env 的 `run-logs.zip` / `prepare-full.log`，qualification 的 `qualification-logs.zip` / `combined-review-raw.zip`，precise-delivery 的 `pre-format-originals.zip`。备份与移出前逐字节相同；前次提交仍保留原始历史，不 amend、不改写 history。

canonical `path` 字段保留原路径结构并替换其中的 run identity，属于证据表示，不是可直接打开的 raw 文件路径；原始位置由 Temp 中的映射保留。

历史 manifest 的 SHA、Git blob、source fingerprint 都仍指原始 reviewed/qualified 内容；不把旧 hash 冒充 canonical export 的 hash。本轮 current representation 以本目录 equivalence bindings 为准。原 Full Maven、qualification、独立 review 摘要和格式归一化历史仍保留。

## 验证与交付边界

- [回归测试](../../../../backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/test_synthetic_evidence.py)：相同/不同 raw、类型隔离、run 隔离、前向引用、复合键、凭证字段保持原值；对全部 48 份 B2 proof 结构注入新随机身份，验证 raw occurrence=0 和完整树逆映射相等。
- [Maven 测试入口](../../../../backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/SyntheticEvidenceExportTest.java)：调用上述回归并验证 raw 文件不变、canonical 副本单独生成。
- [pinned scanner 验证程序](../../../../backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/verify_synthetic_secrets.py)：核对 canonical supply-chain lock 的 archive SHA/version，原样提取当前 CI config 与 safe-file scope；全部 tracked safe files 加本任务明确新文件一起扫描。不是修改 scanner implementation。
- 六个执行负例为既有 B1 new-value/real-shaped/extended-value 三例，以及本轮要求的 apiKey/token/Authorization 三例；六例均先经过导出器且值不变，随后 pinned Gitleaks 返回 `2 / REJECT`。没有把新例误记成六个已定位的旧 fixture。
- commit 前必须保存最终 targeted、secret、stage-assets、diff-check 结果，并校验 exact staged paths。stage-assets 若不命中 changed bound asset，则 hash changes=0，不人为同步其他 hash。

最终本地检查见 [local validation](local-validation.json) 与 [pinned scan results](secret-validation.json)。

不重跑 Full Maven、48-run real-process qualification、Independent Correctness Review、Playwright 或 C2 PostgreSQL。本轮 `.github/**`、secret scanning implementation、production/Flyway、AGENTS、Skill topology 和 C1/C2 无变化。F3–F6 保持 `OPEN / P2 / NON_BLOCKING`。

新 exact-head CI 全绿后的下一动作：`NQ-GATEAUDIT-PHASE6-L4-B3-KILL-IN-FLIGHT-QUALIFICATION`。
