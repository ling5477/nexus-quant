import {createElement} from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import {MemoryRouter} from 'react-router-dom';
import {App} from 'antd';
import type {ButtonProps} from 'antd';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {apiClient} from '@/api/client';
import {backtestsQueryKeys, evaluationsQueryKeys, publishesQueryKeys} from '@/api/query-keys';
import {publishesApi} from '@/features/publishes/api/publishes';
import type {BacktestRunDetailItem} from '@/features/backtests/backtestsTypes';
import type {BacktestPublishDetailItem} from '@/features/publishes/types/publishes';
import i18n, {t} from '@/i18n';

const fixture = vi.hoisted(() => ({
    owner: 17 as number | null,
    options: {} as {queryKey: readonly unknown[]; enabled: boolean; retry: boolean},
    query: {
        data: undefined as BacktestPublishDetailItem | undefined,
        isError: false,
        isSuccess: false,
        isFetching: false,
        error: null as unknown,
        refetch: vi.fn(),
    },
    mutation: {isPending: false, variables: undefined as string | undefined, mutate: vi.fn()},
    mutationOptions: {} as {
        onError: (error: unknown, action: 'start' | 'evaluate' | 'publish') => Promise<void>;
    },
    invalidations: vi.fn().mockResolvedValue(undefined),
    buttons: [] as {label: unknown; click: () => void; disabled?: boolean}[],
}));

// 只截取真实按钮事件；仍渲染AntD按钮，验证源组件在React尚未重绘时的同步互斥。
vi.mock('antd', async (importOriginal) => {
    const actual = await importOriginal<typeof import('antd')>();
    return {
        ...actual,
        Button: (props: ButtonProps) => {
            fixture.buttons.push({
                label: props.children,
                disabled: props.disabled,
                click: () =>
                    props.onClick?.({} as Parameters<NonNullable<ButtonProps['onClick']>>[0]),
            });
            return createElement(actual.Button, props);
        },
    };
});

// 仅固定外部状态与查询生命周期，实际组件、来源校验、按钮和导航均由真实渲染执行。
vi.mock('@tanstack/react-query', () => ({
    useQuery: (options: typeof fixture.options) => {
        fixture.options = options;
        return fixture.query;
    },
    useMutation: (options: typeof fixture.mutationOptions) => {
        fixture.mutationOptions = options;
        return fixture.mutation;
    },
    useQueryClient: () => ({invalidateQueries: fixture.invalidations}),
}));
vi.mock('@/store/auth-store', async (importOriginal) => ({
    ...(await importOriginal<typeof import('@/store/auth-store')>()),
    useAuthStore: (
        select: (value: {currentUser: {userId: number; roles: string[]} | null; sessionId: string | null; accessToken: string | null}) => unknown,
    ) =>
        select({
            currentUser: fixture.owner == null ? null : {userId: fixture.owner, roles: ['ADMIN']},
            sessionId: fixture.owner == null ? null : `fixture-session-${fixture.owner}`,
            accessToken: fixture.owner == null ? null : 'synthetic-access-token',
        }),
}));

import {BacktestRunActions} from '@/features/backtests/BacktestRunActions';

const run: BacktestRunDetailItem = {
    backtestRunId: 'brn-source',
    backtestConfigId: 'bcf-source',
    researchConfigId: 'rcf-source',
    sourceStrategyId: 'strategy-source',
    strategyVersionId: 'sv-source',
    status: 'SUCCEEDED',
    evaluationStatus: 'SUCCEEDED',
    publishStatus: 'SUCCEEDED',
    requestedAt: '2026-10-15T00:00:00Z',
    startedAt: null,
    finishedAt: null,
    strategyVersionSnapshotJson: '{}',
    paramSnapshotJson: '{}',
    configSnapshotJson: '{}',
    datasetSnapshotJson: '{}',
};
const publication: BacktestPublishDetailItem = {
    publishRecordId: 'pub-original+saved',
    backtestRunId: run.backtestRunId,
    backtestConfigId: run.backtestConfigId,
    researchConfigId: run.researchConfigId,
    sourceStrategyId: run.sourceStrategyId,
    strategyVersionId: run.strategyVersionId,
    targetStrategyDefinitionId: 'definition-published',
    publishStatus: 'SUCCEEDED',
    publishName: null,
    publishedAt: '2026-10-15T00:00:01Z',
    evaluationSummaryJson: '{}',
    failureCode: null,
    failureMessage: null,
    publishSnapshotJson: '{}',
    versionSnapshotJson: '{}',
};
function render(current = run, onChanged = vi.fn()) {
    return renderToStaticMarkup(
        createElement(
            MemoryRouter,
            null,
            createElement(BacktestRunActions, {run: current, onChanged}),
        ),
    );
}
function published() {
    fixture.query.data = {...publication};
    fixture.query.isSuccess = true;
}

beforeEach(async () => {
    // 当前用例的前提是没有待恢复评估；不可用存储的拒绝由专项目标用例保留。
    vi.stubGlobal('localStorage',{getItem:()=>null});
    await i18n.changeLanguage('en-US');
    fixture.owner = 17;
    Object.assign(fixture.query, {
        data: undefined,
        isError: false,
        isSuccess: false,
        isFetching: false,
        error: null,
    });
    fixture.mutation.isPending = false;
    fixture.mutation.variables = undefined;
    vi.clearAllMocks();
    fixture.buttons.length = 0;
    // SSR 不挂载宿主消息容器；只补齐外部提示端口，保留真实错误格式化和查询恢复。
    vi.spyOn(App, 'useApp').mockReturnValue({
        message: {error: vi.fn(), warning: vi.fn()},
    } as unknown as ReturnType<typeof App.useApp>);
});
afterEach(() => {vi.restoreAllMocks();vi.unstubAllGlobals();});

describe('original publication recovery', () => {
    it.each(['start', 'evaluate', 'publish'] as const)(
        '%s sends one command for repeated clicks before the pending render',
        (action) => {
            render({
                ...run,
                status: action === 'start' ? 'CREATED' : 'SUCCEEDED',
                evaluationStatus: action === 'publish' ? 'SUCCEEDED' : null,
                publishStatus: null,
            });
            const key =
                action === 'start'
                    ? 'workflowStartBacktest'
                    : action === 'evaluate'
                      ? 'workflowEvaluateRun'
                      : 'workflowPublishRun';
            const button = fixture.buttons.find((item) => item.label === t(`pages:${key}`));
            expect(button).toBeDefined();
            expect(button!.disabled).not.toBe(true);
            button!.click();
            button!.click();
            expect(fixture.mutation.mutate).toHaveBeenCalledOnce();
            const [sent, callbacks] = fixture.mutation.mutate.mock.calls[0] as [
                string,
                {onSettled: () => void},
            ];
            expect(sent).toBe(action);
            callbacks.onSettled();
            button!.click();
            expect(fixture.mutation.mutate).toHaveBeenCalledTimes(2);
        },
    );
    it.each(['start', 'evaluate'] as const)(
        '%s errors await original queries without sending another business command',
        async (action) => {
            const changed = vi.fn();
            const post = vi.spyOn(apiClient, 'post');
            let release!: () => void;
            const pending = new Promise<void>((resolve) => {
                release = resolve;
            });
            fixture.invalidations.mockReturnValueOnce(pending);
            render({...run, status: 'CREATED', publishStatus: null}, changed);
            const completed = fixture.mutationOptions.onError(
                {status: 409, code: 'STATE_CONFLICT', method: 'POST'},
                action,
            );
            expect(fixture.invalidations).toHaveBeenCalledWith({queryKey: backtestsQueryKeys.all});
            expect(fixture.invalidations).toHaveBeenCalledWith({
                queryKey: evaluationsQueryKeys.all,
            });
            expect(fixture.invalidations).toHaveBeenCalledWith({queryKey: publishesQueryKeys.all});
            expect(changed).not.toHaveBeenCalled();
            release();
            await completed;
            expect(changed).toHaveBeenCalledOnce();
            expect(post).not.toHaveBeenCalled();
            expect(fixture.mutation.mutate).not.toHaveBeenCalled();
        },
    );
    it('renders a durably failed run with a persistent explanation and no start action', () => {
        const html = render({
            ...run,
            status: 'FAILED',
            publishStatus: null,
            failureCode: 'BACKTEST_EXECUTION_FAILED',
        });
        const start = [...html.matchAll(/<button\b[^>]*>[\s\S]*?<\/button>/g)]
            .map((match) => match[0])
            .find((button) => button.includes(t('pages:workflowStartBacktest')));
        expect(start).toBeDefined();
        expect(start).toMatch(/\bdisabled=/);
        expect(html).toContain(t('pages:workflowRunFailed'));
        expect(html).toContain('BACKTEST_EXECUTION_FAILED');
        expect(fixture.mutation.mutate).not.toHaveBeenCalled();
    });
    it('reads the exact original run by GET and forwards cancellation without publishing', async () => {
        const signal = new AbortController().signal;
        const get = vi.spyOn(apiClient, 'get').mockResolvedValue({data: publication});
        const post = vi.spyOn(apiClient, 'post');
        expect(await publishesApi.forRun('brn+original/identity', signal)).toEqual(publication);
        expect(get).toHaveBeenCalledExactlyOnceWith(
            '/backtest-runs/brn%2Boriginal%2Fidentity/publish',
            {signal},
        );
        expect(post).not.toHaveBeenCalled();
    });
    it('restores the original identity and encoded SIM link from persisted query data', () => {
        published();
        const html = render();
        expect(html).toContain('pub-original+saved');
        expect(html).toContain('/paper-trading/runs?publishId=pub-original%2Bsaved');
        expect(fixture.mutation.mutate).not.toHaveBeenCalled();
        expect(fixture.options).toMatchObject({
            enabled: true,
            retry: false,
            queryKey: publishesQueryKeys.forRun(17, run.backtestRunId),
        });
    });
    it('separates current owner queries and does not use a disabled stale successful query for an unpublished run', () => {
        published();
        const html = render({...run, publishStatus: null});
        expect(html).not.toContain('/paper-trading/runs?publishId=');
        expect(fixture.options.enabled).toBe(false);
        fixture.owner = 18;
        render();
        expect(fixture.options.queryKey).toEqual(publishesQueryKeys.forRun(18, run.backtestRunId));
        fixture.owner = null;
        render();
        expect(fixture.options.enabled).toBe(false);
    });
    it('keeps read errors distinct from publication failure and offers only a read retry', () => {
        published();
        fixture.query.isError = true;
        fixture.query.error = {status: 503, code: 'SERVICE_UNAVAILABLE', method: 'GET'};
        const html = render();
        expect(html).toContain(t('pages:workflowPublishReadFailed'));
        expect(html).toContain(t('pages:workflowReloadPublish'));
        expect(html).not.toContain('/paper-trading/runs?publishId=');
        expect(html).not.toContain('pub-original+saved');
        expect(fixture.mutation.mutate).not.toHaveBeenCalled();
    });
    it('rejects wrong parents, blank identity/status, null and damaged navigation without rendering a continuation', () => {
        for (const value of [
            {...publication, backtestRunId: 'another-run'},
            {...publication, backtestConfigId: 'another-config'},
            {...publication, publishRecordId: ''},
            {...publication, publishRecordId: '\ud800'},
            {...publication, publishStatus: ''},
            {...publication, failureCode: {unexpected: 'object'}},
            null,
        ]) {
            fixture.query.data = value as unknown as BacktestPublishDetailItem;
            fixture.query.isSuccess = true;
            const html = render();
            expect(html).toContain(t('pages:workflowPublishSourceMismatch'));
            expect(html).not.toContain('/paper-trading/runs?publishId=');
        }
    });
    it('shows a pending original read rather than a fabricated successful publication', () => {
        fixture.query.isFetching = true;
        const html = render();
        expect(html).toContain(t('pages:workflowReadingPublish'));
        expect(html).not.toContain('/paper-trading/runs?publishId=');
    });
    it('preserves an actual failed record and does not present it as a successful SIM source', () => {
        published();
        fixture.query.data = {
            ...publication,
            publishStatus: 'FAILED',
            failureCode: 'PUBLISH_FAILED',
        };
        const html = render({...run, publishStatus: 'FAILED'});
        expect(html).toContain(t('pages:workflowPublishNotSucceeded'));
        expect(html).toContain('PUBLISH_FAILED');
        expect(html).not.toContain('/paper-trading/runs?publishId=');
        expect(fixture.mutation.mutate).not.toHaveBeenCalled();
    });
});
