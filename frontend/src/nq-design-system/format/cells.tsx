// cells.tsx — NQ Console 表格列格式组件(B0.2)。
// 数字类用等宽 tabular-nums(右对齐由列 className .nq-ds-col-num 负责)。涨跌色读 var(--nq-up/--nq-down/--nq-flat),
// 随行情惯例翻转,且与 success/danger 解耦。颜色一律读 var(--nq-*),不私配 hex。
import type {CSSProperties} from 'react';

import {StatusTag, type StatusTone} from '../status/StatusTag';
import {
  NQ_DIRECTION_VAR,
  formatNqMoney,
  formatNqNumber,
  formatNqPercent,
  nqDirectionOf,
  type NqMoneyFormatOptions,
  type NqNumberFormatOptions,
  type NqPercentFormatOptions,
} from './nqFormat';

const NUM_STYLE: CSSProperties = {
  fontFamily: 'var(--nq-font-mono)',
  fontVariantNumeric: 'tabular-nums',
  whiteSpace: 'nowrap',
};

/** 通用数字单元格:等宽 tabular。右对齐由列 className 控制(见 nq-table.css 的 .nq-ds-col-num)。 */
export function NumberCell({value, ...opts}: {value: string | number | null | undefined} & NqNumberFormatOptions) {
  return <span style={NUM_STYLE}>{formatNqNumber(value, opts)}</span>;
}

/** 金额单元格:默认 2 位小数 + 千分位,可选货币后缀。 */
export function MoneyCell({value, ...opts}: {value: string | number | null | undefined} & NqMoneyFormatOptions) {
  return <span style={NUM_STYLE}>{formatNqMoney(value, opts)}</span>;
}

export interface PercentCellProps extends NqPercentFormatOptions {
  value: string | number | null | undefined;
  /** 是否按涨跌方向着色(默认 false:百分比未必是涨跌语义)。 */
  colorBySign?: boolean;
}

/** 百分比单元格:默认带符号 + "%";colorBySign=true 时按涨跌方向着色。 */
export function PercentCell({value, colorBySign = false, ...opts}: PercentCellProps) {
  const style: CSSProperties = colorBySign
    ? {...NUM_STYLE, color: NQ_DIRECTION_VAR[nqDirectionOf(value)]}
    : NUM_STYLE;

  return <span style={style}>{formatNqPercent(value, opts)}</span>;
}

export interface ChangeCellProps {
  value: string | number | null | undefined;
  precision?: number;
  /** 以百分比展示(带 "%");ratio=true 时输入按比例值换算。 */
  percent?: boolean;
  ratio?: boolean;
  /** 是否显示方向箭头(▲ / ▼ / —)。 */
  arrow?: boolean;
}

const ARROW: Record<string, string> = {up: '▲', down: '▼', flat: '—'};

/**
 * 涨跌单元格:盈利/上涨用 up 色、亏损/下跌用 down 色、持平用 flat 色。
 * 关键约束:必须使用行情方向色(var(--nq-up/--nq-down/--nq-flat)),不得复用 success/danger;
 * 颜色随行情惯例开关(applyNqCssVars)一处翻转。始终带正负号。
 */
export function ChangeCell({value, precision = 2, percent = false, ratio = false, arrow = false}: ChangeCellProps) {
  const direction = nqDirectionOf(value);
  const text = percent
    ? formatNqPercent(value, {precision, signed: true, ratio})
    : formatNqNumber(value, {precision, signed: true});

  return (
    <span style={{...NUM_STYLE, color: NQ_DIRECTION_VAR[direction], fontWeight: 600}}>
      {arrow && <span style={{marginRight: 4}}>{ARROW[direction]}</span>}
      {text}
    </span>
  );
}

/**
 * 状态列:把后端原始状态值映射为 StatusTag 的语义色(success/info/neutral/warning/danger)。
 * 渲染文本保持后端原值(审计 / E2E 依赖原文);同名状态语义冲突时用 tone 显式覆盖。
 */
const STATUS_TONE: Record<string, StatusTone> = {
  // 运行 / 成功
  RUNNING: 'success', ACTIVE: 'success', SUCCEEDED: 'success', PASSED: 'success', OK: 'success',
  ENABLED: 'success', FILLED: 'success', RESOLVED: 'success', GENERATED: 'success',
  // 等待 / 新建
  PENDING: 'info', CREATED: 'info', NEW: 'info', SUBMITTED: 'info', QUEUED: 'info',
  // 暂停 / 终止(中性)
  PAUSED: 'neutral', SKIPPED: 'neutral', DISABLED: 'neutral', STOPPED: 'neutral',
  CANCELLED: 'neutral', CANCELED: 'neutral', EXPIRED: 'neutral', CLOSED: 'neutral',
  // 风险提示
  WARNING: 'warning', DEGRADED: 'warning', PARTIAL: 'warning', LAGGING: 'warning',
  ACKED: 'warning', RETRYING: 'warning', RECOVERING: 'warning',
  // 失败 / 阻断
  FAILED: 'danger', BLOCKED: 'danger', REJECTED: 'danger', CRITICAL: 'danger', ERROR: 'danger',
};

export interface StatusCellProps {
  status: string | null | undefined;
  /** 语义冲突时显式覆盖(例如告警的 OPEN 应为 danger)。 */
  tone?: StatusTone;
  variant?: 'dot' | 'pill';
}

export function StatusCell({status, tone, variant = 'dot'}: StatusCellProps) {
  if (!status) {
    return <span style={{color: 'var(--nq-text-tertiary)'}}>-</span>;
  }

  const resolved = tone ?? STATUS_TONE[status.toUpperCase()] ?? 'neutral';
  return <StatusTag label={status} tone={resolved} variant={variant}/>;
}
