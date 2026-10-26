import type {MarketdataIngestionJob} from '@/types/marketdata';

const day = 86_400_000;
export const researchSymbols = ['BTC-USDT', 'ETH-USDT'] as const;

/** 日期输入按字面UTC日解释；结束日期排他，既有接入和Dataset接口使用包含末毫秒的区间。 */
export function researchWindow(symbol: string, startDate: string, endDate: string, now = Date.now()) {
    const parse = (value: string) => {
        if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return NaN;
        const millis = Date.parse(value + 'T00:00:00.000Z');
        return Number.isFinite(millis) && new Date(millis).toISOString().slice(0, 10) === value ? millis : NaN;
    };
    const start = parse(startDate);
    const end = parse(endDate);
    if (!researchSymbols.includes(symbol as (typeof researchSymbols)[number]) || !Number.isFinite(start)
        || !Number.isFinite(end) || end <= start || end - start > 30 * day
        || end > Math.floor(now / day) * day) throw new Error('RESEARCH_WINDOW_INVALID');
    return {exchangeCode: 'OKX', marketType: 'SPOT', symbol, interval: '1h',
        startTime: new Date(start).toISOString(), endTime: new Date(end - 1).toISOString()};
}

export function isResearchJob(job: Pick<MarketdataIngestionJob, 'exchangeCode' | 'marketType' | 'symbol' | 'interval' | 'startTime' | 'endTime'>) {
    const start = Date.parse(job.startTime);
    const end = Date.parse(job.endTime) + 1;
    return job.exchangeCode === 'OKX' && job.marketType === 'SPOT' && job.interval === '1h'
        && researchSymbols.includes(job.symbol as (typeof researchSymbols)[number])
        && start % day === 0 && end % day === 0 && end > start && end - start <= 30 * day;
}

export function datasetFromJobLink(job: MarketdataIngestionJob) {
    const params = new URLSearchParams({section: 'preparation', preparationTab: 'datasets',
        datasetFromJob: job.jobId});
    return '/marketdata?' + params;
}

export function researchFromDatasetLink(datasetId: string) {
    return '/research?' + new URLSearchParams({create: 'true', datasetId});
}
