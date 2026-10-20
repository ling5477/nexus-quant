package com.guidinglight.nexusquant.research.domain.port;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;

/** 研究域只依赖费用来源合同；所有者和账户事实由组合根解析，不引入私有读取或第二账务源。 */
public interface ResearchFeeSourcePort {
    String prepareConfiguration(String executionSpec);

    String freezeRunConfiguration(String configurationSnapshot, String datasetSnapshot, Instant at);

    void validateFrozenExecution(String executionSpec, String datasetSnapshot);

    /** null仅表示受信内部调用；无用户的HTTP请求和未装配来源端口必须返回负身份以拒绝私有行。 */
    Long currentReadOwner();

    /** 没有来源装配时只保留旧研究假设；禁止把调用方自带快照当作实际费用，私有行默认不可读。 */
    static ResearchFeeSourcePort disabled() {
        return new ResearchFeeSourcePort() {
            private void check(String raw) {
                try {
                    var node =
                            new ObjectMapper().readTree(raw == null || raw.isBlank() ? "{}" : raw);
                    if ("CURRENT_ACCOUNT_OBSERVATION".equals(node.path("costSource").asText())
                            || Set.of(
                                            "feeObservationId",
                                            "feeSourceOwnerUserId",
                                            "feeSourceSnapshotJson",
                                            "feeSourceSnapshotSha256",
                                            "discountPriceDatasetId",
                                            "discountAssetBudgetFraction",
                                            "discountPriceSnapshotId",
                                            "discountPriceSnapshotSha256")
                                    .stream()
                                    .anyMatch(node::has)) {
                        throw new IllegalArgumentException("ACCOUNT_FEE_MODEL_SOURCE_DISABLED");
                    }
                } catch (IOException ex) {
                    throw new IllegalArgumentException("FEE_MODEL_JSON_INVALID", ex);
                }
            }

            public String prepareConfiguration(String spec) {
                check(spec);
                return spec;
            }

            public String freezeRunConfiguration(String config, String dataset, Instant at) {
                try {
                    check(new ObjectMapper().readTree(config).path("executionSpec").toString());
                    return config;
                } catch (IOException ex) {
                    throw new IllegalArgumentException("FEE_MODEL_JSON_INVALID", ex);
                }
            }

            public void validateFrozenExecution(String spec, String dataset) {
                check(spec);
            }

            public Long currentReadOwner() {
                return -1L;
            }
        };
    }
}
