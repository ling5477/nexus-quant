package com.guidinglight.nexusquant.adapter.api.model;

/**
 * EndpointAccessClass 将 endpoint 的副作用与鉴权边界固定为类型，而非调用方字符串约定。
 */
public enum EndpointAccessClass {

    PUBLIC_READ,
    PRIVATE_READ_ONLY,
    PRIVATE_MUTATING,
    FUNDS_MOVEMENT,
    LOCAL_ONLY,
    UNKNOWN
}
