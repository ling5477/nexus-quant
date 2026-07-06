package com.guidinglight.nexusquant.strategy.application.shadowrun;

/**
 * Shadow Run runner 应用层入口。
 *
 * <p>该接口只授权本地、同步、无副作用的 Shadow Run skeleton 编排。实现不得启动后台任务、
 * scheduler、外部交易所调用、credential 读取、真实订单提交或 account / ledger mutation。
 */
public interface ShadowRunRunner {

    /**
     * 执行一次调用方驱动的本地 Shadow Run skeleton。
     *
     * <p>用途：把调用方已经准备好的只读 payload 保存为本地 Shadow Run fact、event 和 snapshot。
     * Why：GateR-3 只需要可审计的本地 runner skeleton，不需要真实策略执行或外部 provider。
     *
     * @param command 本地只读输入命令；payload 必须已脱敏且不得包含 credential / LIVE / trading authorization 字段
     * @return 本地运行结果；最多表达 COMPLETED、BLOCKED 或幂等复用，不表达真实交易授权
     * @throws ShadowRunRunnerException 当运行期异常发生且 runner 已尽力把 run 标记为 FAILED 时抛出
     */
    ShadowRunRunnerResult run(ShadowRunRunnerCommand command);
}
