# NQ V1 C3 product UX evidence

Baseline: `354c03415e2eb1fc40c289361d352301d2b555a5`, dev CI `37414767478`, 9/9 SUCCESS. Candidate: the code files in this delivery; final Git/CI identities are recorded in PR and current STATUS after CI succeeds.

## Canonical sample

Disposable PostgreSQL 16.15 at loopback port 55443, Flyway validate/migrate V58. Real Spring HTTP API at loopback 18889; real Chromium with Vite proxy. No browser API interception in the real smoke. Public hourly sample is a deterministic test fixture, not a new live market observation. Test-only account settings cause the production AccountTradingEnabledRule to reject; no RiskGate or writer is mocked. Schema is removed after the test.

```json
{
  "paperRunId": "ptr-ca5c8319-3799-4e46-9365-be46979ccfae",
  "canonicalAccountId": 9,
  "strategyVersionId": "sv-gz-00a8eab4",
  "decisionId": "5a10853cf6d5e388e8331deb526dab1aa714482439e8967e2b20a278aaf2e6f0",
  "noSignalRun": "ptr-d50f94f0-fd63-495f-99e7-66dd74fc5599",
  "rejectedRun": "ptr-672f23c1-4af6-4f38-920e-213c2db37195",
  "orderId": "ord-58dc7d63-88e3-4f14-95f6-980eab742e3b",
  "tradeId": "trd-aadddc7e-6796-4666-80d7-0c809a0ba11f",
  "exactValues": {
    "positionQuantity": "0.95040000",
    "markPrice": "105",
    "pnl": "-0.20444054",
    "cash": "0.00355946",
    "equity": "99.79555946"
  },
  "markAsOf": "2026-09-25T06:01:01Z",
  "ledgerAsOf": "2026-10-06T05:26:02.421+00:00",
  "positionAsOf": "2026-10-06T05:26:02.600+00:00"
}
```

continuousSimRunId uses the existing paperRunId; there is no separate continuous identity. Strategy run and input/dataset identity are displayed from persisted backend responses.

## Cross-page consistency

Strategy SIM is the canonical context embedded in Paper. Trading uses the same read component and paperRunId deep link. Browser compares complete canonical panel text across the two routes; Paper orders/trades/positions and Trading order-by-ID/trade API are additionally compared against /facts.

| Fact | Strategy SIM | Paper | Trading |
| --- | --- | --- | --- |
| Account | sample canonicalAccountId | exact match | exact match |
| Order / trade identity | sample IDs above | exact match | exact match |
| Symbol / side | BTC-USDT / BUY | exact match | exact match |
| Filled quantity | 0.95040000 | exact match | exact match |
| Execution price | 105.11 | exact match | exact match |
| Position | 0.95040000 BTC | exact match | exact match |
| Cash | 0.00355946 USDT | exact match | exact match |
| Equity | 99.79555946 USDT | exact match | exact match |
| PnL | -0.20444054 USDT | exact match | exact match |

Mark=105; markAsOf/ledgerAsOf/positionAsOf above are shown separately. /facts reads use one repeatable-read database snapshot, but historical event times differ. UI does not recalculate PnL.

NO_SIGNAL shows persisted decision reason and no order produced. RISK_REJECTED shows ACCOUNT_TRADING_DISABLED, linked decision/order and canonical risk event. FILLED shows Order/Trade/Ledger/Position and exact economic values. Paper RUNNING and Continuous STOPPED are separate persisted statuses: stopping continuous polling does not change Paper lifecycle.

## Precision, state and compatibility

BTC 0.00119354 previously displayed as 0.00 under default two decimals; exact display now preserves 0.00119354. Decimal text from the backend avoids binary Number rounding. Real zero, missing '-', UNKNOWN and NOT_AVAILABLE stay distinct. Missing mark time suppresses mark/equity/PnL display.

Run switches use query keys tied to paperRunId. Read failures hide cached current state and disable continuous controls. Reload restores the same persisted identity, cursor, block reason and STOPPED state. C1 process restart proof is not rerun as a new C3 qualification.

Scheduler page already exposes enabled, last start/finish/result/error, failures and next execution. Real browser verifies CONTINUOUS_SIM_POLL and PAPER_MATCHING. State smoke verifies disabled/error separately from run blockReason, mobile readability, run isolation, Legacy separation and success-to-503 behavior.

Legacy Research Paper retains its original defaults and read path, explicitly labeled; canonical contexts do not request legacy summary/risk/curves/replay/emergency-stop facts. No new page or second fact source.

## Verification

- `npm --prefix frontend run build`: 45 unit tests; tsc; Vite build PASS.
- `npm --prefix frontend run test:e2e -- product-ux-states.spec.ts --output=test-results/c3-states`: 1/1 PASS; runner support 10/10 PASS.
- Targeted Maven: StrategySimPostgresIntegrationTest methods productUxRealApiAndBrowserUseOneCanonicalRun, syntheticBudgetCreatesOneCanonicalFillAndLedgerWithoutPrivateProvider, continuousCursorWindowProjectsDurableRiskRejectionBeforeNextRun, continuousRestartSettlesFilledCursorWindowBeforeNextBarAndKeepsRunsIsolated, factsKeepLatestExecutionMarkWhenLaterSignalHasNoTradableBar; JdbcPaperRunCanonicalFactsRepositoryPostgresTest. Required isolated PG properties and `nq.c3.browser.required=true`; 6 tests, zero failures/errors/skips.
- Real API/browser smoke: 1/1 PASS, no mocked HTTP response; reload, filled/no-signal/risk and scheduler verified.
- `git diff --check` and stage semantic guard PASS.
- Independent REVIEW_ONLY review: final tracked diff fingerprint `d0a1fd85c8ee5b00dd845ffa500cf2682610d1dd`, start=end, stage=0. Two P2 cache state issues and one P3 obsolete lookup issue fixed; final unresolved P0/P1/P2/P3=0/0/0/0. Review does not claim to have rerun implementation tests.

Earlier fixture/locator attempts failed and were corrected; those attempts are not claimed as PASS. Existing Vite size/config and AntD deprecation warnings remain observations.

Production writer delta=0; migration delta=0. APIs add canonicalAccountId, exact decimal text, linked read facts and valuation timestamps; existing numeric fields and write paths remain unchanged. No LIVE/private provider call, production deployment or real funds action. Residual: query limits (500 orders/trades/risk/positions, 2000 ledger) are visible; unsupported target page filters use copyable identities. This evidence does not claim V1 or release readiness.
