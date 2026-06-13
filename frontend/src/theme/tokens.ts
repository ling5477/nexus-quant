/**
 * NQ Design System v1 — design tokens (TypeScript 侧)
 *
 * 职责：为 AntD ConfigProvider 与图表主题（ECharts 等）提供与 CSS variables 同源的取值。
 * 关键约束：
 * 1) 本文件与 `src/styles/tokens.css` 互为镜像（命名一致、取值一致），修改任一侧必须同步另一侧；
 *    镜像而非运行时读取，是为了避免样式表加载时序导致主题取值不稳定。
 * 2) 图表与 UI 不允许出现两套割裂配色：图表系列色必须从 `nqColor` 取值。
 */
export const nqColor = {
    bgPage: '#0d1219',
    bgPanel: '#131a23',
    bgElevated: '#18212c',
    bgSunken: '#0a0e14',
    bgHover: '#1b2531',
    bgSelected: 'rgba(79, 124, 247, 0.14)',

    border: '#263141',
    borderSubtle: '#1d2734',

    text: '#dbe4ee',
    textSecondary: '#93a1b4',
    textTertiary: '#5d6b7e',
    textDisabled: '#49566a',

    primary: '#4f7cf7',
    primaryHover: '#6b93f9',
    primaryActive: '#3a63d8',
    primaryBg: 'rgba(79, 124, 247, 0.12)',

    success: '#3dd68c',
    info: '#54a9ff',
    warning: '#e8b339',
    danger: '#e5484d',
    neutral: '#8b98ab',
    disabled: '#49566a',

    // 涨跌语义：红涨绿跌（国内习惯）
    up: '#f23645',
    down: '#089981',

    // 环境语义：PAPER=蓝，DEMO=紫，LIVE=红色强警示（本阶段 LIVE 能力 disabled）
    paper: '#54a9ff',
    demo: '#9d7bff',
    live: '#e5484d',
} as const;

export const nqSpace = {
    xs: 4,
    sm: 8,
    md: 12,
    lg: 16,
    xl: 24,
    xxl: 32,
} as const;

export const nqRadius = {
    sm: 2,
    md: 4,
    lg: 6,
} as const;

export const nqFont = {
    family: "'Segoe UI Variable', 'Microsoft YaHei UI', 'PingFang SC', sans-serif",
    familyMono: "'JetBrains Mono', 'Cascadia Mono', 'Consolas', ui-monospace, monospace",
    sizeXs: 11,
    sizeSm: 12,
    sizeMd: 13,
    sizeLg: 15,
    sizeXl: 18,
    sizeXxl: 24,
} as const;

export const nqShadow = {
    low: '0 1px 2px rgba(0, 0, 0, 0.35)',
    overlay: '0 8px 24px rgba(0, 0, 0, 0.5)',
} as const;

export const nqMotion = {
    fast: '0.1s',
    mid: '0.2s',
    slow: '0.3s',
    ease: 'cubic-bezier(0.4, 0, 0.2, 1)',
} as const;
