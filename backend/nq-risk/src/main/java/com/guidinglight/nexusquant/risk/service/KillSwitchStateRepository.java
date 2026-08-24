package com.guidinglight.nexusquant.risk.service;

import java.util.Optional;

/**
 * Kill switch durable current-state port。
 *
 * <p>生产 mutation surface 只暴露 engage；未来 disengage 必须由独立 human-review Gate
 * 设计，不能通过本 port、Controller、配置或启动任务释放。</p>
 */
public interface KillSwitchStateRepository {

    /**
     * 读取指定安全作用域。
     *
     * @param scope 稳定 scope
     * @return durable state；缺记录返回 empty 并由 service fail-closed
     */
    Optional<KillSwitchState> findByScope(KillSwitchScope scope);

    /**
     * 以 optimistic lock 将现有状态置为 ENGAGED，并追加审计事件。
     *
     * @param command engage-only command
     * @return 更新后或已处于 ENGAGED 的幂等 current state
     * @throws KillSwitchVersionConflictException expectedVersion 不匹配或并发更新时抛出
     */
    KillSwitchState engage(KillSwitchEngageCommand command);

    /**
     * 仅当数据库中同一 lease 为 ACTIVE 且尚未过期时切换为 DISENGAGED。
     * 默认实现拒绝，只有显式 GateY pilot runtime adapter 可以开放该 capability。
     */
    default KillSwitchState disengageForPilot(PilotKillSwitchDisengageCommand command) {
        throw new UnsupportedOperationException("pilot kill window is not implemented");
    }
}
