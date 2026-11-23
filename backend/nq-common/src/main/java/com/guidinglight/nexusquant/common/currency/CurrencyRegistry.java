package com.guidinglight.nexusquant.common.currency;

import java.util.List;

/** 可扩展的币种目录；资产原币不以首批六种作为白名单，新增币种不需要修改核心枚举。 */
public interface CurrencyRegistry {
    List<CurrencyDefinition> list();
}
