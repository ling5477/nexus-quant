package com.guidinglight.nexusquant.config.service;

import com.guidinglight.nexusquant.config.model.ConfigSnapshot;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * InMemoryConfigSnapshotService 提供内存级占位实现。
 */
public class InMemoryConfigSnapshotService implements ConfigSnapshotService {

    private final Map<String, ConfigSnapshot> snapshots = new ConcurrentHashMap<>();

    @Override
    public ConfigSnapshot save(ConfigSnapshot snapshot) {
        snapshots.put(snapshot.snapshotId(), snapshot);
        return snapshot;
    }

    @Override
    public Optional<ConfigSnapshot> findById(String snapshotId) {
        return Optional.ofNullable(snapshots.get(snapshotId));
    }
}
