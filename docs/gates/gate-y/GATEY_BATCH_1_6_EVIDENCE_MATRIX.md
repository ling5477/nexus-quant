# GateY Batch 1–6 Evidence Matrix

| Batch / history cluster | Status | Commit / release relation | Tests / CI | Files / capability summary | Safety boundary / residual |
| --- | --- | --- | --- | --- | --- |
| GateY-1 | `ACCEPTED / CI GREEN` | `76ef325f7b8a3d3325df63af2cb1b979309bd141` | `31581317959` | LiveSession、approval、risk、intent/receipt schema/work-order contract | 无 runtime/交易 |
| GateY-2 | `ACCEPTED / CI GREEN` | `19ac2d1cdc7a1982f97fb0e1b0e62c081d003018` | `31608725854` | V39 control-plane facts、JDBC/PostgreSQL constraints | forward-only，无生产 pilot |
| GateY-3 | `ACCEPTED / CI GREEN` | `1f2ad2324166872a567a0420b71a8b4a5b68f7f1` | `31622259352` | fake execution、NO BLIND RETRY、UNKNOWN recovery、canonical legacy bridge | fake/no-egress |
| GateY-4 | `ACCEPTED / CI GREEN` | canonical `44ac9b3c014bcd7a46499c4180053742e64c7709`；acceptance `b3a6b1fd550d8ccb5132c7b16942a4b11b67f78e` | `31679311259` | scoped credential reference、read-only boundary、kill、stable handle、deployment contract | credential material不入库/文档 |
| GateY-5 | `ACCEPTED / CI GREEN` | implementation `8d594f1a...`；forward remediation `88f6f7f...` | failed CI `31727172181` preserved；green `31761584826` | isolated worker、rollback/restore、lock window、incident/replay | fake/synthetic/disposable only |
| GateY-6B | `ACCEPTED / CI GREEN / CONTRACT ONLY` | `990f8c5680c23d02dec059ca72e7355f88faa72e` | `31811302301` | typed OKX Spot LIMIT contract、query-first、default-deny | 无 real credential/network/mutation |
| GateY-6C | `ACCEPTED / CI GREEN` | `febf30ad...` + forward remediation `696963a7...` | failed `31892305007` preserved；green `31893000098` | credential correction、trusted bootstrap、READ/TRADE verified、WITHDRAW absent、IP matched | 唯一 read-only call；mutation=0 |
| GateY-6D | `ACCEPTED / CI GREEN` | `b56e68bdc45fd6a7f27e6e830447e995ff683bfb` | `31944962448` | V40 pilot scope、trusted observation authority、operator binding | PLACE=0；authorization fail-closed |
| GateY-6E | `ACCEPTED / CI GREEN` | `0708bd9d...` + forward remediation `c4b2668e...` | failed `31958446614` preserved；green `31997221424` | V41 prerequisite facts、real transport capability、non-web security | runtime unbound；pilot未授权 |
| GateY-6F preparation | BLOCKED / FAIL / remediation 全保留 | source evidence covers V43/V44/V45/V46, release reproducibility, server/bootstrap/operator authority/lease recovery | exact-head CI history retained | immutable release、deployment、exact binding、one-time authority、lease lineage | 任何 blocker 均无第二 PLACE |
| Minimal live Attempt-01 | `PASS / ACCEPTED` | production release `8e3dd0cf6104eb85f36a0e434ca51ea9d903705a`；manifest `d49ca03a...e046` | `32978280738` + final docs CI `32981327378` | exactly-one PLACE、query-only reconciliation、Trade/Ledger、final close | PLACE=1/retry=0/CANCEL=0；LIVE=false；kill engaged |
| GateY freeze archive | `PRETAG CANDIDATE` | freeze commit/tag pending | archive/authority/link/frozen/secret checks required | 16 role docs + 75 source evidence files | no real side effect；P2 externalOrderId residual |

## Evidence coverage

`source/task-evidence/**` 保存 72 份 attempt Markdown、README 与 2 份 sanitized manifest JSON。历史 BLOCKED、FAIL、security rejection、CI failure、retry、remediation、V43～V46、credential correction、trusted bootstrap、release reproducibility、non-web security、operator authority、lease recovery、canonical legacy bridge、最终真实订单与 reconciliation 均原文件名保留；freeze summary 不覆盖或删除中间历史。

API/UI/backend/DB/Python 口径：GateY 触达 Java backend、V39～V46、最小 API/UI 与 runtime/deploy；Python production/live execution 未启用，本 freeze 不修改 Python。全部 current capability 仍由代码与 current capability docs 决定，本 matrix 只保存 GateY 历史证据。

Credential/secret 边界：归档不包含 credential material、raw private response、签名、cookie 或完整 durable clientOrderId。无 transfer/withdraw；无第二 pilot；freeze readiness verdict 为 `PASS / PRETAG_ARCHIVE_CANDIDATE`。
