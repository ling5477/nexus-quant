// nqFormat.ts — NQ Console 数字 / 金额 / 百分比 / 涨跌方向的纯格式化函数(B0.2)。
// 约束:空值统一显示 "-";数字用 zh-CN 千分位;颜色由消费层按 direction 读 var(--nq-*),此处不决定颜色。

export interface NqNumberFormatOptions {
  /** 小数位数(价格常用 4,金额/百分比常用 2)。 */
  precision?: number;
  /** 正数是否加 "+"(盈亏 / 涨跌幅等带符号字段)。 */
  signed?: boolean;
  /** 是否使用千分位分组(默认 true)。 */
  thousands?: boolean;
}

/** 空值判定:null / undefined / 空串 视为缺失。 */
export function isNqEmpty(value: string | number | null | undefined): boolean {
  return value === null || value === undefined || value === '';
}

/**
 * 统一数字格式化。缺失返回 "-";非有限数原样返回字符串(避免把脏数据显示成 NaN)。
 */
export function formatNqNumber(
  value: string | number | null | undefined,
  {precision = 2, signed = false, thousands = true}: NqNumberFormatOptions = {},
): string {
  if (isNqEmpty(value)) {
    return '-';
  }

  const numeric = Number(value);

  if (!Number.isFinite(numeric)) {
    return String(value);
  }

  const formatted = numeric.toLocaleString('zh-CN', {
    minimumFractionDigits: precision,
    maximumFractionDigits: precision,
    useGrouping: thousands,
  });

  return signed && numeric > 0 ? `+${formatted}` : formatted;
}

export interface NqMoneyFormatOptions extends NqNumberFormatOptions {
  /** 货币 / 单位后缀,例如 "USDT" / "¥";为空则只显示数字。 */
  currency?: string;
}

/** 金额格式化:默认 2 位小数 + 千分位,可选货币后缀。 */
export function formatNqMoney(
  value: string | number | null | undefined,
  {precision = 2, signed = false, currency}: NqMoneyFormatOptions = {},
): string {
  const text = formatNqNumber(value, {precision, signed});

  if (text === '-' || !currency) {
    return text;
  }

  return `${text} ${currency}`;
}

export interface NqPercentFormatOptions extends NqNumberFormatOptions {
  /** 后端为比例值(0.0123 = 1.23%)时设 true,展示前 ×100。 */
  ratio?: boolean;
}

/** 百分比格式化:默认带符号 + 2 位小数 + "%";ratio=true 时按比例值换算。 */
export function formatNqPercent(
  value: string | number | null | undefined,
  {precision = 2, signed = true, ratio = false}: NqPercentFormatOptions = {},
): string {
  if (isNqEmpty(value)) {
    return '-';
  }

  const numeric = Number(value);

  if (!Number.isFinite(numeric)) {
    return String(value);
  }

  const scaled = ratio ? numeric * 100 : numeric;
  return `${formatNqNumber(scaled, {precision, signed})}%`;
}

export type NqDirection = 'up' | 'down' | 'flat';

/**
 * 由数值符号判定涨跌方向(与行情惯例无关:>0=up,<0=down,0/缺失=flat)。
 * 具体颜色由消费层映射到 var(--nq-up/--nq-down/--nq-flat),从而随惯例开关翻转。
 */
export function nqDirectionOf(value: string | number | null | undefined): NqDirection {
  if (isNqEmpty(value)) {
    return 'flat';
  }

  const numeric = Number(value);

  if (!Number.isFinite(numeric) || numeric === 0) {
    return 'flat';
  }

  return numeric > 0 ? 'up' : 'down';
}

/** 涨跌方向对应的 CSS 变量(随行情惯例翻转,不复用 success/danger)。 */
export const NQ_DIRECTION_VAR: Record<NqDirection, string> = {
  up: 'var(--nq-up)',
  down: 'var(--nq-down)',
  flat: 'var(--nq-flat)',
};
