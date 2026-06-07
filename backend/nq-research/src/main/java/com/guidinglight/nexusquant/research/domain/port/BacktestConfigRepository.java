package com.guidinglight.nexusquant.research.domain.port;

import com.guidinglight.nexusquant.research.domain.BacktestConfig;

import java.util.List;
import java.util.Optional;

/**
 * BacktestConfigRepository 负责 backtest_configs 的持久化访问。
 * <p>
 * Why:
 * V28 把回测配置生命周期收口到 status/archive 字段；默认列表隐藏 ARCHIVED，
 * 但按 ID 查询和 includeArchived 内部路径必须保留，避免破坏历史 run/evaluation/publish 追溯。
 */
public interface BacktestConfigRepository {

    void insert(BacktestConfig backtestConfig);

    Optional<BacktestConfig> findByBacktestConfigId(String backtestConfigId);

    /**
     * 查询默认业务列表。
     * Why:
     * 默认列表用于新运行选择面，应排除 ARCHIVED；DISABLED 仍展示但不能用于创建新 run。
     *
     * @return 默认业务可见的回测配置列表
     */
    List<BacktestConfig> listAll();

    /**
     * 查询包含归档配置的内部列表。
     * Why:
     * 历史追溯或内部审计可能需要读取 ARCHIVED；本轮不新增外部 API 参数，
     * 因此 includeArchived 只保留在 Repository 契约内。
     *
     * @return 包含 ARCHIVED 的回测配置列表
     */
    default List<BacktestConfig> listAllIncludingArchived() {
        return listAll();
    }

    /**
     * 按研究配置查询默认业务可见的回测配置。
     * Why:
     * researchConfig 详情页下的配置子列表也属于默认业务列表，应同步隐藏 ARCHIVED。
     *
     * @param researchConfigId 研究配置 ID
     * @return 默认业务可见的回测配置列表
     */
    List<BacktestConfig> listByResearchConfigId(String researchConfigId);

    /**
     * 按研究配置查询包含归档记录的内部列表。
     * Why:
     * 历史追溯需要保留 parent 维度查询能力，但不应把 includeArchived 暴露成外部 API。
     *
     * @param researchConfigId 研究配置 ID
     * @return 包含 ARCHIVED 的回测配置列表
     */
    default List<BacktestConfig> listByResearchConfigIdIncludingArchived(String researchConfigId) {
        String normalizedResearchConfigId = researchConfigId == null ? null : researchConfigId.trim();
        return listAllIncludingArchived().stream()
                .filter(item -> normalizedResearchConfigId == null
                        || normalizedResearchConfigId.equals(item.researchConfigId()))
                .toList();
    }

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
     * 绑定 GateI-2 strategy version 到回测配置。
     * Why:
     * 策略版本、参数快照和版本快照必须在配置层先固化，run 创建时才能复制稳定输入；
     * 仓储只负责字段更新，不校验策略语义、不启动回测。
     *
     * @param backtestConfigId 回测配置 ID
     * @param strategyVersionId 策略版本 ID
     * @param strategyVersionSnapshotJson 策略版本快照 JSON
     * @param paramSnapshotJson 参数快照 JSON
     * @param updatedAt 更新时间
     * @return 是否更新到记录
     */
    default boolean bindStrategyVersion(
            String backtestConfigId,
            String strategyVersionId,
            String strategyVersionSnapshotJson,
            String paramSnapshotJson,
            java.time.Instant updatedAt
    ) {
        return false;
    }

    /**
     * 按 researchConfigId 过滤回测配置，`null` 表示查询全部。
     * Why:
     * 控制台联调早期需要同时支持“全量列表页”和“研究配置详情下的子列表”，
     * 因此这里把是否按 parent 过滤收口到同一个仓储契约里，避免 controller 侧拼装分支。
     *
     * @param researchConfigId 研究配置 ID，可空
     * @return 满足条件的回测配置列表
     */
    default List<BacktestConfig> list(String researchConfigId) {
        return list(researchConfigId, false);
    }

    /**
     * 按 researchConfigId 过滤回测配置，并允许内部调用显式包含归档记录。
     * Why:
     * 外部 API 本轮不增加 includeArchived 参数；Repository 保留内部扩展点，
     * 确保审计或历史追溯能绕过默认列表隐藏规则。
     *
     * @param researchConfigId 研究配置 ID，可空
     * @param includeArchived 是否包含 ARCHIVED
     * @return 满足条件的回测配置列表
     */
    default List<BacktestConfig> list(String researchConfigId, boolean includeArchived) {
        return researchConfigId == null || researchConfigId.isBlank()
                ? includeArchived ? listAllIncludingArchived() : listAll()
                : includeArchived
                        ? listByResearchConfigIdIncludingArchived(researchConfigId.trim())
                        : listByResearchConfigId(researchConfigId.trim());
    }
}


