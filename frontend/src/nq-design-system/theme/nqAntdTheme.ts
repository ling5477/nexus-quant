// nqAntdTheme.ts — AntD 5 ConfigProvider 主题,从唯一来源派生(B0 / Design Tokens v2)
import { theme, type ThemeConfig } from 'antd';
import { nqTokens as t } from '../tokens/nq-tokens';

export const nqAntdTheme: ThemeConfig = {
  algorithm: theme.darkAlgorithm,
  token: {
    colorPrimary: t.semantic.primary,
    colorSuccess: t.semantic.success,
    colorWarning: t.semantic.warning,
    colorError: t.semantic.danger,
    colorInfo: t.semantic.info,
    colorBgBase: t.bg.app,
    colorBgContainer: t.bg.panel,
    colorBgElevated: t.bg.elevated,
    colorBorder: t.border.strong,
    colorBorderSecondary: t.border.subtle,
    colorText: t.text.primary,
    colorTextSecondary: t.text.secondary,
    colorTextTertiary: t.text.tertiary,
    colorTextQuaternary: t.text.disabled,
    borderRadius: t.radius.md,
    borderRadiusSM: t.radius.sm,
    borderRadiusLG: t.radius.md,
    fontSize: t.font.sizeBase,
    fontFamily: t.font.ui,
    wireframe: false,
  },
  components: {
    Table: {
      headerBg: t.bg.elevated,
      headerColor: t.text.secondary,
      rowHoverBg: t.bg.elevated,
      borderColor: t.border.subtle,
      cellPaddingBlock: 6,
      cellPaddingInline: 12,
      cellFontSize: t.font.sizeBase,
    },
    Card: { colorBgContainer: t.bg.panel, paddingLG: 16 },
    Layout: { bodyBg: t.bg.canvas, siderBg: t.bg.app, headerBg: t.bg.app },
    Tag: { borderRadiusSM: t.radius.sm },
    Button: { primaryShadow: 'none', defaultShadow: 'none' },
  },
};
