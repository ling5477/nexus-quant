# GateAUDIT logging sensitive-data protection implementation

## Candidate and scope

- Baseline head: `91f2b0b9da925803acd4534fc579b77fed563351`; tree: `ca3caa30bfdcb535f9b38f60a934824660dc1c21`.
- Worktree: clean isolated checkout `C:\nqlsp`, `core.autocrlf=false`, branch `codex/gateaudit-logging-sensitive-protection`.
- Historical [negative proof](GATEAUDIT_LOGGING_SENSITIVE_DATA_NEGATIVE_PROOF_AUDIT.md) remains unchanged: **13/13 leaked before remediation**. No real credential or production incident is inferred from synthetic canaries.
- Scope: shared application console rendering and reusable pure sanitization rule only. No file appender, new framework, call-site sweep, trading mutation, production access, or SQL audit.

## Implementation and result

`nq-observability` owns `SensitiveLogSanitizer`. The `nq-app` `logback-spring.xml` composes `SensitiveLogEncoder` into the root `ConsoleAppender`, retaining Boot's `CONSOLE_LOG_PATTERN` and UTF-8. The encoder sanitizes the complete `PatternLayoutEncoder` bytes after message formatting and automatic Throwable rendering, so ordinary SLF4J calls, causes and suppressed exception lines share the final protection boundary. An internal rendering/sanitization failure returns only `[LOG_REDACTION_FAILED]`; it never emits the original event or recursively logs the failure.

The original proof was copied into a permanent `SensitiveLoggingRedactionTest`, changed to require zero leaks, and extended for suppressed exceptions, stack frames and safe text. Its minimal Spring context uses the actual `prod` profile, actual root appender and actual encoder-rendered bytes. The sanitizer unit test covers assignment, header, query, JDBC URL, Map, DTO, quoted JSON, mixed case, escaped quote, multi-value Cookie, near-match and diagnostic boundaries. No test-only logging configuration or real secret is used.

Focused command from `backend`:

```text
mvn -pl nq-app -am '-Dtest=SensitiveLogSanitizerTest,SensitiveLoggingRedactionTest,SensitiveLogEncoderTest,TraceIdFilterTest' '-Dsurefire.failIfNoSpecifiedTests=false' test -q
```

Result: exit `0`; `NQ_LOGGING_NEGATIVE_CASES_TOTAL=13 LEAKED=0`. `SensitiveLogSanitizerTest` 3 passed, `SensitiveLogEncoderTest` 1 passed, `SensitiveLoggingRedactionTest` 1 passed, and existing `TraceIdFilterTest` 3 passed. The regression asserts `Throwable.message`, nested cause and suppressed message canaries absent while exception classes, cause hierarchy and stack frames remain. The fixed failure output test confirms raw fallback is absent. Safe assertions cover `trace_id`, `errorCode`, `errorKey`, `eventType`, `orderId`, `clientOrderId`, `strategyRunId`, `venue`, `symbol`, plus `tokenCount`, `passwordPolicy`, `secretary` unchanged (`SAFE_NEAR_MATCH_CASES=3`, `SAFE_NEAR_MATCH_REDACTED=0`).

Source scan of direct logger calls and credential/header/request/config terms found existing Binance/OKX masked fingerprint/URL logs and the `ApiExceptionHandler` exception logger. No call-site changes were required; the latter now passes through the shared encoder. Existing call-site masks remain defense in depth.

## Independent review

One focused independent `REVIEW_ONLY` review found two sanitizer boundary defects, then a Cookie multi-value defect during affected-rule recheck. All findings were fixed without changing the original proof or weakening assertions. Final affected-rule recheck: **PASS**, with independent probes for escaped quotes, multi-word values, multi-value Cookie, inline `Authorization` and safe near-matches; the reviewer reran focused tests and confirmed 13/0. Review start/end fingerprints matched: baseline HEAD/tree, empty tracked/index diff, stage `0`, and identical SHA-256 for all six then-untracked candidate files. Reviewer made no candidate, evidence or Git changes. `REVIEW_ACCEPTED / READY_TO_COMMIT`.

## Boundary and delivery

`CENTRAL_PROTECTION=true`; `NORMAL_LOGGER_BYPASS=false` for the inspected root console path; `THROWABLE_MESSAGE_LEAK=0`; `NESTED_CAUSE_LEAK=0`; `SUPPRESSED_EXCEPTION_LEAK=0`; `SANITIZER_FAILURE_RAW_FALLBACK=false`. `REAL_CREDENTIAL_READS=0`; `PRODUCTION_ACCESS=0`; `EXCHANGE_CALLS=0`; `TRADING_MUTATIONS=0`.

The implementation commit and exact-head CI are bound in the later authority sync after CI completes. Until then, these are local and independent-review results, not CI acceptance. GateAUDIT remains `IN_PROGRESS|NOT_FROZEN`; Phase7-E remains suspended. The SQL ownership and duplication audit remains a separate next task.
