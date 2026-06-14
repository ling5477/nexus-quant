import {Button} from 'antd';
import {useNavigate, useSearchParams} from 'react-router-dom';

import {ExceptionView} from '@/components/standalone/ExceptionView';
import {StandaloneSurface} from '@/components/standalone/StandaloneSurface';

/**
 * ForbiddenPage — 无权限异常页(v2)。
 * 说明缺少哪个角色、如何申请;只做展示与跳转,不在前端绕过任何后端权限校验。
 */
export function ForbiddenPage() {
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    // 缺失角色由调用方通过 ?role= 注入(后端权限错误的可读映射),缺省给通用文案。
    const missingRole = searchParams.get('role')?.trim() || '未指定';

    return (
        <StandaloneSurface ariaLabel="无访问权限">
            <ExceptionView
                tone="warning"
                code="403"
                kicker="无访问权限"
                title="你没有访问该页面的权限"
                description="当前账号缺少访问此功能所需的角色,因此被后端权限校验拦截。"
                meta={[{label: '缺少角色', value: missingRole}]}
                nextSteps={
                    <p style={{margin: 0}}>请向管理员申请对应角色;获得授权后重新进入即可访问。</p>
                }
                actions={
                    <>
                        <Button type="primary" onClick={() => navigate('/')}>
                            返回控制台
                        </Button>
                        <Button onClick={() => navigate('/login')}>切换账号</Button>
                    </>
                }
            />
        </StandaloneSurface>
    );
}
