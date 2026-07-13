package com.guidinglight.nexusquant.adapter.okx.service;

import com.guidinglight.nexusquant.adapter.api.model.EndpointAccessClass;
import com.guidinglight.nexusquant.adapter.api.model.EndpointGuardReason;
import com.guidinglight.nexusquant.adapter.api.model.ExchangeCapability;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * OkxSpotCapabilityMatrix 固化 GateW-1 对 OKX Spot 的类型化 capability matrix。
 *
 * <p>公开行情保持现有 public transport 合同；其 policy 放行不等于 real provider readiness 或
 * trading authorization。所有 private、mutating、资金动作和未知能力均保持 runtime disabled，
 * 后续 Gate 不得通过修改调用方字符串绕过该矩阵。</p>
 */
public final class OkxSpotCapabilityMatrix {

    private static final Map<ExchangeCapability, OkxSpotCapabilityDefinition> DEFINITIONS = buildDefinitions();

    /**
     * 创建无状态 capability matrix；不读取配置、credential 或网络。
     */
    public OkxSpotCapabilityMatrix() {
    }

    /**
     * 返回能力定义；null 或未登记能力统一落入 UNKNOWN 行。
     */
    public OkxSpotCapabilityDefinition definitionFor(ExchangeCapability capability) {
        return DEFINITIONS.get(capability == null ? ExchangeCapability.UNKNOWN : capability);
    }

    /**
     * 返回完整、不可变的 capability matrix，供 diagnostics/tests 读取。
     */
    public Collection<OkxSpotCapabilityDefinition> definitions() {
        return DEFINITIONS.values();
    }

    private static Map<ExchangeCapability, OkxSpotCapabilityDefinition> buildDefinitions() {
        Map<ExchangeCapability, OkxSpotCapabilityDefinition> definitions = new EnumMap<>(ExchangeCapability.class);
        definitions.put(
                ExchangeCapability.PUBLIC_MARKET_DATA,
                definition(
                        ExchangeCapability.PUBLIC_MARKET_DATA,
                        EndpointAccessClass.PUBLIC_READ,
                        true,
                        false,
                        false,
                        true,
                        EndpointGuardReason.ALLOW_PUBLIC_READ
                )
        );
        definitions.put(
                ExchangeCapability.PRIVATE_ACCOUNT_CONFIGURATION_READ,
                privateRead(ExchangeCapability.PRIVATE_ACCOUNT_CONFIGURATION_READ)
        );
        definitions.put(
                ExchangeCapability.PRIVATE_ACCOUNT_BALANCE_READ,
                privateRead(ExchangeCapability.PRIVATE_ACCOUNT_BALANCE_READ)
        );
        definitions.put(
                ExchangeCapability.PRIVATE_PERMISSION_READ,
                privateRead(ExchangeCapability.PRIVATE_PERMISSION_READ)
        );
        definitions.put(
                ExchangeCapability.ORDER_PREVIEW_LOCAL,
                definition(
                        ExchangeCapability.ORDER_PREVIEW_LOCAL,
                        EndpointAccessClass.LOCAL_ONLY,
                        false,
                        false,
                        false,
                        false,
                        EndpointGuardReason.DENY_UNKNOWN_ENDPOINT
                )
        );
        definitions.put(
                ExchangeCapability.ORDER_SUBMISSION,
                definition(
                        ExchangeCapability.ORDER_SUBMISSION,
                        EndpointAccessClass.PRIVATE_MUTATING,
                        false,
                        false,
                        true,
                        true,
                        EndpointGuardReason.DENY_MUTATING_ENDPOINT
                )
        );
        definitions.put(
                ExchangeCapability.ORDER_CANCEL,
                definition(
                        ExchangeCapability.ORDER_CANCEL,
                        EndpointAccessClass.PRIVATE_MUTATING,
                        false,
                        false,
                        true,
                        true,
                        EndpointGuardReason.DENY_MUTATING_ENDPOINT
                )
        );
        definitions.put(
                ExchangeCapability.TRANSFER,
                fundsMovement(ExchangeCapability.TRANSFER)
        );
        definitions.put(
                ExchangeCapability.WITHDRAW,
                fundsMovement(ExchangeCapability.WITHDRAW)
        );
        definitions.put(
                ExchangeCapability.UNKNOWN,
                definition(
                        ExchangeCapability.UNKNOWN,
                        EndpointAccessClass.UNKNOWN,
                        false,
                        false,
                        false,
                        false,
                        EndpointGuardReason.DENY_UNKNOWN_ENDPOINT
                )
        );
        return Collections.unmodifiableMap(definitions);
    }

    private static OkxSpotCapabilityDefinition privateRead(ExchangeCapability capability) {
        return definition(
                capability,
                EndpointAccessClass.PRIVATE_READ_ONLY,
                false,
                false,
                true,
                true,
                EndpointGuardReason.DENY_PRIVATE_RUNTIME_DISABLED
        );
    }

    private static OkxSpotCapabilityDefinition fundsMovement(ExchangeCapability capability) {
        return definition(
                capability,
                EndpointAccessClass.FUNDS_MOVEMENT,
                false,
                false,
                true,
                true,
                EndpointGuardReason.DENY_FUNDS_MOVEMENT
        );
    }

    private static OkxSpotCapabilityDefinition definition(
            ExchangeCapability capability,
            EndpointAccessClass endpointAccessClass,
            boolean implemented,
            boolean runtimeEnabled,
            boolean credentialRequired,
            boolean networkRequired,
            EndpointGuardReason reasonCode
    ) {
        return new OkxSpotCapabilityDefinition(
                capability,
                endpointAccessClass,
                implemented,
                runtimeEnabled,
                credentialRequired,
                networkRequired,
                false,
                reasonCode
        );
    }
}
