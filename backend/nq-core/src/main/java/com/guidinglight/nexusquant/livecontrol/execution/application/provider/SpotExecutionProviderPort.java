package com.guidinglight.nexusquant.livecontrol.execution.application.provider;

import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderRequests.Cancel;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderRequests.FillQuery;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderRequests.OrderQuery;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderRequests.PlaceLimit;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderResults.CancelResult;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderResults.FillPage;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderResults.MutationResult;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotProviderResults.OrderObservation;

/**
 * Execution application owner 持有的窄 provider port。
 *
 * <p>合同不提供 raw method/path/body escape hatch，不读取 credential，也不授予 LIVE/trading
 * authorization。mutation 方法只能由既有 GateY-3 execution owner 在 durable {@code SEND_STARTED}
 * 已成功落库后调用；本 port 不创建、不替代也不延后该事实。GateY-6B 仅实现 fake/stub transport 下的
 * OKX contract，当前没有 runtime caller，默认 runtime 不装配该 port。</p>
 */
public interface SpotExecutionProviderPort {

    MutationResult placeLimit(PlaceLimit request);

    OrderObservation queryOrderByClientOrderId(OrderQuery request);

    CancelResult cancel(Cancel request);

    OrderObservation readOrderStatus(OrderQuery request);

    FillPage readFills(FillQuery request);
}
