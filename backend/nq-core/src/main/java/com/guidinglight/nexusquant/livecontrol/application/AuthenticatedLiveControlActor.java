package com.guidinglight.nexusquant.livecontrol.application;

/**
 * 由认证适配层提供的 LIVE control-plane actor；业务命令不得自行声明该 identity。
 */
public record AuthenticatedLiveControlActor(long userId) {

    public AuthenticatedLiveControlActor {
        if (userId <= 0) {
            throw new IllegalArgumentException("authenticated userId must be positive");
        }
    }
}
