# GateAUDIT Phase5A Canonical Delivery Remediation Attempt-05 Evidence

<!-- nq-runtime-scan:historical-reference:start -->

```text
Evidence classification:
HISTORICAL_EXECUTION_EVIDENCE / NON_RUNTIME_AUTHORITY

Task:
NQ-GATEAUDIT-PHASE5A-CANONICAL-DELIVERY-REMEDIATION

Attempt:
05

Decision:
IMPLEMENTED / CRITICAL_E2E_REACHABILITY_P1_REMEDIATED /
PENDING_INDEPENDENT_CLOSURE_REVIEW

This evidence does not close the finding or accept Phase5A.
```

## 1. Inherited closure history

```text
Remediation Attempt-02=BLOCKED
Remediation Attempt-03=IMPLEMENTED
Remediation Attempt-04=IMPLEMENTED

Closure Review Attempt-01=FAIL
Closure Review Attempt-02=FAIL
Closure Review Attempt-03=FAIL / P1_1

Current P1:
CRITICAL_E2E_JOB_LEVEL_REACHABILITY_BYPASS
```

The accepted gitleaks、provenance and supply-chain closures remain unchanged.

## 2. Root cause and exploit

Critical E2E was represented only by five spec strings in the workflow text. The two real execution steps were absent from the canonical capability ownership map. Moving both steps into a conditional non-required job preserved the strings while making execution unreachable; the validator returned PASS.

## 3. Canonical capability registry

The single registry now contains 18 admission-critical capabilities：

```text
supply-chain lock validation
gitleaks scan
backend artifact build
frontend production build
npm locked install
Playwright locked consumer
backend/frontend SBOM production
backend/frontend manifest production
provenance production
pre-upload admission
backend/frontend/provenance uploads
post-upload readback
loopback critical E2E execution
real-backend critical E2E execution
```

Each registry consumer must exist exactly once, remain step-level unconditional/fail-closed, and be owned by one of the nine required unconditional jobs.

## 4. Critical E2E execution binding

```text
Run loopback critical E2E allowlist:
  command=npm run test:e2e --
  specs=3
  owner=frontend-critical

Run real-backend critical E2E allowlist:
  command=npm run test:e2e --
  specs=2
  owner=frontend-critical

total bound specs=5
```

Spec text in comments、environment values or another optional job does not satisfy the execution contract.

## 5. Permanent mutation regression

New E2E mutations：

```text
conditional non-required relocation=REJECTED
unconditional non-required relocation=REJECTED
execution removed / spec text retained=REJECTED
```

The tests obtain `REQUIRED_JOB_IDS` from the validator's single source and parameterize：

```text
9/9 required job if:false=REJECTED
9/9 required job continue-on-error:true=REJECTED
```

All prior action、image、tool、consumer and provenance mutations remain rejected.

```text
MUTATIONS_REJECTED=40
```

## 6. Positive validation and invariants

```text
canonical workflow=PASS
critical capabilities ownership=18
critical E2E specs bound=5
required jobs unconditional=9
required checks=9
Actions=24/24 pinned
PostgreSQL images=3/3 digest pinned
frontend production build=1
Gitleaks regression=PASS
Provenance regression=PASS
NoSkipReporter=PASS
YAML parse=PASS
PowerShell parser errors=0
git diff --check=PASS
```

Production Java、migration、frontend product、research、deployment、LIVE与private exchange diff均为0。Full Maven=`NOT_REQUIRED / NOT_RUN`。

## 7. Candidate fingerprint

Attempt evidence files are excluded to avoid self-reference. The functional projection covers 22 candidate files as ordinal-sorted `path|sha256` records with LF separators and a trailing LF.

```text
before=82222c2f235a807bf665a2201e30688d9611bc9ab171170756e23e2029275fdb
after=fb7c160556f1e6827c498dc82e4367df3039223475decda41de2475db0c208f6
functional files=22
```

## 8. Severity, residuals and next action

```text
P0=0
P1 target=REMEDIATED_PENDING_INDEPENDENT_CLOSURE_REVIEW
P2 IMAGE_DIGEST_RUNTIME_PULL_PENDING_EXACT_HEAD_CI=UNCHANGED
P2 CRITICAL_E2E_ADMISSION_PENDING_FIXTURE_REPAIR=UNCHANGED
P3 Playwright generated-file cleanup=UNCHANGED
```

- Rollback：reverse only Attempt-05 changes to the canonical validator、its mutation tests and append-only current/evidence records. Preserve prior Attempt evidence and do not reset/rebase.
- Staged/commit/push=`0/NONE/NONE`。
- Next action：`NQ-GATEAUDIT-PHASE5A-CANONICAL-DELIVERY-CLOSURE-REVIEW / Attempt-04`。
- Final closure authority belongs to the independent reviewer.

<!-- nq-runtime-scan:historical-reference:end -->
