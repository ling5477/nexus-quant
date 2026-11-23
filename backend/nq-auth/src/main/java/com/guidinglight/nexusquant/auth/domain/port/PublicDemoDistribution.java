package com.guidinglight.nexusquant.auth.domain.port;

/** 仅由可选 Demo 制品装配的发行身份，运行参数不能创建此能力。 */
public interface PublicDemoDistribution {
    /** 已验证注册者或无成员关系的独立平台身份才可准入，不能只相信令牌中的角色。 */
    boolean permitsUser(long userId);
}
