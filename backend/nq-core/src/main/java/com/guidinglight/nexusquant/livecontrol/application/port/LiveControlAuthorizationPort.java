package com.guidinglight.nexusquant.livecontrol.application.port;

/** LIVE control-plane 的实时授权查询 port；实现必须 fail-closed。 */
public interface LiveControlAuthorizationPort {

    /**
     * 在当前业务事务中校验并锁定 actor 的 enabled user、role grant 与 role facts。
     */
    boolean lockAndCheckRole(long actorId, String requiredRole);
}
