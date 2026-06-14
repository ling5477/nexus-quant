import {ConfigProvider} from 'antd';
import {useLayoutEffect, type ReactNode} from 'react';

import {applyNqCssVars, nqAntdTheme} from '@/nq-design-system';

import './StandaloneSurface.css';

interface StandaloneSurfaceProps {
    children: ReactNode;
    /** 额外类名,叠加在 .nq-standalone 上(用于登录页/异常页各自布局)。 */
    className?: string;
    /** 无障碍标签,标识当前独立页用途。 */
    ariaLabel?: string;
}

/**
 * StandaloneSurface — 登录页 / 异常页共用的 v2 全屏外壳(AppShell 之外)。
 *
 * 职责:
 * 1) 在该子树内注入 v2 设计系统(ConfigProvider 用 nqAntdTheme),与全局 v1 主题隔离;
 * 2) 把 v2 CSS 变量(--nq-*)注入 :root,供作用域样式只读 var(--nq-*),不私配 hex;
 *    --nq-* 与 v1 的 --nq-color-* 命名空间不冲突,因此对既有页面无副作用。
 * 关键约束:不承载任何鉴权 / 凭证逻辑,只负责主题边界与居中布局;
 * 登录请求与登录态仍由调用方走既有 authApi / auth-store。
 */
export function StandaloneSurface({children, className, ariaLabel}: StandaloneSurfaceProps) {
    useLayoutEffect(() => {
        // 进入独立页时注入 v2 变量;默认 INTL_CRYPTO(本页不展示行情色,惯例不敏感)。
        applyNqCssVars();
    }, []);

    return (
        <ConfigProvider theme={nqAntdTheme}>
            <main className={className ? `nq-standalone ${className}` : 'nq-standalone'} aria-label={ariaLabel}>
                {children}
            </main>
        </ConfigProvider>
    );
}
