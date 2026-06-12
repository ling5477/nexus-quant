package com.guidinglight.nexusquant.app.integration0.support;

import java.util.HashSet;
import java.util.Set;

/**
 * Int0NonceStore 是 test-only 内存 nonce store（INT0-T06）。
 *
 * <p>仅用于在单进程测试内验证防重放逻辑。
 *
 * <p>注意（DH P1-4 residual）：本实现只是单实例内存，<b>不</b>满足生产/多实例防重放要求。
 * 进入 Integration-1 前必须实现持久化或集中缓存的 nonce store（replay nonce persistence），
 * 该残留不阻塞本轮 contract test，但阻塞 Integration-1。
 */
public final class Int0NonceStore {

    private final Set<String> seen = new HashSet<>();

    /** 首次出现返回 true；重放（同 source+tenant+requestId+nonce）返回 false。 */
    public boolean registerIfAbsent(String source, String tenant, String requestId, String nonce) {
        return seen.add(source + "|" + tenant + "|" + requestId + "|" + nonce);
    }

    public int size() {
        return seen.size();
    }
}
