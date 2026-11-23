package com.guidinglight.nexusquant.search.domain;

import java.util.List;

/** 按所有者授权范围查找业务对象摘要；输入作为普通文本匹配，不读取秘密材料或扩大对象权限。 */
public interface ObjectSearchRepository {
    /** 查询须包含用户范围、类型白名单及有界分页；结果仅用于定位，后续操作仍须正式授权。 */
    List<ObjectSearchHit> findOwned(
            long ownerUserId, String query, String kind, int offset, int limit);
}
