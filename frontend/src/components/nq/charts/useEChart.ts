import {useEffect, useRef} from 'react';

import {echarts, type EChartsInstance} from '@/components/nq/charts/echarts-core';

/**
 * useEChart — ECharts 实例生命周期 hook。
 *
 * 职责：初始化、ResizeObserver 自适应、卸载时 dispose（资源释放），option 变化时整体重设。
 * 关键约束：
 * 1) 实例与 ResizeObserver 必须在卸载时释放，避免 Drawer/Tabs 反复开关导致泄漏；
 * 2) setOption 使用 notMerge，确保数据切换（如换 Paper Run）不会残留旧系列。
 */
export function useEChart(option: Record<string, unknown> | null) {
    const containerRef = useRef<HTMLDivElement | null>(null);
    const chartRef = useRef<EChartsInstance | null>(null);

    useEffect(() => {
        const element = containerRef.current;

        if (!element) {
            return;
        }

        const chart = echarts.init(element);
        chartRef.current = chart;

        const observer = new ResizeObserver(() => {
            chart.resize();
        });
        observer.observe(element);

        return () => {
            observer.disconnect();
            chart.dispose();
            chartRef.current = null;
        };
    }, []);

    useEffect(() => {
        if (option && chartRef.current) {
            chartRef.current.setOption(option, {notMerge: true});
        }
    }, [option]);

    return containerRef;
}
