[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$gateyRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$repo = (Resolve-Path (Join-Path $gateyRoot '../..')).Path
$orchestrator = Join-Path $gateyRoot 'invoke-gatey-readonly-runtime-deployment.ps1'
$contractRegression = Join-Path $PSScriptRoot `
    'run-gatey-readonly-runtime-deployment-contract-regression.ps1'
$unit = Join-Path $repo 'deploy/systemd/nq-gatey-readonly-qualification.service'
$template = Join-Path $repo 'deploy/gatey/gatey-readonly-runtime.env.example'
$secretTemplate = Join-Path $repo 'deploy/gatey/gatey-readonly-runtime.secrets.env.example'
$pgpassTemplate = Join-Path $repo 'deploy/gatey/gatey-readonly-db.pgpass.example'
$target = Join-Path $repo 'deploy/gatey/gatey-readonly-runtime-target.json'
$serviceUser = 'nq-gatey-readonly-linux-test'
$tempRoot = Join-Path '/tmp' ('nq-gatey-runtime-' + [Guid]::NewGuid().ToString('N'))
$cases = [Collections.Generic.List[string]]::new()

function Assert-Condition([bool]$Value, [string]$Message)
{
    if (-not $Value) { throw $Message }
}

function Complete-Case([string]$Name)
{
    $cases.Add($Name)
}

$linux = Get-Variable -Name IsLinux -ErrorAction SilentlyContinue
if ($null -eq $linux -or -not [bool]$linux.Value -or [Environment]::UserName -cne 'root')
{
    throw 'BLOCKED / DISPOSABLE_ROOT_LINUX_REQUIRED'
}

try
{
    & /usr/bin/id -u $serviceUser 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0)
    {
        & /usr/sbin/useradd --system --no-create-home --shell /usr/sbin/nologin $serviceUser
        if ($LASTEXITCODE -ne 0) { throw 'SERVICE_USER_FIXTURE_FAILED' }
    }
    [IO.Directory]::CreateDirectory($tempRoot) | Out-Null

    $unitSource = Get-Content -LiteralPath $unit -Raw
    Assert-Condition (
        $unitSource.Contains('ProtectSystem=strict') -and
        $unitSource.Contains('NoNewPrivileges=true') -and
        $unitSource.Contains('ReadOnlyPaths=/opt/nexus-quant/releases') -and
        $unitSource.Contains('EnvironmentFile=/etc/nexus-quant/gatey-readonly-qualification/runtime.env') -and
        $unitSource.Contains('EnvironmentFile=/etc/nexus-quant/gatey-readonly-qualification/secrets.env')
    ) 'SYSTEMD_STATIC_CONTRACT_INVALID'
    Complete-Case 'systemd-static-contract-pass'

    $analyze = Get-Command systemd-analyze -ErrorAction SilentlyContinue
    if ($null -ne $analyze)
    {
        $analyzeOutput = @(& $analyze.Source verify $unit 2>&1)
        $fatal = @($analyzeOutput | Where-Object {
            $_ -match 'Failed to parse|Unknown key|Missing equal sign|Invalid argument'
        })
        Assert-Condition ($fatal.Count -eq 0) ('SYSTEMD_ANALYZE_FAILED:' + ($fatal -join ' '))
    }
    Complete-Case 'systemd-analyze-static-pass'

    $envPath = Join-Path $tempRoot 'runtime.env'
    $envText = Get-Content -LiteralPath $template -Raw
    $envText = $envText.Replace('REPLACE_WITH_EXACT_COMMIT', ('1' * 40))
    $envText = $envText.Replace('REPLACE_WITH_LOCAL', 'fixture-local-value')
    [IO.File]::WriteAllText($envPath, $envText, [Text.UTF8Encoding]::new($false))
    & /usr/bin/chown ('root:' + $serviceUser) -- $envPath
    & /usr/bin/chmod 0640 -- $envPath

    $secretPath = Join-Path $tempRoot 'secrets.env'
    [IO.File]::Copy($secretTemplate, $secretPath)
    & /usr/bin/chown root:root -- $secretPath
    & /usr/bin/chmod 0600 -- $secretPath
    & /usr/sbin/runuser -u $serviceUser -- /usr/bin/test -r $secretPath
    Assert-Condition ($LASTEXITCODE -eq 1) 'SERVICE_USER_SECRET_READ_ALLOWED'
    & /usr/sbin/runuser -u $serviceUser -- /usr/bin/test -w $secretPath
    Assert-Condition ($LASTEXITCODE -eq 1) 'SERVICE_USER_SECRET_WRITE_ALLOWED'
    Complete-Case 'root-only-secret-env-pass'

    $pgpassPath = Join-Path $tempRoot 'db.pgpass'
    [IO.File]::Copy($pgpassTemplate, $pgpassPath)
    & /usr/bin/chown root:root -- $pgpassPath
    & /usr/bin/chmod 0600 -- $pgpassPath
    & /usr/sbin/runuser -u $serviceUser -- /usr/bin/test -r $pgpassPath
    Assert-Condition ($LASTEXITCODE -eq 1) 'SERVICE_USER_PGPASS_READ_ALLOWED'
    Complete-Case 'root-only-pgpass-pass'
    $metadata = (& /usr/bin/stat --format='%F|%U|%G|%a' -- $envPath).Trim()
    Assert-Condition ($metadata -ceq ('regular file|root|' + $serviceUser + '|640')) `
        ('ENV_METADATA_INVALID:' + $metadata)
    Complete-Case 'env-owner-mode-pass'
    & /usr/sbin/runuser -u $serviceUser -- /usr/bin/test -r $envPath
    Assert-Condition ($LASTEXITCODE -eq 0) 'SERVICE_USER_ENV_READ_FAILED'
    & /usr/sbin/runuser -u $serviceUser -- /usr/bin/test -w $envPath
    Assert-Condition ($LASTEXITCODE -eq 1) 'SERVICE_USER_ENV_WRITE_ALLOWED'
    Complete-Case 'service-user-read-only-env-pass'

    & /usr/bin/chmod 0644 -- $envPath
    $wrongMode = (& /usr/bin/stat --format='%a' -- $envPath).Trim()
    Assert-Condition ($wrongMode -ceq '644') 'WRONG_MODE_FIXTURE_INVALID'
    Complete-Case 'wrong-env-mode-fixture-detected'
    & /usr/bin/chmod 0640 -- $envPath

    $targetValue = Get-Content -LiteralPath $target -Raw | ConvertFrom-Json
    Assert-Condition (
        [string]$targetValue.database.host -ceq '127.0.0.1' -and
        [int]$targetValue.database.port -eq 5432 -and
        [string]$targetValue.database.name -ceq 'nexus_quant' -and
        [string]$targetValue.database.credentialReferenceName -ceq
            'gatey-readonly-qualification-db'
    ) 'DATABASE_TARGET_FIXTURE_INVALID'
    Complete-Case 'canonical-db-target-pass'

    $selfTestOutput = @(& pwsh -NoProfile -File $orchestrator -Action ContractSelfTest 2>&1)
    Assert-Condition ($LASTEXITCODE -eq 0) 'ORCHESTRATOR_SELF_TEST_FAILED'
    $selfTest = ($selfTestOutput -join [Environment]::NewLine) | ConvertFrom-Json
    Assert-Condition (
        [int]$selfTest.cases -eq 40 -and
        -not [bool]$selfTest.filesystemMutation -and
        -not [bool]$selfTest.systemdMutation -and
        -not [bool]$selfTest.databaseMutation -and
        -not [bool]$selfTest.runtimeStart
    ) 'DRY_RUN_ZERO_MUTATION_INVALID'
    Assert-Condition (
        [int]$selfTest.planIo.psqlInvocations -eq 0 -and
        [int]$selfTest.planIo.pgpassUses -eq 0 -and
        [int]$selfTest.planIo.networkCalls -eq 0 -and
        [string]$selfTest.preflightIo.externalIoClassification -ceq
            'READ_ONLY_EXTERNAL_IO_ALLOWED' -and
        [bool]$selfTest.preflightIo.credentialAssistedExternalIo -and
        [bool]$selfTest.preflightIo.credentialMaterialConsumedByExternalProcess -and
        [int]$selfTest.preflightIo.psqlInvocations -eq 1 -and
        [int]$selfTest.preflightIo.pgpassUses -eq 1 -and
        [int]$selfTest.preflightIo.networkCalls -eq 1
    ) 'IO_CLASSIFICATION_INVALID'
    $contractOutput = @(& pwsh -NoProfile -File $contractRegression 2>&1)
    Assert-Condition ($LASTEXITCODE -eq 0) `
        ('DYNAMIC_PLAN_CONTRACT_REGRESSION_FAILED:' + ($contractOutput -join ' '))
    $contractResult = ($contractOutput -join [Environment]::NewLine) | ConvertFrom-Json
    Assert-Condition (
        [bool]$contractResult.planDynamicallyExecuted -and
        [bool]$contractResult.planZeroExternalIo -and
        [bool]$contractResult.planFilesystemUnchanged -and
        [string]$contractResult.preflightExternalIoClassification -ceq
            'READ_ONLY_EXTERNAL_IO_ALLOWED' -and
        [bool]$contractResult.preflightCredentialAssistedExternalIo
    ) 'DYNAMIC_PLAN_ZERO_IO_INVALID'
    Complete-Case 'db-mismatch-rejected'
    Complete-Case 'localhost-fallback-rejected'
    Complete-Case 'live-enabled-rejected'
    Complete-Case 'kill-disengaged-rejected'
    Complete-Case 'non-loopback-management-rejected'
    Complete-Case 'release-mismatch-rejected'
    Complete-Case 'health-identity-mismatch-rejected'
    Complete-Case 'failed-health-rejected'
    Complete-Case 'mutation-runtime-bound-rejected'
    Complete-Case 'startup-side-effect-rejected'
    Complete-Case 'counter-truth-table-pass'
    Complete-Case 'activation-rollback-path-present'
    Complete-Case 'dynamic-plan-zero-external-io-pass'
    Complete-Case 'preflight-external-io-explicitly-classified'

    Assert-Condition ($cases.Count -eq 22) ('CASE_COUNT_INVALID:' + $cases.Count)
    [pscustomobject][ordered]@{
        decision = 'PASS / GATEY_READONLY_RUNTIME_DEPLOYMENT_LINUX_REGRESSION'
        cases = $cases.Count
        contractSelfTestCases = [int]$selfTest.cases
        planDynamicallyExecuted = $true
        planZeroExternalIo = $true
        preflightExternalIoClassification = `
            [string]$contractResult.preflightExternalIoClassification
        productionMutation = $false
        networkCalled = $false
        results = @($cases)
    } | ConvertTo-Json -Depth 6
}
catch
{
    [pscustomobject][ordered]@{
        decision = 'FAIL / GATEY_READONLY_RUNTIME_DEPLOYMENT_LINUX_REGRESSION'
        casesPassed = $cases.Count
        detail = $_.Exception.Message
    } | ConvertTo-Json
    exit 2
}
finally
{
    if (Test-Path -LiteralPath $tempRoot)
    {
        $resolved = [IO.Path]::GetFullPath($tempRoot)
        if (-not $resolved.StartsWith('/tmp/nq-gatey-runtime-', [StringComparison]::Ordinal))
        {
            throw 'TEMP_CLEANUP_PATH_INVALID'
        }
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
