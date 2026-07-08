package com.guidinglight.nexusquant.strategy.domain.port;

/**
 * ShadowValidationWorkflowOverviewQueryPort 是 GateT-1 workflow overview 的只读查询端口。
 *
 * <p>实现只能读取本地 GateS fact source，不能提供 create / update / delete / acknowledge / review note 方法；
 * 也不能调用 runner、scheduler、adapter、credential、order、account、ledger 或 private trading 服务。
 */
public interface ShadowValidationWorkflowOverviewQueryPort {

    /**
     * 加载 Shadow Validation Workflow overview 所需的本地只读 facts。
     *
     * <p>事务与副作用：实现层必须 SELECT-only；缺少可选 evidence 时返回空集合或缺失字段，不得伪造 ready
     * 结论。
     *
     * @return bounded local fact projection
     */
    ShadowValidationWorkflowOverviewFacts loadOverviewFacts();
}
