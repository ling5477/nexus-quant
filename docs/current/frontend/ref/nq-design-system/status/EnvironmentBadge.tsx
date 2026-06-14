// EnvironmentBadge.tsx — 环境/读写边界标识,Top Bar 常驻 + 操作区(B0)
// LIVE 必须在视觉上与 PAPER 明显不同(实心高危样式)。
import React from 'react';

export type NqEnv = 'PAPER' | 'DEMO' | 'LIVE' | 'READONLY' | 'AUDITED';

const ENV_VAR: Record<NqEnv, string> = {
  PAPER: 'var(--nq-env-paper)',
  DEMO: 'var(--nq-env-demo)',
  LIVE: 'var(--nq-env-live)',
  READONLY: 'var(--nq-env-readonly)',
  AUDITED: 'var(--nq-env-audited)',
};

export interface EnvironmentBadgeProps {
  env: NqEnv;
  size?: 'sm' | 'md';
}

export function EnvironmentBadge({ env, size = 'md' }: EnvironmentBadgeProps) {
  const color = ENV_VAR[env];
  const isLive = env === 'LIVE';
  const pad = size === 'sm' ? '0 6px' : '1px 8px';
  const fontSize = size === 'sm' ? 11 : 12;

  // LIVE: 实心填充 + 深色字 = 高危;其余: 描边 = 普通
  const liveStyle: React.CSSProperties = { background: color, color: '#0b0b0b', border: `1px solid ${color}`, fontWeight: 600 };
  const normalStyle: React.CSSProperties = { background: 'transparent', color, border: `1px solid ${color}` };

  return (
    <span
      aria-label={`environment ${env}`}
      style={{
        display: 'inline-flex', alignItems: 'center', gap: 6,
        padding: pad, borderRadius: 4, fontSize, lineHeight: '18px',
        letterSpacing: 0.4, whiteSpace: 'nowrap', textTransform: 'uppercase',
        ...(isLive ? liveStyle : normalStyle),
      }}
    >
      {isLive && <span style={{ width: 6, height: 6, borderRadius: '50%', background: '#0b0b0b' }} />}
      {env}
    </span>
  );
}
