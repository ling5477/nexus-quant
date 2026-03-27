import {Button, Result} from 'antd';
import {useNavigate} from 'react-router-dom';

export function NotFoundPage() {
    const navigate = useNavigate();

    return (
        <Result
            status="404"
            title="404"
            subTitle="目标页面不存在或当前路由尚未接入。"
            extra={
                <Button type="primary" onClick={() => navigate('/dashboard')}>
                    返回控制台
                </Button>
            }
        />
    );
}
