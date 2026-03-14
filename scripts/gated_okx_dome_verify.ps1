[CmdletBinding()]
param(
    [string]$BaseUrl,
    [string]$AccountId,
    [string]$AppLogPath,
    [string]$EnvFilePath,
    [switch]$ForcePlaceTimeoutOnce,
    [switch]$ForceCancelTimeoutOnce,
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

function Resolve-ServiceBaseUrl {
    param(
        [Parameter(Mandatory = $true)][hashtable]$EnvMap,
        [string]$ExplicitBaseUrl
    )

    if (-not [string]::IsNullOrWhiteSpace($ExplicitBaseUrl)) {
        return $ExplicitBaseUrl.TrimEnd("/")
    }

    $configuredBaseUrl = Select-FirstNonBlankValue -Candidates @(
        (Get-ConfigValue -EnvMap $EnvMap -Name "NQ_GATED_SERVICE_BASE_URL"),
        (Get-ConfigValue -EnvMap $EnvMap -Name "NQ_APP_BASE_URL")
    )
    if (-not [string]::IsNullOrWhiteSpace($configuredBaseUrl)) {
        return $configuredBaseUrl.TrimEnd("/")
    }

    $configuredPort = Get-ConfigValue -EnvMap $EnvMap -Name "NQ_APP_PORT" -DefaultValue "18888"
    if ([string]::IsNullOrWhiteSpace($configuredPort)) {
        $configuredPort = "18888"
    }

    # Why: 第廿一批修复脚本 health timeout 的根因是默认 serviceBaseUrl 仍残留旧 `28081`；
    # 这里统一收口到应用真实端口配置，未显式提供时默认回退 `http://localhost:18888`，与 nq-app 的 `server.port` 默认值一致。
    return ("http://localhost:" + $configuredPort)
}

function Resolve-VerifyAccountId {
    param(
        [Parameter(Mandatory = $true)][hashtable]$EnvMap,
        [string]$ExplicitAccountId
    )

    $configuredAccountId = Select-FirstNonBlankValue -Candidates @(
        $ExplicitAccountId,
        (Get-ConfigValue -EnvMap $EnvMap -Name "NQ_GATED_ACCOUNT_ID"),
        (Get-ConfigValue -EnvMap $EnvMap -Name "NQ_OKX_VERIFY_ACCOUNT_ID"),
        (Get-ConfigValue -EnvMap $EnvMap -Name "NQ_ACCOUNT_ID")
    )

    if ([string]::IsNullOrWhiteSpace($configuredAccountId)) {
        $configuredAccountId = "1001"
    }

    $parsedAccountId = 0L
    # Why: 第二十二批开始脚本不再写死 `2001`；这里统一把参数 / 环境变量 / 本地默认账号收口成单一 accountId，
    # 并在进入真实 OKX 验收链之前做格式校验，避免再次把“账号参数错误”误判成业务链路或 OKX 本身失败。
    if (-not [long]::TryParse($configuredAccountId, [ref]$parsedAccountId) -or $parsedAccountId -le 0) {
        throw "Unsupported verify accountId='$configuredAccountId'. Provide -AccountId, NQ_GATED_ACCOUNT_ID, NQ_OKX_VERIFY_ACCOUNT_ID, or NQ_ACCOUNT_ID with a positive integer."
    }

    return $parsedAccountId
}

function Resolve-AppLogPath {
    param(
        [Parameter(Mandatory = $true)][hashtable]$EnvMap,
        [string]$ExplicitAppLogPath
    )

    $configuredAppLogPath = Select-FirstNonBlankValue -Candidates @(
        $ExplicitAppLogPath,
        (Get-ConfigValue -EnvMap $EnvMap -Name "NQ_GATED_APP_LOG_PATH"),
        (Get-ConfigValue -EnvMap $EnvMap -Name "NQ_APP_LOG_PATH")
    )

    if (-not [string]::IsNullOrWhiteSpace($configuredAppLogPath)) {
        return $configuredAppLogPath
    }

    return (Join-Path (Split-Path -Parent $PSScriptRoot) "artifacts/gated-okx-dome-app.log")
}

function Resolve-ConfigFlag {
    param(
        [Parameter(Mandatory = $true)][hashtable]$EnvMap,
        [string]$ExplicitValue,
        [Parameter(Mandatory = $true)][string[]]$Names
    )

    $rawValue = Select-FirstNonBlankValue -Candidates @(
        $ExplicitValue
        $(foreach ($name in $Names) { Get-ConfigValue -EnvMap $EnvMap -Name $name })
    )
    if ([string]::IsNullOrWhiteSpace($rawValue)) {
        return $false
    }
    return @("1", "true", "yes", "on") -contains $rawValue.Trim().ToLowerInvariant()
}

function Add-GrepLogFile {
    param(
        [Parameter(Mandatory = $true)][object]$Collection,
        [string]$Path
    )

    if ($null -eq $Collection -or [string]::IsNullOrWhiteSpace($Path)) {
        return
    }

    if (-not ($Collection -contains $Path)) {
        [void]$Collection.Add($Path)
    }
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

function Get-QueryConfirmSamples {
    param(
        [Parameter(Mandatory = $true)][string]$LogPath,
        [Parameter(Mandatory = $true)][string[]]$TraceIds,
        [int]$WaitSec = 3
    )

    $normalizedTraceIds = @($TraceIds | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -Unique)
    if ($normalizedTraceIds.Count -eq 0) {
        return @()
    }

    $deadline = (Get-Date).AddSeconds($WaitSec)
    do {
        if (Test-Path $LogPath) {
            $allLines = Get-Content $LogPath -ErrorAction SilentlyContinue
            $matchedLines = foreach ($line in $allLines) {
                if ($line -notmatch "okx_query_confirm_") { continue }
                foreach ($traceId in $normalizedTraceIds) {
                    if ($line -like "*$traceId*") {
                        $line
                        break
                    }
                }
            }
            if ($matchedLines) {
                return @($matchedLines | Select-Object -Unique)
            }
        }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)

    return @()
}

function Get-LogSamples {
    param(
        [Parameter(Mandatory = $true)][string]$LogPath,
        [string[]]$TraceIds = @(),
        [Parameter(Mandatory = $true)][string[]]$Keywords,
        [int]$WaitSec = 3
    )

    $normalizedTraceIds = @($TraceIds | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -Unique)
    $normalizedKeywords = @($Keywords | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -Unique)
    if ($normalizedKeywords.Count -eq 0) {
        return @()
    }

    $deadline = (Get-Date).AddSeconds($WaitSec)
    do {
        if (Test-Path $LogPath) {
            $allLines = Get-Content $LogPath -ErrorAction SilentlyContinue
            $matchedLines = foreach ($line in $allLines) {
                $keywordMatched = $false
                foreach ($keyword in $normalizedKeywords) {
                    if ($line -like "*$keyword*") {
                        $keywordMatched = $true
                        break
                    }
                }
                if (-not $keywordMatched) { continue }

                if ($normalizedTraceIds.Count -eq 0) {
                    $line
                    continue
                }

                foreach ($traceId in $normalizedTraceIds) {
                    if ($line -like "*$traceId*") {
                        $line
                        break
                    }
                }
            }
            if ($matchedLines) {
                return @($matchedLines | Select-Object -Unique)
            }
        }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)

    return @()
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
        [Parameter(Mandatory = $true)][int]$TimeoutSec,
        [Parameter(Mandatory = $true)][string]$StartupLogPath
    )

    $uri = [Uri]$ServiceBaseUrl
    $port = $uri.Port
    $listener = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -ne $listener) {
        Stop-Process -Id $listener.OwningProcess -Force
        Start-Sleep -Seconds 1
    }

    $repoRootLocal = Split-Path -Parent $PSScriptRoot
    $startupLog = $StartupLogPath
    $startupLogDirectory = Split-Path -Parent $startupLog
    if (-not [string]::IsNullOrWhiteSpace($startupLogDirectory) -and -not (Test-Path $startupLogDirectory)) {
        New-Item -ItemType Directory -Path $startupLogDirectory -Force | Out-Null
    }
    if (Test-Path $startupLog) {
        try {
            Remove-Item $startupLog -Force
        } catch {
            $startupLog = Join-Path $startupLogDirectory (
                [System.IO.Path]::GetFileNameWithoutExtension($StartupLogPath) +
                "-" +
                [DateTimeOffset]::UtcNow.ToString("yyyyMMddHHmmssfff") +
                [System.IO.Path]::GetExtension($StartupLogPath)
            )
        }
    }
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
    return $startupLog
}

function Parse-OrderIdFromDetail {
    param([Parameter(Mandatory = $true)][object]$Body)
    if ($null -eq $Body) { return $null }
    $detailProperty = $Body.PSObject.Properties["detail"]
    if ($null -eq $detailProperty) { return $null }
    if ($null -eq $detailProperty.Value) { return $null }
    $match = [regex]::Match([string]$detailProperty.Value, "order_id=([^,]+),")
    if ($match.Success) { return $match.Groups[1].Value.Trim() }
    return $null
}

function Get-ResponseStatusToken {
    param([object]$Response)
    if ($null -eq $Response) { return "n/a" }
    $statusProperty = $Response.PSObject.Properties["StatusCode"]
    if ($null -eq $statusProperty -or $null -eq $statusProperty.Value) { return "n/a" }
    return [string]$statusProperty.Value
}

function Get-ResponseRawBodyValue {
    param([object]$Response)
    if ($null -eq $Response) { return "" }
    $rawBodyProperty = $Response.PSObject.Properties["RawBody"]
    if ($null -eq $rawBodyProperty -or $null -eq $rawBodyProperty.Value) { return "" }
    return [string]$rawBodyProperty.Value
}

function Get-BodyPropertyValue {
    param(
        [object]$Body,
        [Parameter(Mandatory = $true)][string]$PropertyName
    )
    if ($null -eq $Body) { return $null }
    $bodyProperty = $Body.PSObject.Properties[$PropertyName]
    if ($null -eq $bodyProperty) { return $null }
    return $bodyProperty.Value
}

function Get-ResponseBodyPropertyValue {
    param(
        [object]$Response,
        [Parameter(Mandatory = $true)][string]$PropertyName
    )
    if ($null -eq $Response) { return $null }
    $bodyProperty = $Response.PSObject.Properties["Body"]
    if ($null -eq $bodyProperty) { return $null }
    return Get-BodyPropertyValue -Body $bodyProperty.Value -PropertyName $PropertyName
}

function Parse-ExternalOrderIdFromDetail {
    param([object]$Body)
    if ($null -eq $Body) { return $null }
    $detailProperty = $Body.PSObject.Properties["detail"]
    if ($null -eq $detailProperty) { return $null }
    if ($null -eq $detailProperty.Value) { return $null }
    $match = [regex]::Match([string]$detailProperty.Value, "external_order_id=([^,]+),")
    if ($match.Success) { return $match.Groups[1].Value.Trim() }
    return $null
}

function Should-CancelResidualOrder {
    param([string]$Status)
    if ([string]::IsNullOrWhiteSpace($Status)) {
        return $false
    }

    # Why: 独立 place-timeout probe 只做取证，不留残余挂单。
    # 只有订单仍处于可撤非终态时，才执行 cleanup cancel。
    return $Status -notin @("FILLED", "REJECTED", "CANCELLED", "EXPIRED")
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

$serviceBaseUrl = Resolve-ServiceBaseUrl -EnvMap $envMap -ExplicitBaseUrl $BaseUrl
$verifyAccountId = Resolve-VerifyAccountId -EnvMap $envMap -ExplicitAccountId $AccountId
$appLogPath = Resolve-AppLogPath -EnvMap $envMap -ExplicitAppLogPath $AppLogPath
$forcePlaceTimeoutEvidence = Resolve-ConfigFlag -EnvMap $envMap -ExplicitValue $(if ($ForcePlaceTimeoutOnce) { "true" } else { "" }) -Names @("NQ_OKX_FORCE_PLACE_TIMEOUT_ONCE")
$forceCancelTimeoutEvidence = Resolve-ConfigFlag -EnvMap $envMap -ExplicitValue $(if ($ForceCancelTimeoutOnce) { "true" } else { "" }) -Names @("NQ_OKX_FORCE_CANCEL_TIMEOUT_ONCE")
Set-ProcessConfigValue -Name "NQ_GATED_ACCOUNT_ID" -Value ([string]$verifyAccountId)
Set-ProcessConfigValue -Name "NQ_GATED_APP_LOG_PATH" -Value $appLogPath
Set-ProcessConfigValue -Name "NQ_OKX_FORCE_PLACE_TIMEOUT_ONCE" -Value $(if ($forcePlaceTimeoutEvidence) { "true" } else { "" })
Set-ProcessConfigValue -Name "NQ_OKX_FORCE_CANCEL_TIMEOUT_ONCE" -Value $(if ($forceCancelTimeoutEvidence) { "true" } else { "" })

if (($forcePlaceTimeoutEvidence -or $forceCancelTimeoutEvidence) -and -not $AutoRestart) {
    Write-Host "Forced timeout evidence is enabled without -AutoRestart; ensure the running nq-app process has been restarted with the current env flags."
}

Write-Host "== GateD OKX Verify =="
Write-Host "serviceBaseUrl=$serviceBaseUrl"
Write-Host "verifyAccountId=$verifyAccountId"
Write-Host "appLogPath=$appLogPath"
Write-Host "envFilePath=$envPath"
Write-Host "okxEnv=$($okxRuntime.EnvName)"
Write-Host "credentialSource=$($okxRuntime.Prefix)* -> NQ_OKX_API_* / NQ_OKX_BASE_URL / NQ_OKX_WS_URL"
Write-Host "startupMode=canonical_non_fallback (NQ_OKX_ADAPTER_STUB_ON_BOOTSTRAP_FAILURE=false)"
Write-Host "forcedTimeoutEvidence=$(if ($forcePlaceTimeoutEvidence -and $forceCancelTimeoutEvidence) { 'place_once,cancel_once' } elseif ($forcePlaceTimeoutEvidence) { 'place_once' } elseif ($forceCancelTimeoutEvidence) { 'cancel_once' } else { 'none' })"
Write-Host "limitCancelProbePrice=10000"
Write-Host "limitCancelProbeQuantity=0.00005"
Write-Host "placeTimeoutProbePrice=10000"
Write-Host "placeTimeoutProbeQuantity=0.00005"
Write-Host "marketTradeProbeSymbol=BTC-USDT"
Write-Host "marketTradeProbeSide=SELL"
Write-Host "marketTradeProbeQuantity=0.00002"
Write-Host ""

$restartLogPath = ""
try {
    Wait-ServiceHealth -ServiceBaseUrl $serviceBaseUrl -TimeoutSec 5
} catch {
    if ($AutoRestart) {
        Write-Host "Service is not healthy yet, starting canonical non-fallback nq-app..."
        $appLogPath = Restart-LocalGatedApp -ServiceBaseUrl $serviceBaseUrl -TimeoutSec $StartupTimeoutSec -StartupLogPath $appLogPath
        $restartLogPath = $appLogPath
        Write-Host "restartLogPath=$appLogPath"
    } else {
        throw
    }
}

$summary = @()
$grepLogFiles = New-Object 'System.Collections.Generic.List[string]'
# Why: real OKX 当前可用 USDT 约为 1，旧样本 `10000 * 0.0002 = 2 USDT` 会稳定触发 51008；
# 这里把 A/C 收口到低于当前余额、又仍远离市价的最小可撤 LIMIT 样本。
$limitCancelProbePrice = 10000
$limitCancelProbeQuantity = 0.00005
$placeTimeoutProbePrice = $limitCancelProbePrice
$placeTimeoutProbeQuantity = $limitCancelProbeQuantity
# Why: UseCase-B 目标是拿到当前余额可承受、可解释、可复现的真实 MARKET 样本；
# 当前账户同时有 BTC availBal=0.000380993976 与 USDT availBal=0.9988651685332477；
# 这里改为 BTC-USDT MARKET SELL 0.00002，使名义金额约 1.416 USDT，跨过 51020 的最小下单额门槛，且远低于当前 BTC 可用余额。
$marketTradeProbeSymbol = "BTC-USDT"
$marketTradeProbeSide = "SELL"
$marketTradeProbeQuantity = 0.00002

Add-GrepLogFile -Collection $grepLogFiles -Path $appLogPath

# UseCase P: dedicated place timeout probe -> reconcile/query -> conditional cancel cleanup
$casePClientOrderId = New-ClientOrderId -Prefix "g6p"
$casePTracePlace = New-TraceId -Prefix "p-place"
$casePPlace = Invoke-GatedPost -ServiceBaseUrl $serviceBaseUrl -Path "/__gated/orders" -TraceId $casePTracePlace -Body @{
    accountId     = $verifyAccountId
    venue         = "OKX"
    clientOrderId = $casePClientOrderId
    symbol        = "BTC-USDT"
    side          = "BUY"
    orderType     = "LIMIT"
    price         = $placeTimeoutProbePrice
    quantity      = $placeTimeoutProbeQuantity
}
$casePOrderId = Parse-OrderIdFromDetail -Body $casePPlace.Body
$casePTraceReconcile = New-TraceId -Prefix "p-reconcile"
$casePReconcile = Invoke-GatedPost -ServiceBaseUrl $serviceBaseUrl -Path "/__gated/reconcile/runOnce" -TraceId $casePTraceReconcile -Body @{ limit = 100 }
$casePTraceOrder = if ($casePOrderId) { New-TraceId -Prefix "p-order" } else { $null }
$casePOrder = if ($casePOrderId) {
    Invoke-GatedGet -ServiceBaseUrl $serviceBaseUrl -Path "/__gated/orders/$casePOrderId" -TraceId $casePTraceOrder
} else {
    $null
}
$casePTraceTrade = if ($casePOrderId) { New-TraceId -Prefix "p-trade" } else { $null }
$casePTrade = if ($casePOrderId) {
    Invoke-GatedGet -ServiceBaseUrl $serviceBaseUrl -Path "/__gated/orders/$casePOrderId/trade" -TraceId $casePTraceTrade
} else {
    $null
}
$casePOrderStatusBeforeCleanup = [string](Get-ResponseBodyPropertyValue -Response $casePOrder -PropertyName 'status')
$casePExternalOrderId = [string](Get-ResponseBodyPropertyValue -Response $casePOrder -PropertyName 'externalOrderId')
if ([string]::IsNullOrWhiteSpace($casePExternalOrderId)) {
    $casePExternalOrderId = [string](Parse-ExternalOrderIdFromDetail -Body $casePPlace.Body)
}
$casePTraceCancel = $null
$casePCancel = $null
if ($casePOrderId -and (Should-CancelResidualOrder -Status $casePOrderStatusBeforeCleanup)) {
    $casePTraceCancel = New-TraceId -Prefix "p-cancel"
    $casePCancel = Invoke-GatedPost -ServiceBaseUrl $serviceBaseUrl -Path "/__gated/orders/cancel" -TraceId $casePTraceCancel -Body @{
        accountId     = $verifyAccountId
        clientOrderId = $casePClientOrderId
        reason        = "gated_dome_verify_place_timeout_probe_cleanup"
    }
}
$casePTraceFinalOrder = if ($casePOrderId) { New-TraceId -Prefix "p-order-final" } else { $null }
$casePFinalOrder = if ($casePOrderId) {
    Invoke-GatedGet -ServiceBaseUrl $serviceBaseUrl -Path "/__gated/orders/$casePOrderId" -TraceId $casePTraceFinalOrder
} else {
    $null
}
$casePFinalOrderStatus = [string](Get-ResponseBodyPropertyValue -Response $casePFinalOrder -PropertyName 'status')
if ([string]::IsNullOrWhiteSpace($casePFinalOrderStatus)) {
    $casePFinalOrderStatus = $casePOrderStatusBeforeCleanup
}
$casePEnabledSamples = @(Get-LogSamples -LogPath $appLogPath -Keywords @("okx_force_timeout_place_once_enabled"))
$casePPlaceEvidenceSamples = @(Get-LogSamples -LogPath $appLogPath -TraceIds @($casePTracePlace) -Keywords @(
    "okx_force_timeout_place_once_consumed",
    "okx_force_timeout_place_once_throwing_http_timeout",
    "okx_query_confirm_place_started",
    "okx_query_confirm_place_resolved",
    "okx_query_confirm_place_unconfirmed"
))
$summary += [PSCustomObject]@{
    UseCase = "P"
    Step    = "place-timeout-probe/reconcile/query/conditional-cancel-cleanup"
    Trace   = "$casePTracePlace | $casePTraceReconcile | $casePTraceOrder | $casePTraceTrade | $casePTraceCancel | $casePTraceFinalOrder"
    Status  = "place=$(Get-ResponseStatusToken -Response $casePPlace), reconcile=$(Get-ResponseStatusToken -Response $casePReconcile), order=$(Get-ResponseStatusToken -Response $casePOrder), trade=$(Get-ResponseStatusToken -Response $casePTrade), cancel=$(Get-ResponseStatusToken -Response $casePCancel), finalOrder=$(Get-ResponseStatusToken -Response $casePFinalOrder)"
    Detail  = "clientOrderId=$casePClientOrderId; orderId=$casePOrderId; externalOrderId=$casePExternalOrderId; orderStatusBeforeCleanup=$casePOrderStatusBeforeCleanup; finalOrderStatus=$casePFinalOrderStatus; place=$(Get-ResponseRawBodyValue -Response $casePPlace); reconcile=$(Get-ResponseRawBodyValue -Response $casePReconcile); tradeBody=$(Get-ResponseRawBodyValue -Response $casePTrade); cancel=$(Get-ResponseRawBodyValue -Response $casePCancel); enabled=$(if ($casePEnabledSamples.Count -gt 0) { $casePEnabledSamples -join ' || ' } else { 'no_place_force_enabled_log_sample' }); placeTimeout=$(if ($casePPlaceEvidenceSamples.Count -gt 0) { $casePPlaceEvidenceSamples -join ' || ' } else { 'no_place_timeout_log_sample' })"
}

# UseCase A: limit far from market -> cancel -> reconcile
$caseAClientOrderId = New-ClientOrderId -Prefix "g6a"
$caseATracePlace = New-TraceId -Prefix "a-place"
$caseAPlace = Invoke-GatedPost -ServiceBaseUrl $serviceBaseUrl -Path "/__gated/orders" -TraceId $caseATracePlace -Body @{
    accountId     = $verifyAccountId
    venue         = "OKX"
    clientOrderId = $caseAClientOrderId
    symbol        = "BTC-USDT"
    side          = "BUY"
    orderType     = "LIMIT"
    price         = $limitCancelProbePrice
    quantity      = $limitCancelProbeQuantity
}
$caseAOrderId = Parse-OrderIdFromDetail -Body $caseAPlace.Body
$caseATraceCancel = New-TraceId -Prefix "a-cancel"
$caseACancel = Invoke-GatedPost -ServiceBaseUrl $serviceBaseUrl -Path "/__gated/orders/cancel" -TraceId $caseATraceCancel -Body @{
    accountId     = $verifyAccountId
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
Add-GrepLogFile -Collection $grepLogFiles -Path $appLogPath
$caseAQueryConfirmSamples = @(Get-QueryConfirmSamples -LogPath $appLogPath -TraceIds @($caseATracePlace, $caseATraceCancel))
$summary += [PSCustomObject]@{
    UseCase = "A"
    Step    = "place/cancel/reconcile/query"
    Trace   = "$caseATracePlace | $caseATraceCancel | $caseATraceReconcile | $caseATraceOrder | $caseATraceTrade"
    Status  = "place=$(Get-ResponseStatusToken -Response $caseAPlace), cancel=$(Get-ResponseStatusToken -Response $caseACancel), reconcile=$(Get-ResponseStatusToken -Response $caseAReconcile), order=$(Get-ResponseStatusToken -Response $caseAOrder), trade=$(Get-ResponseStatusToken -Response $caseATrade)"
    Detail  = "orderId=$caseAOrderId; orderStatus=$(Get-ResponseBodyPropertyValue -Response $caseAOrder -PropertyName 'status'); tradeBody=$(Get-ResponseRawBodyValue -Response $caseATrade); place=$(Get-ResponseRawBodyValue -Response $caseAPlace); cancel=$(Get-ResponseRawBodyValue -Response $caseACancel); reconcile=$(Get-ResponseRawBodyValue -Response $caseAReconcile); queryConfirm=$(if ($caseAQueryConfirmSamples.Count -gt 0) { $caseAQueryConfirmSamples -join ' || ' } else { 'no_query_confirm_log_sample' })"
}

# UseCase B: market order -> reconcile
$caseBClientOrderId = New-ClientOrderId -Prefix "g6b"
$caseBTracePlace = New-TraceId -Prefix "b-place"
$caseBPlace = Invoke-GatedPost -ServiceBaseUrl $serviceBaseUrl -Path "/__gated/orders" -TraceId $caseBTracePlace -Body @{
    accountId     = $verifyAccountId
    venue         = "OKX"
    clientOrderId = $caseBClientOrderId
    symbol        = $marketTradeProbeSymbol
    side          = $marketTradeProbeSide
    orderType     = "MARKET"
    quantity      = $marketTradeProbeQuantity
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
Add-GrepLogFile -Collection $grepLogFiles -Path $appLogPath
$caseBQueryConfirmSamples = @(Get-QueryConfirmSamples -LogPath $appLogPath -TraceIds @($caseBTracePlace))
$summary += [PSCustomObject]@{
    UseCase = "B"
    Step    = "place/reconcile/query"
    Trace   = "$caseBTracePlace | $caseBTraceReconcile | $caseBTraceOrder | $caseBTraceTrade"
    Status  = "place=$(Get-ResponseStatusToken -Response $caseBPlace), reconcile=$(Get-ResponseStatusToken -Response $caseBReconcile), order=$(Get-ResponseStatusToken -Response $caseBOrder), trade=$(Get-ResponseStatusToken -Response $caseBTrade)"
    Detail  = "orderId=$caseBOrderId; orderStatus=$(Get-ResponseBodyPropertyValue -Response $caseBOrder -PropertyName 'status'); tradeBody=$(Get-ResponseRawBodyValue -Response $caseBTrade); place=$(Get-ResponseRawBodyValue -Response $caseBPlace); reconcile=$(Get-ResponseRawBodyValue -Response $caseBReconcile); queryConfirm=$(if ($caseBQueryConfirmSamples.Count -gt 0) { $caseBQueryConfirmSamples -join ' || ' } else { 'no_query_confirm_log_sample' })"
}

# UseCase C: non-terminal order -> restart -> recovery/reconcile
$caseCClientOrderId = New-ClientOrderId -Prefix "g6c"
$caseCTracePlace = New-TraceId -Prefix "c-place"
$caseCPlace = Invoke-GatedPost -ServiceBaseUrl $serviceBaseUrl -Path "/__gated/orders" -TraceId $caseCTracePlace -Body @{
    accountId     = $verifyAccountId
    venue         = "OKX"
    clientOrderId = $caseCClientOrderId
    symbol        = "BTC-USDT"
    side          = "BUY"
    orderType     = "LIMIT"
    price         = $limitCancelProbePrice
    quantity      = $limitCancelProbeQuantity
}

Write-Host ""
if ($AutoRestart) {
    Write-Host "UseCase-C: auto restart mode enabled (stop/start + health wait)."
    $appLogPath = Restart-LocalGatedApp -ServiceBaseUrl $serviceBaseUrl -TimeoutSec $StartupTimeoutSec -StartupLogPath $appLogPath
    $restartLogPath = $appLogPath
    Write-Host "restartLogPath=$appLogPath"
} else {
    Write-Host "UseCase-C: restart nq-app now, then continue."
    if (-not $SkipRestartPause) {
        [void](Read-Host "Press Enter after restart to continue recovery/reconcile")
        Write-Host "UseCase-C: waiting for service health after manual restart..."
        Wait-ServiceHealth -ServiceBaseUrl $serviceBaseUrl -TimeoutSec $StartupTimeoutSec
    }
}

$caseCTraceRecovery = New-TraceId -Prefix "c-recovery"
$caseCRecovery = Invoke-GatedPost -ServiceBaseUrl $serviceBaseUrl -Path "/__gated/recovery/runOnce" -TraceId $caseCTraceRecovery
$caseCTraceReconcile = New-TraceId -Prefix "c-reconcile"
$caseCReconcile = Invoke-GatedPost -ServiceBaseUrl $serviceBaseUrl -Path "/__gated/reconcile/runOnce" -TraceId $caseCTraceReconcile -Body @{ limit = 200 }
$caseCTraceCancel = New-TraceId -Prefix "c-cancel"
$caseCCancel = Invoke-GatedPost -ServiceBaseUrl $serviceBaseUrl -Path "/__gated/orders/cancel" -TraceId $caseCTraceCancel -Body @{
    accountId     = $verifyAccountId
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
Add-GrepLogFile -Collection $grepLogFiles -Path $appLogPath
$caseCQueryConfirmSamples = @(Get-QueryConfirmSamples -LogPath $appLogPath -TraceIds @($caseCTracePlace, $caseCTraceCancel))
$summary += [PSCustomObject]@{
    UseCase = "C"
    Step    = "place/restart/recovery/reconcile/cancel/query"
    Trace   = "$caseCTracePlace | $caseCTraceRecovery | $caseCTraceReconcile | $caseCTraceCancel | $caseCTraceOrder | $caseCTraceTrade"
    Status  = "place=$(Get-ResponseStatusToken -Response $caseCPlace), recovery=$(Get-ResponseStatusToken -Response $caseCRecovery), reconcile=$(Get-ResponseStatusToken -Response $caseCReconcile), cancel=$(Get-ResponseStatusToken -Response $caseCCancel), order=$(Get-ResponseStatusToken -Response $caseCOrder), trade=$(Get-ResponseStatusToken -Response $caseCTrade)"
    Detail  = "orderId=$caseCOrderId; orderStatus=$(Get-ResponseBodyPropertyValue -Response $caseCOrder -PropertyName 'status'); tradeBody=$(Get-ResponseRawBodyValue -Response $caseCTrade); place=$(Get-ResponseRawBodyValue -Response $caseCPlace); recovery=$(Get-ResponseRawBodyValue -Response $caseCRecovery); reconcile=$(Get-ResponseRawBodyValue -Response $caseCReconcile); cancel=$(Get-ResponseRawBodyValue -Response $caseCCancel); queryConfirm=$(if ($caseCQueryConfirmSamples.Count -gt 0) { $caseCQueryConfirmSamples -join ' || ' } else { 'no_query_confirm_log_sample' })"
}

Write-Host ""
Write-Host "== GateD Dome Verify Summary =="
$summaryMetadata = [PSCustomObject]@{
    appLogPath               = $appLogPath
    restartLogPath           = $(if ([string]::IsNullOrWhiteSpace($restartLogPath)) { "n/a" } else { $restartLogPath })
    grepLogFiles             = $(if ($grepLogFiles.Count -gt 0) { $grepLogFiles.ToArray() -join " | " } else { "none" })
    marketTradeProbeSymbol   = $marketTradeProbeSymbol
    marketTradeProbeSide     = $marketTradeProbeSide
    marketTradeProbeQuantity = $marketTradeProbeQuantity
}
$summaryMetadata | Format-List
$summary | Format-Table -AutoSize









