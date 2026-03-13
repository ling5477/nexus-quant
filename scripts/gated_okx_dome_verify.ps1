[CmdletBinding()]
param(
    [string]$BaseUrl,
    [string]$EnvFilePath,
    [switch]$SkipRestartPause,
    [switch]$AutoRestart,
    [int]$StartupTimeoutSec = 180
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# Why: 真实 OKX 验收脚本在 Windows 上经常从 `powershell.exe` 5.1 被调用，但当前仓库 `.env` 使用 UTF-8，
# 5.1 对该文件的解析兼容性不稳定；这里统一切到 PowerShell 7 继续执行，避免“脚本逻辑没问题但 5.1 读不出凭证”的伪阻塞。
if ($PSVersionTable.PSVersion.Major -lt 7) {
    $pwshCommand = Get-Command "pwsh" -ErrorAction SilentlyContinue
    if ($null -eq $pwshCommand) {
        throw "gated_okx_dome_verify.ps1 requires PowerShell 7+ to load the current UTF-8 .env reliably. Install pwsh or run this script from PowerShell 7."
    }

    $forwardArgs = @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $PSCommandPath)
    foreach ($entry in $PSBoundParameters.GetEnumerator()) {
        $forwardArgs += ("-" + $entry.Key)
        if ($entry.Value -is [switch]) {
            if (-not $entry.Value.IsPresent) {
                $forwardArgs = $forwardArgs[0..($forwardArgs.Count - 2)]
            }
            continue
        }
        $forwardArgs += [string]$entry.Value
    }
    if ($null -ne $MyInvocation.UnboundArguments -and $MyInvocation.UnboundArguments.Count -gt 0) {
        $forwardArgs += $MyInvocation.UnboundArguments
    }

    & $pwshCommand.Source @forwardArgs
    exit $LASTEXITCODE
}

function Read-DotEnv {
    param([Parameter(Mandatory = $true)][string]$Path)
    $values = @{}
    if (-not (Test-Path $Path)) { return $values }
    foreach ($line in Get-Content $Path) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        if ($line.TrimStart().StartsWith("#")) { continue }
        $parts = $line.Split("=", 2)
        if ($parts.Length -ne 2) { continue }
        $key = $parts[0].Trim()
        $value = $parts[1].Trim()
        if (-not [string]::IsNullOrWhiteSpace($key)) {
            $values[$key] = $value
        }
    }
    return $values
}

function Get-ConfigValue {
    param(
        [Parameter(Mandatory = $true)][hashtable]$EnvMap,
        [Parameter(Mandatory = $true)][string]$Name,
        [string]$DefaultValue = ""
    )
    $runtimeValue = [Environment]::GetEnvironmentVariable($Name, "Process")
    if (-not [string]::IsNullOrWhiteSpace($runtimeValue)) { return $runtimeValue }
    if ($EnvMap.ContainsKey($Name)) { return $EnvMap[$Name] }
    return $DefaultValue
}

function Set-ProcessConfigValue {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [string]$Value = ""
    )
    if ([string]::IsNullOrWhiteSpace($Value)) {
        [Environment]::SetEnvironmentVariable($Name, $null, "Process")
        return
    }
    [Environment]::SetEnvironmentVariable($Name, $Value, "Process")
}

function Select-FirstNonBlankValue {
    param([Parameter(Mandatory = $true)][AllowEmptyString()][string[]]$Candidates)

    foreach ($candidate in $Candidates) {
        if (-not [string]::IsNullOrWhiteSpace($candidate)) {
            return $candidate
        }
    }
    return ""
}

function Import-EnvMapToProcess {
    param([Parameter(Mandatory = $true)][hashtable]$EnvMap)

    foreach ($entry in $EnvMap.GetEnumerator()) {
        $currentValue = [Environment]::GetEnvironmentVariable([string]$entry.Key, "Process")
        if (-not [string]::IsNullOrWhiteSpace($currentValue)) {
            continue
        }
        if ([string]::IsNullOrWhiteSpace([string]$entry.Value)) {
            continue
        }
        # Why: 第十九批开始脚本显式加载 `.env`；这里把文件中的键导入当前进程，确保后续解析与子进程启动看到的是同一份运行态配置。
        [Environment]::SetEnvironmentVariable([string]$entry.Key, [string]$entry.Value, "Process")
    }
}

function Resolve-OkxRuntimeConfig {
    param([Parameter(Mandatory = $true)][hashtable]$EnvMap)

    $rawEnv = (Get-ConfigValue -EnvMap $EnvMap -Name "NQ_OKX_ENV" -DefaultValue "dome").ToLowerInvariant()
    if ($rawEnv -eq "demo") { $rawEnv = "dome" }
    if ($rawEnv -notin @("dome", "real")) {
        throw "Unsupported NQ_OKX_ENV='$rawEnv'. Allowed values: dome | real"
    }

    $prefix = if ($rawEnv -eq "real") { "NQ_OKX_REAL_" } else { "NQ_OKX_DOME_" }
    # Why: 真实验收脚本要同时兼容统一运行时变量和 dome/real 两套原始变量；这里显式选取 first-non-blank，
    # 避免嵌套默认参数求值把有效的 env-specific 值吞掉，导致“文件里有凭证但脚本仍判定缺失”。
    $apiKey = Select-FirstNonBlankValue -Candidates @(
        (Get-ConfigValue -EnvMap $EnvMap -Name "NQ_OKX_API_KEY"),
        (Get-ConfigValue -EnvMap $EnvMap -Name ($prefix + "API_KEY"))
    )
    $apiSecret = Select-FirstNonBlankValue -Candidates @(
        (Get-ConfigValue -EnvMap $EnvMap -Name "NQ_OKX_API_SECRET"),
        (Get-ConfigValue -EnvMap $EnvMap -Name ($prefix + "API_SECRET"))
    )
    $apiPassphrase = Select-FirstNonBlankValue -Candidates @(
        (Get-ConfigValue -EnvMap $EnvMap -Name "NQ_OKX_API_PASSPHRASE"),
        (Get-ConfigValue -EnvMap $EnvMap -Name ($prefix + "API_PASSPHRASE"))
    )
    $baseUrl = Select-FirstNonBlankValue -Candidates @(
        (Get-ConfigValue -EnvMap $EnvMap -Name "NQ_OKX_BASE_URL"),
        (Get-ConfigValue -EnvMap $EnvMap -Name ($prefix + "BASE_URL"))
    )
    $wsUrl = Select-FirstNonBlankValue -Candidates @(
        (Get-ConfigValue -EnvMap $EnvMap -Name "NQ_OKX_WS_URL"),
        (Get-ConfigValue -EnvMap $EnvMap -Name ($prefix + "WS_URL"))
    )

    $missing = @()
    if ([string]::IsNullOrWhiteSpace($apiKey)) { $missing += ($prefix + "API_KEY / NQ_OKX_API_KEY") }
    if ([string]::IsNullOrWhiteSpace($apiSecret)) { $missing += ($prefix + "API_SECRET / NQ_OKX_API_SECRET") }
    if ([string]::IsNullOrWhiteSpace($apiPassphrase)) { $missing += ($prefix + "API_PASSPHRASE / NQ_OKX_API_PASSPHRASE") }
    if ($missing.Count -gt 0) {
        throw "Missing required config for NQ_OKX_ENV=${rawEnv}: $($missing -join ', ')"
    }

    return [PSCustomObject]@{
        EnvName       = $rawEnv
        Prefix        = $prefix
        ApiKey        = $apiKey
        ApiSecret     = $apiSecret
        ApiPassphrase = $apiPassphrase
        BaseUrl       = $baseUrl
        WsUrl         = $wsUrl
    }
}

function Apply-OkxRuntimeConfig {
    param([Parameter(Mandatory = $true)][object]$RuntimeConfig)

    # Why: 启动脚本统一把 dome/real 两套凭证归一成单套运行时变量，避免应用层继续感知两套命名。
    Set-ProcessConfigValue -Name "NQ_OKX_ENV" -Value $RuntimeConfig.EnvName
    Set-ProcessConfigValue -Name "NQ_OKX_API_KEY" -Value $RuntimeConfig.ApiKey
    Set-ProcessConfigValue -Name "NQ_OKX_API_SECRET" -Value $RuntimeConfig.ApiSecret
    Set-ProcessConfigValue -Name "NQ_OKX_API_PASSPHRASE" -Value $RuntimeConfig.ApiPassphrase
    Set-ProcessConfigValue -Name "NQ_OKX_BASE_URL" -Value $RuntimeConfig.BaseUrl
    Set-ProcessConfigValue -Name "NQ_OKX_WS_URL" -Value $RuntimeConfig.WsUrl
}

function New-TraceId {
    param([Parameter(Mandatory = $true)][string]$Prefix)
    return ("trc-gated-" + $Prefix + "-" + [DateTimeOffset]::UtcNow.ToString("yyyyMMddHHmmss") + "-" + (Get-Random -Minimum 100 -Maximum 999))
}

function New-ClientOrderId {
    param([Parameter(Mandatory = $true)][string]$Prefix)
    $seed = [DateTimeOffset]::UtcNow.ToString("MMddHHmmss")
    return ($Prefix + $seed)
}

function Invoke-GatedPost {
    param(
        [Parameter(Mandatory = $true)][string]$ServiceBaseUrl,
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$TraceId,
        [object]$Body = $null
    )

    $headers = @{ "X-NQ-TRACE-ID" = $TraceId }
    $uri = ($ServiceBaseUrl.TrimEnd("/") + $Path)
    $jsonBody = $null
    if ($null -ne $Body) { $jsonBody = $Body | ConvertTo-Json -Depth 8 }

    $response = if ($null -ne $jsonBody) {
        Invoke-WebRequest -Method Post -Uri $uri -Headers $headers -ContentType "application/json" -Body $jsonBody -SkipHttpErrorCheck
    } else {
        Invoke-WebRequest -Method Post -Uri $uri -Headers $headers -SkipHttpErrorCheck
    }

    $parsedBody = $null
    if (-not [string]::IsNullOrWhiteSpace($response.Content)) {
        try { $parsedBody = $response.Content | ConvertFrom-Json } catch { $parsedBody = $response.Content }
    }

    return [PSCustomObject]@{
        Uri        = $uri
        TraceId    = $TraceId
        StatusCode = [int]$response.StatusCode
        Body       = $parsedBody
        RawBody    = $response.Content
    }
}

function Invoke-GatedGet {
    param(
        [Parameter(Mandatory = $true)][string]$ServiceBaseUrl,
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$TraceId
    )

    $headers = @{ "X-NQ-TRACE-ID" = $TraceId }
    $uri = ($ServiceBaseUrl.TrimEnd("/") + $Path)
    $response = Invoke-WebRequest -Method Get -Uri $uri -Headers $headers -SkipHttpErrorCheck

    $parsedBody = $null
    if (-not [string]::IsNullOrWhiteSpace($response.Content)) {
        try { $parsedBody = $response.Content | ConvertFrom-Json } catch { $parsedBody = $response.Content }
    }

    return [PSCustomObject]@{
        Uri        = $uri
        TraceId    = $TraceId
        StatusCode = [int]$response.StatusCode
        Body       = $parsedBody
        RawBody    = $response.Content
    }
}

function Wait-ServiceHealth {
    param(
        [Parameter(Mandatory = $true)][string]$ServiceBaseUrl,
        [Parameter(Mandatory = $true)][int]$TimeoutSec
    )
    $healthUrl = ($ServiceBaseUrl.TrimEnd("/") + "/actuator/health")
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -Method Get -Uri $healthUrl -TimeoutSec 5 -SkipHttpErrorCheck
            if ([int]$response.StatusCode -eq 200) { return }
        } catch {
            # Expected during restart window.
        }
        Start-Sleep -Seconds 2
    }
    throw "Health check timeout: $healthUrl"
}

function Restart-LocalGatedApp {
    param(
        [Parameter(Mandatory = $true)][string]$ServiceBaseUrl,
        [Parameter(Mandatory = $true)][int]$TimeoutSec
    )

    $uri = [Uri]$ServiceBaseUrl
    $port = $uri.Port
    $listener = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -ne $listener) {
        Stop-Process -Id $listener.OwningProcess -Force
        Start-Sleep -Seconds 1
    }

    $repoRootLocal = Split-Path -Parent $PSScriptRoot
    $startupLog = Join-Path $repoRootLocal "artifacts/gated-okx-dome-app.log"
    $startupCommand = @"
Set-Location '$repoRootLocal'
`$env:NQ_DB_PORT='5432'
`$env:NQ_GATED_VERIFY_ENABLED='true'
`$env:NQ_OKX_ADAPTER_STUB_ON_BOOTSTRAP_FAILURE='false'
mvn -q -o -f backend/pom.xml -pl nq-app -am spring-boot:run *> '$startupLog'
"@
    Start-Process -FilePath "powershell.exe" -ArgumentList @(
        "-NoProfile",
        "-ExecutionPolicy", "Bypass",
        "-Command", $startupCommand
    ) -WorkingDirectory $repoRootLocal -WindowStyle Hidden | Out-Null
    try {
        Wait-ServiceHealth -ServiceBaseUrl $ServiceBaseUrl -TimeoutSec $TimeoutSec
    } catch {
        if (Test-Path $startupLog) {
            $tail = (Get-Content $startupLog -Tail 40) -join [Environment]::NewLine
            throw "Health check timeout after canonical non-fallback startup.`nstartupLog=$startupLog`n$tail"
        }
        throw
    }
}

function Parse-OrderIdFromDetail {
    param([Parameter(Mandatory = $true)][object]$Body)
    if ($Body -isnot [pscustomobject]) { return $null }
    if ($null -eq $Body.detail) { return $null }
    $match = [regex]::Match([string]$Body.detail, "order_id=([^,]+),")
    if ($match.Success) { return $match.Groups[1].Value.Trim() }
    return $null
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$envPath = if (-not [string]::IsNullOrWhiteSpace($EnvFilePath)) { $EnvFilePath } else { Join-Path $repoRoot ".env" }
$envMap = Read-DotEnv -Path $envPath
Import-EnvMapToProcess -EnvMap $envMap

$okxRuntime = Resolve-OkxRuntimeConfig -EnvMap $envMap
Apply-OkxRuntimeConfig -RuntimeConfig $okxRuntime

$verifyEnabled = (Get-ConfigValue -EnvMap $envMap -Name "NQ_GATED_VERIFY_ENABLED" -DefaultValue "false").ToLowerInvariant()
if ($verifyEnabled -ne "true" -and -not $AutoRestart) {
    throw "Acceptance endpoint disabled. Set NQ_GATED_VERIFY_ENABLED=true or run with -AutoRestart so the script can start nq-app with canonical verify enabled."
}
if ($verifyEnabled -ne "true" -and $AutoRestart) {
    Write-Host "NQ_GATED_VERIFY_ENABLED is not true in current env; canonical managed startup will force it to true."
}
Set-ProcessConfigValue -Name "NQ_GATED_VERIFY_ENABLED" -Value "true"

# Why: real 环境下绝不允许 fallback 冒充成功；dome 验收脚本也强制 non-fallback，和本地 smoke 区分开。
Set-ProcessConfigValue -Name "NQ_OKX_ADAPTER_STUB_ON_BOOTSTRAP_FAILURE" -Value "false"

$serviceBaseUrl = if (-not [string]::IsNullOrWhiteSpace($BaseUrl)) { $BaseUrl } else { "http://localhost:28081" }

Write-Host "== GateD OKX Verify =="
Write-Host "serviceBaseUrl=$serviceBaseUrl"
Write-Host "envFilePath=$envPath"
Write-Host "okxEnv=$($okxRuntime.EnvName)"
Write-Host "credentialSource=$($okxRuntime.Prefix)* -> NQ_OKX_API_* / NQ_OKX_BASE_URL / NQ_OKX_WS_URL"
Write-Host "startupMode=canonical_non_fallback (NQ_OKX_ADAPTER_STUB_ON_BOOTSTRAP_FAILURE=false)"
Write-Host ""

try {
    Wait-ServiceHealth -ServiceBaseUrl $serviceBaseUrl -TimeoutSec 5
} catch {
    if ($AutoRestart) {
        Write-Host "Service is not healthy yet, starting canonical non-fallback nq-app..."
        Restart-LocalGatedApp -ServiceBaseUrl $serviceBaseUrl -TimeoutSec $StartupTimeoutSec
    } else {
        throw
    }
}

$summary = @()

# UseCase A: limit far from market -> cancel -> reconcile
$caseAClientOrderId = New-ClientOrderId -Prefix "g6a"
$caseATracePlace = New-TraceId -Prefix "a-place"
$caseAPlace = Invoke-GatedPost -ServiceBaseUrl $serviceBaseUrl -Path "/__gated/orders" -TraceId $caseATracePlace -Body @{
    accountId     = 2001
    venue         = "OKX"
    clientOrderId = $caseAClientOrderId
    symbol        = "BTC-USDT"
    side          = "BUY"
    orderType     = "LIMIT"
    price         = 10000
    quantity      = 0.0002
}
$caseAOrderId = Parse-OrderIdFromDetail -Body $caseAPlace.Body
$caseATraceCancel = New-TraceId -Prefix "a-cancel"
$caseACancel = Invoke-GatedPost -ServiceBaseUrl $serviceBaseUrl -Path "/__gated/orders/cancel" -TraceId $caseATraceCancel -Body @{
    accountId     = 2001
    clientOrderId = $caseAClientOrderId
    reason        = "gated_dome_verify_a"
}
$caseATraceReconcile = New-TraceId -Prefix "a-reconcile"
$caseAReconcile = Invoke-GatedPost -ServiceBaseUrl $serviceBaseUrl -Path "/__gated/reconcile/runOnce" -TraceId $caseATraceReconcile -Body @{ limit = 100 }
$caseATraceOrder = if ($caseAOrderId) { New-TraceId -Prefix "a-order" } else { $null }
$caseAOrder = if ($caseAOrderId) {
    Invoke-GatedGet -ServiceBaseUrl $serviceBaseUrl -Path "/__gated/orders/$caseAOrderId" -TraceId $caseATraceOrder
} else {
    $null
}
$caseATraceTrade = if ($caseAOrderId) { New-TraceId -Prefix "a-trade" } else { $null }
$caseATrade = if ($caseAOrderId) {
    Invoke-GatedGet -ServiceBaseUrl $serviceBaseUrl -Path "/__gated/orders/$caseAOrderId/trade" -TraceId $caseATraceTrade
} else {
    $null
}
$summary += [PSCustomObject]@{
    UseCase = "A"
    Step    = "place/cancel/reconcile/query"
    Trace   = "$caseATracePlace | $caseATraceCancel | $caseATraceReconcile | $caseATraceOrder | $caseATraceTrade"
    Status  = "place=$($caseAPlace.StatusCode), cancel=$($caseACancel.StatusCode), reconcile=$($caseAReconcile.StatusCode), order=$($caseAOrder.StatusCode), trade=$($caseATrade.StatusCode)"
    Detail  = "orderId=$caseAOrderId; orderStatus=$($caseAOrder.Body.status); tradeBody=$($caseATrade.RawBody); place=$($caseAPlace.RawBody); cancel=$($caseACancel.RawBody); reconcile=$($caseAReconcile.RawBody)"
}

# UseCase B: market order -> reconcile
$caseBClientOrderId = New-ClientOrderId -Prefix "g6b"
$caseBTracePlace = New-TraceId -Prefix "b-place"
$caseBPlace = Invoke-GatedPost -ServiceBaseUrl $serviceBaseUrl -Path "/__gated/orders" -TraceId $caseBTracePlace -Body @{
    accountId     = 2001
    venue         = "OKX"
    clientOrderId = $caseBClientOrderId
    symbol        = "BTC-USDT"
    side          = "BUY"
    orderType     = "MARKET"
    quantity      = 12
}
$caseBTraceReconcile = New-TraceId -Prefix "b-reconcile"
$caseBReconcile = Invoke-GatedPost -ServiceBaseUrl $serviceBaseUrl -Path "/__gated/reconcile/runOnce" -TraceId $caseBTraceReconcile -Body @{ limit = 200 }
$caseBOrderId = Parse-OrderIdFromDetail -Body $caseBPlace.Body
$caseBTraceOrder = if ($caseBOrderId) { New-TraceId -Prefix "b-order" } else { $null }
$caseBOrder = if ($caseBOrderId) {
    Invoke-GatedGet -ServiceBaseUrl $serviceBaseUrl -Path "/__gated/orders/$caseBOrderId" -TraceId $caseBTraceOrder
} else {
    $null
}
$caseBTraceTrade = if ($caseBOrderId) { New-TraceId -Prefix "b-trade" } else { $null }
$caseBTrade = if ($caseBOrderId) {
    Invoke-GatedGet -ServiceBaseUrl $serviceBaseUrl -Path "/__gated/orders/$caseBOrderId/trade" -TraceId $caseBTraceTrade
} else {
    $null
}
$summary += [PSCustomObject]@{
    UseCase = "B"
    Step    = "place/reconcile/query"
    Trace   = "$caseBTracePlace | $caseBTraceReconcile | $caseBTraceOrder | $caseBTraceTrade"
    Status  = "place=$($caseBPlace.StatusCode), reconcile=$($caseBReconcile.StatusCode), order=$($caseBOrder.StatusCode), trade=$($caseBTrade.StatusCode)"
    Detail  = "orderId=$caseBOrderId; orderStatus=$($caseBOrder.Body.status); tradeBody=$($caseBTrade.RawBody); place=$($caseBPlace.RawBody); reconcile=$($caseBReconcile.RawBody)"
}

# UseCase C: non-terminal order -> restart -> recovery/reconcile
$caseCClientOrderId = New-ClientOrderId -Prefix "g6c"
$caseCTracePlace = New-TraceId -Prefix "c-place"
$caseCPlace = Invoke-GatedPost -ServiceBaseUrl $serviceBaseUrl -Path "/__gated/orders" -TraceId $caseCTracePlace -Body @{
    accountId     = 2001
    venue         = "OKX"
    clientOrderId = $caseCClientOrderId
    symbol        = "BTC-USDT"
    side          = "BUY"
    orderType     = "LIMIT"
    price         = 10000
    quantity      = 0.0002
}

Write-Host ""
if ($AutoRestart) {
    Write-Host "UseCase-C: auto restart mode enabled (stop/start + health wait)."
    Restart-LocalGatedApp -ServiceBaseUrl $serviceBaseUrl -TimeoutSec $StartupTimeoutSec
} else {
    Write-Host "UseCase-C: restart nq-app now, then continue."
    if (-not $SkipRestartPause) {
        [void](Read-Host "Press Enter after restart to continue recovery/reconcile")
    }
}

$caseCTraceRecovery = New-TraceId -Prefix "c-recovery"
$caseCRecovery = Invoke-GatedPost -ServiceBaseUrl $serviceBaseUrl -Path "/__gated/recovery/runOnce" -TraceId $caseCTraceRecovery
$caseCTraceReconcile = New-TraceId -Prefix "c-reconcile"
$caseCReconcile = Invoke-GatedPost -ServiceBaseUrl $serviceBaseUrl -Path "/__gated/reconcile/runOnce" -TraceId $caseCTraceReconcile -Body @{ limit = 200 }
$caseCTraceCancel = New-TraceId -Prefix "c-cancel"
$caseCCancel = Invoke-GatedPost -ServiceBaseUrl $serviceBaseUrl -Path "/__gated/orders/cancel" -TraceId $caseCTraceCancel -Body @{
    accountId     = 2001
    clientOrderId = $caseCClientOrderId
    reason        = "gated_dome_verify_c_finalize"
}
$caseCOrderId = Parse-OrderIdFromDetail -Body $caseCPlace.Body
$caseCTraceOrder = if ($caseCOrderId) { New-TraceId -Prefix "c-order" } else { $null }
$caseCOrder = if ($caseCOrderId) {
    Invoke-GatedGet -ServiceBaseUrl $serviceBaseUrl -Path "/__gated/orders/$caseCOrderId" -TraceId $caseCTraceOrder
} else {
    $null
}
$caseCTraceTrade = if ($caseCOrderId) { New-TraceId -Prefix "c-trade" } else { $null }
$caseCTrade = if ($caseCOrderId) {
    Invoke-GatedGet -ServiceBaseUrl $serviceBaseUrl -Path "/__gated/orders/$caseCOrderId/trade" -TraceId $caseCTraceTrade
} else {
    $null
}
$summary += [PSCustomObject]@{
    UseCase = "C"
    Step    = "place/restart/recovery/reconcile/cancel/query"
    Trace   = "$caseCTracePlace | $caseCTraceRecovery | $caseCTraceReconcile | $caseCTraceCancel | $caseCTraceOrder | $caseCTraceTrade"
    Status  = "place=$($caseCPlace.StatusCode), recovery=$($caseCRecovery.StatusCode), reconcile=$($caseCReconcile.StatusCode), cancel=$($caseCCancel.StatusCode), order=$($caseCOrder.StatusCode), trade=$($caseCTrade.StatusCode)"
    Detail  = "orderId=$caseCOrderId; orderStatus=$($caseCOrder.Body.status); tradeBody=$($caseCTrade.RawBody); place=$($caseCPlace.RawBody); recovery=$($caseCRecovery.RawBody); reconcile=$($caseCReconcile.RawBody); cancel=$($caseCCancel.RawBody)"
}

Write-Host ""
Write-Host "== GateD Dome Verify Summary =="
$summary | Format-Table -AutoSize




