import {Table} from 'antd';
import type {TableProps} from 'antd';
import type {ColumnType} from 'antd/es/table';

/**
 * NqDataTable — 高密度业务表格的统一包装。
 *
 * 关键约束：
 * 1) 核心交易/Paper Trading/回测页面统一走本组件，不直接套 ProTable；
 * 2) 默认 size=small 高密度，分页与滚动由调用方按业务决定；
 * 3) 数字列必须用 nqNumericColumn 注入右对齐 + tabular-nums（.nq-col-num）。
 */
export function NqDataTable<RecordType extends object>(props: TableProps<RecordType>) {
    const {size = 'small', ...rest} = props;

    return <Table<RecordType> size={size} {...rest}/>;
}

/**
 * 给数字列统一注入右对齐 + tabular-nums 列样式。
 * Why any：antd 5 的 dataIndex 是 DeepNamePath 泛型，行内对象字面量会把 RecordType
 * 误推断为字符串字面量；本 helper 只做样式注入，列定义类型仍由外层 columns 数组约束。
 */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function nqNumericColumn(column: ColumnType<any>): ColumnType<any> {
    return {
        ...column,
        align: 'right',
        className: column.className ? `${column.className} nq-col-num` : 'nq-col-num',
    };
}
