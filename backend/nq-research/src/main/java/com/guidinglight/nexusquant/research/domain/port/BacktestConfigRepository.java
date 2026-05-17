package com.guidinglight.nexusquant.research.domain.port;

import com.guidinglight.nexusquant.research.domain.BacktestConfig;

import java.util.List;
import java.util.Optional;

/**
 * BacktestConfigRepository 负责 backtest_configs 的持久化访问。
 */
public interface BacktestConfigRepository {

    void insert(BacktestConfig backtestConfig);

    Optional<BacktestConfig> findByBacktestConfigId(String backtestConfigId);

    List<BacktestConfig> listAll();

    List<BacktestConfig> listByResearchConfigId(String researchConfigId);

    /**
     * 绑定 GateH-3 marketdata dataset 到回测配置。
     * Why:
     * dataset 绑定只更新配置事实和快照，不启动回测、不修改回测算法；
     * 这样 run 创建时可以从配置固化 dataset_snapshot_json，用于后续复盘。
     *
     * @param backtestConfigId 回测配置 ID
     * @param datasetId marketdata dataset ID
     * @param datasetSnapshotJson 绑定时的数据集快照 JSON
     * @param updatedAt 更新时间
     * @return 是否更新到记录
     */
    default boolean bindDataset(
            String backtestConfigId,
            String datasetId,
            String datasetSnapshotJson,
            java.time.Instant updatedAt
    ) {
        return false;
    }

    /**
     * 按 researchConfigId 过滤回测配置，`null` 表示查询全部。
     * Why:
     * GateG 联调前需要同时支持“全量列表页”和“研究配置详情下的子列表”，
     * 因此这里把是否按 parent 过滤收口到同一个仓储契约里，避免 controller 侧拼装分支。
     *
     * @param researchConfigId 研究配置 ID，可空
     * @return 满足条件的回测配置列表
     */
    default List<BacktestConfig> list(String researchConfigId) {
        return researchConfigId == null || researchConfigId.isBlank()
                ? listAll()
                : listByResearchConfigId(researchConfigId.trim());
    }
}


