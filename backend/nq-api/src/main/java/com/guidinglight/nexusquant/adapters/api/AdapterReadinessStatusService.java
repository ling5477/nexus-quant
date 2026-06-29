package com.guidinglight.nexusquant.adapters.api;

import com.guidinglight.nexusquant.adapter.api.model.AdapterCapability;
import com.guidinglight.nexusquant.adapter.api.service.AdapterReadinessService;
import com.guidinglight.nexusquant.adapters.api.dto.AdapterReadinessItemResponse;
import com.guidinglight.nexusquant.adapters.api.dto.AdapterReadinessResponse;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * AdapterReadinessStatusService 组装只读 adapter readiness 快照。
 * <p>
 * Why:
 * GateM-5A 要给前端一个只读入口，展示「当前各 venue × capability 是否可实盘、为什么不可」。本服务只对固定
 * venue / capability 矩阵逐项调用 {@link AdapterReadinessService#evaluate}（GateM-0 的纯静态 fail-closed 策略），
 * 把内部决策映射成对外 DTO。它**只读静态 readiness 决策**：不触达任何 adapter delegate，不发起 HTTP / socket、
 * 不读取文件 / env / credential、不触发下单 / 撤单 / 行情订阅。当前 baseline 下所有真实交易能力恒 allowed=false。
 *
 * <p>线程安全：无可变状态（矩阵为不可变常量，依赖为不可变引用），可被多线程并发调用。
 */
@Service
public class AdapterReadinessStatusService {

    /**
     * 上报的 venue 集合。
     * <p>
     * Why:
     * 覆盖 no-real / paper 家族（NOOP / PAPER / SIM）与当前两个交易所 adapter（OKX / BINANCE）。
     * 该列表仅用于 readiness 展示，不代表任何 venue 被授权真实交易。
     */
    private static final List<String> REPORTED_VENUES = List.of("NOOP", "PAPER", "SIM", "OKX", "BINANCE");

    /**
     * 上报的 capability 集合。
     * <p>
     * Why:
     * 覆盖行情订阅类（public / bars / trades / orderbook）、交易类（place / cancel / query）、账户类（balance）
     * 与权限探测（permission probe），让前端能完整看到 fail-closed 边界。
     */
    private static final List<AdapterCapability> REPORTED_CAPABILITIES = List.of(
            AdapterCapability.PUBLIC_MARKETDATA,
            AdapterCapability.SUBSCRIBE_BARS,
            AdapterCapability.SUBSCRIBE_TRADES,
            AdapterCapability.SUBSCRIBE_ORDERBOOK,
            AdapterCapability.PLACE_ORDER,
            AdapterCapability.CANCEL_ORDER,
            AdapterCapability.QUERY_ORDER,
            AdapterCapability.ACCOUNT_BALANCE,
            AdapterCapability.PERMISSION_PROBE
    );

    private final AdapterReadinessService readinessService;
    private final Clock clock;

    /**
     * 生产装配构造器：使用系统时钟。
     *
     * @param readinessService readiness 评估服务；不可为空
     */
    @Autowired
    public AdapterReadinessStatusService(AdapterReadinessService readinessService) {
        this(readinessService, Clock.systemUTC());
    }

    /**
     * 可测试构造器：注入固定时钟以稳定 generatedAt。
     *
     * @param readinessService readiness 评估服务；不可为空
     * @param clock            快照时间来源；不可为空
     */
    AdapterReadinessStatusService(AdapterReadinessService readinessService, Clock clock) {
        this.readinessService = Objects.requireNonNull(readinessService, "readinessService must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 返回当前 readiness 快照。
     * <p>
     * Why:
     * 逐项 evaluate 固定矩阵，纯计算无副作用。当前 baseline 下：NOOP/PAPER/SIM → NO_REAL
     * （PAPER/SIM 额外携带 READY_FOR_PAPER_ONLY reason），OKX/BINANCE → NOT_READY，
     * 全部 allowed=false / liveAuthorized=false，绝不返回 READY。
     *
     * @return 不可变 readiness 响应（generatedAt + items）
     */
    public AdapterReadinessResponse currentReadiness() {
        List<AdapterReadinessItemResponse> items = new ArrayList<>(REPORTED_VENUES.size() * REPORTED_CAPABILITIES.size());
        for (String venue : REPORTED_VENUES) {
            for (AdapterCapability capability : REPORTED_CAPABILITIES) {
                // 只读静态 readiness 决策；evaluate 是纯函数式策略，不触达 adapter delegate / HTTP / credential。
                items.add(AdapterReadinessItemResponse.from(readinessService.evaluate(venue, capability)));
            }
        }
        return new AdapterReadinessResponse(Instant.now(clock), items);
    }
}
