package com.guidinglight.nexusquant.strategy.application.papershadowcomparison;

/**
 * PaperShadowComparisonFactRepository 暴露 GateQ-2 所需的本地只读事实查询。
 *
 * <p>Why: core service 只依赖该端口读取事实。端口不得提供 save/update/delete、调度、外部 HTTP、
 * Paper run 启动、Shadow runner、真实交易或敏感材料读取能力。
 */
public interface PaperShadowComparisonFactRepository {

    /**
     * 读取 Paper vs Shadow 对照所需的最小事实集合。
     *
     * @param query 只读查询范围
     * @return 本地事实集合；缺失事实必须显式建模为 missing / notImplemented
     */
    PaperShadowComparisonFacts loadFacts(PaperShadowComparisonQuery query);
}
