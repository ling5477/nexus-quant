# Python 工程守则

只在任务触及相应主题时应用以下检查，不为追求形式完整而扩张改动。

## 结构与依赖方向

- 保持现有 package architecture；新项目可默认 `src/` layout，已有项目不得无请求重排。
- 明确模块责任与依赖方向，避免 god module、`utils.py` dumping ground、巨大 `service.py`、跨层 import、circular import、隐式全局状态与 import-time side effects。
- 把 domain logic 与 transport、serialization、persistence、filesystem、subprocess 和 external adapter 分离。

## Typing 与数据模型

- 正式代码积极使用项目支持的 typing：`Iterable`、`Sequence`、`Mapping`、`Protocol`、`TypedDict`、`Literal`、`TypeAlias`、`Generic` 等。
- 用 `dataclass` 表达内部 value object 或简单结构化数据；需要稳定不可变语义时考虑 `frozen=True`。
- 用 `TypedDict` 约束既有 dict/JSON-like contract；不要为了类型而强制 runtime model。
- 用 `Protocol` 做 structural typing 和 adapter inversion，避免不必要的继承体系。
- 仅在项目已使用或确需 runtime validation、API schema、外部输入校验、序列化时采用 Pydantic。

## 错误、资源与状态

- 捕获最窄异常，保留 traceback 并添加可操作上下文；禁止 bare `except`、静默吞错和无语义 wrapper exception。
- 仅在明确 process boundary 统一捕获异常，并完成日志、状态、退出码或响应映射。
- 用 `with` / `async with` 管理文件、socket、HTTP client、DB connection/transaction、临时文件和锁。
- 确保 subprocess、thread/process、async task 在成功、失败、timeout 和 cancellation 路径均被回收。
- 检查 mutable default、共享 class-level mutable state、late-binding closure、iterator exhaustion、generator single-use、shadowed builtins、thread safety、multiprocessing semantics 与 race condition。

## Async / sync 与性能

- 不为同步项目无意义引入 async。
- 检查 async function 中的 blocking DB driver、HTTP、filesystem、CPU-heavy work、sleep 和 subprocess；隔离或改用匹配的 async 能力。
- 为外部 I/O 明确 timeout；重试必须有上限、退避、幂等判断和可观测失败。
- 管理 cancellation、task lifecycle、bounded concurrency、queue capacity 和 backpressure，禁止无界 fan-out。
- 数据处理评估复杂度、内存、复制、vectorization 与 streaming/chunking；避免大循环逐条外调或 I/O。

## 配置、日志与 CLI

- 沿用项目配置体系；配置来自 environment、配置文件、secret provider 或 application settings，不硬编码凭证。
- 对环境变量标明 required/optional、合法值和安全默认值；禁止 silently fallback 到危险环境或生产端点。
- 长期运行代码沿用既有 logging framework；没有既有方案时优先标准 `logging`，不用散落的 `print` 代替日志。
- 不记录 secret、token、password、完整个人数据或巨大 payload。
- CLI 沿用 argparse/click/typer 等既有框架，保持明确 exit code、stdout/stderr 分离和可操作错误信息。

## 时间、数值与数据

- 跨系统时间优先内部 UTC、边界显式转换；不得静默混用 naive/aware datetime。
- 金额和必须精确的金融值沿用项目 money model，必要时使用 `Decimal` 或 integer minor units；数值算法合理使用 float 时不要机械替换。
- Pandas/NumPy 处理显式约束 missing values、dtype、schema、index semantics 和 memory footprint；避免 chained assignment 与无控制复制。
- 对随机、时间序列、回测和 Monte Carlo 固定或注入 seed、clock、timezone、input ordering、dataset version，并使用合理 floating-point tolerance。

## 安全边界

- 校验不可信路径并防 path traversal；安全创建临时文件和解压 archive。
- 参数化 SQL；限制 URL/host 以降低 SSRF 风险；校验外部 payload/schema。
- 避免 `shell=True`、`eval`、`exec`、pickle 不可信反序列化和不安全 YAML loader。
- 新依赖前检查标准库/现有依赖是否足够、runtime/dev 分类、维护与安全风险；通过项目工具更新 declaration 和 lockfile，禁止手工编辑 lockfile。
