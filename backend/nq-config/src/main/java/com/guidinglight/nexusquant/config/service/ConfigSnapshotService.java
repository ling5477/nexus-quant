package com.guidinglight.nexusquant.config.service;

import com.guidinglight.nexusquant.config.model.ConfigSnapshot;
import java.util.Optional;

/**
 * ConfigSnapshotService 定义配置快照读写占位接口。
 */
public interface ConfigSnapshotService {

    /**
     * 保存快照。
     */
    ConfigSnapshot save(ConfigSnapshot snapshot);

    /**
     * 按 ID 查询快照。
     */
    Optional<ConfigSnapshot> findById(String snapshotId);
}
