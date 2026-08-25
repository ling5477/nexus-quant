package com.guidinglight.nexusquant.livecontrol.domain;

/**
 * LiveSession 的互斥 authority 类型；未知值必须 fail closed。
 */
public enum LiveSessionAuthorityType {
    STRATEGY,
    OPERATOR_PILOT
}
