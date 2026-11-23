interface QueryEvidence {
    isSuccess: boolean;
    isFetching: boolean;
}

/** 只判断当前查询证据是否完整；不把缓存、未知心跳或焦点运行推导为全局健康。 */
export function hasCurrentFocusEvidence(
    alerts: QueryEvidence,
    heartbeats: QueryEvidence,
    heartbeatStatus?: string,
) {
    return alerts.isSuccess && !alerts.isFetching
        && heartbeats.isSuccess && !heartbeats.isFetching
        && heartbeatStatus === 'OK';
}
