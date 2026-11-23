import {useMemo} from 'react';
import {useSearchParams} from 'react-router-dom';

import {
    hasQueryValue,
    queryFromSearchParams,
    StrategyValidationWorkspace,
} from '@/features/validation/StrategyValidationWorkspace';
import type {StrategyValidationQuery} from '@/features/validation/types/strategy-validation';

/**
 * 页面层只协调 URL、查询提交态与 feature composition；业务 section 和 server state 由 validation feature 持有。
 */
export function StrategyValidationPage() {
    const [searchParams, setSearchParams] = useSearchParams();
    // URL 定义已提交查询；忽略复核事项选择，避免切换事项改变业务范围或历史返回失配。
    const querySearch = new URLSearchParams(
        queryFromSearchParams(searchParams) as Record<string, string>,
    ).toString();
    const initialQuery = useMemo(
        () => queryFromSearchParams(new URLSearchParams(querySearch)),
        [querySearch],
    );
    // 空筛选也可查询缺失证据；显式提交标记区分该操作与清空，刷新及历史导航仍可恢复。
    const queryRequested = hasQueryValue(initialQuery) || searchParams.get('querySubmitted') === 'true';
    const submittedQuery = queryRequested ? initialQuery : null;

    function submitQuery(query: StrategyValidationQuery) {
        const next = new URLSearchParams(query as Record<string, string>);
        if (!hasQueryValue(query)) next.set('querySubmitted', 'true');
        setSearchParams(next);
    }

    function resetQuery() {
        setSearchParams({});
    }

    return (
        <StrategyValidationWorkspace
            initialQuery={initialQuery}
            submittedQuery={submittedQuery}
            onSubmit={submitQuery}
            onReset={resetQuery}
        />
    );
}
