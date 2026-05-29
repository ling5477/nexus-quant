export const EXCHANGE_OPTIONS = [
    {label: 'OKX', value: 'OKX'},
    {label: 'BINANCE', value: 'BINANCE'},
];

export const MARKET_TYPE_OPTIONS = [
    {label: 'SPOT', value: 'SPOT'},
];

export const SYMBOL_OPTIONS = [
    {label: 'BTC-USDT', value: 'BTC-USDT'},
    {label: 'ETH-USDT', value: 'ETH-USDT'},
    {label: 'SOL-USDT', value: 'SOL-USDT'},
];

export const INTERVAL_OPTIONS = ['1m', '5m', '15m', '1h', '1d'].map((value) => ({label: value, value}));

export const TRADE_ENV_OPTIONS = [
    {label: 'SIM', value: 'SIM'},
    {label: 'LIVE', value: 'LIVE'},
];

export const BOOLEAN_FILTER_OPTIONS = [
    {label: '全部', value: 'all'},
    {label: '已启用', value: 'true'},
    {label: '未启用', value: 'false'},
];

export const STRATEGY_TYPE_OPTIONS = ['GRID', 'BUY_AND_HOLD_FIXTURE', 'E2E_SMOKE'].map((value) => ({
    label: value,
    value,
}));

export const STRATEGY_STATUS_OPTIONS = ['ENABLED', 'DISABLED'].map((value) => ({label: value, value}));

export const SCHEDULE_TYPE_OPTIONS = ['CRON'].map((value) => ({label: value, value}));

export const SCHEDULE_STATUS_OPTIONS = ['ENABLED', 'DISABLED'].map((value) => ({label: value, value}));

export const RUN_STATUS_OPTIONS = ['CREATED', 'DISPATCHING', 'RUNNING', 'FAILED'].map((value) => ({
    label: value,
    value,
}));

export const RUN_TRIGGER_TYPE_OPTIONS = ['MANUAL', 'SCHEDULED'].map((value) => ({label: value, value}));

export const PAPER_RUN_STATUS_OPTIONS = ['CREATED', 'RUNNING', 'STOPPED', 'FAILED'].map((value) => ({
    label: value,
    value,
}));

export const EVALUATION_STATUS_OPTIONS = ['SUCCEEDED', 'FAILED'].map((value) => ({label: value, value}));

export const PUBLISH_STATUS_OPTIONS = ['SUCCEEDED', 'FAILED'].map((value) => ({label: value, value}));
