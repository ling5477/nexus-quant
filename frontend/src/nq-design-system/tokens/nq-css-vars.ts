// nq-css-vars.ts — 从唯一来源生成 CSS 变量,避免手抄漂移(B0 / Design Tokens v2)
import { nqTokens, DEFAULT_MARKET_CONVENTION, type MarketConvention } from './nq-tokens';

export function nqCssVars(convention: MarketConvention = DEFAULT_MARKET_CONVENTION): Record<string, string> {
  const m = nqTokens.market[convention];
  return {
    '--nq-bg-app': nqTokens.bg.app, '--nq-bg-canvas': nqTokens.bg.canvas,
    '--nq-bg-panel': nqTokens.bg.panel, '--nq-bg-elevated': nqTokens.bg.elevated,
    '--nq-text-primary': nqTokens.text.primary, '--nq-text-secondary': nqTokens.text.secondary,
    '--nq-text-tertiary': nqTokens.text.tertiary, '--nq-text-disabled': nqTokens.text.disabled,
    '--nq-border-subtle': nqTokens.border.subtle, '--nq-border-strong': nqTokens.border.strong,
    '--nq-primary': nqTokens.semantic.primary, '--nq-success': nqTokens.semantic.success,
    '--nq-warning': nqTokens.semantic.warning, '--nq-danger': nqTokens.semantic.danger,
    '--nq-info': nqTokens.semantic.info,
    '--nq-up': m.up, '--nq-down': m.down, '--nq-flat': m.flat,
    '--nq-env-paper': nqTokens.env.PAPER, '--nq-env-demo': nqTokens.env.DEMO,
    '--nq-env-live': nqTokens.env.LIVE, '--nq-env-readonly': nqTokens.env.READONLY,
    '--nq-env-audited': nqTokens.env.AUDITED,
    '--nq-font-ui': nqTokens.font.ui, '--nq-font-mono': nqTokens.font.mono,
  };
}

/** 应用到 :root。行情惯例切换时重新调用即可(用户偏好开关)。 */
export function applyNqCssVars(convention: MarketConvention = DEFAULT_MARKET_CONVENTION): void {
  const vars = nqCssVars(convention);
  for (const [k, v] of Object.entries(vars)) document.documentElement.style.setProperty(k, v);
}
