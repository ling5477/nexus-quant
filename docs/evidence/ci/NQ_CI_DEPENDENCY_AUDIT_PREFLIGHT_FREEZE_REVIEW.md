# NQ CI Dependency Audit Preflight Freeze Review

任务：NQ-CI-SECURITY-GUARD-BATCH-4F-A-FREEZE-REVIEW

日期：2026-06-18

状态：**PASS / ACCEPTED / FROZEN**

## 审查结论

结论：**PASS / ACCEPTED / FROZEN**。

- `NQ-CI-SECURITY-GUARD-BATCH-4F-A-FREEZE-REVIEW` = **PASS / ACCEPTED / FROZEN**。
- Batch 4F-A preflight = **FROZEN / ACCEPTED**，作为 Batch 4F-B 的唯一 dependency input / toolchain preflight 基线。
- Python local audit = **NOT READY**；该 P2 保留为 4F-B execution prerequisite。
- Batch 4F-B / 4F-C / 4F-D / 4F-E / 4F-F = **NOT STARTED**。
- Batch 4C = **FROZEN / ACCEPTED**。
- Static workflow assertion = **OPTIONAL FUTURE HARDENING / NOT IMPLEMENTED**。
- Batch 5 = **PENDING**。
- LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter = 未开启、未接入、未实现。

`NQ_CI_DEPENDENCY_AUDIT_PREFLIGHT.md` 的 `PASS / READY FOR REVIEW`、`NQ_CI_DEPENDENCY_AUDIT_PREFLIGHT_REVIEW.md` 的 `PASS / ACCEPTED / READY FOR FREEZE REVIEW` 与本文件的 `PASS / ACCEPTED / FROZEN` 是同一 4F-A 基线按 implementation、review、freeze 依次推进的历史状态，不构成冲突。本文件是 4F-A 当前最新冻结结论；历史文档不回写为当前状态。

## 范围

- 已审查：
  - `docs/current/NQ_CI_DEPENDENCY_AUDIT_PREFLIGHT.md`
  - `docs/current/NQ_CI_DEPENDENCY_AUDIT_PREFLIGHT_REVIEW.md`
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
  - 未运行 Maven / npm / Python vulnerability audit，未验证 advisory finding。
  - 未生成或检查 SBOM。
  - 未运行构建或测试。
- 明确不涉及：
  - 不修改 workflow，不新增 GitHub Actions job。
  - 不修改依赖文件、代码、测试、migration、frontend、research、scripts 或 deploy。
  - 不进入 4F-B 至 4F-F 或 Batch 5。
  - 不开启 LIVE、AI、DH runtime，不实现 RealClient、real provider 或 real exchange adapter。

## Findings

### P0

- 无。

### P1

- 无。

### P2

- Python local audit 仍 **NOT READY**：`python` 解析到 WindowsApps stub，`python --version` 与 `python -m pip --version` 均返回 exit code `9009`。4F-B 若覆盖 Python，必须使用已确认的真实解释器路径，或 GitHub Actions `actions/setup-python@v5` 提供的确定环境；不得把当前本机状态写成 local audit ready。
- Python tracked dependency input 仅为 `research/py/pyproject.toml`，无 tracked requirements、constraints 或 Python lockfile。后续 Python finding 必须保持 report-only / advisory，除非另行冻结 deterministic resolution policy。

### P3

- GitHub Actions official actions 仍使用 major tags。该 gap 保留为 4F-E 输入，本轮不修复。
- gitleaks CLI 固定版本为 `8.18.4`，但 release asset download 未验证 SHA256。该 gap 保留为 4F-E 输入，本轮不修复。

## 证据

### Dependency input / toolchain

- Java = `21.0.8` LTS；Maven = `3.9.12`。两者只证明本机命令可用，不代表 vulnerability audit 已执行或已通过。
- `backend/pom.xml` 是 packaging=`pom` 的 root reactor parent；root modules = 22，tracked child POM = 22，missing module POM = 0，extra child POM = 0，invalid parent = 0。
- `frontend/package.json` 与 `frontend/package-lock.json` 存在；lockfileVersion = 3，package entries = 214。未复制或持久化完整 lockfile。
- Python tracked input 仅为 `research/py/pyproject.toml`；无 tracked `requirements*.txt`、`constraints*.txt`、`poetry.lock` 或 `Pipfile.lock`。
- `python` path 为 WindowsApps stub；`python --version` exit code = `9009`；`python -m pip --version` exit code = `9009`。
- `.github/workflows/ci.yml` 使用 action major tags；`GITLEAKS_VERSION = 8.18.4`；未发现 gitleaks release asset SHA256 verification。

### 4F-B frozen handoff

4F-B 只允许 sanitized advisory audit summary，且必须包含以下 10 个 bounded fields：

1. ecosystem
2. input file
3. tool name/version
4. command status
5. severity count
6. advisory ID
7. affected direct package
8. scope
9. remediation category
10. sanitized failure reason

`scope` 是 mandatory bounded field，只能表达 direct/transitive、runtime/test/dev/build/CI 或等价最小范围；不得用于展开或重建完整 dependency tree。

- Vulnerability findings 仅为 report-only / advisory，不得因 finding 本身阻断。
- 仅 tool、parser、config、raw-output hygiene、credential-like output、malformed dependency input 或 policy violation 可以阻断。
- 禁止输出、保存或上传 raw JSON、完整 dependency tree、完整 POM/effective POM、完整 lockfile、raw/generated SBOM、环境变量、token mask 原始上下文、带凭证的 Maven/npm/pip 配置或本地绝对路径。
- 任何 future artifact upload 必须先通过 Batch 4C redaction gate，并采用 bounded retention。

### Credential hygiene

- 检查范围：`docs/current` 与 `.github` 共 86 个 tracked files。
- 宽松前缀规则命中仅作为规则定义、proof 文本和 false-positive 说明分类，不输出匹配正文。
- 高置信完整 credential pattern：files = 0，matches = 0。
- 未发现真实 credential material。

## Checklist

| # | Freeze review item | Result |
| --- | --- | --- |
| 1 | preflight 与 review 状态是否按阶段一致 | PASS |
| 2 | Java/Maven、npm、Python、GitHub Actions 输入盘点是否准确 | PASS |
| 3 | root modules=22、child POM=22、missing/extra=0 是否明确 | PASS |
| 4 | WindowsApps stub 与 exit code 9009 是否保留 | PASS |
| 5 | 是否禁止把 Python 写成 local audit ready | PASS |
| 6 | 4F-B Python 真实解释器或 setup-python 前置条件是否明确 | PASS |
| 7 | 4F-B 十个 sanitized fields 与 mandatory bounded `scope` 是否明确 | PASS |
| 8 | vulnerability findings 是否仅 report-only / advisory | PASS |
| 9 | 阻断条件是否限定于 tool/parser/config/output hygiene/credential-like output/malformed input/policy violation | PASS |
| 10 | future artifact 是否复用 Batch 4C redaction gate 与 bounded retention | PASS |
| 11 | action major tags 与 gitleaks SHA256 gap 是否保留为 4F-E 输入 | PASS |
| 12 | workflow、代码、测试、migration、依赖输入文件是否无本轮修改 | PASS |
| 13 | 是否未运行 dependency audit、scanner、SBOM、构建或测试 | PASS |
| 14 | 是否未进入 4F-B 至 4F-F 或 Batch 5 | PASS |
| 15 | `docs/current` 是否无真实 credential material | PASS |

## 风险

- 影响面：仅冻结 4F-A dependency input / toolchain preflight 与 4F-B 输入契约；当前 CI 行为不变。
- 触发条件：4F-B 使用 WindowsApps stub、遗漏 bounded `scope`、将 advisory finding fail-closed、上传 raw dependency metadata、或绕过 Batch 4C redaction gate。
- 最坏结果：非确定性 Python audit、CI 误阻断、dependency metadata 或 credential-like context 扩散。

## 修复建议

- 最小修复：无 P0/P1 blocker，无需 workflow、依赖或代码修复。
- 验证方式：4F-B 启动前重新确认 Python execution environment、10-field bounded schema、report-only policy 与 Batch 4C artifact gate。
- 回滚方式：删除本 freeze review，并还原 `README.md`、`STATUS.md`、`TESTING.md`、`WORKLOG.md` 的本轮状态同步；无需回滚 workflow、依赖文件、代码、测试或 migration。

## Validation

已执行只读验证：

```powershell
Get-Location
git status --short
git branch --show-current
git log --oneline -10
git diff --check
git diff --stat
git diff -- .github/workflows/ci.yml
git diff -- backend frontend research scripts deploy
git diff -- "backend/**/db/migration"
git ls-files
java -version
mvn -version
node --version
npm --version
python --version
python -m pip --version
rg -n "uses:|gitleaks|8\.18\.4|sha256|checksum|retention-days|setup-python" .github/workflows/ci.yml
```

结构化只读解析：

- Maven XML：root modules、tracked child POM、parent group/artifact/version/relativePath 一致性。
- npm JSON：`package.json` 摘要与 `package-lock.json` lockfileVersion/package entry count。PowerShell 默认 `ConvertFrom-Json` 因 lockfile root package 的空字符串 key 失败；RCA 后使用 `ConvertFrom-Json -AsHashTable` 重验通过，得到 lockfileVersion=3、package entries=214。
- credential hygiene：只统计文件数与命中数，不输出匹配正文或 secret-like value。

未执行：

- 未运行 Maven vulnerability audit、`npm audit`、`pip-audit`、OSV、Snyk、Trivy、Grype、OWASP dependency-check 或外部 scanner。
- 未生成、保存或上传 SBOM、raw JSON、dependency tree、完整 lockfile 或 dependency report。
- 未运行 backend Maven test、frontend build/E2E 或 Python pytest/mypy/ruff。
- 未安装工具或依赖，未调用外部服务，未触发 GitHub Actions run。

## 未验证项

- 原因：本轮只冻结 input/toolchain preflight，明确禁止执行 dependency audit、scanner、SBOM、构建和测试。
- 后续验证命令：由 4F-B 独立 plan/review 定义；本轮不将 vulnerability finding、SBOM 或构建测试写成已验证。

## Review Decision

**NQ-CI-SECURITY-GUARD-BATCH-4F-A-FREEZE-REVIEW = PASS / ACCEPTED / FROZEN**

**Batch 4F-A preflight = FROZEN / ACCEPTED**

**Python local audit = NOT READY**

**Batch 4F-B / 4F-C / 4F-D / 4F-E / 4F-F = NOT STARTED**

**Batch 4C = FROZEN / ACCEPTED**

**Static workflow assertion = OPTIONAL FUTURE HARDENING / NOT IMPLEMENTED**

**Batch 5 = PENDING**

**LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter = 未开启、未接入、未实现**
