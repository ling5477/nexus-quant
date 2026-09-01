# GateAUDIT Phase5A Canonical Delivery Remediation Attempt-04 Evidence

<!-- nq-runtime-scan:historical-reference:start -->

```text
Evidence classification:
HISTORICAL_EXECUTION_EVIDENCE / NON_RUNTIME_AUTHORITY

Task:
NQ-GATEAUDIT-PHASE5A-CANONICAL-DELIVERY-REMEDIATION

Attempt:
04

Decision:
IMPLEMENTED / JOB_LEVEL_REACHABILITY_P1_REMEDIATED /
PENDING_INDEPENDENT_CLOSURE_REVIEW

This evidence does not close the finding or accept Phase5A.
```

## 1. Inherited closure status

```text
Closure Review Attempt-02:
FAIL / P0_0 / P1_1 / NOT_READY_TO_COMMIT

Blocking finding:
CANONICAL_VALIDATOR_JOB_LEVEL_REACHABILITY_BYPASS
```

Attempt-01/02/03 evidence and the accepted gitleaks absolute-path execution contract remain unchanged.

## 2. Root cause

The validator proved critical step existence, step-level reachability, fail-closed semantics and ordering, but did not prove that the owning required job itself was reachable. A mutation adding `if: ${{ false }}` to `frontend-critical` skipped npm、Playwright、CycloneDX、provenance admission and delivery uploads while the validator returned PASS.

## 3. Implementation

- A single ordered map now owns the nine canonical required job IDs and their check names.
- The validator extracts real job blocks below the workflow `jobs` mapping.
- Every required job must exist exactly once, retain its canonical check name, have no job-level `if`, and omit `continue-on-error` or set it strictly to `false`.
- Security-critical capabilities map from step to owning job; every owner must belong to the required-job set.
- Critical capabilities include lock validation、npm、Playwright、frontend/backend SBOM producers、provenance producer/admission、three delivery uploads and the canonical gitleaks scan.
- `.github/workflows/ci.yml` did not require modification in Attempt-04.

## 4. Permanent mutation regression

```text
frontend-critical job if:false=REJECTED
secret-scan job if:false=REJECTED
diff-check job continue-on-error:true=REJECTED
```

The previous nineteen workflow/consumer/provenance mutations remain REJECTED, including step-level provenance/lock conditional and soft-fail mutations、npm bypass、Playwright latest、Maven unpinned and gitleaks consumer removal.

## 5. Positive and invariant regression

```text
canonical workflow=PASS
required jobs unconditional=9
required checks=9
Actions=24/24 pinned
PostgreSQL images=3/3 digest pinned
frontend production build=1
critical E2E allowlist=5 specs
Gitleaks execution regression=PASS
Provenance content/tamper regression=PASS
NoSkipReporter contract=PASS
YAML parse=PASS
PowerShell parser errors=0
git diff --check=PASS
```

Production Java、Flyway migration、frontend product、research、deployment、LIVE与private exchange diff均为0。Full Maven=`NOT_REQUIRED / NOT_RUN`。

## 6. Candidate fingerprint

Evidence files containing fingerprint values are excluded to avoid self-reference. The functional projection covers the other 22 candidate files as ordinal-sorted `path|sha256` records separated by LF with a trailing LF.

```text
before=0f0ecec4e2598497b834699b9e4c3ad020f7f0633d9a3cd7c68c2ecf2e5b2c2a
after=82222c2f235a807bf665a2201e30688d9611bc9ab171170756e23e2029275fdb
functional files=22
```

## 7. Findings and residuals

```text
P0=0
P1 target=REMEDIATED_PENDING_INDEPENDENT_CLOSURE_REVIEW
P2 IMAGE_DIGEST_RUNTIME_PULL_PENDING_EXACT_HEAD_CI=UNCHANGED
P2 CRITICAL_E2E_ADMISSION_PENDING_FIXTURE_REPAIR=UNCHANGED
P3 Playwright generated-file cleanup=UNCHANGED
```

## 8. Failure evidence, rollback and next action

- First `git fetch origin --prune` failed before ref update because of a Schannel TLS handshake error; the identical read-only retry succeeded and baseline/ancestry verification passed.
- Rollback：reverse only Attempt-04 changes to `Test-CanonicalDeliveryWorkflow.ps1`、its mutation tests and append-only current/evidence records. Do not reset/rebase or modify prior Attempt evidence.
- Staged/commit/push=`0/NONE/NONE`。
- Next action：`NQ-GATEAUDIT-PHASE5A-CANONICAL-DELIVERY-CLOSURE-REVIEW / Attempt-03`。
- Final closure authority belongs to the independent reviewer.

<!-- nq-runtime-scan:historical-reference:end -->
