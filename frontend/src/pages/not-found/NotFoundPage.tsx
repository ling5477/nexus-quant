import {Button} from 'antd';
import {useNavigate} from 'react-router-dom';

import {ExceptionView} from '@/components/standalone/ExceptionView';
import {StandaloneSurface} from '@/components/standalone/StandaloneSurface';

/**
 * NotFoundPage — 404 异常页(v2)。
 * 用统一异常表现层替代 AntD 默认 404 模板,与其余异常页同源。
 */
export function NotFoundPage() {
    const navigate = useNavigate();

    return (
        <StandaloneSurface ariaLabel="页面未找到">
            <ExceptionView
                tone="neutral"
                code="404"
                kicker="页面未找到"
                title="找不到这个页面"
                description="目标页面不存在,或当前路由尚未接入。"
                actions={
                    <Button type="primary" onClick={() => navigate('/dashboard')}>
                        返回控制台
                    </Button>
                }
            />
        </StandaloneSurface>
    );
}
