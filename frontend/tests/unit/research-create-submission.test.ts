import {createElement, type PropsWithChildren} from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import {MemoryRouter} from 'react-router-dom';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {App, type FormProps} from 'antd';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import type {ResearchConfigCreateRequest} from '@/features/research/types/research';
import i18n from '@/i18n';

const fixture = vi.hoisted(() => ({
    submit: undefined as ((value: ResearchConfigCreateRequest) => void) | undefined,
    mutate: vi.fn(),
}));

vi.mock('@/features/research/hooks/useResearchListQuery', () => ({
    useResearchListQuery: () => ({data: undefined, isFetching: false}),
    useResearchDetailQuery: () => ({data: undefined, isLoading: false}),
    useCreateResearchMutation: () => ({mutate: fixture.mutate, isPending: false}),
    useArchiveResearchMutation: () => ({mutateAsync: vi.fn(), isPending: false}),
}));
vi.mock('@/features/strategies/components/StrategyDefinitionPicker', () => ({
    StrategyDefinitionPicker: () => null,
}));
vi.mock('@/features/marketdata/components/MarketdataDatasetPicker', () => ({
    MarketdataDatasetPicker: () => null,
}));
vi.mock('antd', async (importOriginal) => {
    const actual = await importOriginal<typeof import('antd')>();
    return {
        ...actual,
        // SSR不挂载Portal，只替换容器；真实页面、Form、校验与onFinish保持不变。
        Drawer: ({children}: PropsWithChildren) => createElement('section', null, children),
        Form: Object.assign((props: FormProps<ResearchConfigCreateRequest>) => {
            if (props.name === 'research-createForm') fixture.submit = props.onFinish;
            return createElement(actual.Form<ResearchConfigCreateRequest>, props);
        }, actual.Form),
    };
});

import {ResearchPage} from '@/pages/research/ResearchPage';

const input: ResearchConfigCreateRequest = {
    sourceStrategyId: 'str-owned',
    name: 'Original draft',
    parameterSchema: '{}',
    parameterDefaults: '{}',
    datasetSpec: '{"datasetId":"dataset-owned"}',
};

beforeEach(async () => {
    await i18n.changeLanguage('en-US');
    vi.clearAllMocks();
    fixture.submit = undefined;
    vi.spyOn(App, 'useApp').mockReturnValue({
        message: {error: vi.fn(), warning: vi.fn(), success: vi.fn()},
    } as unknown as ReturnType<typeof App.useApp>);
});
afterEach(() => vi.restoreAllMocks());

function render() {
    // 页面查询保持真实Hook；每次渲染独立缓存，提交幂等测试不依赖其他用例的查询状态。
    const client = new QueryClient({defaultOptions: {queries: {retry: false}}});
    try {
        renderToStaticMarkup(
            createElement(
                QueryClientProvider,
                {client},
                createElement(
                    MemoryRouter,
                    {
                        initialEntries: ['/research?create=true&sourceStrategyId=str-owned'],
                    },
                    createElement(ResearchPage),
                ),
            ),
        );
    } finally {
        client.clear();
    }
    expect(fixture.submit).toBeTypeOf('function');
}

describe('research create submission', () => {
    it('sends one original request when two finish events precede the pending render', () => {
        render();
        fixture.submit!(input);
        fixture.submit!(input);
        expect(fixture.mutate).toHaveBeenCalledOnce();
        expect(fixture.mutate.mock.calls[0][0]).toMatchObject(input);
    });

    it('does not retry on error and accepts a corrected draft after the attempt settles', () => {
        render();
        fixture.submit!(input);
        const callbacks = fixture.mutate.mock.calls[0][1] as {
            onError: (error: unknown) => void;
            onSettled: () => void;
        };
        callbacks.onError({status: 400, code: 'BAD_REQUEST', method: 'POST'});
        expect(fixture.mutate).toHaveBeenCalledOnce();
        callbacks.onSettled();
        fixture.submit!({...input, name: 'Corrected draft'});
        expect(fixture.mutate).toHaveBeenCalledTimes(2);
        expect(fixture.mutate.mock.calls[1][0]).toMatchObject({...input, name: 'Corrected draft'});
    });
});
