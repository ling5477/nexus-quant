package com.guidinglight.nexusquant.trading.domain;

/** PLACE 的一次性决定；两个终点均不可撤销，MAY 不表示可重试。 */
public enum OrdinaryPlaceAuthorityState {
    NOT_ARMED, MAY_HAVE_ESCAPED, REVOKED_BEFORE_SEND
}
