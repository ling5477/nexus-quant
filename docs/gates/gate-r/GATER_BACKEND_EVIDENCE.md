# GateR Backend Evidence Index

## Backend scope

- API / service / domain / repository / test 的冻结证据。

## Runner and orchestration

- 本地运行器：`backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/application/shadowrun/ShadowRunRunnerService.java`
- 状态机：`backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/domain/shadowrun/ShadowRunStateMachine.java`
- runner 并未作为 scheduler 或 background job 运行，属于 diagnostic local skeleton。

## Decision trace / consistency

- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/application/shadowrun/ShadowRunRunnerStep.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/application/shadowrun/ShadowRunRunnerResult.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/application/shadowrun/ShadowRunConsistencyReportService.java`

## Read-only API and DTO

- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/strategy/api/web/ShadowRunReadOnlyController.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/strategy/api/web/ShadowRunListResponse.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/strategy/api/web/ShadowRunDetailResponse.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/strategy/api/web/ShadowRunEventResponse.java`

## API tests

- `backend/nq-api/src/test/java/com/guidinglight/nexusquant/strategy/api/web/ShadowRunReadOnlyControllerTest.java`
- `backend/nq-api/src/test/java/com/guidinglight/nexusquant/strategy/api/web/ShadowRunReadOnlyResponseTest.java`
- `backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategy/application/shadowrun/ShadowRunReadOnlyQueryServiceTest.java`

## Read-only / no-side-effect closure

- 控制器仅 `GET`。
- 无 `POST /placeOrder`、`/cancelOrder`、`/execute`、`/stop`、`/rerun`、`/approve`。
- 证据文件中未引入新的 migration、CI workflow 或真实交易所 runtime。
