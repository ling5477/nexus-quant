// RiskBanner.tsx — 页级阻断/熔断/警报横幅(B0)。放页头下方,不可藏角落。
import React from 'react';

export type RiskSeverity = 'info' | 'warning' | 'danger';

const SEV: Record<RiskSeverity, { color: string; label: string }> = {
  info: { color: 'var(--nq-info)', label: '提示' },
  warning: { color: 'var(--nq-warning)', label: '警告' },
  danger: { color: 'var(--nq-danger)', label: '阻断' },
};

export interface RiskBannerProps {
  severity: RiskSeverity;
  /** 阻断/警报的可读原因。失败/阻断不靠 toast 一闪,要有常驻原因区。 */
  message: React.ReactNode;
  /** 处置入口,例如"查看规则""申请解除" */
  actions?: React.ReactNode;
  onDismiss?: () => void;
}

export function RiskBanner({ severity, message, actions, onDismiss }: RiskBannerProps) {
  const { color, label } = SEV[severity];
  return (
    <div
      role="alert"
      style={{
        display: 'flex', alignItems: 'center', gap: 12,
        padding: '8px 16px', borderRadius: 6,
        background: 'var(--nq-bg-elevated)',
        borderLeft: `3px solid ${color}`,
        border: '1px solid var(--nq-border-subtle)',
        borderLeftWidth: 3, borderLeftColor: color,
        color: 'var(--nq-text-primary)', fontSize: 13,
      }}
    >
      <span style={{ color, fontWeight: 600, whiteSpace: 'nowrap' }}>{label}</span>
      <span style={{ flex: 1 }}>{message}</span>
      {actions}
      {onDismiss && (
        <button
          onClick={onDismiss}
          aria-label="dismiss"
          style={{ background: 'transparent', border: 'none', color: 'var(--nq-text-tertiary)', cursor: 'pointer', fontSize: 14 }}
        >
          ×
        </button>
      )}
    </div>
  );
}
