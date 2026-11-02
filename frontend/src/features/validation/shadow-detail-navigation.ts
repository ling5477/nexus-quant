export const SHADOW_DETAIL_SECTIONS = [
    'overview',
    'consistency',
    'events',
    'snapshots',
    'report',
    'source',
] as const;

export type ShadowDetailSection = (typeof SHADOW_DETAIL_SECTIONS)[number];

/** 深链接保留当前业务分区；重复或未知分区明确拒绝，不能静默展示另一块内容。 */
export function shadowDetailSection(search: string): ShadowDetailSection | null {
    const values = new URLSearchParams(search).getAll('view');
    if (values.length === 0) return 'overview';
    return values.length === 1 && SHADOW_DETAIL_SECTIONS.some((value) => value === values[0])
        ? (values[0] as ShadowDetailSection)
        : null;
}

/** 切换分区只改变 view，原对象参数和列表返回上下文由当前路由继续承载。 */
export function shadowDetailSearch(search: string, section: ShadowDetailSection): string {
    const params = new URLSearchParams(search);
    if (section === 'overview') params.delete('view');
    else params.set('view', section);
    return params.size ? '?' + params.toString() : '';
}
