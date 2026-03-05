[CmdletBinding()]
param(
    [string]$BaseUrl,
    [switch]$SkipRestartPause,
    [switch]$AutoRestart,
    [int]$StartupTimeoutSec = 180
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

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

function New-TraceId {
    param([Parameter(Mandatory = $true)][string]$Prefix)
    return ("trc-gatec-" + $Prefix + "-" + [DateTimeOffset]::UtcNow.ToString("yyyyMMddHHmmss") + "-" + (Get-Random -Minimum 100 -Maximum 999))
}

function New-ClientOrderId {
    param([Parameter(Mandatory = $true)][string]$Prefix)
    $seed = [DateTimeOffset]::UtcNow.ToString("MMddHHmmss")
    return ($Prefix + $seed)
}

function Invoke-GatecPost {
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

function Restart-LocalGatecApp {
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
    $startScript = Join-Path $repoRootLocal "artifacts/start-gatec-app-local.cmd"
    if (-not (Test-Path $startScript)) {
        throw "Missing startup script: $startScript"
    }

    Start-Process -FilePath $startScript -ArgumentList "true" -WorkingDirectory $repoRootLocal -WindowStyle Hidden | Out-Null
    Wait-ServiceHealth -ServiceBaseUrl $ServiceBaseUrl -TimeoutSec $TimeoutSec
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
$envPath = Join-Path $repoRoot ".env"
$envMap = Read-DotEnv -Path $envPath

$okxEnv = (Get-ConfigValue -EnvMap $envMap -Name "NQ_OKX_ENV" -DefaultValue "").ToLowerInvariant()
if ($okxEnv -ne "dome") {
    throw "Script requires NQ_OKX_ENV=dome, current value is '$okxEnv'"
}

$verifyEnabled = (Get-ConfigValue -EnvMap $envMap -Name "NQ_GATEC_VERIFY_ENABLED" -DefaultValue "false").ToLowerInvariant()
if ($verifyEnabled -ne "true") {
    throw "Acceptance endpoint disabled. Set NQ_GATEC_VERIFY_ENABLED=true first."
}

$requiredKeys = @(
    "NQ_OKX_DOME_BASE_URL",
    "NQ_OKX_DOME_API_KEY",
    "NQ_OKX_DOME_API_SECRET",
    "NQ_OKX_DOME_API_PASSPHRASE"
)
foreach ($key in $requiredKeys) {
    $value = Get-ConfigValue -EnvMap $envMap -Name $key -DefaultValue ""
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "Missing required config: $key (set it in local .env)"
    }
}

$serviceBaseUrl = if (-not [string]::IsNullOrWhiteSpace($BaseUrl)) { $BaseUrl } else { "http://localhost:28081" }

Write-Host "== GateC OKX Dome Verify =="
Write-Host "serviceBaseUrl=$serviceBaseUrl"
Write-Host "okxEnv=$okxEnv"
Write-Host ""

Wait-ServiceHealth -ServiceBaseUrl $serviceBaseUrl -TimeoutSec $StartupTimeoutSec

$summary = @()

# UseCase A: limit far from market -> cancel -> reconcile
$caseAClientOrderId = New-ClientOrderId -Prefix "g6a"
$caseATracePlace = New-TraceId -Prefix "a-place"
$caseAPlace = Invoke-GatecPost -ServiceBaseUrl $serviceBaseUrl -Path "/__gatec/orders" -TraceId $caseATracePlace -Body @{
    accountId     = 2001
    venue         = "OKX"
    clientOrderId = $caseAClientOrderId
    symbol        = "BTC-USDT"
    side          = "BUY"
    type          = "LIMIT"
    price         = 10000
    qty           = 0.0002
}
$caseAOrderId = Parse-OrderIdFromDetail -Body $caseAPlace.Body
$caseATraceCancel = New-TraceId -Prefix "a-cancel"
$caseACancel = Invoke-GatecPost -ServiceBaseUrl $serviceBaseUrl -Path "/__gatec/orders/cancel" -TraceId $caseATraceCancel -Body @{
    accountId     = 2001
    clientOrderId = $caseAClientOrderId
    reason        = "gatec_dome_verify_a"
}
$caseATraceReconcile = New-TraceId -Prefix "a-reconcile"
$caseAReconcile = Invoke-GatecPost -ServiceBaseUrl $serviceBaseUrl -Path "/__gatec/reconcile/runOnce" -TraceId $caseATraceReconcile -Body @{ limit = 100 }
$summary += [PSCustomObject]@{
    UseCase = "A"
    Step    = "place/cancel/reconcile"
    Trace   = "$caseATracePlace | $caseATraceCancel | $caseATraceReconcile"
    Status  = "place=$($caseAPlace.StatusCode), cancel=$($caseACancel.StatusCode), reconcile=$($caseAReconcile.StatusCode)"
    Detail  = "orderId=$caseAOrderId; place=$($caseAPlace.RawBody); cancel=$($caseACancel.RawBody); reconcile=$($caseAReconcile.RawBody)"
}

# UseCase B: market order -> reconcile
$caseBClientOrderId = New-ClientOrderId -Prefix "g6b"
$caseBTracePlace = New-TraceId -Prefix "b-place"
$caseBPlace = Invoke-GatecPost -ServiceBaseUrl $serviceBaseUrl -Path "/__gatec/orders" -TraceId $caseBTracePlace -Body @{
    accountId     = 2001
    venue         = "OKX"
    clientOrderId = $caseBClientOrderId
    symbol        = "BTC-USDT"
    side          = "BUY"
    type          = "MARKET"
    qty           = 12
}
$caseBTraceReconcile = New-TraceId -Prefix "b-reconcile"
$caseBReconcile = Invoke-GatecPost -ServiceBaseUrl $serviceBaseUrl -Path "/__gatec/reconcile/runOnce" -TraceId $caseBTraceReconcile -Body @{ limit = 200 }
$summary += [PSCustomObject]@{
    UseCase = "B"
    Step    = "place/reconcile"
    Trace   = "$caseBTracePlace | $caseBTraceReconcile"
    Status  = "place=$($caseBPlace.StatusCode), reconcile=$($caseBReconcile.StatusCode)"
    Detail  = "place=$($caseBPlace.RawBody); reconcile=$($caseBReconcile.RawBody)"
}

# UseCase C: non-terminal order -> restart -> recovery/reconcile
$caseCClientOrderId = New-ClientOrderId -Prefix "g6c"
$caseCTracePlace = New-TraceId -Prefix "c-place"
$caseCPlace = Invoke-GatecPost -ServiceBaseUrl $serviceBaseUrl -Path "/__gatec/orders" -TraceId $caseCTracePlace -Body @{
    accountId     = 2001
    venue         = "OKX"
    clientOrderId = $caseCClientOrderId
    symbol        = "BTC-USDT"
    side          = "BUY"
    type          = "LIMIT"
    price         = 10000
    qty           = 0.0002
}

Write-Host ""
if ($AutoRestart) {
    Write-Host "UseCase-C: auto restart mode enabled (stop/start + health wait)."
    Restart-LocalGatecApp -ServiceBaseUrl $serviceBaseUrl -TimeoutSec $StartupTimeoutSec
} else {
    Write-Host "UseCase-C: restart nq-app now, then continue."
    if (-not $SkipRestartPause) {
        [void](Read-Host "Press Enter after restart to continue recovery/reconcile")
    }
}

$caseCTraceRecovery = New-TraceId -Prefix "c-recovery"
$caseCRecovery = Invoke-GatecPost -ServiceBaseUrl $serviceBaseUrl -Path "/__gatec/recovery/runOnce" -TraceId $caseCTraceRecovery
$caseCTraceReconcile = New-TraceId -Prefix "c-reconcile"
$caseCReconcile = Invoke-GatecPost -ServiceBaseUrl $serviceBaseUrl -Path "/__gatec/reconcile/runOnce" -TraceId $caseCTraceReconcile -Body @{ limit = 200 }
$caseCTraceCancel = New-TraceId -Prefix "c-cancel"
$caseCCancel = Invoke-GatecPost -ServiceBaseUrl $serviceBaseUrl -Path "/__gatec/orders/cancel" -TraceId $caseCTraceCancel -Body @{
    accountId     = 2001
    clientOrderId = $caseCClientOrderId
    reason        = "gatec_dome_verify_c_finalize"
}
$summary += [PSCustomObject]@{
    UseCase = "C"
    Step    = "place/restart/recovery/reconcile/cancel"
    Trace   = "$caseCTracePlace | $caseCTraceRecovery | $caseCTraceReconcile | $caseCTraceCancel"
    Status  = "place=$($caseCPlace.StatusCode), recovery=$($caseCRecovery.StatusCode), reconcile=$($caseCReconcile.StatusCode), cancel=$($caseCCancel.StatusCode)"
    Detail  = "place=$($caseCPlace.RawBody); recovery=$($caseCRecovery.RawBody); reconcile=$($caseCReconcile.RawBody); cancel=$($caseCCancel.RawBody)"
}

Write-Host ""
Write-Host "== GateC Dome Verify Summary =="
$summary | Format-Table -AutoSize
