export const EXCHANGE_OPTIONS = [
    {label: 'OKX', value: 'OKX'},
    {label: 'BINANCE', value: 'BINANCE'},
];

export const MARKET_TYPE_OPTIONS = [
    {
        get label() {
            return t('catalog.spot');
        },
        value: 'SPOT',
    },
];

export const SYMBOL_OPTIONS = [
    {label: 'BTC-USDT', value: 'BTC-USDT'},
    {label: 'ETH-USDT', value: 'ETH-USDT'},
    {label: 'SOL-USDT', value: 'SOL-USDT'},
];

export const INTERVAL_OPTIONS = ['1m', '5m', '15m', '1h', '4h', '1d'].map((value) => ({
    label: value,
    value,
}));

export const TRADE_ENV_OPTIONS = [
    {label: 'SIM', value: 'SIM'},
    {label: 'LIVE', value: 'LIVE'},
];

export const BOOLEAN_FILTER_OPTIONS = [
    {
        get label() {
            return t('all');
        },
        value: 'all',
    },
    {
        get label() {
            return t('enabled');
        },
        value: 'true',
    },
    {
        get label() {
            return t('disabled');
        },
        value: 'false',
    },
];

// 筛选补齐正式可执行类型，保留历史定义类型；选择筛选项本身不授予执行权限。
export const STRATEGY_TYPE_OPTIONS = [
    {
        get label() {
            return t('pages:spotSmaTargetStrategy');
        },
        value: 'SPOT_SMA_TARGET_V1',
    },
    ...['GRID', 'BUY_AND_HOLD_FIXTURE', 'E2E_SMOKE'].map((value) => ({label: value, value})),
];

export const SCHEDULE_TYPE_OPTIONS = ['CRON'].map((value) => ({label: value, value}));

export const SCHEDULE_STATUS_OPTIONS = ['ENABLED', 'DISABLED'].map((value) => ({
    label: value,
    value,
}));

export const RUN_STATUS_OPTIONS = ['CREATED', 'DISPATCHING', 'RUNNING', 'SUCCEEDED', 'FAILED'].map(
    (value) => ({
        label: value,
        value,
    }),
);

// 同时保留历史请求口径、正式调度和恢复运行；不能因旧页面只有两项就隐藏真实能力。
export const RUN_TRIGGER_TYPE_OPTIONS = [
    {
        get label() {
            return t('pages:runTriggerManual');
        },
        value: 'MANUAL',
    },
    {
        get label() {
            return t('pages:runTriggerLegacySchedule');
        },
        value: 'SCHEDULED',
    },
    {
        get label() {
            return t('pages:runTriggerScheduler');
        },
        value: 'SCHEDULER',
    },
    {
        get label() {
            return t('pages:runTriggerRecovery');
        },
        value: 'RECOVERY',
    },
];

export const PAPER_RUN_STATUS_OPTIONS = ['CREATED', 'RUNNING', 'STOPPED', 'FAILED'].map(
    (value) => ({
        label: value,
        value,
    }),
);

export const EVALUATION_STATUS_OPTIONS = ['SUCCEEDED', 'FAILED'].map((value) => ({
    label: value,
    value,
}));

export const PUBLISH_STATUS_OPTIONS = ['SUCCEEDED', 'FAILED'].map((value) => ({
    label: value,
    value,
}));
import {t} from '@/i18n';
