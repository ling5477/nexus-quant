import {Button} from 'antd';
import {useMemo} from 'react';
import {useNavigate, useSearchParams} from 'react-router-dom';

import {ExceptionView} from '@/components/standalone/ExceptionView';
import {StandaloneSurface} from '@/components/standalone/StandaloneSurface';

function buildFallbackRequestId(): string {
    // 无 request id 时生成可读占位,便于用户口头/截图反馈;不含任何敏感信息。
    return `req-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}

/**
 * SystemErrorPage — 系统错误异常页(v2)。
 * 包含 request id + 发生时间 + 返回入口,便于用户凭事实联系运维;不暴露异常栈/内部 path。
 */
export function SystemErrorPage() {
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();

    // request id 与时间在进入页面时固定一次,避免重渲染时跳动。
    const requestId = useMemo(
        () => searchParams.get('requestId')?.trim() || buildFallbackRequestId(),
        [searchParams],
    );
    const occurredAt = useMemo(() => new Date().toLocaleString('zh-CN', {hour12: false}), []);

    return (
        <StandaloneSurface ariaLabel="系统错误">
            <ExceptionView
                tone="danger"
                code="500"
                kicker="系统错误"
                title="控制台遇到一个错误"
                description="请求未能完成。错误已被记录,可凭下面的信息联系运维定位。"
                meta={[
                    {label: 'Request ID', value: requestId},
                    {label: '发生时间', value: occurredAt},
                ]}
                nextSteps={<p style={{margin: 0}}>可先返回控制台重试;若反复出现,请把上面的 Request ID 提供给运维。</p>}
                actions={
                    <>
                        <Button type="primary" onClick={() => navigate('/')}>
                            返回控制台
                        </Button>
                        <Button onClick={() => navigate(-1)}>返回上一页</Button>
                    </>
                }
            />
        </StandaloneSurface>
    );
}
