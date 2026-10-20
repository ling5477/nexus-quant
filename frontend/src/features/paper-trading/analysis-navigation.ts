/** 子页面共享实例和统计期间；明细tab与订单定位不属于公共上下文，不能误带到另一页。 */
export function simAnalysisNavigationSearch(search: string): string {
    const current = new URLSearchParams(search),
        next = new URLSearchParams();
    for (const key of [
        'paperRunId',
        'simFrom',
        'simTo',
        'analysisSource',
        'legacyView',
        'legacyPortfolioView',
    ]) {
        const value = current.get(key);
        // 保留明确输入的无效值，由目标表单解释；不能在导航时悄悄变成另一个有效期间。
        if (value !== null) next.set(key, value);
    }
    const value = next.toString();
    return value ? '?' + value : '';
}
