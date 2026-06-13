import {theme} from 'antd';
import type {ThemeConfig} from 'antd';

import {nqColor, nqFont, nqMotion, nqRadius, nqShadow} from '@/theme/tokens';

/**
 * NQ Design System v1 — AntD ConfigProvider 主题。
 *
 * 主题方向：深色优先、高信息密度、小圆角、低阴影、1px 分割线。
 * 关键约束：
 * 1) 取值必须来自 `@/theme/tokens`，不允许在此处再发明颜色；
 * 2) darkAlgorithm 负责派生组件态（hover/active/disabled），这里只锚定语义 token；
 * 3) controlHeight/fontSize 压缩为高密度档位，但保持中文可读性（13px 正文）。
 */
export const nqAntdTheme: ThemeConfig = {
    algorithm: theme.darkAlgorithm,
    token: {
        colorPrimary: nqColor.primary,
        colorInfo: nqColor.info,
        colorSuccess: nqColor.success,
        colorWarning: nqColor.warning,
        colorError: nqColor.danger,
        colorLink: nqColor.primaryHover,

        colorBgBase: nqColor.bgPage,
        colorTextBase: nqColor.text,
        colorBgLayout: nqColor.bgPage,
        colorBgContainer: nqColor.bgPanel,
        colorBgElevated: nqColor.bgElevated,

        colorBorder: nqColor.border,
        colorBorderSecondary: nqColor.borderSubtle,
        colorSplit: nqColor.borderSubtle,

        colorText: nqColor.text,
        colorTextSecondary: nqColor.textSecondary,
        colorTextTertiary: nqColor.textTertiary,
        colorTextQuaternary: nqColor.textDisabled,

        borderRadius: nqRadius.md,
        borderRadiusSM: nqRadius.sm,
        borderRadiusLG: nqRadius.lg,

        fontFamily: nqFont.family,
        fontSize: nqFont.sizeMd,
        fontSizeSM: nqFont.sizeSm,

        controlHeight: 30,
        controlHeightSM: 24,
        controlHeightLG: 38,

        boxShadow: nqShadow.low,
        boxShadowSecondary: nqShadow.overlay,

        motionDurationFast: nqMotion.fast,
        motionDurationMid: nqMotion.mid,
        motionDurationSlow: nqMotion.slow,
    },
    components: {
        Layout: {
            siderBg: nqColor.bgSunken,
            headerBg: nqColor.bgPanel,
            bodyBg: nqColor.bgPage,
        },
        Menu: {
            darkItemBg: 'transparent',
            darkSubMenuItemBg: 'transparent',
            darkItemSelectedBg: nqColor.bgSelected,
            darkItemSelectedColor: nqColor.primaryHover,
            itemBorderRadius: nqRadius.md,
            itemMarginBlock: 2,
        },
        Table: {
            headerBg: nqColor.bgElevated,
            headerColor: nqColor.textSecondary,
            headerSplitColor: 'transparent',
            borderColor: nqColor.borderSubtle,
            rowHoverBg: nqColor.bgHover,
            cellPaddingBlock: 10,
            cellPaddingInline: 12,
            cellPaddingBlockSM: 6,
            cellPaddingInlineSM: 8,
        },
        Card: {
            headerBg: 'transparent',
            headerHeight: 44,
            headerFontSize: nqFont.sizeLg,
            paddingLG: 16,
        },
        Tabs: {
            horizontalMargin: '0 0 12px 0',
            horizontalItemPadding: '8px 0',
            titleFontSize: nqFont.sizeMd,
        },
        Tag: {
            defaultBg: nqColor.bgElevated,
            defaultColor: nqColor.textSecondary,
        },
        Modal: {
            contentBg: nqColor.bgElevated,
            headerBg: nqColor.bgElevated,
        },
        Descriptions: {
            labelBg: 'rgba(255, 255, 255, 0.02)',
            itemPaddingBottom: 8,
        },
        Form: {
            itemMarginBottom: 12,
        },
        Button: {
            fontWeight: 500,
        },
        Alert: {
            defaultPadding: '6px 12px',
            withDescriptionPadding: '12px 16px',
        },
    },
};
