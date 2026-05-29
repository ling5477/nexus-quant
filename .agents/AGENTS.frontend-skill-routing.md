# Suggested AGENTS.md Snippet

把下面内容追加到项目根目录 `AGENTS.md`，用于给 Codex / Claude 做 skill 路由约束。

```md
## Agent Skills Routing

When working on frontend product pages, apply these skills in order:
1. `frontend-product-ui-design` for business UX, page structure, status semantics, empty/error/risky operation states.
2. `frontend-antd-page-builder` for React + TypeScript + Ant Design implementation and API wiring.
3. `frontend-quality-regression` for frontend review, bug fixing, and Playwright regression.
4. `ui-visual-system-polish` only when the request is specifically about visual polish, layout, typography, color, responsiveness, or design-system consistency.

For NexusQuant and Decision Hub:
- Prefer professional fintech dashboard style.
- Do not introduce marketing-page visuals into operational backend pages.
- Do not change backend API, database migration, or business behavior unless explicitly requested.
- Keep TanStack Query as the source of server-state management.
- Use Zustand only for lightweight client-side UI state.
- Risk, failure, stopped, stale, no-permission, and not-configured states must be visible and business-readable.
```
