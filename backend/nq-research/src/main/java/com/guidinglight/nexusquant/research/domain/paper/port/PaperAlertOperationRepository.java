package com.guidinglight.nexusquant.research.domain.paper.port;

import com.guidinglight.nexusquant.research.domain.paper.PaperAlertOperation;

import java.util.Optional;
import java.util.UUID;

/** 持久实现必须与原告警修改共用事务；锁只协调原编号，不授予运行或告警权限。 */
public interface PaperAlertOperationRepository {
    void lock(UUID operationId);

    Optional<PaperAlertOperation> find(UUID operationId);

    void complete(PaperAlertOperation operation, String traceId);
}
