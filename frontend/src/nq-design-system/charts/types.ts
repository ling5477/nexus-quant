import type {Time} from 'lightweight-charts';

import type {MarketConvention} from '../tokens/nq-tokens';

export interface NqKlineBar {
    time: string | number | Time;
    open: number;
    high: number;
    low: number;
    close: number;
    volume?: number | null;
    qualityStatus?: string | null;
    closeTime?: string | null;
    availableAt?: string | null;
    exact?: Partial<Record<'open' | 'high' | 'low' | 'close' | 'volume', string>>;
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
    intervalSeconds?: number;
    movingAverageWindow?: number | null;
    visibleRange?: {from: number; to: number} | null;
    onVisibleRangeChange?: (range: {from: number; to: number} | null) => void;
}
