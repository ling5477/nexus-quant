import {parseExactJson} from '@/api/exact-json';
import type {BacktestRunDetailItem} from './backtestsTypes';

/** 研究来源与执行版本可属于不同策略，只从匹配当前绑定身份的冻结快照读取父策略。 */
export function boundVersionStrategyCode(
    versionId: string | null | undefined,
    snapshotJson: string | null | undefined,
): string | null {
    if (!versionId || !snapshotJson) return null;
    try {
        const snapshot: unknown = JSON.parse(snapshotJson);
        if (!snapshot || typeof snapshot !== 'object' || Array.isArray(snapshot)) return null;
        const value = snapshot as Record<string, unknown>;
        return value.strategyVersionId === versionId &&
            typeof value.strategyCode === 'string' &&
            value.strategyCode.trim()
            ? value.strategyCode
            : null;
    } catch {
        // 历史缺失或损坏的快照不推断为研究来源；页面保留原 ID 和快照供核查。
        return null;
    }
}

/** 只投影本次运行的冻结事实；损坏或缺失字段保持未知，不回退到仍可编辑的配置与目录。 */
export function frozenBacktestFacts(
    run: BacktestRunDetailItem | null | undefined,
    configId: string,
    runId: string | null,
) {
    if (!runId || run?.backtestRunId !== runId || run.backtestConfigId !== configId) return null;
    const object = (json: string | null) => {
        try {
            const value = parseExactJson(
                json ?? 'null',
                new Set(['initialCapital', 'barCount', 'gapCount']),
            );
            return value && typeof value === 'object' && !Array.isArray(value)
                ? (value as Record<string, unknown>)
                : null;
        } catch {
            return null;
        }
    };
    const text = (value: Record<string, unknown> | null, key: string) =>
        typeof value?.[key] === 'string' && value[key].trim() ? (value[key] as string) : null;
    const configuration = object(run.configSnapshotJson);
    const snapshot = object(run.datasetSnapshotJson);
    const datasetId = text(snapshot, 'datasetId');
    return {
        strategyVersionId: run.strategyVersionId,
        strategyVersionSnapshotJson: run.strategyVersionSnapshotJson,
        paramSnapshotJson: run.paramSnapshotJson,
        configSnapshotJson: run.configSnapshotJson,
        startTime: text(configuration, 'startTime'),
        endTime: text(configuration, 'endTime'),
        initialCapital: text(configuration, 'initialCapital'),
        createdAt: run.requestedAt,
        datasetId,
        dataset: datasetId
            ? {
                  datasetId,
                  exchangeCode: text(snapshot, 'exchangeCode'),
                  symbol: text(snapshot, 'symbol'),
                  interval: text(snapshot, 'interval'),
                  startTime: text(snapshot, 'startTime'),
                  endTime: text(snapshot, 'endTime'),
                  barCount: text(snapshot, 'barCount'),
                  gapCount: text(snapshot, 'gapCount'),
                  qualityStatus: text(snapshot, 'qualityStatus'),
                  status: text(snapshot, 'status'),
              }
            : null,
    };
}
