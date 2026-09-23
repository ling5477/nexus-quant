# GateAUDIT logging sensitive-data negative proof audit

Result: `BLOCKED / LOGGING_SENSITIVE_DATA_PROTECTION_IMPLEMENTATION_GAP`. This is a synthetic local reproduction of the actual Spring Boot 3.5.10 / SLF4J / Logback production console rendering chain at baseline `c00291003cd0bb03688e054b3c38d7a6cd1c320b`, after blocker-materialization commit `3bfb2ce00bee3174f502983caaf76a6f153f3fdd` passed [exact-head NQ CI Baseline run 35813320683](https://github.com/ling5477/nexus-quant/actions/runs/35813320683), 9/9 jobs success. It is not evidence of a real production incident or exposure of a real credential.

## Logging ownership and protection

| Surface | Actual path | Classification |
| --- | --- | --- |
| Application sink | `nq-app` `application.yml` `logging.pattern.console` → Boot Logback root `ConsoleAppender` → `PatternLayoutEncoder` → stdout; `application-prod.yml` contains no logging override | `NO_PROTECTION` at final sink |
| File / journald | No application file appender, custom logback config or journald redactor in the inspected source; downstream stdout collection is outside this proof | `NOT_APPLICABLE` to app-level negative proof |
| HTTP trace | `TraceIdFilter` → `TraceIdContext` → MDC `trace_id`, cleared in `finally` | `SOURCE_DISCIPLINE_ONLY` for trace identity |
| Exception | `ApiExceptionHandler.handleException` calls `log.error("api_unhandled_exception path={} trace_id={}", path, traceId, ex)` | `NO_PROTECTION` for Throwable rendering |
| Exchange adapters | Binance WS and runtime config mask selected listen key/API key/signature values before logging; public marketdata redactor sanitizes selected call sites | `CALL_SITE_SANITIZATION` |
| Scheduler / reconciliation / recovery / third party | Ordinary SLF4J loggers share the root appender; no global filter/converter/encoder protection found | `SOURCE_DISCIPLINE_ONLY` or `NO_PROTECTION` depending on caller |

Source review covered production Java logger declarations/calls, MDC, `Authorization`, `Cookie`, `Bearer`, `apiKey`, `api_key`, `secret`, `passphrase`, `password`, `jdbc:`, `token`, `JWT`, `headers`, `request`, `response`, and `credential`; application profile logging config and logback files were searched. `CENTRAL_PROTECTION=false`, `CALL_SITE_ONLY_PROTECTION=true`, `NORMAL_LOGGER_BYPASS=true`. The ordinary `ApiExceptionHandler` logger and the synthetic logger call reach the same production root appender without a sanitizer wrapper. `ACTUAL_LEAK_PATH_FOUND` denotes the reachable raw exception logging path; no claim is made that a real exception containing a real credential has occurred.

## Actual rendered-output proof

The archived one-shot JUnit source is [GATEAUDIT_LOGGING_SENSITIVE_DATA_NEGATIVE_PROOF_REPRO.java.txt](GATEAUDIT_LOGGING_SENSITIVE_DATA_NEGATIVE_PROOF_REPRO.java.txt), SHA-256 `2f17c0aa9233ce682c123947079b0e00c1e5b26dc5a1cc1906844c5c5367e42b`. To reproduce in a disposable checkout at this candidate, copy it to `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/SensitiveLoggingNegativeProofTest.java` and run from `backend`:

```text
mvn -pl nq-app -am '-Dtest=SensitiveLoggingNegativeProofTest' '-Dsurefire.failIfNoSpecifiedTests=false' test -q
```

The test starts a minimal Spring context under exactly the `prod` profile, supplies only synthetic configuration values, asserts the production console pattern, locates Boot's actual root `ConsoleAppender`, and captures bytes *after* its `PatternLayoutEncoder`. It does not inspect raw `ILoggingEvent` or use a test-only sanitizer/filter/configuration. The first local attempt with no synthetic prod configuration was correctly rejected by `ProductionConfigurationApplicationContextInitializer`; that attempt has no negative-proof result. With synthetic configuration the focused test exited 0 and reported `NQ_LOGGING_NEGATIVE_CASES_TOTAL=13 LEAKED=13` (1 test, 0 failures/errors). Its assertion characterizes the current leak, not the desired security contract; the next implementation task must turn this into an absence assertion after remediation. No runtime source was modified.

| Case | Input shape | Rendered raw canary |
| --- | --- | --- |
| 1 | `Authorization: Bearer` | present |
| 2 | `Cookie: session=` | present |
| 3 | `apiKey=` formatted argument | present |
| 4 | `secret:` | present |
| 5 | mixed-case `PASSphrase=` | present |
| 6 | quoted JSON JWT | present |
| 7 | `password=` | present |
| 8 | JDBC URL query password | present |
| 9 | Java `Map` payload | present |
| 10 | DTO-like `toString()` | present |
| 11 | query-string token | present |
| 12 | `Throwable.message` | present |
| 13 | nested cause message | present |

Each of the first 11 uses normal formatted SLF4J arguments. The last two use `log.error(..., exception)` and assert on final console bytes. `trace_id=trace-safe-123`, `errorCode=NQ-TRD-1001`, `errorKey=ORDER_VERSION_CONFLICT`, `eventType=TradeExecuted`, `orderId`, `clientOrderId`, `strategyRunId`, `venue`, and `symbol` remained present in all relevant captured messages: `SAFE_TRACE_ID_PRESERVED=true`, `SAFE_ERROR_IDENTITY_PRESERVED=true`.

## Disposition

`NEGATIVE_CASES_TOTAL=13`, `NEGATIVE_CASES_LEAKED=13`, `IMPLEMENTATION_GAP_CONFIRMED=true`, `RUNTIME_IMPLEMENTATION_DELTA=0`, `TRACEABILITY_GAPS_BEFORE=2`, `TRACEABILITY_GAPS_AFTER=2`, `PHASE7_E_ELIGIBLE=false`. Minimal likely remediation boundary is the shared Logback output path, including rendered message and Throwable/cause, while retaining safe identifiers; a call-site-only sanitizer is bypassable. No logging runtime remediation was attempted. Severity remains `P0=0 / P1=0` for *confirmed current incidents/findings* because this local synthetic proof establishes a protection contract failure without evidence of real credential emission. The implementation task must assess actual severity from its own scope and evidence.

Current safety: `LIVE=DISABLED`, `KILL_SWITCH=ENGAGED`, `PRODUCTION_ACCESS=0`, `EXCHANGE_CALLS=0`, `REAL_CREDENTIAL_READS=0`. No real secret, production database, exchange endpoint, or provider was used. Phase7-E remains suspended; SQL ownership audit is not started.
