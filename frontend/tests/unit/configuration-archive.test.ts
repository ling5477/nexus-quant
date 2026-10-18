import {createElement} from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import {App, type ButtonProps, type ModalFuncProps} from 'antd';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {apiClient} from '@/api/client';
import {researchApi} from '@/features/research/api/research';
import {backtestsApi} from '@/features/backtests/backtestsApi';
import i18n from '@/i18n';

const fixture = vi.hoisted(() => ({
    click: undefined as (() => void) | undefined,
    confirm: vi.fn(),
}));
vi.mock('antd', async (importOriginal) => {
    const actual = await importOriginal<typeof import('antd')>();
    return {
        ...actual,
        Button: (props: ButtonProps) => {
            fixture.click = () =>
                props.onClick?.({} as Parameters<NonNullable<ButtonProps['onClick']>>[0]);
            return createElement(actual.Button, props);
        },
    };
});

import {
    ConfigurationArchiveAction,
    createArchiveSubmission,
} from '@/features/research/components/ConfigurationArchiveAction';

beforeEach(async () => {
    await i18n.changeLanguage('en-US');
    vi.clearAllMocks();
    vi.spyOn(App, 'useApp').mockReturnValue({
        modal: {confirm: fixture.confirm},
        message: {error: vi.fn(), warning: vi.fn(), success: vi.fn()},
    } as unknown as ReturnType<typeof App.useApp>);
});
afterEach(() => vi.restoreAllMocks());

describe('configuration archive contract', () => {
    it.each([
        {api: researchApi, root: 'research-configs'},
        {api: backtestsApi, root: 'backtest-configs'},
    ])('uses the existing $root endpoint with the original encoded ID', async ({api, root}) => {
        const result = {status: 'ARCHIVED', archiveReason: 'Reviewed fixture'};
        const post = vi.spyOn(apiClient, 'post').mockResolvedValue({data: result});
        expect(await api.archive('config+original/identity', 'Reviewed fixture')).toEqual(result);
        expect(post.mock.calls[0][0]).toBe(`/${root}/config%2Boriginal%2Fidentity/archive`);
        expect(post.mock.calls[0][1]).toEqual({archiveReason: 'Reviewed fixture'});
        expect(post).toHaveBeenCalledOnce();
    });

    it.each([undefined, 'ARCHIVED'])('disables archiving when status is %s', (status) => {
        const html = renderToStaticMarkup(
            createElement(ConfigurationArchiveAction, {
                label: 'Archive this configuration',
                configurationId: 'config-original',
                configuration: {name: 'Original configuration', status},
                pending: false,
                onArchive: vi.fn(),
            }),
        );
        const button = [...html.matchAll(/<button\b[^>]*>[\s\S]*?<\/button>/g)]
            .map((match) => match[0])
            .find((value) => value.includes('Archive this configuration'));
        expect(button).toMatch(/\bdisabled=/);
        expect(fixture.confirm).not.toHaveBeenCalled();
    });

    it('shares one pending command, releases rejection and permits retry with the same input', async () => {
        const gate = createArchiveSubmission();
        let reject!: (reason: unknown) => void;
        const submit = vi.fn(
            () =>
                new Promise((_resolve, fail) => {
                    reject = fail;
                }),
        );
        const first = gate.run(submit);
        const repeated = gate.run(submit);
        expect(repeated).toBe(first);
        await Promise.resolve();
        expect(submit).toHaveBeenCalledOnce();
        reject({status: 400});
        await expect(first).rejects.toMatchObject({status: 400});
        const retry = vi.fn().mockResolvedValue({status: 'ARCHIVED'});
        expect(await gate.run(retry)).toEqual({status: 'ARCHIVED'});
        expect(retry).toHaveBeenCalledOnce();
    });
});
