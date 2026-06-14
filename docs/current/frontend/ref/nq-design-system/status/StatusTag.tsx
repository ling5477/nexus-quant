// StatusTag.tsx — 实体状态标签(订单/Run/策略/发布…),可嵌入表格/卡片/列表/页头(B0)
import React from 'react';

export type StatusTone = 'neutral' | 'primary' | 'success' | 'warning' | 'danger' | 'info';

const TONE_VAR: Record<StatusTone, string> = {
  neutral: 'var(--nq-text-tertiary)',
  primary: 'var(--nq-primary)',
  success: 'var(--nq-success)',
  warning: 'var(--nq-warning)',
  danger: 'var(--nq-danger)',
  info: 'var(--nq-info)',
};

export interface StatusTagProps {
  label: string;
  tone?: StatusTone;
  /** 实心点 + 文字(默认),或带描边的胶囊 */
  variant?: 'dot' | 'pill';
  title?: string;
}

export function StatusTag({ label, tone = 'neutral', variant = 'dot', title }: StatusTagProps) {
  const color = TONE_VAR[tone];
  if (variant === 'pill') {
    return (
      <span
        title={title ?? label}
        style={{
          display: 'inline-flex', alignItems: 'center', gap: 6,
          padding: '1px 8px', borderRadius: 4, fontSize: 12, lineHeight: '18px',
          color, border: `1px solid ${color}`, background: 'transparent', whiteSpace: 'nowrap',
        }}
      >
        {label}
      </span>
    );
  }
  return (
    <span title={title ?? label} style={{ display: 'inline-flex', alignItems: 'center', gap: 6, whiteSpace: 'nowrap', color: 'var(--nq-text-secondary)' }}>
      <span style={{ width: 7, height: 7, borderRadius: '50%', background: color, flex: '0 0 auto' }} />
      {label}
    </span>
  );
}
