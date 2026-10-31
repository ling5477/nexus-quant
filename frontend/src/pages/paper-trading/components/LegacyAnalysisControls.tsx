import {createContext, useContext, type ReactNode} from 'react';
import {Button, Drawer, List, Space, Typography} from 'antd';
import {Link, useSearchParams} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import {updateLegacyParams, type LegacyFilterKey} from '@/features/paper-trading/legacy-analysis';
import type {PaperExecutionRunDiagnostic} from '@/features/paper-trading/types/paper-trading';
import {localizedPaperLabel} from './paperAnalysisOptions';

export function useLegacyFilter<T extends string>(key: LegacyFilterKey, fallback: T) {
    const [params, setParams] = useSearchParams();
    return [
        (params.get(key) ?? fallback) as T,
        // 明确的筛选输入必须先提交当前URL；否则下一个控件可能仍用旧闭包覆盖前一项选择。
        (value: T) =>
            setParams(updateLegacyParams(params, {[key]: value === fallback ? null : value}), {
                flushSync: true,
            }),
    ] as const;
}

/** 清除一个表的多项条件时必须一次导航，不能让后一次写入恢复已清除的旧条件。 */
export function useResetLegacyFilters(keys: LegacyFilterKey[]) {
    const [params, setParams] = useSearchParams();
    return () =>
        setParams(updateLegacyParams(params, Object.fromEntries(keys.map((key) => [key, null]))), {
            flushSync: true,
        });
}

const LegacyRuns = createContext<PaperExecutionRunDiagnostic[]>([]);

/** 版本组只能用真实非空版本身份关联运行；缺失身份的分组键不能被当作正式版本ID。 */
export function LegacyAnalysisRelations({
    runs,
    children,
}: {
    runs: PaperExecutionRunDiagnostic[];
    children: ReactNode;
}) {
    const {t} = useTranslation('pages');
    const [params, setParams] = useSearchParams();
    const version = params.get('legacyVersion');
    const matches = version ? runs.filter((run) => run.strategyVersionId === version) : [];
    return (
        <LegacyRuns.Provider value={runs}>
            {children}
            <Drawer
                open={version !== null}
                title={t('pages:legacyAnalysis.versionRuns')}
                width={640}
                onClose={() => setParams(updateLegacyParams(params, {legacyVersion: null}))}
            >
                <Typography.Paragraph copyable>{version}</Typography.Paragraph>
                <List
                    dataSource={matches}
                    locale={{emptyText: t('pages:legacyAnalysis.noVersionRuns')}}
                    renderItem={(run) => (
                        <List.Item>
                            <Space direction="vertical">
                                <LegacyAnalysisObjectLink kind="run" id={run.paperRunId} />
                                <Typography.Text title={run.status}>
                                    {localizedPaperLabel(run.status)}
                                </Typography.Text>
                                {run.publishId && (
                                    <LegacyAnalysisObjectLink kind="publish" id={run.publishId} />
                                )}
                            </Space>
                        </List.Item>
                    )}
                />
            </Drawer>
        </LegacyRuns.Provider>
    );
}

/** 运行与发布沿现有正式路由定位；版本组在当前快照中展开，避免猜测未知的strategyCode。 */
export function LegacyAnalysisObjectLink({
    kind,
    id,
}: {
    kind: 'run' | 'strategy' | 'publish';
    id: string;
}) {
    const runs = useContext(LegacyRuns);
    const {t} = useTranslation('pages');
    const [params, setParams] = useSearchParams();
    const known = runs.some(
        (run) =>
            (kind === 'run'
                ? run.paperRunId
                : kind === 'strategy'
                  ? run.strategyVersionId
                  : run.publishId) === id && id.trim(),
    );
    if (!known) {
        // 装配器的缺失分组键不是业务ID；真正命中的版本/发布仍保留原标识和原跳转。
        const label =
            kind === 'strategy' && id === '(未绑定策略版本)'
                ? t('paperRules.missingVersion')
                : kind === 'publish' && id === '(未知发布)'
                  ? t('paperRules.missingPublish')
                  : id;
        return (
            <span className="nq-mono" title={id}>
                {label}
            </span>
        );
    }
    if (kind === 'strategy')
        return (
            <Button
                type="link"
                size="small"
                className="nq-mono"
                onClick={() => setParams(updateLegacyParams(params, {legacyVersion: id}))}
            >
                {id}
            </Button>
        );
    return (
        <Link
            className="nq-mono"
            to={
                kind === 'run'
                    ? `/paper-trading/runs?paperRunId=${encodeURIComponent(id)}&analysisSource=legacy`
                    : `/publishes?publishId=${encodeURIComponent(id)}`
            }
        >
            {id}
        </Link>
    );
}
