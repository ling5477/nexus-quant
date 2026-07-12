# GateV-1 至 GateV-4 Evidence Matrix

以下 SHA 均以 `git cat-file -t` 验证为 `commit`；implementation 与 acceptance 不同的批次均以 `git merge-base --is-ancestor` 验证 exit `0`。CI 由 `gh run view <run-id> --json status,conclusion,headSha,name` 直接核验，均为 `NQ CI Baseline / completed / success`，且 `headSha` 等于对应 acceptance head。

| Batch | 已接受范围 | Implementation | Acceptance head | CI run |
| --- | --- | --- | --- | --- |
| GateV-1 | migration、fact model、repository、state machine | `f7d71d5a80241ade049a83fa3f90b3ac6ce46806` | `b3dd5f74f154d5ed9e2343bc18e451f48770814f` | `29144345430` |
| GateV-2 | lifecycle API、RBAC、owner scope、idempotency、audit | `99158738ec980f519637af8df75e4153dfa2869f` | 同 implementation | `29150549978` |
| GateV-3A | PostgreSQL advisory scheduler lock | `45c7df9799c0534ddd3ee291dc9347076dec9ddd` | 同 implementation | `29152330658` |
| GateV-3 | controlled read-only scheduler | `6cbceba9d0fbc0fca67f43e898c416ec64a6fa33` | `b209c416e0daf402216140b62785726f5fd116b6` | `29155396719` |
| GateV-4 | frontend review workbench 与 Playwright | `d7da91a662be1f0fc0bbf64df70ea57318773697` | `fad9b20900b49fbb918288f8d32d09fc60976444` | `29181214506` |

上述证据只接受各批次已实现范围，不将 GateV 整体提升为 frozen/tagged。当前 closeout 工作仍是未提交文档实现，等待独立 review 与后续 exact-HEAD CI。
