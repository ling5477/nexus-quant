import {Button, Card, Empty, Flex, Skeleton, Space, Table, Tag} from 'antd';

import {PageHero} from '@/components/page/PageHero';

interface ListPageShellProps {
    title: string;
    description: string;
    tableTitle: string;
}

const placeholderColumns = [
    {
        title: '主键 / 编码',
        dataIndex: 'primaryKey',
        key: 'primaryKey',
    },
    {
        title: '摘要',
        dataIndex: 'summary',
        key: 'summary',
    },
    {
        title: '状态',
        dataIndex: 'status',
        key: 'status',
        render: () => <Tag color="default">待接入</Tag>,
    },
    {
        title: '最近更新时间',
        dataIndex: 'updatedAt',
        key: 'updatedAt',
    },
];

/**
 * ListPageShell 负责交付 GateG-1 的统一首屏壳子。
 * Why:
 * 当前批次只建立可运行的页面结构，不进入复杂业务字段；
 * 因此每个业务页共享同一套查询区骨架、表格占位和空状态，后续批次在此基础上替换真实列定义与查询表单即可。
 */
export function ListPageShell({title, description, tableTitle}: ListPageShellProps) {
    return (
        <Space direction="vertical" size={16} style={{display: 'flex'}}>
            <Card className="page-card" bordered={false}>
                <PageHero
                    title={title}
                    description={description}
                    tip="当前页面已接入正式路由、菜单与控制台布局。GateG-1 先交付查询区与列表区壳子，后续 GateG-3/4/5 再按实际接口补齐字段和操作。"
                />
            </Card>
            <Card
                className="page-section"
                bordered={false}
                title="查询区"
                extra={
                    <Space>
                        <Button disabled type="primary">
                            查询
                        </Button>
                        <Button disabled>重置</Button>
                    </Space>
                }
            >
                <Flex gap={12} wrap="wrap">
                    <Skeleton.Input active style={{width: 240}} size="large"/>
                    <Skeleton.Input active style={{width: 200}} size="large"/>
                    <Skeleton.Input active style={{width: 180}} size="large"/>
                </Flex>
            </Card>
            <Card className="page-section" bordered={false} title={tableTitle}>
                <Table
                    columns={placeholderColumns}
                    dataSource={[]}
                    pagination={false}
                    scroll={{x: 900}}
                    locale={{
                        emptyText: (
                            <Empty
                                description="当前批次未接入真实列表数据。列表结构、空状态与后续扩展点已就位。"
                            />
                        ),
                    }}
                />
            </Card>
        </Space>
    );
}
