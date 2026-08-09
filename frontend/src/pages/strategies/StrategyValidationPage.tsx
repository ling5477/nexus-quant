import {useMemo, useState} from 'react';
import {useSearchParams} from 'react-router-dom';

import {
    hasQueryValue,
    queryFromSearchParams,
    StrategyValidationWorkspace,
} from '@/features/validation/StrategyValidationWorkspace';
import type {StrategyValidationQuery} from '@/types/strategy-validation';

/**
 * 页面层只协调 URL、查询提交态与 feature composition；业务 section 和 server state 由 validation feature 持有。
 */
export function StrategyValidationPage() {
    const [searchParams, setSearchParams] = useSearchParams();
    const initialQuery = useMemo(() => queryFromSearchParams(searchParams), [searchParams]);
    const [submittedQuery, setSubmittedQuery] = useState<StrategyValidationQuery | null>(
        hasQueryValue(initialQuery) ? initialQuery : null,
    );

    function submitQuery(query: StrategyValidationQuery) {
        setSubmittedQuery(query);
        setSearchParams(query as Record<string, string>);
    }

    function resetQuery() {
        setSubmittedQuery(null);
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
