# GateX Known Limitations and Residuals

## P0 / P1

- P0：0。
- P1：0。GateX final independent review 的产品 P1 已关闭，`ADMISSION_MATERIALIZATION_FACT_TEAR=CLOSED`。

## P2 residuals

### `PRODUCTION_LOCK_WINDOW_NOT_MEASURED`

GateX migrations 已通过 PostgreSQL/Flyway regression，但尚未按真实生产表规模测量锁等待、长事务与部署窗口。未来部署前必须执行 sizing、设置 lock/statement timeout、定义停止条件和 rollback；不得用本 freeze tag 直接推导安全生产窗口。

### `FILESYSTEM_STABLE_HANDLE_LIMITATION_INHERITED`

server-controlled locator、trusted-root containment、identity snapshot、manifest/digest 和 revision guard 已显著缩小风险并 fail closed，但当前实现不承诺跨全部 OS/filesystem 的原子稳定句柄。未来 runner/precheck 消费 artifact 前必须重新验证，不能复用过期验证结果。

## P3

既有 Mockito/SLF4J、Vite chunk 与 Ant Design React compatibility warning 属非阻断工具链提示；它们未由 freeze archive 引入，也不改变 non-LIVE correctness。

## Deployment boundary

两个 P2 进入 GateX boundary/deployment residual，不作为 non-LIVE freeze blocker。它们不允许降低校验、跳过 trusted root、扩大 runtime、开启 Shadow trading/LIVE 或访问 credential。

Freeze 后问题只能通过 forward remediation 处理；annotated tag 一旦推送，禁止删除、移动、覆盖或 force update。
