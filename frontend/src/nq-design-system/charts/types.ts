import type { Time } from 'lightweight-charts';

import type { MarketConvention } from '../tokens/nq-tokens';

export type NqChartState = 'ready' | 'loading' | 'empty' | 'error';

export interface NqKlineBar {
  time: string | number | Time;
  open: number;
  high: number;
  low: number;
  close: number;
  volume?: number | null;
  qualityStatus?: string | null;
}

export interface NqChartBaseProps {
  bars: readonly NqKlineBar[];
  height?: number;
  loading?: boolean;
  error?: string | Error | null;
  stale?: boolean;
  staleDetail?: string;
  sourceLabel?: string;
  convention?: MarketConvention;
  title?: string;
  emptyText?: string;
  className?: string;
}
