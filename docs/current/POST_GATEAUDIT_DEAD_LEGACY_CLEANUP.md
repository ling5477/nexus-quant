# Post-GateAUDIT dead legacy cleanup

Candidate identity: `refactor/post-gateaudit-repository-consolidation`, starting at `6b6a6881d472ce76246951528a3614867496ba46`. The [candidate matrix](POST_GATEAUDIT_DEAD_LEGACY_CANDIDATE_MATRIX.csv) records 261 classified candidates with paths, consumers, roles, disposition, and verification. Its counts are candidate rows, not distinct deleted files: one deleted component also had an export candidate, and some deleted declarations also appeared in the export scan.

## Disposition

| Measure | Result |
| --- | ---: |
| Java production objects removed | 10 |
| Obsolete Java compatibility objects removed (included above) | 2 |
| Superseded test prototypes removed | 0 |
| Stale current scripts removed | 0 |
| Stale current configuration removed | 0 |
| Frontend source files removed | 2 |
| Frontend barrel exports removed | 17 |
| Frontend owner-file exports eliminated (some by declaration/file deletion) | 105 |
| Frontend unused declarations removed | 6 |
| Placeholder locale keys removed | 8 |
| Python objects removed | 0 |
| Candidate rows: KEEP_ACTIVE_RUNTIME / COMPATIBILITY / SAFETY_FALLBACK | 73 / 7 / 7 |
| Candidate rows: KEEP_FORMAL_QUALIFICATION_SUPPORT / ACTIVE_TEST_SUPPORT / ACTIVE_TOOLING | 3 / 15 / 7 |
| Candidate rows: KEEP_HISTORICAL_IDENTITY / INTENTIONAL_REFERENCE_IMPLEMENTATION / FALSE_POSITIVE | 1 / 1 / 0 |
| Candidate rows without an allowed final disposition | 0 |

Deleted Java objects: `LocalAuthService`, `LocalUserAccount`, `AppException`, `ErrorCode`, `OrderAggregate`, `OrdinaryPlaceAuthorityState`, `NoopRiskGate`, `LedgerService`, `NoopLedgerService`, `LedgerEntry`. The local/test `NoopLedgerService` factory was removed from `LocalTestFallbackConfiguration`; the corresponding architecture test branch was removed. These classes have zero remaining main/test token consumers in the candidate source, and no observed Spring, profile, configuration, reflection, service-loader, or script consumer. `LocalAuthService` was superseded by `DbAuthService`; the ledger compatibility interface and noop had no remaining injection point. The matrix records the per-object proof and canonical replacement where applicable.

Deleted frontend files: `PaperTradingPlaceholderPage.tsx` and `ListPageShell.tsx`. The former had no active route or import and was replaced by current paper-trading pages. Its eight exclusive locale keys were removed. The remaining frontend edits remove unconsumed module exports or unused declarations without changing rendered implementation. Source and test symbol scans, `noUnusedLocals`, unit tests, TypeScript, and Vite build cover these edits; the matrix gives each candidate.

`NoopAccountAdapter`, `NoopMarketDataAdapter`, `DisabledPublicMarketDataOutboundClient`, `OkxBootstrapFallbackFactory`, `LocalTestFallbackConfiguration`, and `PaperTradingAdapter` retain local/test or no-outbound safety contracts. `CanonicalLegacyAccountBridgeService` remains consumed by minimal LIVE pilot controls. Fake dry-run and disposable fake venue objects retain diagnostic or qualification roles. The 15 strategy-release prototype files remain because their independent assertion coverage is not proven to be fully subsumed; seven are executable `*Test` classes and eight are fixtures. The matrix records their proposed production/test counterparts and preserves unknown unique assertion counts rather than treating them as zero. The research CLI compatibility launcher, active scripts, Spring profile files, and optional design-token stylesheet are retained for their recorded contracts.

## Verification and boundaries

The full Maven reactor ran against a disposable PostgreSQL fixture with Flyway V1–V51 and finished `BUILD SUCCESS`, with zero failures and errors. The frontend unit, TypeScript, and Vite build completed successfully. Current stage-asset and stage-semantic guards returned zero errors. `check-doc-links.ps1` returned zero errors with 123 warnings in retained historical current logs. Final exact-head CI identity is recorded in the task delivery, after push and workflow dispatch.

No schema, Flyway, SQL, transaction, API, trading, LIVE, credential, or Python source was changed. Historical evidence, qualification records, the freeze tag, old worktrees, and their local assets were left untouched. These are source-diff boundaries; exact-head CI supplies the final integration check.
