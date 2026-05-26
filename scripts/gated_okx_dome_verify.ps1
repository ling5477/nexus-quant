[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# AUDIT-FIX(2026-05-26): 旧 OKX dome 验收脚本已经移出可执行 scripts 区域。
# Why:
# 1) 旧脚本调用 `/__gated/**` 历史路径，该路径不属于当前正式 `/api/**` API 面。
# 2) GateJ 只允许 Paper Trading 稳定运行验收，不允许执行真实交易所验收脚本。
# 3) 为避免误执行，本 stub 只保留阻断信息，不保留任何真实下单、撤单、恢复或对账逻辑。
$message = @(
    "gated_okx_dome_verify.ps1 has been deprecated and blocked by AUDIT-FIX.",
    "The legacy /__gated/** endpoints are historical paths, not current executable APIs.",
    "GateJ must not execute this script and must not use it for real trading acceptance.",
    "Historical evidence has been archived at docs/archive/scripts/gated_okx_dome_verify.ps1."
) -join [Environment]::NewLine

throw $message
