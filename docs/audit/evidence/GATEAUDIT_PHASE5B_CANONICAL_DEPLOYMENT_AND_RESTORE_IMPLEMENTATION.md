# GateAUDIT Phase5B Canonical Deployment and Restore Implementation Evidence

<!-- nq-runtime-scan:historical-reference:start -->

```text
Evidence classification:
HISTORICAL_EXECUTION_EVIDENCE / NON_RUNTIME_AUTHORITY

Task:
NQ-GATEAUDIT-PHASE5B-CANONICAL-DEPLOYMENT-AND-RESTORE-IMPLEMENTATION

Decision:
IMPLEMENTED / PHASE5B_CANONICAL_DEPLOYMENT_AND_RESTORE_CANDIDATE_COMPLETE /
P0_0 / P1_TARGETS_REMEDIATED / PENDING_INDEPENDENT_REVIEW

This evidence does not close P5-F002 or P5-F003 and does not accept Phase5B.
```

## 1. Baseline and boundary

```text
branch=audit/post-gatey-agent-baseline
HEAD=origin/audit/post-gatey-agent-baseline=4c2b393ef0b3806a60cd3240c2e75ba1b350cc87
origin/dev=4c19cb775ebb18b4288400a5a1a402145c2fe30a
origin/dev ancestor=PASS
starting worktree/staged=0/0
authority=PASS / CURRENT_AUTHORITY_VALID
accepted_batch=GateAUDIT-PHASE5A-CANONICAL-CI-AND-SUPPLY-CHAIN / ACCEPTED|CI_GREEN
work_batch before=GateAUDIT-PHASE5B-CANONICAL-DEPLOYMENT-AND-RESTORE / NOT_STARTED
```

仅使用本地 filesystem、随机 loopback disposable PostgreSQL 与任务生成的 non-secret fixture。production server、production DB、credential、LIVE/private trading、Git publication=`NONE`。

## 2. Existing asset inventory

| Asset | Existing capability | Disposition | Canonical use |
| --- | --- | --- | --- |
| `scripts/gatew/gatew-release-contract.psm1`、builder/verifier/installer | deterministic manifest/archive、JAR/path/link/mode、immutable install | `REUSE_PRIMITIVE + LEGACY_INPUT_ONLY` | 复用安全约束，不复用GateW production identity/caller |
| `scripts/gatew/run-gatew4-disposable-restore-drill.ps1` | disposable restore through V35 | `LEGACY_INPUT_ONLY` | 仅作为安全容器/loopback/cleanup输入，不作为current proof |
| `scripts/gatey/gatey-readonly-release-contract.psm1`、builder/installer | dynamic migration inventory、link identity、atomic current、forward-only rollback语义 | `REUSE_PRIMITIVE + LEGACY_INPUT_ONLY` | 复用底层约束，不复用GateY runtime/pilot identity |
| `scripts/gatey/run-gatey5-post-restore-drill.ps1` | post-restore proof through V39 | `LEGACY_INPUT_ONLY` | 不作为current schema proof |
| `scripts/build-freeze-release.ps1`、`deploy-freeze.sh`、`backup-db.sh` | freeze-specific build/deploy/backup | `LEGACY_INPUT_ONLY` | caller与语义保持不变，P5-F009后置 |
| `deploy/systemd/**`、`deploy/gatey/**` | Gate-specific runtime assets | `LEGACY_INPUT_ONLY` | 不删除、不改写 |
| `.github/workflows/ci.yml`、`scripts/ci/**` | 9 required jobs与Phase5A admission ownership | `WRAP` | 在既有required jobs内加入canonical release/restore capability |

Canonical implementation不引用Gate名作为production identity，未修改任何GateW/GateY file或frozen archive，也未复制Gate-specific runtime/pilot framework。

## 3. Canonical release and deployment

```text
contract schema=nq-canonical-release.v1
release ID=nq-4c2b393ef0b3-35de8f47bfed6426
manifest SHA-256=e383ff2b8fc111df3303effac72866e5825f930f5095dcc55f19cad7b1b29462
source commit=4c2b393ef0b3806a60cd3240c2e75ba1b350cc87
Java major=21
schema target=V46
migration inventory SHA-256=32ca6191784d7578da662d1a6e02467f27c97ff74d161eafd6107cc50883dd8f
artifacts=9
identity deterministic=true
```

- Manifest identity只含source commit、runtime/schema identity和ordinal artifact descriptors；不含timestamp、absolute path、username、CI run ID或UUID。
- Backend application由显式`spring-boot:repackage`生成可执行Spring Boot JAR；frontend production dist与deployment assets一并绑定。
- Verifier fail-closed覆盖missing/unexpected/path escape、hash/size、symlink/reparse/hard-link、POSIX mode、source/schema identity，以及JAR central directory、ZIP64/encryption、path alias、entry size/count、全量CRC32与executable structure。
- Installer先验证、仅复制declared files、staging后再次验证、immutable release directory、atomic current pointer、previous release receipt、dry-run/preflight和verified code rollback；不需要Git checkout、safe.directory或production Maven cache。
- `deploy/canonical/deployment-contract.json`明确区分`CODE_ROLLBACK`与`DATABASE_RECOVERY_REQUIRED`，Flyway只允许forward migration，不声称简单可逆。

Local actual artifact执行build→verify→install→activate→verify全部PASS；installation root位于任务临时目录，验证后精确删除，没有启动service。

## 4. Current-schema backup and restore proof

由于Docker exact-digest下载两次在同一CDN blob返回`EOF`，本地proof使用PostgreSQL 17.7 `initdb`建立独立随机临时cluster；未连接系统现有PostgreSQL service。CI仍绑定Phase5A lock中的PostgreSQL 16 exact digest。

```text
actual latest migration=V46
pending migrations=0
Flyway validate=PASS
backup format=POSTGRESQL_CUSTOM
backup size=802718
backup SHA-256=1b614f4ed85fcdfa9cdd540a24fbef57fec2fb72549b4c46ab6ffe53decd2d17
tool identity=pg_dump (PostgreSQL) 17.7
source canary=75|464|276|46|V46|1
restored canary=75|464|276|46|V46|1
repository smoke=PASS
application context smoke=PASS
```

Sequence：fresh disposable cluster→Flyway V1..V46→non-secret canonical role fixture→custom backup→SHA/size/schema/source/tool metadata verification→fresh target DB→restore→Flyway validate/current/pending→schema/data canary compare→repository smoke→Spring app-context smoke。

Permanent negative cases全部`REJECTED`：

```text
tampered backup
truncated backup
wrong schema target
restore command failure
post-restore validation mismatch
missing Flyway history
```

Proof artifact：`artifacts/phase5b-current-schema-restore-local/restore-proof.json`。所有native temporary cluster/process已停止并删除；production access=`NONE`。

## 5. CI admission

```text
required jobs=9 (unchanged)
canonical release owner=frontend-critical
current-schema restore owner=postgres-flyway
required jobs unconditional=9
critical capabilities ownership=20
workflow mutations rejected=47
```

- `Build, verify, and install canonical release candidate`与`Run current-schema backup and restore drill`均为required owner内唯一、unconditional、fail-closed step；无`continue-on-error`。
- Validator读取真实release regression与restore implementation，持有release build/verify/install、backup integrity、post-restore validation ownership。
- Phase5B新增7项关键mutation全部REJECTED；Phase5A原40项suite继续REJECTED。
- required check names、permissions、action/tool/image pinning、remote enforcement与platform attestation边界不变。

## 6. Validation and residuals

```text
canonical release fixture regression=PASS / 18 cases
actual backend executable JAR=PASS / 36.7 MB
frontend production build=PASS
actual canonical build/verify/install/activate/verify=PASS
current-schema restore proof=PASS
repository/app-context restored-DB smoke=PASS/PASS
workflow validator=PASS
mutation suite=47/47 REJECTED
PowerShell parser=PASS (errors=0)
SnakeYAML 2.4 workflow parse=PASS / jobs=9
Full Maven=NOT_REQUIRED / NOT_RUN
exact-head CI=NOT_RUN
```

Finding disposition：

```text
P5-F002=REMEDIATED_PENDING_INDEPENDENT_REVIEW
P5-F003=REMEDIATED_PENDING_INDEPENDENT_REVIEW
P5-F007=OPEN / NOT_IMPLEMENTED
P5-F008=OPEN / NOT_IMPLEMENTED
P5-F009=OPEN / NOT_IMPLEMENTED
remote enforcement=NOT_APPLIED / NOT_VERIFIED
platform attestation=DEFERRED
```

- Rollback：candidate未提交，仅对本证据列出的source/docs/workflow files应用文件级反向补丁；保留历史Attempt/FAIL/BLOCKED。不得reset/rebase accepted audit branch或改写GateY frozen history。
- Staged/commit/push=`0/NONE/NONE`。
- Next action：`NQ-GATEAUDIT-PHASE5B-CANONICAL-DEPLOYMENT-AND-RESTORE-REVIEW`。
- Final closure/acceptance authority属于独立高风险Review与后续exact-head CI，不属于本实现任务。

<!-- nq-runtime-scan:historical-reference:end -->
