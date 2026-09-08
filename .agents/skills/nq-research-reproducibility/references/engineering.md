# Research 工程边界

Notebook 适合编排、可视化与实验；需要复用和测试的逻辑提取到已有工程的正式模块，明确输入输出，避免隐式全局状态。

保留数据集、时钟、时区、seed 与版本信息，验证输入顺序和浮点容差。进入生产边界前验证错误处理、配置、资源回收和外部接口契约。

依赖声明和 lockfile 使用项目已有工具维护；不强制 uv、Poetry、Ruff、mypy、Pydantic 或 async。类型、lint、打包、CLI smoke 只在已配置且受影响时执行。

处理大数据时按实际规模判断分批、流式处理、复制和内存；外调需要超时，重试须有界且副作用安全。脚本写入应有适当 dry-run 和重跑保护，无需为无副作用脚本构造完整框架。

## 可维护 Python 与 operational helper 的按需细则

普通 package/helper 任务可直接读取以下命中小节，不因文件类型调用 research Skill；实验可复现性或研究逻辑正式复用才触发原有 Skill。

- 结构：domain logic 与 HTTP/DB/filesystem/subprocess adapter 分离；不制造 god module、utils dumping ground、circular import、隐式全局状态或 import-time 副作用。保留公开 API 与既有 layout，新增抽象/依赖须有明确价值。
- Typing/model：沿用项目支持的 typing；Iterable/Sequence/Mapping 表达消费契约，dataclass 表达 value object，TypedDict 约束 JSON，Protocol 表达 adapter 边界。仅在实际 runtime validation 需要时引入模型框架，不机械强制继承或 Pydantic。
- 错误/资源：捕获最窄异常并保留 traceback 和上下文；禁止 bare except、吞错。仅在 process boundary 统一映射日志/状态/exit code。with/async with 管理连接、事务、文件、临时文件和锁；subprocess/thread/process/task 在失败、timeout、cancellation 后均回收。
- 状态/并发：检查 mutable default、class-level mutable state、late-binding closure、iterator exhaustion、single-use generator、shadowed builtins、thread safety、multiprocessing race。异步函数不隐式执行阻塞 I/O/重 CPU/sleep；并发、queue、fan-out 有界且具备背压和取消语义。
- 配置/CLI：参数化路径/环境，明确 required/optional、合法值、安全默认值；不 silently fallback 到生产端点。沿用 logging 和 CLI 框架，清晰 main 入口、exit code、stdout/stderr 与错误信息，不输出 secret 或巨大原始 payload。
- 数据/性能：显式约束 dtype、schema、missing values、index、排序和内存；避免 chained assignment、无控制复制和大循环逐条外调。时间内部 UTC、边界显式转换，不混用 naive/aware；精确金融值采用现有 Decimal/最小货币单位模型，研究 float 使用合理容差，不机械替换算法。
- 安全：校验路径避免 traversal，安全创建临时文件/解压；SQL 参数化，URL/host 受控，外部 payload 校验；不对不可信输入用 shell=True、eval/exec、pickle 或不安全 YAML loader。重试有界、退避、幂等且失败可观察。
- helper 写入：校验输入，声明输出与副作用，必要时 dry-run、重跑保护和失败恢复；正常/空/非法/边界输入、缺文件/权限、重复执行、外部依赖失败按影响验证。无副作用脚本无需额外确认框架，生产/破坏性执行仍需明确授权。
- 测试/交付：fixture 控制 clock/seed/ID/timezone/data version/排序，不依赖真实账户或网络；不过度 mock SQL/序列化/事务。沿用现有测试/type/lint/format 入口，packaging/依赖变化再验证官方工具生成 lockfile、wheel/sdist、安装和 CLI entry point。失败/skip 不写 PASS，不删断言/关闭规则制造通过；发布另需授权。
