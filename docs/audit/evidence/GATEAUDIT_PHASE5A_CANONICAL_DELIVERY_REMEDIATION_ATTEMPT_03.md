# GateAUDIT Phase5A Canonical Delivery Remediation Attempt-03 Evidence

<!-- nq-runtime-scan:historical-reference:start -->

```text
Evidence classification:
HISTORICAL_EXECUTION_EVIDENCE / NON_RUNTIME_AUTHORITY

Task:
NQ-GATEAUDIT-PHASE5A-CANONICAL-DELIVERY-REMEDIATION

Attempt:
03

Decision:
IMPLEMENTED / P1_TARGETS_REMEDIATED / PENDING_INDEPENDENT_CLOSURE_REVIEW

This file does not close findings or accept Phase5A.
```

## 1. Attempt-02 preservation and corrected baseline

Attempt-02 remains：

```text
BLOCKED / BASELINE_CHANGED
files changed=none
staged=0
commit=NONE
push=NONE
```

The blocked task incorrectly required `origin/dev == HEAD`. Attempt-03 verified the repository model instead：

```text
branch=audit/post-gatey-agent-baseline
HEAD=f8a1d047f923cd940902f9ac3ad1c2dec9431b5b
origin/audit/post-gatey-agent-baseline=f8a1d047f923cd940902f9ac3ad1c2dec9431b5b
origin/dev=4c19cb775ebb18b4288400a5a1a402145c2fe30a
merge-base(origin/dev,HEAD)=4c19cb775ebb18b4288400a5a1a402145c2fe30a
origin/dev ancestor exit=0
staged=0
starting candidate files=20
```

Attempt-02 candidate fingerprint：

```text
3c7929c01777af8a5203cedaf8a2adadc0d3562efe9003b82c7a044e4807ebd6
```

Attempt-03 before fingerprint matched exactly.

## 2. Root causes and implementation

### GITLEAKS_SYSTEM_BINARY_OVERWRITE

- Root cause：verified binary directory was added to `GITHUB_PATH`, but the scan used bare `gitleaks detect` and could fall back to another PATH binary.
- Implementation：resolve the isolated executable with `realpath`, persist exact `NQ_GITLEAKS_BIN`, and invoke `Invoke-VerifiedGitleaks.ps1 -BinaryPath <absolute path>`.
- System paths are not written or deleted; missing binary fails before invocation.

### PROVENANCE_GENERATED_BUT_NOT_ENFORCED

- Root cause：backend/frontend delivery evidence was uploaded before the complete provenance readback validator ran; the workflow checker accepted conditional or soft-failing validator steps.
- Implementation：generate both local artifact sets, SBOMs, manifests and internal provenance in the same producer job; run unconditional local readback admission before all three delivery uploads; retain a second readback after artifact download.
- Required steps reject `if:` and `continue-on-error: true`; ordering binds the admission step before backend/frontend/provenance uploads.

### SUPPLY_CHAIN_LOCK_PARTIALLY_ENFORCED

- Root cause：the lock validated producer identities but did not fully bind active npm, Playwright, Maven and gitleaks consumers or validator reachability.
- Implementation：enforce `npm ci`, `npx --no-install playwright`, locked CycloneDX version, absolute gitleaks consumer, unconditional lock validator and consumer/order mutation regression.

## 3. Positive and negative regression

Positive：

```text
canonical workflow=PASS
Actions=24/24 pinned
PostgreSQL images=3/3 digest pinned
required checks=9
frontend production build=1
gitleaks isolated scanner success=PASS
provenance generation/readback/determinism=PASS
NoSkipReporter contract=PASS
YAML parse=PASS
PowerShell parse errors=0
```

Workflow/consumer mutations rejected：

```text
lock-validator-if-false
lock-validator-continue-on-error
npm-lock-bypass
playwright-latest
maven-plugin-unpinned
gitleaks-scan-removed
provenance-continue-on-error
provenance-if-false
provenance-validator-removed
provenance-validator-after-upload
```

Gitleaks execution regressions：

```text
PATH sentinel executed=NO
PATH sentinel hash changed=NO
verified binary missing=REJECTED
finding exit 2=REJECTED
scanner error exit 3=REJECTED
checksum mismatch=REJECTED
```

Provenance regressions：

```text
wrong commit/runId=REJECTED
missing provenance/SBOM=REJECTED
tampered SBOM/manifest/artifact digest=REJECTED
tampered backend/frontend subject=REJECTED
missing field/unexpected schema=REJECTED
```

## 4. Candidate fingerprint

The evidence file containing this value is excluded to avoid a self-referential hash. The projection covers every other modified/untracked candidate file as `path|sha256`, sorted ordinally with LF separators.

```text
before files=20
before=3c7929c01777af8a5203cedaf8a2adadc0d3562efe9003b82c7a044e4807ebd6

after files excluding this evidence=22
after=0f0ecec4e2598497b834699b9e4c3ad020f7f0633d9a3cd7c68c2ecf2e5b2c2a
```

## 5. Findings, residuals and boundary

```text
P0=0
P1 targets=REMEDIATED_PENDING_INDEPENDENT_CLOSURE_REVIEW
P2 IMAGE_DIGEST_RUNTIME_PULL_PENDING_EXACT_HEAD_CI=UNCHANGED
P2 CRITICAL_E2E_ADMISSION_PENDING_FIXTURE_REPAIR=UNCHANGED
P3 Playwright generated-file cleanup=UNCHANGED
```

Production Java、migration、frontend product、research、deployment、LIVE、private exchange、repository settings均未修改。Full Maven=`NOT_REQUIRED`。Commit/push=`NONE/NONE`。

## 6. Rollback and next action

- Rollback：对Attempt-03新增的workflow/scripts/tests/current evidence变更应用文件级反向补丁；保留原20-file Phase5A candidate与Attempt-02 BLOCKED事实，不执行reset/rebase。
- Next action：`NQ-GATEAUDIT-PHASE5A-CANONICAL-DELIVERY-CLOSURE-REVIEW / Attempt-02`。
- Finding最终关闭权属于独立Closure Review。

<!-- nq-runtime-scan:historical-reference:end -->
