import {Button} from 'antd';
import {useNavigate} from 'react-router-dom';

import {ExceptionView} from '@/components/standalone/ExceptionView';
import {StandaloneSurface} from '@/components/standalone/StandaloneSurface';

/**
 * WelcomePage — 空系统初始化引导页(v2)。
 * 当控制台还没有可用数据时,给出明确的第一步动作,而不是空白页。
 */
export function WelcomePage() {
    const navigate = useNavigate();

    return (
        <StandaloneSurface ariaLabel="系统待初始化">
            <ExceptionView
                tone="info"
                kicker="系统待初始化"
                title="控制台尚未配置数据"
                description="当前还没有可用的账户、策略或行情数据。完成下面任一步即可开始。"
                nextSteps={
                    <>
                        <div className="nq-exception__next-title">第一步</div>
                        <ol className="nq-exception__steps">
                            <li>创建 Paper 账户:用于模拟交易与回测的资金上下文。</li>
                            <li>导入策略:登记策略定义与可发布的版本。</li>
                            <li>配置市场数据:同步标的目录与历史行情。</li>
                        </ol>
                    </>
                }
                actions={
                    <>
                        <Button type="primary" onClick={() => navigate('/accounts')}>
                            创建 Paper 账户
                        </Button>
                        <Button onClick={() => navigate('/strategies')}>导入策略</Button>
                        <Button onClick={() => navigate('/marketdata')}>配置市场数据</Button>
                    </>
                }
            />
        </StandaloneSurface>
    );
}
