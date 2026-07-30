[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:TestRoot = [IO.Path]::GetFullPath($PSScriptRoot)
$script:GateWRoot = [IO.Path]::GetFullPath((Join-Path $script:TestRoot '..'))
$script:ControlPath = Join-Path $script:GateWRoot 'gatew-okx-readonly-soak-control.ps1'
$script:WorkerPath = Join-Path $script:GateWRoot 'gatew-okx-readonly-soak.ps1'
$script:FailClosePath = Join-Path $script:GateWRoot 'gatew-okx-readonly-soak-failclose.ps1'
$script:ContractPath = Join-Path $script:GateWRoot 'gatew-soak-remediation-contract.psm1'
$script:Cases = [Collections.Generic.List[object]]::new()

Import-Module $script:ContractPath -Force

function Complete-Case
{
    param(
        [Parameter(Mandatory = $true)][int]$Number,
        [Parameter(Mandatory = $true)][string]$Name,
        [string]$Detail = 'PASS'
    )

    if ($Number -ne $script:Cases.Count + 1)
    {
        throw "SECURITY_CASE_ORDER_INVALID expected=$( $script:Cases.Count + 1 ) actual=$Number"
    }
    $script:Cases.Add([pscustomobject][ordered]@{
        number = $Number
        name = $Name
        result = 'PASS'
        detail = $Detail
    })
}

function Assert-OrderedText
{
    param(
        [Parameter(Mandatory = $true)][string]$Text,
        [Parameter(Mandatory = $true)][string[]]$Tokens,
        [Parameter(Mandatory = $true)][string]$FailureCode
    )

    $offset = 0
    foreach ($token in $Tokens)
    {
        $index = $Text.IndexOf($token, $offset, [StringComparison]::Ordinal)
        if ($index -lt 0)
        {
            throw $FailureCode
        }
        $offset = $index + $token.Length
    }
}

function Get-FunctionText
{
    param(
        [Parameter(Mandatory = $true)][string]$Text,
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$NextName
    )

    $start = $Text.IndexOf("function $Name", [StringComparison]::Ordinal)
    $end = $Text.IndexOf("function $NextName", $start, [StringComparison]::Ordinal)
    if ($start -lt 0 -or $end -le $start)
    {
        throw "SECURITY_FUNCTION_NOT_FOUND name=$Name"
    }
    return $Text.Substring($start, $end - $start)
}

function Start-TerminalContender
{
    param(
        [Parameter(Mandatory = $true)][string]$HelperPath,
        [Parameter(Mandatory = $true)][string]$Root,
        [Parameter(Mandatory = $true)][string]$Role,
        [Parameter(Mandatory = $true)][int]$DelayMilliseconds
    )

    $engine = (Get-Process -Id $PID).Path
    $arguments = @(
        '-NoProfile',
        '-ExecutionPolicy', 'Bypass',
        '-File', "`"$HelperPath`"",
        '-Root', "`"$Root`"",
        '-Role', $Role,
        '-DelayMilliseconds', [string]$DelayMilliseconds
    )
    $startParameters = @{
        FilePath = $engine
        ArgumentList = $arguments
        PassThru = $true
    }
    if ([Environment]::OSVersion.Platform -eq [PlatformID]::Win32NT)
    {
        $startParameters.WindowStyle = 'Hidden'
    }
    return Start-Process @startParameters
}

function Invoke-TerminalRace
{
    param(
        [Parameter(Mandatory = $true)][string]$HelperPath,
        [Parameter(Mandatory = $true)][string]$Root,
        [Parameter(Mandatory = $true)][string]$FirstRole,
        [Parameter(Mandatory = $true)][string]$SecondRole
    )

    [IO.Directory]::CreateDirectory($Root) | Out-Null
    $first = Start-TerminalContender $HelperPath $Root $FirstRole 0
    $second = Start-TerminalContender $HelperPath $Root $SecondRole 250
    $first.WaitForExit()
    $second.WaitForExit()
    if ($first.ExitCode -ne 0 -or $second.ExitCode -ne 0)
    {
        throw 'SECURITY_TERMINAL_CONTENDER_FAILED'
    }
    $terminalPath = Join-Path $Root 'terminal-status.json'
    if (-not (Test-Path -LiteralPath $terminalPath -PathType Leaf))
    {
        throw 'SECURITY_TERMINAL_MISSING'
    }
    $candidates = @(Get-ChildItem -LiteralPath $Root -File -Filter 'terminal-status*.json')
    $value = (Get-Content -LiteralPath $terminalPath -Raw).Trim()
    if ($candidates.Count -ne 1 -or $value -cne $FirstRole)
    {
        throw 'SECURITY_DUAL_OR_OVERWRITTEN_TERMINAL'
    }
    return $value
}

try
{
    foreach ($path in @(
        $script:ControlPath, $script:WorkerPath, $script:FailClosePath, $script:ContractPath
    ))
    {
        if (-not (Test-Path -LiteralPath $path -PathType Leaf))
        {
            throw "SECURITY_INPUT_MISSING path=$path"
        }
    }
    $controlText = [IO.File]::ReadAllText($script:ControlPath)
    $workerText = [IO.File]::ReadAllText($script:WorkerPath)
    $failCloseText = [IO.File]::ReadAllText($script:FailClosePath)
    $contractText = [IO.File]::ReadAllText($script:ContractPath)
    $securityText = [IO.File]::ReadAllText($PSCommandPath)
    if (-not $securityText.Contains(
            'if ([Environment]::OSVersion.Platform -eq [PlatformID]::Win32NT)'
    ) -or
            -not $securityText.Contains('$startParameters.WindowStyle = ''Hidden''') -or
            $securityText.Contains(('-WindowStyle' + ' Hidden')))
    {
        throw 'SECURITY_CROSS_PLATFORM_WINDOW_STYLE_INVALID'
    }

    if ($workerText.Contains('completion-marker.json') -or
            -not $workerText.Contains('Confirm-FormalCompletionBoundary') -or
            -not $controlText.Contains('"$( Get-ControlRoot $Value )/completion-marker.json"') -or
            -not $controlText.Contains(
                "Assert-PosixContract `$path 'regular file' '600' 'root' 'root'"
            ))
    {
        throw 'SECURITY_WORKER_FORGEABLE_COMPLETION_MARKER'
    }
    Complete-Case 1 'completion-marker-root-control-only'

    $verifyAcceptance = Get-FunctionText `
        $controlText 'Verify-FormalAcceptance' 'Verify-FormalTerminal'
    Assert-OrderedText $verifyAcceptance @(
        '$preMarkerResult = Test-GateWAcceptanceSnapshot $preMarkerSnapshot',
        'if ([bool]$preMarkerResult.accepted)',
        'Commit-AcceptanceCompletionMarker $RunId $snapshot',
        '$result = Test-GateWAcceptanceSnapshot $snapshot'
    ) 'SECURITY_COMPLETION_ATTESTATION_PRECONDITION_ORDER_INVALID'
    Complete-Case 2 'completion-marker-after-all-acceptance-preconditions'

    $finalizer = Get-FunctionText `
        $controlText 'Finalize-FormalAcceptance' 'Verify-FormalRun'
    Assert-OrderedText $finalizer @(
        "throw 'BLOCKED / ACCEPTANCE_VERIFY_REQUIRED'",
        'Test-AcceptanceCompletionMarker $RunId $proof',
        "Invoke-Native `$script:SystemctlPath @('stop'",
        'Write-JsonCreateOnce $terminalPath $terminal'
    ) 'SECURITY_ACCEPTANCE_FINALIZER_ORDER_INVALID'
    Complete-Case 3 'proof-and-root-marker-before-stop-and-terminal'

    $terminalVerifier = Get-FunctionText `
        $controlText 'Verify-FormalTerminal' 'Complete-AcceptedLifecycle'
    if (-not $terminalVerifier.Contains('Assert-GateWTerminalRecord $terminal') -or
            -not $terminalVerifier.Contains('Test-AcceptanceCompletionMarker $RunId $proof') -or
            -not $terminalVerifier.Contains("'terminal-status*.json'"))
    {
        throw 'SECURITY_TERMINAL_VERIFIER_BINDING_INVALID'
    }
    Complete-Case 4 'terminal-verifier-binds-proof-marker-and-single-candidate'

    $controlLock = Get-FunctionText `
        $controlText 'Enter-TerminalAuthorityLock' 'Assert-PathBelowRoot'
    $failCloseLock = Get-FunctionText `
        $failCloseText 'Enter-FinalizerLock' 'Assert-ExactFields'
    if (-not $controlLock.Contains('/failclose.lock') -or
            -not $failCloseLock.Contains('/failclose.lock') -or
            -not $controlLock.Contains('[IO.FileShare]::None') -or
            -not $failCloseLock.Contains('[IO.FileShare]::None'))
    {
        throw 'SECURITY_TERMINAL_LOCK_NOT_SHARED'
    }
    Complete-Case 5 'acceptance-and-failclose-share-exclusive-lock'

    Assert-OrderedText $finalizer @(
        '$terminalPath = "$( Get-ControlRoot $RunId )/terminal-status.json"',
        'if (Test-Path -LiteralPath $terminalPath -PathType Leaf)',
        "throw 'BLOCKED / ACCEPTANCE_VERIFY_REQUIRED'"
    ) 'SECURITY_CRASH_RECOVERY_EXISTING_TERMINAL_INVALID'
    if (-not $finalizer.Contains('NO_CHANGE / ACCEPTANCE_RESULT_ALREADY_FINALIZED') -or
            -not $finalizer.Contains('BLOCKED / TERMINAL_RESULT_CONFLICT'))
    {
        throw 'SECURITY_FINALIZER_IDEMPOTENCY_INVALID'
    }
    Complete-Case 6 'crash-retry-idempotency-and-conflict-rejection'

    $temporary = Join-Path ([IO.Path]::GetTempPath()) (
        'nq-gatew-remediation-security-' + [Guid]::NewGuid().ToString('N')
    )
    $systemTemporary = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    [IO.Directory]::CreateDirectory($temporary) | Out-Null
    try
    {
        $helperPath = Join-Path $temporary 'terminal-contender.ps1'
        $helperText = @'
param(
    [Parameter(Mandatory = $true)][string]$Root,
    [Parameter(Mandatory = $true)][string]$Role,
    [Parameter(Mandatory = $true)][int]$DelayMilliseconds
)
$ErrorActionPreference = 'Stop'
Start-Sleep -Milliseconds $DelayMilliseconds
$lockPath = Join-Path $Root 'failclose.lock'
$terminalPath = Join-Path $Root 'terminal-status.json'
$deadline = [DateTimeOffset]::UtcNow.AddSeconds(5)
$lock = $null
while ($null -eq $lock)
{
    try
    {
        $lock = [IO.FileStream]::new(
            $lockPath, [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite,
            [IO.FileShare]::None, 1, [IO.FileOptions]::WriteThrough
        )
    }
    catch
    {
        if ([DateTimeOffset]::UtcNow -ge $deadline)
        {
            exit 2
        }
        Start-Sleep -Milliseconds 25
    }
}
try
{
    if (-not (Test-Path -LiteralPath $terminalPath -PathType Leaf))
    {
        $bytes = [Text.UTF8Encoding]::new($false).GetBytes($Role)
        try
        {
            $stream = [IO.FileStream]::new(
                $terminalPath, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write,
                [IO.FileShare]::None, 4096, [IO.FileOptions]::WriteThrough
            )
            try
            {
                $stream.Write($bytes, 0, $bytes.Length)
                $stream.Flush($true)
            }
            finally
            {
                $stream.Dispose()
            }
        }
        finally
        {
            [Array]::Clear($bytes, 0, $bytes.Length)
        }
    }
}
finally
{
    $lock.Dispose()
}
'@
        [IO.File]::WriteAllText(
            $helperPath, $helperText, [Text.UTF8Encoding]::new($false)
        )

        $failCloseFirst = Join-Path $temporary 'failclose-first'
        $null = Invoke-TerminalRace `
            $helperPath $failCloseFirst 'REJECTED_BY_FAILCLOSE' 'ACCEPTED_BY_FINALIZER'
        Complete-Case 7 'concurrency-failclose-first-rejects-acceptance-overwrite'

        $acceptanceFirst = Join-Path $temporary 'acceptance-first'
        $null = Invoke-TerminalRace `
            $helperPath $acceptanceFirst 'ACCEPTED_BY_FINALIZER' 'REJECTED_BY_FAILCLOSE'
        Complete-Case 8 'concurrency-acceptance-first-rejects-failclose-overwrite'

        $temporaryOnly = Join-Path $temporary 'temporary-file-window'
        [IO.Directory]::CreateDirectory($temporaryOnly) | Out-Null
        [IO.File]::WriteAllText(
            (Join-Path $temporaryOnly '.create-deadbeef'),
            'PARTIAL',
            [Text.UTF8Encoding]::new($false)
        )
        if (@(Get-ChildItem -LiteralPath $temporaryOnly -File -Filter 'terminal-status*.json').Count -ne 0)
        {
            throw 'SECURITY_TEMPORARY_FILE_TREATED_AS_TERMINAL'
        }
        $null = Invoke-TerminalRace `
            $helperPath $temporaryOnly 'ACCEPTED_AFTER_RETRY' 'REJECTED_AFTER_RETRY'
        Complete-Case 9 'temporary-write-crash-window-recoverable'

        foreach ($fixture in @(
            @{ Name = 'accepted-existing'; Value = 'ACCEPTED_EXISTING'; Contender = 'REJECTED_LATE' },
            @{ Name = 'rejected-existing'; Value = 'REJECTED_EXISTING'; Contender = 'ACCEPTED_LATE' }
        ))
        {
            $root = Join-Path $temporary ([string]$fixture.Name)
            [IO.Directory]::CreateDirectory($root) | Out-Null
            [IO.File]::WriteAllText(
                (Join-Path $root 'terminal-status.json'),
                [string]$fixture.Value,
                [Text.UTF8Encoding]::new($false)
            )
            $process = Start-TerminalContender `
                $helperPath $root ([string]$fixture.Contender) 0
            $process.WaitForExit()
            if ($process.ExitCode -ne 0 -or
                    (Get-Content -LiteralPath (Join-Path $root 'terminal-status.json') -Raw).Trim() -cne
                            [string]$fixture.Value)
            {
                throw 'SECURITY_EXISTING_TERMINAL_OVERWRITTEN'
            }
        }
        Complete-Case 10 'accepted-and-rejected-terminals-cannot-overwrite-each-other'
    }
    finally
    {
        $resolvedTemporary = [IO.Path]::GetFullPath($temporary)
        if (-not $resolvedTemporary.StartsWith(
                $systemTemporary,
                [StringComparison]::OrdinalIgnoreCase
        ))
        {
            throw 'SECURITY_TEMPORARY_CLEANUP_BOUNDARY_INVALID'
        }
        if (Test-Path -LiteralPath $resolvedTemporary)
        {
            Remove-Item -LiteralPath $resolvedTemporary -Recurse -Force
        }
    }

    $markerSnapshot = [pscustomobject][ordered]@{
        runId = 'gatew-soak-20260722T111144Z-ac00f878'
        releaseCommit = ('1' * 40)
        mainPid = 4074358L
        lastValidSampleAt = '2026-07-29T11:19:59.5201964Z'
        evidenceManifestSha256 = ('2' * 64)
        evidenceFinalChainHash = ('3' * 64)
    }
    $marker = New-GateWCompletionMarkerRecord $markerSnapshot
    Assert-GateWCompletionMarkerRecord $marker $markerSnapshot | Out-Null
    $tamperedMarker = $marker | ConvertTo-Json -Depth 10 | ConvertFrom-Json
    $tamperedMarker.evidenceFinalChainHash = ('4' * 64)
    $tamperRejected = $false
    try
    {
        Assert-GateWCompletionMarkerRecord $tamperedMarker $markerSnapshot | Out-Null
    }
    catch
    {
        $tamperRejected = $true
    }
    if (-not $tamperRejected)
    {
        throw 'SECURITY_COMPLETION_MARKER_TAMPER_ACCEPTED'
    }
    Complete-Case 11 'completion-marker-schema-checksum-and-evidence-binding'

    $stopIntent = Get-FunctionText `
        $controlText 'Write-ControlledStopIntent' 'Request-OperatorStop'
    Assert-OrderedText $stopIntent @(
        "throw 'BLOCKED / STOP_INTENT_STALE_AFTER_EXIT'",
        'stop-intent-retired-',
        'Move-Item -LiteralPath $path -Destination $retiredPath',
        '$requestedAt = (Get-UtcNow).ToString(''o'')',
        'Write-JsonCreateOnce $path $record'
    ) 'SECURITY_STOP_INTENT_RETIREMENT_ORDER_INVALID'
    if (-not $contractText.Contains(
            "'OPERATOR_STOP_REQUESTED', 'ACCEPTANCE_FINALIZATION'"
    ) -or -not $controlText.Contains(
            "'PREPARING', 'STARTING', 'RUNNING', 'OPERATOR_STOPPING'"
    ))
    {
        throw 'SECURITY_STOP_INTENT_REASON_OR_RECOVERY_INVALID'
    }
    Complete-Case 12 'stop-intent-reason-allowlist-and-stale-retirement'

    if ($script:Cases.Count -ne 12)
    {
        throw "SECURITY_CASE_COUNT_INVALID actual=$( $script:Cases.Count )"
    }
    [pscustomobject][ordered]@{
        decision = 'PASS / GATEW_SOAK_REMEDIATION_SECURITY_REGRESSION'
        cases = $script:Cases.Count
        completionMarkerAuthority = 'PASS / ROOT_CONTROL_ONLY'
        crashWindows = 'PASS / FAIL_CLOSED_AND_RETRY_SAFE'
        terminalConcurrency = 'PASS / SHARED_LOCK_AND_CREATE_ONCE'
        results = @($script:Cases)
    } | ConvertTo-Json -Depth 8
}
catch
{
    [pscustomobject][ordered]@{
        decision = 'FAIL / GATEW_SOAK_REMEDIATION_SECURITY_REGRESSION'
        casesPassed = $script:Cases.Count
        detail = $_.Exception.Message
    } | ConvertTo-Json -Depth 4
    exit 2
}
