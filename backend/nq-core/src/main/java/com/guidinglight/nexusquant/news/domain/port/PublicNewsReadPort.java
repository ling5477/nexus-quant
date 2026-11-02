package com.guidinglight.nexusquant.news.domain.port;

import com.guidinglight.nexusquant.news.domain.NewsFeedSnapshot;

/** 公开新闻来源端口；实现须限制超时、响应大小及文章数量，不访问账户凭据或执行交易。 */
public interface PublicNewsReadPort {
    /** 获取一份真实有界快照；无法得到可信来源时抛出来源异常，禁止用示例文章或空成功掩盖失败。 */
    NewsFeedSnapshot fetch();
}
