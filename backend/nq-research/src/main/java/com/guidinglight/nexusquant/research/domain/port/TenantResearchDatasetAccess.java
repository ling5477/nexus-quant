package com.guidinglight.nexusquant.research.domain.port;

/** 研究只能从授权描述符重建数据快照，不能由客户端选择任意文件或公共来源。 */
public interface TenantResearchDatasetAccess {
    String canonicalSnapshot(String requestedSnapshot);
}
