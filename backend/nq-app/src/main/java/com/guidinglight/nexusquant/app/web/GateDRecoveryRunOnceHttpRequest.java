package com.guidinglight.nexusquant.app.web;

/**
 * GateDRecoveryRunOnceHttpRequest 表示一次手动 recovery 触发请求。
 * <p>
 * Why:
 * UC-D10 需要单独观察 Binance recovery，而当前共享 `RecoveryService` 仍保留历史 OKX 语义。
 * 这里把 venue 选择显式建模，避免验收批为了验证 Binance 而误触发 OKX 主链恢复。
 *
 * @param venue 可选恢复目标；为空时保持现有默认 OKX 行为，只允许 `OKX / BINANCE`
 */
public record GateDRecoveryRunOnceHttpRequest(String venue) {
}
