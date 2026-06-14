// AppShell.tsx — NQ Console 固定产品壳(B0)
// 进入控制台后一律:固定框架 + 高密度内容 + 分层状态。登录页之外不做花哨头图。
import React from 'react';
import { Layout } from 'antd';

const { Sider, Header, Content } = Layout;

export interface AppShellProps {
  /** 侧边导航内容(导航项由调用方提供) */
  nav: React.ReactNode;
  brand?: React.ReactNode;
  /** Top Bar 右侧集群:EnvironmentBadge / 风险摘要 / 告警 / 搜索 / 用户 */
  topRight?: React.ReactNode;
  /** 页头:标题 / 实体 ID / 状态 / 操作 */
  pageHeader?: React.ReactNode;
  /** 页级风险横幅(RiskBanner),页头与内容之间 */
  riskBanner?: React.ReactNode;
  children: React.ReactNode;
}

export function AppShell({ nav, brand, topRight, pageHeader, riskBanner, children }: AppShellProps) {
  return (
    <Layout style={{ minHeight: '100vh', background: 'var(--nq-bg-app)' }}>
      <Sider
        width={220}
        style={{ background: 'var(--nq-bg-app)', borderRight: '1px solid var(--nq-border-subtle)' }}
        breakpoint="lg"
        collapsedWidth={56}
      >
        <div style={{ height: 48, display: 'flex', alignItems: 'center', padding: '0 16px', color: 'var(--nq-text-primary)', fontWeight: 600, borderBottom: '1px solid var(--nq-border-subtle)' }}>
          {brand ?? 'NexusQuant'}
        </div>
        <nav style={{ padding: 8 }}>{nav}</nav>
      </Sider>

      <Layout style={{ background: 'var(--nq-bg-canvas)' }}>
        <Header
          style={{
            height: 48, lineHeight: '48px', padding: '0 16px',
            background: 'var(--nq-bg-app)',
            borderBottom: '1px solid var(--nq-border-subtle)',
            display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 12,
          }}
        >
          {topRight}
        </Header>

        {pageHeader && (
          <div
            style={{
              padding: '12px 24px', background: 'var(--nq-bg-canvas)',
              borderBottom: '1px solid var(--nq-border-subtle)',
              display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 16,
            }}
          >
            {pageHeader}
          </div>
        )}

        {riskBanner && <div style={{ padding: '12px 24px 0' }}>{riskBanner}</div>}

        <Content style={{ padding: 24, background: 'var(--nq-bg-canvas)' }}>{children}</Content>
      </Layout>
    </Layout>
  );
}
