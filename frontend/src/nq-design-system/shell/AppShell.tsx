import {useEffect, useState, type ReactNode} from 'react';
import {Button, Drawer, Grid, Layout} from 'antd';
import {MenuFoldOutlined, MenuUnfoldOutlined} from '@ant-design/icons';
import {useTranslation} from 'react-i18next';

export interface AppShellProps {
    nav: ReactNode;
    brand?: ReactNode;
    topRight?: ReactNode;
    header?: ReactNode;
    pageHeader?: ReactNode;
    riskBanner?: ReactNode;
    collapsed?: boolean;
    onCollapsedChange?: (value: boolean) => void;
    children: ReactNode;
}

/** 正式控制台与设计预览共用产品壳；窄屏抽屉避免展开侧栏挤出业务内容。 */
export function AppShell({nav, brand, topRight, header, pageHeader, riskBanner,
    collapsed, onCollapsedChange, children}: AppShellProps) {
    const {t} = useTranslation();
    const screens = Grid.useBreakpoint();
    const mobile = screens.md === false;
    const [localCollapsed, setLocalCollapsed] = useState(false);
    const isCollapsed = collapsed ?? localCollapsed;

    const setCollapsed = (value: boolean) => {
        setLocalCollapsed(value);
        onCollapsedChange?.(value);
    };

    useEffect(() => {
        if (screens.lg === false) {
            setLocalCollapsed(true);
            onCollapsedChange?.(true);
        }
    }, [screens.lg, onCollapsedChange]);

    const navigation = <nav className="app-shell__nav" aria-label={t('shell.console')}>{nav}</nav>;

    return (
        <Layout className="app-shell">
            {mobile ? (
                <Drawer
                    title={brand ?? 'NexusQuant'}
                    placement="left"
                    width={264}
                    open={!isCollapsed}
                    onClose={() => setCollapsed(true)}
                    className="app-shell__drawer"
                >
                    {navigation}
                </Drawer>
            ) : (
                <Layout.Sider className="app-shell__sider" width={240} collapsedWidth={72} collapsed={isCollapsed}>
                    <div className="app-shell__logo">{brand ?? 'NexusQuant'}</div>
                    {navigation}
                </Layout.Sider>
            )}
            <Layout className="app-shell__body">
                {header ?? (
                    <header className="app-shell__header">
                        <Button type="text" aria-label={isCollapsed ? t('shell.expand') : t('shell.collapse')}
                            icon={isCollapsed ? <MenuUnfoldOutlined/> : <MenuFoldOutlined/>}
                            onClick={() => setCollapsed(!isCollapsed)}/>
                        {topRight}
                    </header>
                )}
                <Layout.Content className="app-shell__content">
                    <div className="app-shell__main">
                        {pageHeader}
                        {riskBanner}
                        {children}
                    </div>
                </Layout.Content>
            </Layout>
        </Layout>
    );
}
