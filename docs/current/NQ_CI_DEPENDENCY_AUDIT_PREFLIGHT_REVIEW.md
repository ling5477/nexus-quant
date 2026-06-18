# NQ CI Dependency Audit Preflight Review

任务：NQ-CI-SECURITY-GUARD-BATCH-4F-A-PREFLIGHT-REVIEW
日期：2026-06-18
状态：**PASS / ACCEPTED / READY FOR FREEZE REVIEW**

## 审查结论

结论：**PASS / ACCEPTED**

- Batch 4F-A preflight = **ACCEPTED / READY FOR FREEZE REVIEW**。
- 允许进入 4F-A freeze review。
- Batch 4F-B / 4F-C / 4F-D / 4F-E / 4F-F = **NOT STARTED**。
- Batch 4C = **FROZEN / ACCEPTED**。
- Static workflow assertion = **OPTIONAL FUTURE HARDENING / NOT IMPLEMENTED**。
- Batch 5 = **PENDING**。
- LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter = 未开启、未接入、未实现。

4F-A 已准确记录 dependency input、local toolchain availability、Python local interpreter limitation、GitHub Actions / downloaded CLI supply-chain gap 和 4F-B output hygiene。未发现 P0/P1 blocker。

Review-time clarification：

- `NQ_CI_DEPENDENCY_AUDIT_PREFLIGHT.md` 的 4F-B sanitized summary 字段清单未单列 `scope`。
- 本 review 将 `scope` 明确为 4F-B mandatory bounded field；其含义仅允许表达 direct/transitive、runtime/test/dev/build/CI 或等价的最小依赖范围，不得展开 dependency tree。
- 该 clarification 是 4F-A freeze input 的组成部分；freeze review 和后续 4F-B 必须同时引用 preflight 与本 review，不得将 `scope` 省略。
- 该缺口已在 review 层收口，不需要修改 workflow、依赖文件或运行 scanner，因此不阻断进入 freeze review。

## 范围

- 已审查：
  - `docs/current/NQ_CI_DEPENDENCY_AUDIT_PREFLIGHT.md`
  - `docs/current/NQ_CI_DEPENDENCY_AUDIT_PLAN.md`
  - `docs/current/NQ_CI_SECURITY_GUARD_PLAN.md`
  - `docs/current/NQ_CI_BASELINE_PLAN.md`
  - `docs/current/NQ_CI_SECURITY_GUARD_BATCH_4F_PLAN_REVIEW.md`
  - `docs/current/NQ_CI_SECURITY_GUARD_BATCH_4C_FREEZE_REVIEW.md`
  - `backend/pom.xml` 与 22 个 tracked child POM
  - `frontend/package.json`
  - `frontend/package-lock.json`
  - `research/py/pyproject.toml`
  - `.github/workflows/ci.yml`
- 未审查：
  - 未运行 Maven / npm / Python vulnerability audit。
  - 未验证任何 advisory finding。
  - 未生成或检查 SBOM。
- 明确不涉及：
  - 不修改 workflow。
  - 不新增 CI job。
  - 不修改依赖文件、代码、测试、migration、scripts 或 deploy。
  - 不进入 4F-B 至 4F-F 或 Batch 5。

## Findings

### P0

- 无。

### P1

- 无。

### P2

- Python 本地解释器不可用：`python` 解析到 WindowsApps stub，`python --version` 与 `python -m pip --version` 均返回 exit code `9009`。该事实已正确降级为后续 4F-B 输入条件，不得写成 Python local audit ready。4F-B 若覆盖 Python，必须使用已确认的真实解释器绝对路径，或 GitHub Actions `actions/setup-python@v5` 提供的确定环境。
- 4F-B sanitized summary 原字段清单未单列 `scope`。本 review 已将 `scope` 固定为 mandatory bounded field；不得借此输出完整 dependency tree。

### P3

- GitHub Actions 主要 action 仍使用 major tags：`actions/checkout@v4`、`actions/setup-java@v4`、`actions/upload-artifact@v4`、`actions/setup-node@v4`、`actions/setup-python@v5`。该 gap 正确归入 4F-E，本轮不修复。
- gitleaks CLI 固定为 `8.18.4`，release asset download 未发现 SHA256 checksum verification。该 gap 正确归入 4F-E，本轮不修复。
- Python/research 无 tracked requirements、constraints 或 lockfile。4F-B 的 Python 结果必须保持 advisory/report-only，除非后续单独冻结 deterministic resolution policy。

## 证据

### Java / Maven

- `backend/pom.xml`：
  - artifactId = `nexus-quant-backend`
  - version = `0.1.0-SNAPSHOT`
  - packaging = `pom`
  - modules = 22
- tracked child POM = 22。
- 22 个 child POM 均声明 parent：
  - groupId = `com.guidinglight.nexusquant`
  - artifactId = `nexus-quant-backend`
  - version = `0.1.0-SNAPSHOT`
  - relativePath = `../pom.xml`
- root modules、tracked child POM、parent declarations 一一对应；missing module POM = 0，extra child POM = 0，invalid parent = 0。
- Java `21.0.8` 与 Maven `3.9.12` 仅证明本地命令可用。未运行 vulnerability scanner，因此不构成 Maven dependency vulnerability audit 已验证。

### frontend / npm

- `frontend/package.json` 存在。
- `frontend/package-lock.json` 存在。
- `lockfileVersion = 3`。
- `packages` entries = 214。
- preflight 仅记录输入摘要；未复制完整 lockfile、dependency tree 或 npm credential-bearing config。

### Python / research

- tracked Python dependency input 仅有 `research/py/pyproject.toml`。
- 无 tracked `requirements*.txt`、`constraints*.txt`、`poetry.lock` 或 `Pipfile.lock`。
- `python` path = WindowsApps stub；`pip` command 未找到。
- `python --version` exit code = `9009`。
- `python -m pip --version` exit code = `9009`。
- Python 未被描述为 local audit ready。

### GitHub Actions / supply chain

- official actions 当前使用 major tags。
- `GITLEAKS_VERSION = 8.18.4`。
- gitleaks release asset 通过 `curl --fail --silent --show-error --location` 下载。
- 未发现 release asset SHA256 checksum verification。
- 以上 gap 已明确交给 4F-E；4F-A review 不修改 workflow。

### 4F-B handoff

4F-B sanitized summary 至少包含：

- ecosystem
- input file
- tool name/version
- command status
- severity count
- advisory ID
- affected direct package
- scope
- remediation category
- sanitized failure reason

4F-B 禁止输出、保存或上传：

- raw JSON
- 完整 dependency tree
- 完整 POM / effective POM
- 完整 lockfile
- raw 或 generated SBOM
- 环境变量
- token mask 原始上下文
- 带凭证的 Maven / npm / pip 配置
- 本地绝对路径扩散

Blocking policy：

- vulnerability findings = report-only / advisory。
- 仅 tool、parser、config、raw-output hygiene、credential-like output 或 policy violation 可阻断。
- malformed dependency input 仅可按 parser/config/policy failure 分类，不得伪装成 vulnerability finding blocker。
- 任意未来 artifact 上传必须先通过 Batch 4C redaction gate，并使用 bounded retention。

## 风险

- 影响面：仅 4F-A docs-only preflight review 与后续 4F-B 输入契约。
- 触发条件：后续遗漏 `scope`、直接上传 raw audit/SBOM、把 Python stub 写成 audit ready、或将 advisory findings 提前 fail-closed。
- 最坏结果：dependency metadata 过度扩散、credential-like context 泄露、非确定性 Python audit、CI 噪声阻断开发。

## 修复建议

- 最小修复：本 review 已补齐 `scope` mandatory clarification；无 workflow、dependency 或 code fix。
- 验证方式：4F-A freeze review 同时核对 preflight 与本 review；4F-B 启动前再次验证 Python execution environment 和 bounded sanitized schema。
- 回滚方式：删除本 review 文档并还原 `README.md`、`STATUS.md`、`TESTING.md`、`WORKLOG.md` 本轮同步；无需回滚 workflow、依赖文件或业务代码。

## 未验证项

- 原因：本轮明确禁止运行 dependency audit、scanner、SBOM 或 workflow implementation。
- 后续验证命令：由 4F-B 独立 plan/review 定义；本轮不提供已执行结果。

## Boundary Confirmation

- `.github/workflows/ci.yml`：0 diff。
- backend / frontend / research / scripts / deploy：0 diff。
- migration：0 diff。
- POM / package / lockfile / pyproject / requirements / constraints：0 diff。
- scanner install：未执行。
- dependency audit：未执行。
- SBOM generation / upload：未执行。
- artifact upload：未执行。
- 4F-B 至 4F-F：NOT STARTED。
- Batch 5：PENDING。
- LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter：未开启、未接入、未实现。

## Review Decision

**NQ-CI-SECURITY-GUARD-BATCH-4F-A-PREFLIGHT-REVIEW = PASS / ACCEPTED**

**Batch 4F-A preflight = ACCEPTED / READY FOR FREEZE REVIEW**

**Batch 4F-B / 4F-C / 4F-D / 4F-E / 4F-F = NOT STARTED**

**Batch 4C = FROZEN / ACCEPTED**

**Batch 5 = PENDING**

**LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter = 未开启、未接入、未实现**
