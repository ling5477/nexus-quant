import {Alert, Button, Card, Space, Typography} from 'antd';
import {useMemo, useState} from 'react';
import {useSearchParams} from 'react-router-dom';

import {ValidationReviewCaseDrawer} from '@/components/validation-review/ValidationReviewCaseDrawer';
import {ValidationReviewQueue} from '@/components/validation-review/ValidationReviewQueue';
import {useValidationReviewListQuery} from '@/hooks/useValidationReviewQueries';
import {useAuthStore} from '@/store/auth-store';
import type {AppApiError} from '@/types/api';
import type {ValidationReviewSeverity, ValidationReviewState} from '@/types/validation-review';

const {Paragraph, Text, Title} = Typography;
const PAGE_LIMIT = 20;
const REVIEW_CASE_ID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

/** URL 只接受 canonical UUID；非法、空值或超长值都不进入 API/query key。 */
function normalizeReviewCaseId(value: string | null): string | null {
    if (!value || value.length > 36 || !REVIEW_CASE_ID_PATTERN.test(value)) {
        return null;
    }
    return value;
}

/**
 * GateV-4 Review Workbench orchestration。
 *
 * 职责：连接 URL 恢复的 case selection、server-side queue filters、ADMIN owner scope、detail/events 和
 * lifecycle actions。后端仍是权限与状态机最终来源；本组件不创建 case、不保存服务端数据到 Zustand。
 */
export function ValidationReviewWorkbench() {
    const [searchParams, setSearchParams] = useSearchParams();
    const currentUser = useAuthStore((state) => state.currentUser);
    const isAdmin = Boolean(currentUser?.roles.some((role) => role.toUpperCase() === 'ADMIN'));
    const rawSelectedCaseId = searchParams.get('reviewCaseId');
    const selectedCaseId = normalizeReviewCaseId(rawSelectedCaseId);
    const invalidSelection = Boolean(rawSelectedCaseId && !selectedCaseId);
    const [state, setState] = useState<ValidationReviewState>();
    const [severity, setSeverity] = useState<ValidationReviewSeverity>();
    const [ownerId, setOwnerId] = useState<number>();
    const [offset, setOffset] = useState(0);
    const request = useMemo(() => ({
        state,
        severity,
        ownerId: isAdmin ? ownerId : undefined,
        limit: PAGE_LIMIT,
        offset,
    }), [isAdmin, offset, ownerId, severity, state]);
    const listQuery = useValidationReviewListQuery(request);

    function selectCase(caseId: string | null) {
        const next = new URLSearchParams(searchParams);
        if (caseId) next.set('reviewCaseId', caseId);
        else next.delete('reviewCaseId');
        // Push history so browser back/forward can restore the previous case selection.
        setSearchParams(next);
    }

    return (
        <Card className="page-section" variant="borderless" data-testid="validation-review-workbench">
            <Space direction="vertical" size={16} style={{display: 'flex'}}>
                <div>
                    <Title level={3} style={{marginBottom: 4}}>Validation Review Workbench</Title>
                    <Text strong>本地验证审查工作台</Text>
                    <Paragraph type="secondary" style={{marginTop: 8, marginBottom: 0}}>
                        查询 GateV-2 durable review cases，查看安全详情和 lifecycle events，并执行四类有限人工动作。
                    </Paragraph>
                </div>
                <Alert
                    type="warning"
                    showIcon
                    message="诊断审查流程，不构成交易授权，也不会启动 LIVE 或 Shadow trading。"
                    description="所有权限、owner scope、乐观锁和状态流转仍由后端强制执行；按钮隐藏不替代服务端安全控制。"
                />
                {invalidSelection ? (
                    <Alert
                        type="error"
                        showIcon
                        message="reviewCaseId 无效"
                        description="非法或超长 case ID 不会触发 detail/events 请求。"
                        action={<Button size="small" onClick={() => selectCase(null)}>清除失效选择</Button>}
                    />
                ) : null}
                <ValidationReviewQueue
                    data={listQuery.data ?? []}
                    error={listQuery.error as AppApiError | null}
                    isLoading={listQuery.isLoading}
                    isFetching={listQuery.isFetching}
                    isAdmin={isAdmin}
                    selectedCaseId={selectedCaseId}
                    state={state}
                    severity={severity}
                    ownerId={ownerId}
                    limit={PAGE_LIMIT}
                    offset={offset}
                    onStateChange={(value) => { setState(value); setOffset(0); }}
                    onSeverityChange={(value) => { setSeverity(value); setOffset(0); }}
                    onOwnerChange={(value) => { setOwnerId(value); setOffset(0); }}
                    onSelectCase={selectCase}
                    onPageChange={setOffset}
                    onRefresh={() => listQuery.refetch()}
                />
                <ValidationReviewCaseDrawer caseId={selectedCaseId} onClose={() => selectCase(null)}/>
            </Space>
        </Card>
    );
}
