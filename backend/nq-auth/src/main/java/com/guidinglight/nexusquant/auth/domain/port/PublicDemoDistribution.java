package com.guidinglight.nexusquant.auth.domain.port;

/** 仅由可选 Demo 制品装配的发行身份，运行参数不能创建此能力。 */
public interface PublicDemoDistribution {
    /** 发行物认证准入必须从已提交的注册验证事实读取，不能只相信角色名称。 */
    boolean permitsUser(long userId);
}
