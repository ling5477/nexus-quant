# GateK CI Security Final Freeze Spec v1

任务：NQ-GATEK-CI-SECURITY-FINAL-FREEZE-SPEC
日期：2026-06-28
任务类型：CI_SECURITY_SPECIFICATION + FINAL_FROZEN_CONTRACT + LOG_REDACTION_STANDARD + ARTIFACT_POLICY + BACKEND_ECHO_RULES + SELF_REVIEW
状态：NQ-GATEK-CI-SECURITY-FINAL-FREEZE-SPEC：READY

本文件定义 GateK CI Security Final Freeze 的 deterministic contract。它不是新的 workflow / backend / frontend 实现，也不把任何当前 pending first-run / re-run 状态直接提升为 accepted。后续 `NQ-GATEK-CI-SECURITY-FREEZE-DECISION` 必须用本文件作为唯一裁决标准。

## 1. Scope

本 spec 适用于：

- Batch 4C security artifact / log redaction freeze and re-freeze review。
- Batch 4C-C log redaction proof guard first-run / re-run / freeze decision。
- Batch 5 CI acceptance，包括 frontend E2E backend smoke 的 log / artifact / stdout 判定。
- 后续 security regression gate、workflow 变更 review、artifact upload review、backend stdout hygiene review。

本 spec 不执行：

- 不修改 `.github/workflows/ci.yml`。
- 不修改 backend / frontend / research / scripts / deploy。
- 不新增 migration。
- 不读取 credential、`.env`、key、pem、token、secret。
- 不启用 LIVE，不接 AI，不接 DH runtime，不访问真实交易所。
- 不把 Batch 5 frontend E2E hardening 作为本轮启动项。

## 2. Decision Surfaces

以下 surface 均受本 spec 约束：

| Surface | 判定规则 |
| --- | --- |
| CI stdout / stderr | 最高优先级判定面；任何 forbidden log shape 出现即 violation。 |
| backend stdout / stderr captured by CI | 等同 CI stdout；不能用 "这是测试输出" 规避。 |
| uploaded artifacts | 必须先过 redaction gate；raw / binary sensitive artifact 不得上传。 |
| generated reports | 若被上传或打印，按 artifact / stdout 规则处理。 |
| workflow step controlled output | proof step 必须遵守严格输出契约。 |

Policy-source exception：本文件作为规范源会列出 forbidden marker literals。静态文档中出现这些 marker 仅用于定义规则，不构成 runtime leak；但 CI 不得 `cat` / replay 本文件内容到 job log，否则仍按 CI stdout violation 判定。

## 3. Definition of Secret Leakage

Secret Leakage 是 P0 级事件。以下任一情况 MUST BLOCK：

- real secret value 出现在 CI stdout / backend stdout / artifact / report。
- API key raw value、API secret raw value、passphrase raw value、token raw value、signature raw value。
- private key content，包括 PEM / OpenSSH / PGP private key body。
- authorization header raw value。
- cookie raw value。
- credential plaintext，包括 decrypted credential、raw credential、keystore、mnemonic、seed phrase。
- real exchange endpoint credentials，包括 OKX / Binance / 其他交易所真实 credential material。
- production environment credential、cloud token、GitHub token、OpenAI / Anthropic style API key。
- raw request / response dump 中包含任一 credential material。

Deterministic rule：

- 是否真实值以 value 的 authority 和可用性判断：能认证外部服务、生产环境、真实账户或真实 provider 的值为 real secret。
- CI-only disposable placeholder、fake fixture、平台 `***` mask、local-only generated value 不是 Secret Leakage，但仍必须通过 Forbidden Log Shape 规则；若使用 forbidden shape 输出，仍为 violation。
- 不允许以 "值已 masked" 作为豁免理由保留 forbidden key-shape。例如 `apiKey=***` 仍是 forbidden log shape。

## 4. Allowed Log Shape

以下 log shape 允许出现在 CI stdout / backend stdout / sanitized artifact，前提是它们不包含 forbidden marker、不包含 real secret value、不包含 raw request / response dump。

| Allowed shape | 条件 |
| --- | --- |
| `credentialKeyFingerprint` | 只表示不可逆 fingerprint；不得泄露 raw key 的 prefix / suffix / body。 |
| `credentialFingerprint` | 同上；建议作为 credential identifier 的 neutral label。 |
| `credential fingerprint` / `credential fp` | 文本日志可用；不得携带 raw credential label。 |
| masked credential metadata | 只能包含 provider、environment、account id、credential id、fingerprint、status；不得包含 secret-like key label。 |
| status only logs | 例如 job status、guard pass / fail、test result summary、execution result summary。 |
| rule/file based failure output | 只允许 rule id 与 repo-relative file path；不允许 line / value / snippet。 |
| synthetic proof logs | 仅用于验证 redaction guard 的 clean / failure path；不得 replay matched content。 |
| non-sensitive test class names | 允许普通测试类名；若类名包含 forbidden marker literal，则在 CI stdout 中按 violation 处理。 |

Allowed examples：

```text
credentialKeyFingerprint=sha256:ab12...
credentialFingerprint present
PROOF_OK
REDACTION_HIT rule=credential-key-shape file=backend/example/Test.java
RawBodySuppressionTest
```

## 5. Forbidden Log Shape

Forbidden Log Shape 是形状规则，不要求出现真实 secret value。只要出现在 CI stdout / backend stdout / uploaded text artifact / generated report，即判定为 violation。

Minimum forbidden markers：

| Rule id | Forbidden marker |
| --- | --- |
| credential-api-key-camel | `apiKey=` |
| credential-api-key-snake | `api_key=` |
| credential-secret | `secret=` |
| credential-token | `token=` |
| credential-signature | `signature=` |
| credential-passphrase | `passphrase=` |
| http-authorization-header | `Authorization:` |
| http-cookie-header | `Cookie:` |
| private-key-marker | `PRIVATE KEY` |
| raw-payload | `rawPayload` |
| raw-request-camel | `rawRequest` |
| raw-response-snake | `raw_response` |
| raw-response-camel | `rawResponse` |
| raw-credential | `rawCredential` |

Normalization rules：

- Credential assignment labels are case-insensitive for enforcement.
- Separator variants are forbidden when they preserve the same meaning, including `_`, `-`, camelCase and direct `=`, `:` assignment forms.
- `secret-scan` as a job name is not forbidden because it is not `secret=` and does not carry credential assignment shape.
- `credentialKeyFingerprint` is allowed because it uses a neutral fingerprint label and does not expose the raw key shape.
- `Authorization:` and `Cookie:` are forbidden even when the value is `***` or empty, because the header shape itself invites raw header echo.
- `PRIVATE KEY` is forbidden in CI stdout and artifacts, including fake PEM headers, unless the output is a static policy-source file that is not printed by CI.

Decision examples：

| Output | Decision | Reason |
| --- | --- | --- |
| `apiKey=***` | Violation | Forbidden shape even without raw value。 |
| `credentialKeyFingerprint=sha256:...` | Allowed | Neutral fingerprint label。 |
| `Authorization: ***` | Violation | Header shape is forbidden。 |
| `RawBodySuppressionTest` | Allowed | Non-sensitive class name。 |
| `rawPayloadSuppressionTest` | Violation if printed | Contains forbidden marker literal。 |
| `secret-scan job success` | Allowed | Does not contain `secret=` assignment。 |

## 6. CI Proof Contract

The CI proof step is a deterministic evidence producer. It must prove both the clean path and failure path without echoing sensitive material.

Clean path controlled output MUST be exactly:

```text
PROOF_OK
```

Failure path controlled output MUST be exactly:

```text
REDACTION_HIT rule=<rule> file=<file>
```

`<rule>` requirements：

- Safe rule id only, for example `credential-api-key-camel` or `raw-payload`。
- Must not be a full regex dump。
- Must not include matched text, matched value, full pattern, credential name, header value, or secret-like substring。

`<file>` requirements：

- Repo-relative file path or safe generated-file name。
- Must not include line number, column number, matched line, snippet, URL query, credential value, or temp path containing sensitive data。

Proof step MUST：

- fail closed on any forbidden marker hit。
- fail closed if scanner setup, pattern construction, file enumeration, or cleanup fails。
- run before any related artifact upload。
- use runtime pattern assembly when needed so the workflow log does not echo the complete forbidden pattern list as executable output。
- keep synthetic sensitive test content in temporary files under runner temp and delete it before step exit where practical。

Proof step MUST NOT：

- output matched value。
- output matched line。
- echo secret。
- dump regex。
- replay scanned log。
- print full CI log。
- upload raw proof input files。
- use `continue-on-error` or any soft-fail mode for security proof。

## 7. Artifact Policy

Artifact policy is fail-closed. Any artifact upload path that can contain CI logs, backend stdout, HTTP payload, test report, browser output, or generated report must have a pre-upload redaction gate.

Allowed artifact classes：

| Artifact | Conditions |
| --- | --- |
| sanitized logs | Generated after redaction; no forbidden marker; no raw secret; bounded retention。 |
| CI metadata JSON | Only run id, job id, commit, branch, status, timings, safe file names。 |
| test summary text | Counts and safe test names only; no raw stdout dump, no request / response body。 |
| redaction summary | rule/file/status only; no matched line / matched value。 |

Forbidden artifact classes：

| Artifact | Rule |
| --- | --- |
| Playwright `trace.zip` | Forbidden by default。 |
| screenshots | Forbidden by default for CI security freeze unless separately sanitized and approved。 |
| video recordings | Forbidden by default。 |
| Playwright HTML report / binary report | Forbidden if it may contain request, response, console, storage, screenshot, trace, or payload。 |
| raw backend logs | Forbidden。 |
| full HTTP dump | Forbidden。 |
| raw request / response capture | Forbidden。 |
| binary reports containing sensitive payload | Forbidden even if the value is not easily searchable。 |
| raw gitleaks / scanner reports with values | Forbidden。 |

Upload ordering rules：

- Redaction gate must run before upload.
- Redaction gate failure must prevent upload.
- `if: always()` may only upload already-sanitized metadata or summaries; it must not upload raw logs, traces, screenshots, videos, HTTP dumps, or scanner inputs.
- If log visibility is unavailable, the review must be `BLOCKED` or `CONDITIONAL PASS`; it cannot be `FIRST GREEN CONFIRMED` or `FROZEN / ACCEPTED`.

## 8. Backend Stdout Rules

Backend stdout captured by CI is part of CI stdout. It must be judged by the same forbidden-shape and secret-leakage rules.

Backend stdout allowed：

- status logs。
- startup / shutdown status without credential material。
- execution result summaries。
- guard result summaries。
- masked credential metadata using neutral labels。
- fingerprint metadata using neutral labels, for example `credentialKeyFingerprint` or `credentialFingerprint`。
- no-real / disabled state summaries, for example `REAL_EXCHANGE_PROBE_DISABLED` or `SKIPPED`。

Backend stdout forbidden：

- credential-shaped output。
- `apiKey=` style logs。
- `secret=` / `token=` / `signature=` / `passphrase=` assignment labels。
- `Authorization:` / `Cookie:` header echo。
- private key marker or private key body。
- raw request dump。
- raw response dump。
- raw payload / raw credential dump。
- endpoint URL containing credential query params。
- full serialized DTO / JSON when it contains forbidden marker or credential material。

API contract rule：

- DTO field names are not automatically stdout. Existing API contracts must not be changed solely because a DTO field has a sensitive name.
- If a backend test, smoke test, controller log, exception, assertion failure, or report prints a DTO / JSON containing forbidden marker, the output is a violation.
- Contract-preserving remediation should first change log labels / summary formatting, not credential storage, encryption, decryption, schema, or business semantics.

Fingerprint rule：

- Fingerprint calculation may remain unchanged when only the log label is unsafe.
- Fingerprint output must be non-reversible and must not include raw secret prefix, raw secret suffix, plaintext credential, or provider auth header.
- Labels must be neutral; `credentialKeyFingerprint` is allowed, `apiKeyFingerprint` is not allowed in CI stdout because it preserves the credential key-shape.

## 9. Batch Applicability

| Batch / gate | Applicability |
| --- | --- |
| Batch 4C | Artifact / log redaction freeze must satisfy this spec before `FROZEN / ACCEPTED`。 |
| Batch 4C-C | Proof step first-run / re-run / freeze must satisfy this spec; green workflow alone is insufficient if log shape violates。 |
| Batch 5 CI acceptance | Frontend E2E, backend smoke, health artifacts, logs, screenshots, traces and reports must satisfy this spec。 |
| Security regression gate | Any future workflow / backend stdout / artifact path touching credential or raw payload shape must use this spec as blocking policy。 |
| Existing historical evidence | Historical accepted evidence remains historical; any new freeze decision after this spec must be judged against v1。 |

## 10. Enforcement Rules

1. Security surfaces are judged before functional success. A green workflow with forbidden CI stdout is not security accepted.
2. Raw secret value is P0 and MUST BLOCK.
3. Forbidden shape in CI stdout / backend stdout / uploaded text artifact is at least P1 and MUST BLOCK freeze.
4. Proof clean path must emit `PROOF_OK`; any other controlled proof success output is non-compliant.
5. Proof failure path must emit only `REDACTION_HIT rule=<rule> file=<file>`.
6. Matched line, matched value, regex dump, log replay and raw proof input upload are prohibited.
7. Artifact upload must be after redaction gate; upload ordering bypass is a blocker.
8. Default CI must not reference repository `secrets.*` for these security proof paths.
9. Default CI must not enable LIVE or real provider flags, including `NQ_LIVE_ENABLED=true`.
10. Default CI must not print real OKX / Binance URLs or credential-bearing exchange endpoint URLs.
11. Batch 5 frontend E2E hardening must not be considered started or accepted by this spec alone.
12. A review with unavailable logs / artifacts cannot claim final green; it must record `BLOCKED`, `CONDITIONAL PASS`, or the exact visibility limitation.

## 11. Failure Classification Matrix

| Classification | Trigger | Freeze effect | Required next action |
| --- | --- | --- | --- |
| P0 Secret Leakage | Real secret value, raw token, private key content, raw auth header, raw cookie, credential plaintext。 | FAIL; immediate blocker。 | Remove leak source, rotate if real, re-run CI, repeat review。 |
| P0 Real Boundary Breach | LIVE enabled, real provider enabled, real exchange credential or credential-bearing real endpoint in default CI。 | FAIL; immediate blocker。 | Disable path, prove no-real boundary, re-run CI。 |
| P1 Forbidden Log Shape | Forbidden marker appears in CI stdout / backend stdout / artifact without confirmed raw secret。 | FAIL for freeze; no secret rotation unless value is real。 | Change label / output shape; re-run proof。 |
| P1 Proof Contract Failure | Missing `PROOF_OK`, wrong failure output, matched line/value printed, regex dump, log replay, proof soft-fail。 | FAIL for freeze。 | Harden proof step; re-run first-run review。 |
| P1 Artifact Upload Ordering Failure | Upload occurs before redaction gate, or raw logs/traces/screenshots/videos/reports are uploaded。 | FAIL for freeze。 | Move gate before upload or remove artifact; re-run CI。 |
| P1 Backend Stdout Violation | Production or test backend stdout prints credential-shaped label, raw request / response, raw credential shape。 | FAIL for freeze。 | Change log / summary label only when business logic is unaffected; re-run tests/CI。 |
| P2 Non-sensitive Hygiene Residual | Disposable CI placeholder or local-only generated value appears without forbidden marker and without external authority。 | Not P0; may block only if final decision requires zero residual。 | Document residual or mask in follow-up。 |
| P2 Review Visibility Limitation | GitHub logs/artifacts unavailable, but metadata is green。 | Cannot be `FROZEN / ACCEPTED`; at most `CONDITIONAL PASS`。 | Obtain logs/artifact evidence or rerun with accessible evidence。 |
| P3 Documentation Drift | Status wording is stale but no runtime / CI security violation。 | Does not block security if clearly historical; blocks docs freeze if current status is wrong。 | Fix current-state wording in allowed docs scope。 |
| Environment Failure | Dependency install, GitHub runner, Maven/npm/pytest infra failure unrelated to security proof。 | Not security accepted; classify separately。 | Re-run or fix environment; do not claim green。 |
| Unrelated Existing CI Failure | Non-security job fails while proof is compliant。 | Overall workflow not green; security proof may be conditionally valid only for its own job。 | Fix unrelated job before final freeze。 |

## 12. Deterministic Review Checklist

Reviewers must answer every item with PASS / FAIL / BLOCKED:

- CI run belongs to the reviewed commit.
- Workflow overall is green.
- `secret-scan` job is green.
- `Verify CI log redaction proof` step exists and executed.
- Clean proof output contains `PROOF_OK`.
- Failure proof semantics are `REDACTION_HIT rule=<rule> file=<file>`.
- No matched value output.
- No matched line output.
- No regex dump.
- No log replay.
- No forbidden log shape in CI stdout.
- No forbidden backend stdout shape.
- No raw backend logs uploaded.
- No Playwright trace / screenshot / video / report binary artifact uploaded.
- Artifact upload happens only after redaction gate.
- No repository `secrets.*` reference in default CI security proof path.
- No `NQ_LIVE_ENABLED=true`.
- No real OKX / Binance URL or credential-bearing exchange endpoint URL in default CI logs/artifacts.
- No Batch 5 frontend E2E hardening accidentally started by a Batch 4C decision.

## 13. Self Review

This spec resolves the disputed gray zones as follows:

| Dispute | Final rule |
| --- | --- |
| "log shape without value" | Forbidden marker in CI stdout is violation even with no real value。 |
| "fingerprint label" | Neutral fingerprint labels are allowed; raw credential-shaped fingerprint labels are forbidden。 |
| "test class name" | Non-sensitive class names are allowed; class names containing forbidden marker literals are violations when printed in CI stdout。 |
| "artifact leak" | Raw / binary browser or backend artifacts are forbidden unless separately sanitized and approved; metadata and summaries are allowed。 |
| "backend stdout" | Backend stdout is CI stdout; production log labels must satisfy this spec。 |
| "workflow green but policy fail" | Security freeze fails; functional green does not override log / artifact policy。 |

## 14. Acceptance and Next Action

This spec is READY as the proposed v1 freeze contract. It becomes ACCEPTED only after the dedicated decision task records acceptance.

Next task:

```text
NQ-GATEK-CI-SECURITY-FREEZE-DECISION
```

Commit recommendation if this spec is committed:

```text
docs(gatek): finalize CI security freeze specification v1
```
