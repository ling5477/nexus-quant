import {
    AreaChartOutlined,
    DashboardOutlined,
    ExperimentOutlined,
    FileSearchOutlined,
    FundProjectionScreenOutlined,
    RocketOutlined,
    ScheduleOutlined,
    SettingOutlined,
    SwapOutlined,
} from '@ant-design/icons';

import type {AppNavItem} from '@/types/navigation';

export const appNavItems: AppNavItem[] = [
    {
        key: 'dashboard',
        path: '/dashboard',
        label: 'Dashboard',
        icon: <DashboardOutlined/>,
        title: '控制台总览',
        description: '查看当前登录态、环境标识与 GateG 入口导航。',
    },
    {
        key: 'strategies',
        path: '/strategies',
        label: '策略定义',
        icon: <SettingOutlined/>,
        title: '策略定义',
        description: '策略列表、策略详情和后续触发操作入口。',
    },
    {
        key: 'schedules',
        path: '/schedules',
        label: '调度计划',
        icon: <ScheduleOutlined/>,
        title: '调度计划',
        description: '调度列表、扫描任务和运行编排入口。',
    },
    {
        key: 'runs',
        path: '/runs',
        label: '运行记录',
        icon: <RocketOutlined/>,
        title: '运行记录',
        description: '策略运行查询、状态追踪和详情入口。',
    },
    {
        key: 'research',
        path: '/research',
        label: '研究配置',
        icon: <ExperimentOutlined/>,
        title: '研究配置',
        description: '研究参数列表和详情扩展点。',
    },
    {
        key: 'backtests',
        path: '/backtests',
        label: '回测配置',
        icon: <FundProjectionScreenOutlined/>,
        title: '回测配置',
        description: '回测配置列表与后续创建入口。',
    },
    {
        key: 'evaluations',
        path: '/evaluations',
        label: '评估结果',
        icon: <AreaChartOutlined/>,
        title: '评估结果',
        description: '评估列表、指标摘要和详情扩展点。',
    },
    {
        key: 'publishes',
        path: '/publishes',
        label: '发布结果',
        icon: <FileSearchOutlined/>,
        title: '发布结果',
        description: '发布结果列表与链路审计入口。',
    },
    {
        key: 'trade-validation',
        path: '/trade-validation',
        label: '交易验证',
        icon: <SwapOutlined/>,
        title: '交易验证',
        description: '下单、撤单、对账与恢复页面入口。',
    },
];

export function resolveMenuKey(pathname: string): string {
    const matched = [...appNavItems]
        .sort((left, right) => right.path.length - left.path.length)
        .find((item) => pathname === item.path || pathname.startsWith(`${item.path}/`));

    return matched?.key ?? 'dashboard';
}
