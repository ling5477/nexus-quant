package com.guidinglight.nexusquant.integration.dh;

/**
 * DhDryRunForbiddenCapability 是每个 dry-run request 必须随 payload 明确声明的禁止能力。
 *
 * <p>Why: NQ -> DH dry-run 只能请求只读决策快照。把禁止能力显式放入 envelope，
 * 是为了让 DH 与审计记录都能看见本次请求不授权 NQ mutation、provider call 或 credential forwarding。</p>
 */
public enum DhDryRunForbiddenCapability {
    /** 禁止由 DH 输出或 NQ client 触发下单。 */
    PLACE_ORDER,
    /** 禁止由 DH 输出或 NQ client 触发撤单。 */
    CANCEL_ORDER,
    /** 禁止修改 NQ 风控或策略状态。 */
    MUTATE_NQ_STATE,
    /** 禁止读取 NQ 数据库作为 DH runtime 查询。 */
    READ_NQ_DB,
    /** 禁止写入 NQ 数据库事实。 */
    WRITE_NQ_DB,
    /** 禁止启动 Paper Run。 */
    START_PAPER_RUN,
    /** 禁止启动 LIVE Run。 */
    START_LIVE_RUN,
    /** 禁止通过 DH 或 NQ client 调用真实 provider。 */
    CALL_PROVIDER,
    /** 禁止转发 credential 或任何 secret-like material。 */
    FORWARD_CREDENTIAL
}
