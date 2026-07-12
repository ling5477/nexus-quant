# GateV Testing and CI Evidence Summary

验证日期：2026-07-12；基线 HEAD：`e08f18b1189225824228f10ca2f43194f5579002`。

## CI 与后端

- 当前 exact-HEAD CI：run `29189447582`，`completed / success`。
- `mvn -f backend/pom.xml test` 在长期本地 DB 因历史 V33 checksum drift fail-closed；未执行 repair、未改该 DB。
- 在 disposable PostgreSQL 16.14 fresh DB 上，以 CI 等价 no-outbound 配置重跑全量：23/23 modules `BUILD SUCCESS`；`nq-core` 239 tests；`nq-app` 133 tests、3 configured skips；0 failures/errors。
- V1..V33 Flyway migration、GateV repository/lock integration、repository smoke 与 Spring app-context PostgreSQL smoke 均通过。

## 前端

- `npm run build`：PASS；Vite production build 完成，保留既有 large-chunk warning。
- `validation-review-workbench-smoke.spec.ts`：4 passed。
- `strategy-validation-paper-shadow-smoke.spec.ts`：2 passed。
- Playwright 仅出现既有 Ant Design v5 / React 19 compatibility warning，测试 exit code 为 0。

## 治理

- manifest regression：PASS，覆盖 GateV pre-tag/post-tag 正负 fixtures 与 GateU preservation。
- GateV `-PreTag` strict checker：12 roles independent，warnings 0、errors 0，`PASS / GATE_ARCHIVE_PRETAG_VALID`。
- next-action regression 与 current authority checker：PASS；work batch 为 `IMPLEMENTED|PENDING_REVIEW / UNCOMMITTED / NOT_RUN`。
- doc link checker：76 checked、1 个既有 GateJ historical ledger warning、0 errors，PASS。

pre-tag PASS 只证明归档结构与当前未打 tag 状态一致，不等于 GateV frozen。
