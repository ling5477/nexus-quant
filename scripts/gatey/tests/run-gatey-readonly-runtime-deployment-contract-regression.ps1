[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$gateyRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$repo = (Resolve-Path (Join-Path $gateyRoot '../..')).Path
$orchestrator = Join-Path $gateyRoot 'invoke-gatey-readonly-runtime-deployment.ps1'
$contract = Join-Path $gateyRoot 'gatey-readonly-release-contract.psm1'
$deploymentContract = Join-Path $gateyRoot 'invoke-gatey-readonly-deployment-contract.ps1'
$installer = Join-Path $gateyRoot 'install-gatey-readonly-release.ps1'
$unit = Join-Path $repo 'deploy/systemd/nq-gatey-readonly-qualification.service'
$profile = Join-Path $repo 'backend/nq-app/src/main/resources/application-gatey-readonly-qualification.yml'
$template = Join-Path $repo 'deploy/gatey/gatey-readonly-runtime.env.example'
$secretTemplate = Join-Path $repo 'deploy/gatey/gatey-readonly-runtime.secrets.env.example'
$pgpassTemplate = Join-Path $repo 'deploy/gatey/gatey-readonly-db.pgpass.example'
$target = Join-Path $repo 'deploy/gatey/gatey-readonly-runtime-target.json'
$gatewVerifier = Join-Path $repo 'scripts/gatew/verify-gatew-release.ps1'
$gatewContract = Join-Path $repo 'scripts/gatew/gatew-release-contract.psm1'
$engine = (Get-Process -Id $PID).Path
$tempRoot = Join-Path ([IO.Path]::GetTempPath()) `
    ('nq-gatey-runtime-contract-' + [Guid]::NewGuid().ToString('N'))
$utf8 = [Text.UTF8Encoding]::new($false)
Import-Module $contract -Force -DisableNameChecking

function Assert-Condition([bool]$Value, [string]$Message)
{
    if (-not $Value) { throw $Message }
}

function Write-TestText([string]$Path, [string]$Value)
{
    [IO.Directory]::CreateDirectory((Split-Path -Parent $Path)) | Out-Null
    [IO.File]::WriteAllText($Path, $Value, $utf8)
}

function Add-TestArtifact(
    [string]$Root,
    [string]$RelativePath,
    [string]$Source,
    [string]$Mode,
    [string]$Role
)
{
    $destination = Join-Path $Root $RelativePath
    [IO.Directory]::CreateDirectory((Split-Path -Parent $destination)) | Out-Null
    Copy-Item -LiteralPath $Source -Destination $destination
    return [pscustomobject][ordered]@{
        relativePath = $RelativePath.Replace('\', '/')
        size = (Get-Item -LiteralPath $destination).Length
        sha256 = Get-GateYReadonlySha256File $destination
        mode = $Mode
        role = $Role
    }
}

function New-PlanTestRelease([string]$Root, [string]$ReleaseId, [string]$MigrationRoot)
{
    $appSource = Join-Path $Root 'app-source.jar'
    Write-TestText $appSource 'plan-fixture-application'
    $artifacts = @(
        Add-TestArtifact $Root 'app/nq-app.jar' $appSource '0644' 'application'
        Add-TestArtifact $Root 'config/application-gatey-readonly-qualification.yml' $profile '0644' 'runtime-profile'
        Add-TestArtifact $Root 'bin/gatey-readonly-release-contract.psm1' $contract '0644' 'release-contract'
        Add-TestArtifact $Root 'bin/invoke-gatey-readonly-deployment-contract.ps1' $deploymentContract '0755' 'deployment-contract'
        Add-TestArtifact $Root 'bin/install-gatey-readonly-release.ps1' $installer '0755' 'release-installer'
        Add-TestArtifact $Root 'bin/invoke-gatey-readonly-runtime-deployment.ps1' $orchestrator '0755' 'runtime-deployment-orchestrator'
        Add-TestArtifact $Root 'config/nq-gatey-readonly-qualification.service' $unit '0644' 'systemd-runtime-contract'
        Add-TestArtifact $Root 'config/gatey-readonly-runtime.env.example' $template '0644' 'runtime-environment-template'
        Add-TestArtifact $Root 'config/gatey-readonly-runtime.secrets.env.example' $secretTemplate '0600' 'runtime-secret-environment-template'
        Add-TestArtifact $Root 'config/gatey-readonly-db.pgpass.example' $pgpassTemplate '0600' 'database-credential-reference-template'
        Add-TestArtifact $Root 'config/gatey-readonly-runtime-target.json' $target '0644' 'runtime-target-contract'
        Add-TestArtifact $Root 'bin/verify-gatew-release.ps1' $gatewVerifier '0755' 'gatew-rollback-verifier'
        Add-TestArtifact $Root 'bin/gatew-release-contract.psm1' $gatewContract '0644' 'gatew-rollback-contract'
    )
    Remove-Item -LiteralPath $appSource -Force
    $manifest = New-GateYReadonlyReleaseManifest `
        $ReleaseId '2026-08-21T00:00:00Z' $artifacts $MigrationRoot
    Write-GateYReadonlyCanonicalManifest (Join-Path $Root 'release-manifest.json') $manifest
    return $manifest
}

function Get-TestTreeDigest([string]$Root)
{
    $lines = @(Get-ChildItem -LiteralPath $Root -Recurse -File | Sort-Object FullName | ForEach-Object {
        $_.FullName.Substring($Root.Length).Replace('\', '/') + '|' +
            (Get-GateYReadonlySha256File $_.FullName)
    })
    return Get-GateYReadonlySha256Text (($lines -join "`n") + "`n")
}

try
{
    foreach ($path in @($orchestrator, $unit, $template, $secretTemplate, $pgpassTemplate, $target))
    {
        Assert-Condition (Test-Path -LiteralPath $path -PathType Leaf) ('CONTRACT_FILE_MISSING:' + $path)
    }
    $selfTestOutput = @(& $engine -NoProfile -File $orchestrator -Action ContractSelfTest 2>&1)
    Assert-Condition ($LASTEXITCODE -eq 0) 'CONTRACT_SELF_TEST_PROCESS_FAILED'
    $selfTest = ($selfTestOutput -join [Environment]::NewLine) | ConvertFrom-Json
    Assert-Condition (
        [string]$selfTest.decision -ceq 'PASS / GATEY_READONLY_RUNTIME_DEPLOYMENT_CONTRACT_SELF_TEST' -and
        [int]$selfTest.cases -eq 41 -and
        -not [bool]$selfTest.filesystemMutation -and
        -not [bool]$selfTest.systemdMutation -and
        -not [bool]$selfTest.databaseMutation -and
        -not [bool]$selfTest.runtimeStart
    ) 'CONTRACT_SELF_TEST_RESULT_INVALID'

    $tokens = $null
    $parseErrors = $null
    $ast = [Management.Automation.Language.Parser]::ParseFile(
        $orchestrator, [ref]$tokens, [ref]$parseErrors
    )
    Assert-Condition ($parseErrors.Count -eq 0) 'ORCHESTRATOR_PARSE_FAILED'
    $planFunction = $ast.Find({
        param($node)
        $node -is [Management.Automation.Language.FunctionDefinitionAst] -and
            $node.Name -ceq 'Invoke-Plan'
    }, $true)
    $localPlanFunction = $ast.Find({
        param($node)
        $node -is [Management.Automation.Language.FunctionDefinitionAst] -and
            $node.Name -ceq 'Invoke-LocalPlanPreflight'
    }, $true)
    Assert-Condition ($null -ne $planFunction -and $null -ne $localPlanFunction) `
        'PURE_PLAN_FUNCTION_MISSING'
    $planCommands = @($planFunction.Body.FindAll({
        param($node) $node -is [Management.Automation.Language.CommandAst]
    }, $true) | ForEach-Object { $_.GetCommandName() } | Where-Object { $_ })
    $localPlanCommands = @($localPlanFunction.Body.FindAll({
        param($node) $node -is [Management.Automation.Language.CommandAst]
    }, $true) | ForEach-Object { $_.GetCommandName() } | Where-Object { $_ })
    $forbiddenPlanCommands = @(
        'Invoke-ReleasePreflight', 'Invoke-DatabaseFacts', 'Invoke-Native',
        'Invoke-RestMethod', 'Start-Process', 'systemctl', 'psql'
    )
    Assert-Condition (
        @($planCommands + $localPlanCommands | Where-Object {
            $forbiddenPlanCommands -contains $_
        }).Count -eq 0 -and
        (($planFunction.Extent.Text + $localPlanFunction.Extent.Text) -notmatch
            'PGPASSFILE|/usr/bin/psql|systemctl')
    ) 'PLAN_EXTERNAL_IO_CALL_GRAPH_INVALID'

    [IO.Directory]::CreateDirectory($tempRoot) | Out-Null
    $migrations = Join-Path $tempRoot 'migrations'
    Write-TestText (Join-Path $migrations 'V1__first.sql') ('SELECT 1;' + [char]10)
    Write-TestText (Join-Path $migrations 'V2__second.sql') ('SELECT 2;' + [char]10)
    $releaseId = '1111111111111111111111111111111111111111'
    $releaseRoot = Join-Path $tempRoot 'release'
    $null = New-PlanTestRelease $releaseRoot $releaseId $migrations
    $manifestSha256 = Get-GateYReadonlySha256File (Join-Path $releaseRoot 'release-manifest.json')
    $treeBefore = Get-TestTreeDigest $tempRoot
    $planOutput = @(& $engine -NoProfile -File $orchestrator `
        -Action Plan `
        -ReleaseRoot $releaseRoot `
        -TargetContractPath $target `
        -ExpectedReleaseId $releaseId `
        -ExpectedManifestSha256 $manifestSha256 2>&1)
    Assert-Condition ($LASTEXITCODE -eq 0) ('DYNAMIC_PLAN_FAILED:' + ($planOutput -join ' '))
    $plan = ($planOutput -join [Environment]::NewLine) | ConvertFrom-Json
    $treeAfter = Get-TestTreeDigest $tempRoot
    Assert-Condition (
        [string]$plan.decision -ceq 'PASS / GATEY_READONLY_LOCAL_PLAN_GENERATED' -and
        [string]$plan.io.scope -ceq 'LOCAL_ONLY' -and
        [string]$plan.io.externalIoClassification -ceq 'ZERO_EXTERNAL_IO' -and
        -not [bool]$plan.io.credentialAssistedExternalIo -and
        [int]$plan.io.psqlInvocations -eq 0 -and [int]$plan.io.pgpassUses -eq 0 -and
        [int]$plan.io.networkCalls -eq 0 -and [int]$plan.io.filesystemMutations -eq 0 -and
        [int]$plan.io.systemdMutations -eq 0 -and [int]$plan.io.runtimeStarts -eq 0 -and
        -not [bool]$plan.runtimeEnvironmentVerified -and
        -not [bool]$plan.databaseFactsVerified -and $treeBefore -ceq $treeAfter
    ) 'DYNAMIC_PLAN_ZERO_IO_INVALID'
    Assert-Condition (
        [string]$selfTest.preflightIo.externalIoClassification -ceq
            'READ_ONLY_EXTERNAL_IO_ALLOWED' -and
        [bool]$selfTest.preflightIo.credentialAssistedExternalIo -and
        [bool]$selfTest.preflightIo.credentialMaterialConsumedByExternalProcess -and
        -not [bool]$selfTest.preflightIo.credentialBytesExposedToScript -and
        [int]$selfTest.preflightIo.psqlInvocations -eq 1 -and
        [int]$selfTest.preflightIo.pgpassUses -eq 1 -and
        [int]$selfTest.preflightIo.networkCalls -eq 1 -and
        [int]$selfTest.preflightIo.databaseReads -eq 1 -and
        [int]$selfTest.preflightIo.databaseWrites -eq 0
    ) 'PREFLIGHT_IO_CLASSIFICATION_INVALID'

    $unitSource = Get-Content -LiteralPath $unit -Raw
    foreach ($marker in @(
        'User=nq-gatey-readonly', 'Group=nq-gatey-readonly',
        'WorkingDirectory=/opt/nexus-quant',
        'EnvironmentFile=/etc/nexus-quant/gatey-readonly-qualification/runtime.env',
        'EnvironmentFile=/etc/nexus-quant/gatey-readonly-qualification/secrets.env',
        'ExecStart=/usr/bin/java -jar /opt/nexus-quant/current/app/nq-app.jar',
        'Restart=no', 'TimeoutStartSec=120s', 'TimeoutStopSec=30s', 'UMask=0077',
        'NoNewPrivileges=true', 'PrivateTmp=true', 'ProtectSystem=strict', 'ProtectHome=true',
        'ReadOnlyPaths=/opt/nexus-quant/releases'
    ))
    {
        Assert-Condition $unitSource.Contains($marker) ('SYSTEMD_MARKER_MISSING:' + $marker)
    }
    foreach ($forbidden in @('nq-gatew', 'chmod 777', 'NQ_GATEY_QUALIFICATION_DB_PASSWORD='))
    {
        Assert-Condition (
            $unitSource.IndexOf($forbidden, [StringComparison]::OrdinalIgnoreCase) -lt 0
        ) ('SYSTEMD_FORBIDDEN_MARKER:' + $forbidden)
    }

    $templateSource = Get-Content -LiteralPath $template -Raw
    foreach ($required in @(
        'SPRING_PROFILES_ACTIVE=gatey-readonly-qualification',
        'NQ_APP_BIND_ADDRESS=127.0.0.1',
        'NQ_GATEY_MANAGEMENT_ADDRESS=127.0.0.1',
        'NQ_GATEY_QUALIFICATION_DB_URL=jdbc:postgresql://127.0.0.1:55432/nexus_quant',
        'NQ_LIVE_ENABLED=false', 'NQ_TRADING_COMPONENTS_ENABLED=false',
        'NQ_RUNTIME_PROVIDER_OBSERVATION_ENABLED=true',
        'NQ_GATEY_EXPECTED_KILL_SWITCH=ENGAGED'
    ))
    {
        Assert-Condition $templateSource.Contains($required) ('ENV_MARKER_MISSING:' + $required)
    }
    foreach ($forbiddenSecret in @(
        'NQ_GATEY_QUALIFICATION_DB_PASSWORD=', 'NQ_SECURITY_SECRET=',
        'NQ_ACCOUNT_CREDENTIALS_MASTER_KEY='
    ))
    {
        Assert-Condition (-not $templateSource.Contains($forbiddenSecret)) `
            ('NON_SECRET_ENV_CONTAINS_SECRET:' + $forbiddenSecret)
    }
    $secretSource = Get-Content -LiteralPath $secretTemplate -Raw
    Assert-Condition (
        $secretSource.Contains('NQ_GATEY_QUALIFICATION_DB_PASSWORD=REPLACE_WITH_LOCAL') -and
        $secretSource.Contains('NQ_SECURITY_SECRET=REPLACE_WITH_LOCAL') -and
        $secretSource.Contains('NQ_ACCOUNT_CREDENTIALS_MASTER_KEY=REPLACE_WITH_LOCAL')
    ) 'SECRET_ENV_PLACEHOLDER_MISSING'
    $pgpassSource = Get-Content -LiteralPath $pgpassTemplate -Raw
    Assert-Condition (
        $pgpassSource.Contains('127.0.0.1:55432:nexus_quant:REPLACE_WITH_LOCAL:REPLACE_WITH_LOCAL')
    ) 'PGPASS_PLACEHOLDER_MISSING'

    $targetValue = Get-Content -LiteralPath $target -Raw | ConvertFrom-Json
    Assert-Condition (
        [string]$targetValue.schemaVersion -ceq 'gatey-readonly-runtime-target.v1' -and
        [string]$targetValue.unit.name -ceq 'nq-gatey-readonly-qualification.service' -and
        [string]$targetValue.environment.path -ceq
            '/etc/nexus-quant/gatey-readonly-qualification/runtime.env' -and
        [string]$targetValue.environment.owner -ceq 'root' -and
        [string]$targetValue.environment.group -ceq 'nq-gatey-readonly' -and
        [string]$targetValue.environment.mode -ceq '640' -and
        [string]$targetValue.environment.secretPath -ceq
            '/etc/nexus-quant/gatey-readonly-qualification/secrets.env' -and
        [string]$targetValue.environment.secretMode -ceq '600' -and
        [string]$targetValue.database.targetId -ceq 'gatey-production-control-plane' -and
        [string]$targetValue.database.host -ceq '127.0.0.1' -and
        [int]$targetValue.database.port -eq 55432 -and
        [string]$targetValue.database.name -ceq 'nexus_quant' -and
        [string]$targetValue.database.credentialPath -ceq
            '/etc/nexus-quant/gatey-readonly-qualification/db.pgpass' -and
        [string]$targetValue.database.credentialMode -ceq '600'
    ) 'TARGET_CONTRACT_INVALID'

    $orchestratorSource = Get-Content -LiteralPath $orchestrator -Raw
    foreach ($marker in @(
        "'Plan'", "'UnitPreflight'", "'InstallUnit'", "'Start'", "'Stop'",
        "'Health'", "'Activate'", "'Rollback'", 'Invoke-ReleasePreflight',
        'Assert-EnvironmentMetadata', 'Assert-DatabaseFacts', 'Assert-HealthFacts',
        'Write-DeploymentEvidence', 'DEPLOYMENT_EVIDENCE_PATH_INVALID',
        'ROLLBACK_CURRENT_VERIFICATION_NOT_IMPLEMENTED',
        'Test-PreviousRelease', 'Set-CurrentPointer', 'Resolve-CanonicalPath',
        'nq-gatew-release-v3'
    ))
    {
        Assert-Condition $orchestratorSource.Contains($marker) ('ORCHESTRATOR_MARKER_MISSING:' + $marker)
    }
    Assert-Condition (
        $orchestratorSource -notmatch '\$previous\s*=\s*\(Resolve-Path' -and
        $orchestratorSource -notmatch '\$resolvedCurrent\s*=\s*\(Resolve-Path'
    ) 'CURRENT_SYMLINK_NOT_CANONICALLY_RESOLVED'
    foreach ($forbidden in @(
        'observePrerequisites(', '/api/v5/', 'Invoke-RestMethod -Method Post', 'permission probe'
    ))
    {
        Assert-Condition (
            $orchestratorSource.IndexOf($forbidden, [StringComparison]::OrdinalIgnoreCase) -lt 0
        ) ('ORCHESTRATOR_FORBIDDEN_MARKER:' + $forbidden)
    }

    [pscustomobject][ordered]@{
        decision = 'PASS / GATEY_READONLY_RUNTIME_DEPLOYMENT_CONTRACT_REGRESSION'
        cases = 49
        contractSelfTestCases = [int]$selfTest.cases
        systemdContract = $true
        environmentContract = $true
        databaseTargetContract = $true
        activationRollbackContract = $true
        planDynamicallyExecuted = $true
        planZeroExternalIo = $true
        planFilesystemUnchanged = $true
        preflightExternalIoClassification = [string]$selfTest.preflightIo.externalIoClassification
        preflightCredentialAssistedExternalIo = [bool]$selfTest.preflightIo.credentialAssistedExternalIo
        serverMutation = $false
    } | ConvertTo-Json -Depth 6
}
catch
{
    [pscustomobject][ordered]@{
        decision = 'FAIL / GATEY_READONLY_RUNTIME_DEPLOYMENT_CONTRACT_REGRESSION'
        detail = $_.Exception.Message
    } | ConvertTo-Json
    exit 2
}
finally
{
    if (Test-Path -LiteralPath $tempRoot)
    {
        $resolved = [IO.Path]::GetFullPath($tempRoot)
        $tempBase = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
        if (-not $resolved.StartsWith($tempBase, [StringComparison]::OrdinalIgnoreCase) -or
                [IO.Path]::GetFileName($resolved) -notmatch
                    '^nq-gatey-runtime-contract-[0-9a-f]{32}$')
        {
            throw 'TEMP_CLEANUP_PATH_INVALID'
        }
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
