# AGENTS（Codex 开发指引 - NexusQuant）

> 目的：让 Codex 生成代码时严格遵循本仓库 Gate A 的架构/契约/正确性约束。  
> 当前：Gate A 只补文档与骨架；实现由你触发 Codex 完成。

## 1. 强制约束（必须遵守）

- 语言：除代码/技术名词外，解释与文档输出使用**简体中文**。
- 严格状态机：不得任意 setStatus；必须通过显式事件驱动迁移。
- 幂等：`client_order_id` 必须贯穿订单/事件/账本引用。
- 可审计：所有关键决策点必须记录 traceId 与原因（reason）。
- 可恢复：投影表允许丢失，但必须能从事实（事件/账本）重建。
- 可观测：日志为结构化（JSON），并统一字段（trace_id、run_id、strategy_id、account_id、symbol 等）。

## 2. 文档即事实（Source of Truth）

实现必须对齐以下文档：
- `docs/ARCHITECTURE.md`
- `docs/MODULES.md`
- `docs/CONTRACTS.md`
- `docs/EVOLUTION_RULES.md`
- `docs/NUMERIC_POLICY.md`
- `docs/DB_SCHEMA.md`
- `docs/RECOVERY_RUNBOOK.md`

## 3. 模块实现顺序（推荐）

1. `nq-contracts` / `nq-common`
2. `nq-core`（域模型 + 状态机 + 幂等键）
3. `nq-ledger`（不可变流水 + 平衡校验）
4. `nq-risk`（规则框架 + 事件记录）
5. `nq-observability`（日志/trace/metrics）
6. `nq-config` / `nq-scheduler`（骨架）
7. `nq-app`（启动载体：装配与健康检查）
8. `nq-gateway` / `nq-auth` / `nq-security`（最小控制面）

## 4. 禁止项（Gate A）

- 禁止实现真实交易所网络连接（只冻结接口与 DTO）
- 禁止实现真实策略（仅允许最小示例/占位）
- 禁止在 infra 模块塞领域逻辑（infra 只做技术设施封装）

---

## Appendix：原英文版本（保留参考）

# Repository Guidelines

## Project Structure & Module Organization
This repository is currently documentation-first. Use `docs/` as the source of truth:
- `docs/ARCHITECTURE.md`: target mono-repo layout and module boundaries
- `docs/CONTRACTS.md`: HTTP/event contracts and trace rules
- `docs/DECISIONS.md`: ADRs that must be updated before major design changes
- `docs/GATE_A_CHECKLIST.md`: Gate A acceptance checklist

Planned structure (per architecture baseline): `backend/` (Java services), `research/` (Python research), `frontend/`, `infra/`, and `docs/`.

## Build, Test, and Development Commands
Use PowerShell from repo root:
- `Get-ChildItem docs` - quick documentation sanity check.
- `cd backend; mvn -q test` - run backend unit tests (after backend scaffold exists).
- `docker compose up -d postgres` - start local PostgreSQL for Flyway migration testing.
- `docker compose down` - stop local infrastructure.

If a command is not available yet, align the missing files first with `docs/ARCHITECTURE.md` and `docs/GATE_A_CHECKLIST.md`.

## Coding Style & Naming Conventions
- Java package base: `com.guidinglight.nexusquant`.
- Module naming: `nq-*` (for example, `nq-core`, `nq-auth`, `nq-gateway`).
- Use `BigDecimal` for price/qty/amount and `Instant` (UTC) for timestamps.
- Prefer 4-space indentation, UTF-8, and descriptive names (`OrderStateMachine`, `RecoveryService`).
- Keep changes minimal and scoped; avoid cross-module refactors in a single commit.

## Testing Guidelines
- Add unit tests for every core logic change, especially state machine, idempotency, ledger balance, and recovery.
- Use regression tests for bug fixes.
- Test naming: `*Test` for unit tests; method names should describe behavior (for example, `shouldRejectInvalidTransition`).
- Main verification command: `cd backend; mvn -q test`.

## Commit & Pull Request Guidelines
Current history is bootstrap-level (`init`), so contributors should standardize now:
- Use Conventional Commits: `feat(scope): ...`, `fix(scope): ...`, `test(scope): ...`, `docs(scope): ...`.
- Keep one concern per commit (feature vs formatting vs docs).
- PRs should include: summary, changed paths, linked issue/ADR, and test evidence (command + result).

## Security & Configuration Tips
- Never commit secrets (tokens, keys, passwords).
- Use environment variables for credentials and local overrides.
- Preserve traceability: keep `X-Trace-Id` propagation and audit-related changes aligned with `docs/CONTRACTS.md`.
