import * as echarts from 'echarts/core';
import {LineChart} from 'echarts/charts';
import {GridComponent, TooltipComponent} from 'echarts/components';
import {CanvasRenderer} from 'echarts/renderers';

/**
 * ECharts 按需注册收口。
 *
 * 关键约束：
 * 1) 全仓只在此注册一次，业务图表组件统一从这里 import echarts，
 *    避免页面各自全量引入 echarts 导致包体膨胀与重复注册；
 * 2) 当前只注册折线图所需模块，新图表类型按需在此追加。
 */
echarts.use([LineChart, GridComponent, TooltipComponent, CanvasRenderer]);

export {echarts};
export type EChartsInstance = ReturnType<typeof echarts.init>;
