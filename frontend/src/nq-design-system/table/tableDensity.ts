// tableDensity.ts — NQ Console 表格密度 token(B0.2)。
// 与设计规范一致(NQ_DESIGN_TOKENS_V2.md §3):主表 32 · 次级事实表 28 · 摘要表 36。
import {nqTokens} from '../tokens/nq-tokens';

export type NqTableDensity = 'compact' | 'standard' | 'comfortable';

export interface NqTableDensityToken {
  /** 行高(px)。 */
  rowHeight: number;
  /** 单元格上下内边距(px)。 */
  paddingBlock: number;
  /** 单元格左右内边距(px)。 */
  paddingInline: number;
  /** 字号(px)。 */
  fontSize: number;
  /** 中文档位标签,用于自检 / 切换控件。 */
  label: string;
}

/**
 * 表格密度档位:
 * - compact:次级 / 高密度事实表(28);
 * - standard:主数据表默认(32);
 * - comfortable:摘要 / 详情表(36)。
 */
export const NQ_TABLE_DENSITY: Record<NqTableDensity, NqTableDensityToken> = {
  compact: {rowHeight: 28, paddingBlock: 4, paddingInline: 12, fontSize: nqTokens.font.sizeBase, label: '紧凑 28'},
  standard: {rowHeight: 32, paddingBlock: 6, paddingInline: 12, fontSize: nqTokens.font.sizeBase, label: '标准 32'},
  comfortable: {rowHeight: 36, paddingBlock: 8, paddingInline: 12, fontSize: nqTokens.font.sizeBase, label: '宽松 36'},
};

export const NQ_DEFAULT_TABLE_DENSITY: NqTableDensity = 'standard';

/**
 * 生成 NQ 表格 class:`nq-ds-table nq-ds-table--<density>`(配合 table/nq-table.css)。
 * 使用方需 import 'nq-design-system/table/nq-table.css'。数字列再加 className `nq-ds-col-num`(右对齐 + tabular)。
 */
export function nqTableClassName(density: NqTableDensity = NQ_DEFAULT_TABLE_DENSITY): string {
  return `nq-ds-table nq-ds-table--${density}`;
}

/**
 * AntD Table 的 components/token 适配:在页面迁移时,把密度档位映射成 AntD Table 的单元格内边距。
 * 仅返回数值,具体接入由页面在 ConfigProvider/Table 上消费,本切片不改任何业务页。
 */
export function nqAntdTableCellPadding(density: NqTableDensity = NQ_DEFAULT_TABLE_DENSITY): {
  cellPaddingBlock: number;
  cellPaddingInline: number;
} {
  const token = NQ_TABLE_DENSITY[density];
  return {cellPaddingBlock: token.paddingBlock, cellPaddingInline: token.paddingInline};
}
