package com.guidinglight.nexusquant.adapter.api.model;

import java.math.BigDecimal;

/** 只读规则计算结果；尚未发出订单，调用方必须先持久化有效值再申请发送资格。 */
public record AdapterOrderNormalization(BigDecimal quantity, BigDecimal price, AdapterError rejection) { }
