package com.guidinglight.nexusquant.strategy.strategyrelease.application.exception;

/** 创建主事实与原始审计不完整或不一致；不能将结果未知降级成未创建或创建失败。 */
public class ShadowRunCreationEvidenceException extends RuntimeException {

    public ShadowRunCreationEvidenceException() {
        super("Shadow creation result evidence is unavailable");
    }
}
