// StatusTag.tsx — canonical 业务状态标签。系统状态色与行情 up/down 色严格分离。
import type {CSSProperties} from 'react';

export type StatusTone = 'neutral' | 'primary' | 'success' | 'warning' | 'danger' | 'info';

const TONE_VAR: Record<StatusTone, string> = {
  neutral: 'var(--nq-text-tertiary, var(--nq-color-neutral))',
  primary: 'var(--nq-primary, var(--nq-color-primary))',
  success: 'var(--nq-success, var(--nq-color-success))',
  warning: 'var(--nq-warning, var(--nq-color-warning))',
  danger: 'var(--nq-danger, var(--nq-color-danger))',
  info: 'var(--nq-info, var(--nq-color-info))',
};

const STATUS_TONE: Record<string, StatusTone> = {
  RUNNING: 'success', ACTIVE: 'success', SUCCEEDED: 'success', PASSED: 'success', OK: 'success',
  ENABLED: 'success', FILLED: 'success', RESOLVED: 'success', GENERATED: 'success',
  PENDING: 'info', CREATED: 'info', NEW: 'info', SUBMITTED: 'info', QUEUED: 'info',
  PAUSED: 'neutral', SKIPPED: 'neutral', DISABLED: 'neutral', STOPPED: 'neutral',
  CANCELLED: 'neutral', CANCELED: 'neutral', EXPIRED: 'neutral', CLOSED: 'neutral',
  WARNING: 'warning', DEGRADED: 'warning', PARTIAL: 'warning', LAGGING: 'warning',
  ACKED: 'warning', RETRYING: 'warning', RECOVERING: 'warning',
  FAILED: 'danger', BLOCKED: 'danger', REJECTED: 'danger', CRITICAL: 'danger', ERROR: 'danger',
  APPLIED: 'danger',
};

/** 未映射状态始终回退为 neutral，不允许自动解释为成功。 */
export function statusToneOf(status: string | null | undefined): StatusTone {
  const normalized = status?.trim().toUpperCase();
  if (!normalized) return 'neutral';
  if (STATUS_TONE[normalized]) return STATUS_TONE[normalized];
  if (normalized.startsWith('BLOCKED') || normalized.includes('FAILED') || normalized.includes('ERROR')) return 'danger';
  if (normalized.includes('WARNING') || normalized.includes('MISSING') || normalized.includes('INCOMPLETE')) return 'warning';
  if (normalized.includes('PENDING') || normalized.startsWith('READY_FOR')) return 'info';
  return 'neutral';
}

export interface StatusTagProps {
  /** 后端原始状态，用于 canonical tone 映射。 */
  status?: string | null;
  /** 可选展示文案；领域页面可保留审计状态并附加稳定业务解释。 */
  label?: string | null;
  tone?: StatusTone;
  /** 实心点 + 文字(默认),或带描边的胶囊 */
  variant?: 'dot' | 'pill';
  title?: string;
  className?: string;
  style?: CSSProperties;
}

export function StatusTag({status, label, tone, variant = 'dot', title, className, style}: StatusTagProps) {
  const displayLabel = label?.trim() || status?.trim() || '-';
  const resolvedTone = tone ?? statusToneOf(status ?? label);
  const color = TONE_VAR[resolvedTone];
  if (variant === 'pill') {
    return (
      <span
        title={title ?? displayLabel}
        className={className}
        style={{
          display: 'inline-flex', alignItems: 'center', gap: 6,
          padding: '1px 8px', borderRadius: 4, fontSize: 12, lineHeight: '18px',
          color, border: `1px solid ${color}`, background: 'transparent', whiteSpace: 'nowrap',
          ...style,
        }}
      >
        {displayLabel}
      </span>
    );
  }
  return (
    <span
      title={title ?? displayLabel}
      className={className}
      style={{display: 'inline-flex', alignItems: 'center', gap: 6, whiteSpace: 'nowrap', color: 'var(--nq-text-secondary, var(--nq-color-text-secondary))', ...style}}
    >
      <span style={{ width: 7, height: 7, borderRadius: '50%', background: color, flex: '0 0 auto' }} />
      {displayLabel}
    </span>
  );
}
