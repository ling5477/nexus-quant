package com.guidinglight.nexusquant.account.application.command;

/**
 * ExchangeAccountUpdateCommand 表示账户基础信息更新输入。
 * <p>
 * Why:
 * RC1-4 本轮只开放 alias / external ref 的最小更新面，
 * 不允许在写侧第一版就支持任意切换 exchange/env 造成默认账户作用域漂移。
 */
public record ExchangeAccountUpdateCommand(
        String accountAlias,
        String externalAccountRef
) {
}
