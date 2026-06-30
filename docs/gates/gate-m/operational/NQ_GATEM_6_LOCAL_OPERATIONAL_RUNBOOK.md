# NQ GateM-6 Local Operational Readiness Runbook

Task: NQ-GATEM-6E-LOCAL-OPERATIONAL-RUNBOOK

Status: PASS / DOCS ONLY / READY TO COMMIT

Date: 2026-06-30

## Purpose

This runbook documents a local GateM-6 operational readiness validation path only. It helps a developer verify that the local backend process, the operational readiness API, and the `/runtime/readiness` page can be checked safely on a workstation.

This runbook is not:

- a production deploy runbook;
- LIVE authorization;
- real exchange readiness;
- permission probe verification;
- AI or DH runtime integration;
- RealClient / real provider implementation evidence.

Actuator health, API reachability, and UI rendering are evidence for local operational inspection only. They do not authorize LIVE trading and do not prove real provider readiness.

## Preconditions

- Work from the repository root: `F:\project\nexus-quant`.
- Local PostgreSQL for the `local` Spring profile is available.
- Backend dependencies are already available locally or can be resolved through the normal Maven workflow.
- Frontend dependencies are already installed if the optional page smoke is run.
- LIVE remains `DISABLED`.
- AI remains `NOT STARTED`.
- DH runtime remains `NOT_INTEGRATED`.
- Real exchange adapter / RealClient / real provider remains `NOT_IMPLEMENTED`.
- Real permission probe remains unavailable / skipped.
- Do not read or print credential material, raw env, full config dumps, token values, cookie values, API keys, exchange secrets, generated password values, request signatures, private keys, passphrases, or mnemonic material.

## Backend Startup

Run from the repository root:

```powershell
mvn -f backend/pom.xml -pl nq-app -am spring-boot:run "-Dspring-boot.run.profiles=local"
```

Expected startup properties:

- Spring profile is `local`.
- Tomcat listens on `http://127.0.0.1:18888`.
- Local PostgreSQL is reachable.
- Flyway reports the local schema is up to date.
- Startup logs may include a Spring development generated password warning. Do not copy the generated password value into docs, reports, logs, screenshots, or tickets.

If port `18888` is already in use, stop the existing local backend only if it is your own known process. Do not kill an unknown process just to make the runbook pass.

## Health Check

Check process health:

```powershell
Invoke-RestMethod -Uri "http://127.0.0.1:18888/actuator/health" -TimeoutSec 5
```

Expected local result:

```text
status = UP
```

Boundary:

- `/actuator/health = UP` means the local process and reported dependencies are healthy.
- `/actuator/health = UP` does not mean runtime readiness.
- `/actuator/health = UP` does not mean LIVE authorization.
- `/actuator/health = UP` does not mean real provider readiness.

## Operational Readiness API Check

The operational readiness endpoint is:

```text
GET /api/runtime/operational-readiness
```

If authentication is required, log in first and pass the bearer value only in-memory. Do not print or persist the token value.

Example PowerShell pattern:

```powershell
$loginBody = @{ username = $env:E2E_USERNAME; password = $env:E2E_PASSWORD } | ConvertTo-Json
$login = Invoke-RestMethod `
  -Method Post `
  -Uri "http://127.0.0.1:18888/api/auth/login" `
  -ContentType "application/json" `
  -Body $loginBody `
  -TimeoutSec 10

$tokenType = if ($login.tokenType) { $login.tokenType } else { "Bearer" }
Invoke-RestMethod `
  -Method Get `
  -Uri "http://127.0.0.1:18888/api/runtime/operational-readiness" `
  -Headers @{ Authorization = "$tokenType $($login.accessToken)" } `
  -TimeoutSec 10
```

Expected API result:

- HTTP `200`.
- `liveStatus.status = DISABLED`.
- `aiStatus.status = NOT_STARTED`.
- `dhRuntimeStatus.status = NOT_INTEGRATED`.
- `realProviderStatus.status = NOT_IMPLEMENTED`.
- `credentialExposureStatus.status = NOT_EXPOSED`.
- `permissionProbeStatus.status = SKIPPED`.
- all operational status items keep `ready=false`.

The response is a safe summary. It must not contain raw env, full config dumps, credential material, generated password values, exchange secrets, token values, cookies, signatures, private keys, passphrases, mnemonic material, or provider payloads.

## Frontend Smoke

Start the frontend only if the local UI path needs to be inspected:

```powershell
Set-Location frontend
npm run test:e2e -- tests/e2e/runtime-operational-readiness-real-backend-smoke.spec.ts --project=chromium
```

Manual page path:

```text
/runtime/readiness
```

Expected UI result:

- `Operational Readiness` section is visible.
- Safe summary from `GET /api/runtime/operational-readiness` is displayed.
- LIVE appears as disabled / blocked.
- AI appears as not started.
- DH runtime appears as not integrated.
- Real provider appears as not implemented.
- Credential exposure appears as not exposed.
- Permission probe appears as skipped.
- The page remains fail-closed and does not display live-ready, verified, or LIVE authorization wording.

## Forbidden Actions Checklist

During this local validation, confirm these actions do not happen:

- no permission probe POST;
- no ingestion run-once;
- no order endpoint call;
- no cancel endpoint call;
- no transfer endpoint call;
- no withdraw endpoint call;
- no external exchange call;
- no credential output;
- no raw env output;
- no full config dump;
- no generated password value copied into docs or reports;
- no LIVE enablement;
- no AI runtime connection;
- no DH runtime connection;
- no RealClient / real provider implementation.

## Shutdown

Stop the backend process started for this runbook. If it is running in a foreground terminal, stop it with `Ctrl+C`.

Then verify the process is no longer reachable:

```powershell
try {
  Invoke-RestMethod -Uri "http://127.0.0.1:18888/actuator/health" -TimeoutSec 3
  "UNEXPECTED_HEALTH_REACHABLE"
} catch {
  "HEALTH_DOWN_OR_UNREACHABLE"
}
```

Expected post-shutdown result:

```text
HEALTH_DOWN_OR_UNREACHABLE
```

## Troubleshooting

### Backend port already in use

- Check whether `18888` is owned by a known local backend from this workspace.
- Do not stop an unknown process without confirming ownership.
- If the existing process is intentional, document that it was pre-existing and avoid claiming this runbook started and stopped it.

### PostgreSQL unavailable

- `/actuator/health` may fail or report non-UP.
- Start or repair the local PostgreSQL dependency for the `local` profile.
- Do not replace the real local backend check with route stubs when the task requires real backend validation.

### Authentication required

- `GET /api/runtime/operational-readiness` is under `/api/**` protection.
- Log in through the normal local auth endpoint.
- Keep bearer values in memory only.
- Do not print token values or paste them into documentation.

### Generated password warning

- Spring may print a development generated password warning in local startup logs.
- The warning itself is not a GateM readiness failure.
- The generated password value must not be copied into docs, reports, screenshots, issue text, or work logs.

## Completion Criteria

The local operational readiness check is complete only when all items below are true:

- backend local profile starts successfully;
- `/actuator/health = UP` before the smoke;
- authenticated `GET /api/runtime/operational-readiness` returns HTTP `200`;
- API safe summary reports LIVE disabled, AI not started, DH runtime not integrated, real provider not implemented, credential material not exposed, and permission probe skipped;
- `/runtime/readiness` displays the fail-closed Operational Readiness summary;
- no forbidden permission-probe, ingestion, order, cancel, transfer, withdraw, external exchange, or credential-output side effect is observed;
- backend is stopped after validation;
- post-shutdown `/actuator/health` is DOWN or unreachable.

If any item fails, record `BLOCKED / NEEDS FIX` for that local validation and do not broaden the runbook into production deployment, feature implementation, API changes, migration changes, or CI workflow changes.

## Rollback

This is a docs-only runbook. Rollback is:

```powershell
git restore --worktree -- docs/current/NQ_GATEM_6_LOCAL_OPERATIONAL_RUNBOOK.md docs/current/NQ_GATEM_6_OPERATIONAL_READINESS_PLAN.md docs/current/README.md docs/current/TESTING.md docs/current/WORKLOG.md docs/current/STATUS.md docs/current/ROADMAP.md
```

If the runbook file is untracked, delete that file after confirming the path is exactly:

```text
docs/current/NQ_GATEM_6_LOCAL_OPERATIONAL_RUNBOOK.md
```
