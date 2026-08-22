[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$gateyRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$control = Join-Path $gateyRoot 'invoke-gatey-exact-pilot-scope.ps1'
$engine = (Get-Process -Id $PID).Path
$tempRoot = Join-Path ([IO.Path]::GetTempPath()) `
    ('nq-gatey-exact-scope-' + [Guid]::NewGuid().ToString('N'))
$utf8 = [Text.UTF8Encoding]::new($false)
$cases = [Collections.Generic.List[string]]::new()

function Assert-Decision([string]$Path, [string]$Expected, [int]$ExpectedExit)
{
    $output = @(& $engine -NoProfile -File $control -Action ValidateInput -InputPath $Path 2>&1)
    if ([int]$LASTEXITCODE -ne $ExpectedExit)
    {
        throw "EXIT_INVALID file=$([IO.Path]::GetFileName($Path)) expected=$ExpectedExit actual=$LASTEXITCODE output=$($output -join ' ')"
    }
    $result = ($output -join "`n") | ConvertFrom-Json
    if ([string]$result.decision -cne $Expected)
    {
        throw "DECISION_INVALID expected=$Expected actual=$($result.decision)"
    }
}

function Write-Input([string]$Name, $Value)
{
    $path = Join-Path $tempRoot ($Name + '.json')
    [IO.File]::WriteAllText($path, ($Value | ConvertTo-Json -Depth 10), $utf8)
    return $path
}

function New-Correlation([string]$Suffix)
{
    return [ordered]@{
        requestId = 'request-' + $Suffix
        traceId = 'trace-' + $Suffix
        idempotencyKey = 'idempotency-' + $Suffix
    }
}

function New-ValidInput
{
    return [ordered]@{
        creatorPrincipal = 11
        approverPrincipal = 22
        pilotScope = [ordered]@{
            sessionId = '11111111-1111-1111-1111-111111111111'
            pilotScopeId = '22222222-2222-2222-2222-222222222222'
            exchangeAccountId = 21
            credentialReferenceId = 31
            strategyReleaseId = 'release-record'
            releaseDigest = 'a' * 64
            releaseAdmissionRevision = 1
            risk = [ordered]@{
                riskPolicyId = '33333333-3333-3333-3333-333333333333'
                riskPolicyDigest = 'b' * 64
                version = 1
                capitalCap = '25.00000000'
                maxOrderNotional = '20.00000000'
                maxSymbolPositionNotional = '25.00000000'
                maxDailyRealizedLoss = '5.00000000'
                maxDailyTotalLoss = '10.00000000'
                maxOpenOrders = 1
                maxIntradayOrders = 2
                symbolAllowlist = @('BTC-USDT')
                maxSessionDurationSeconds = 900
                spreadLimitBps = '10.00000000'
                slippageLimitBps = '10.00000000'
                maxMarketDataAgeMs = 1000
                minDataCoverageBps = 9000
            }
            symbolAllowlist = @('BTC-USDT')
            capitalCap = '25.00000000'
            pilotWindowStart = '2026-08-22T10:00:00Z'
            pilotWindowEnd = '2026-08-22T10:10:00Z'
            expectedPilotScopeHash = 'c' * 64
            correlation = New-Correlation 'pilot'
        }
        pilotApproval = [ordered]@{
            approvalId = '44444444-4444-4444-4444-444444444444'
            pilotScopeId = '22222222-2222-2222-2222-222222222222'
            expectedPilotScopeHash = 'c' * 64
            reason = 'exact scope reviewed'
            approvedAt = '2026-08-22T09:59:59Z'
            expiresAt = '2026-08-22T10:05:00Z'
        }
        binding = [ordered]@{
            bindingId = '55555555-5555-5555-5555-555555555555'
            instrumentId = 101
            exchangeInstrumentId = 'BTC-USDT'
            side = 'BUY'
            orderType = 'LIMIT'
            price = '100.00000000'
            quantity = '0.10000000'
            notional = '10.00000000'
            pilotWindowStart = '2026-08-22T10:00:00Z'
            pilotWindowEnd = '2026-08-22T10:10:00Z'
            correlation = New-Correlation 'binding'
            bindingExpiresAt = '2026-08-22T10:05:00Z'
        }
        exactScopeApproval = [ordered]@{
            creatorCorrelation = New-Correlation 'creator'
            approverCorrelation = New-Correlation 'approver'
            reason = 'APPROVED_FOR_EXACT_PILOT_MATERIALIZATION'
            approvedAt = '2026-08-22T09:59:59Z'
            expiresAt = '2026-08-22T10:05:00Z'
        }
    }
}

try
{
    [IO.Directory]::CreateDirectory($tempRoot) | Out-Null
    $valid = New-ValidInput
    Assert-Decision (Write-Input 'valid' $valid) 'PASS / OPERATOR_EXACT_SCOPE_INPUT_VALID' 0
    $cases.Add('closed-exact-input-pass')

    $self = New-ValidInput; $self.approverPrincipal = $self.creatorPrincipal
    Assert-Decision (Write-Input 'self' $self) 'BLOCKED / OPERATOR_EXACT_SCOPE_INPUT_INVALID' 2
    $cases.Add('self-approval-blocked')

    $market = New-ValidInput; $market.binding.orderType = 'MARKET'
    Assert-Decision (Write-Input 'market' $market) 'BLOCKED / OPERATOR_EXACT_SCOPE_INPUT_INVALID' 2
    $cases.Add('market-fallback-blocked')

    $secret = New-ValidInput; $secret | Add-Member -NotePropertyName apiKey -NotePropertyValue 'forbidden'
    Assert-Decision (Write-Input 'secret' $secret) `
        'BLOCKED / OPERATOR_EXACT_SCOPE_INPUT_SCHEMA_INVALID' 2
    $cases.Add('secret-shaped-field-blocked')

    $nestedSecret = New-ValidInput
    $nestedSecret.binding['passphrase'] = 'forbidden'
    Assert-Decision (Write-Input 'nested-secret' $nestedSecret) `
        'BLOCKED / OPERATOR_EXACT_SCOPE_INPUT_SCHEMA_INVALID' 2
    $cases.Add('nested-secret-shaped-field-blocked')

    Assert-Decision (Join-Path $tempRoot 'missing.json') `
        'BLOCKED / OPERATOR_EXACT_SCOPE_INPUT_REQUIRED' 2
    $cases.Add('missing-input-blocked')

    $source = Get-Content -LiteralPath $control -Raw
    $forbiddenPatterns = @(
        '\bcu' + 'rl\b', '\bps' + 'ql\b', '\bJS' + 'hell\b',
        'generic ' + 'bean', 'cons' + 'ume\(', 'PLA' + 'CE', 'CAN' + 'CEL'
    )
    if (@($forbiddenPatterns | Where-Object { $source -match $_ }).Count -ne 0)
    {
        throw 'FORBIDDEN_CONTROL_SURFACE_FOUND'
    }
    $cases.Add('no-debug-http-sql-consume-or-mutation-surface')

    if ($cases.Count -ne 7) { throw "CASE_COUNT_INVALID:$($cases.Count)" }
    [pscustomobject][ordered]@{
        decision = 'PASS / GATEY_EXACT_PILOT_SCOPE_CONTROL_REGRESSION'
        cases = $cases.Count
        results = $cases
        providerCalls = 0
        exchangeMutation = 0
        bindingConsumption = 0
    } | ConvertTo-Json -Depth 5
}
finally
{
    if (Test-Path -LiteralPath $tempRoot)
    {
        $resolved = [IO.Path]::GetFullPath($tempRoot)
        $tempBase = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
        if (-not $resolved.StartsWith($tempBase, [StringComparison]::OrdinalIgnoreCase))
        {
            throw 'TEMP_CLEANUP_PATH_INVALID'
        }
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
