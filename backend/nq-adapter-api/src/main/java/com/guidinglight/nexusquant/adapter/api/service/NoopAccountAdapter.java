package com.guidinglight.nexusquant.adapter.api.service;

import com.guidinglight.nexusquant.adapter.api.model.AccountBalanceSnapshot;
import com.guidinglight.nexusquant.adapter.api.model.AccountSnapshot;
import com.guidinglight.nexusquant.adapter.api.model.PositionSnapshot;
import java.util.List;
import java.util.Objects;

/**
 * NoopAccountAdapter 提供 GateC-0 的最小账户 stub。
 * <p>
 * Why:
 * GateC-0 的重点是链路解耦，不是先把余额/持仓接通；
 * 因此返回空快照即可保证 router 与调用契约稳定。
 */
public class NoopAccountAdapter implements AccountAdapter {

    private final String venue;

    public NoopAccountAdapter(String venue) {
        this.venue = Objects.requireNonNull(venue, "venue must not be null");
    }

    @Override
    public String venue() {
        return venue;
    }

    @Override
    public List<AccountBalanceSnapshot> getBalances(Long accountId, String traceId) {
        return List.of();
    }

    @Override
    public List<PositionSnapshot> getPositions(Long accountId, String traceId) {
        return List.of();
    }

    @Override
    public AccountSnapshot getAccountSnapshot(Long accountId, String traceId) {
        return new AccountSnapshot(accountId, venue, List.of(), List.of(), traceId, "SIM");
    }
}
