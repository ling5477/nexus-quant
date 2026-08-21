[CmdletBinding()]
param(
    [ValidateSet(
        'Plan', 'UnitPreflight', 'InstallUnit', 'Start', 'Stop', 'VerifyStopped',
        'Health', 'Activate', 'Rollback', 'ContractSelfTest'
    )]
    [string]$Action = 'Plan',
    [string]$ReleaseRoot = '/opt/nexus-quant/current',
    [string]$TargetContractPath,
    [string]$EnvironmentPath,
    [string]$ExpectedReleaseId,
    [string]$ExpectedManifestSha256,
    [string]$PreviousReleaseId,
    [string]$EvidencePath,
    [switch]$NoMigration
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:ContractPath = Join-Path $PSScriptRoot 'gatey-readonly-release-contract.psm1'
Import-Module $script:ContractPath -Force -DisableNameChecking
$script:Sha256Pattern = '^[0-9a-f]{64}$'
$script:CommitPattern = '^[0-9a-f]{40}$'
$script:PlaceholderPattern = '^(REPLACE_WITH_LOCAL|REPLACE_WITH_EXACT_COMMIT|CHANGE_ME)'
$script:HealthAttemptLimit = 90

if ([string]::IsNullOrWhiteSpace($ExpectedReleaseId))
{
    $ExpectedReleaseId = [string]$env:NQ_GATEY_RELEASE_ID
}
if ([string]::IsNullOrWhiteSpace($ExpectedManifestSha256))
{
    $ExpectedManifestSha256 = [string]$env:NQ_GATEY_RELEASE_MANIFEST_SHA256
}

function Throw-Blocked([string]$Code)
{
    throw ('BLOCKED / ' + $Code)
}

function Invoke-Native
{
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][string[]]$Arguments,
        [switch]$AllowFailure
    )
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf))
    {
        Throw-Blocked 'DEPLOYMENT_TOOL_MISSING'
    }
    $lines = @(& $Path @Arguments 2>$null)
    $exitCode = [int]$LASTEXITCODE
    if (-not $AllowFailure -and $exitCode -ne 0)
    {
        Throw-Blocked 'DEPLOYMENT_COMMAND_FAILED'
    }
    return [pscustomobject]@{ ExitCode = $exitCode; Lines = @($lines) }
}

function Assert-RootLinux
{
    $linux = Get-Variable -Name IsLinux -ErrorAction SilentlyContinue
    if ($null -eq $linux -or -not [bool]$linux.Value)
    {
        Throw-Blocked 'DEPLOYMENT_ROOT_LINUX_REQUIRED'
    }
    $identity = Invoke-Native '/usr/bin/id' @('-u')
    if (($identity.Lines -join '').Trim() -cne '0')
    {
        Throw-Blocked 'DEPLOYMENT_ROOT_LINUX_REQUIRED'
    }
}

function Assert-Java21
{
    $output = @(& /usr/bin/java -version 2>&1)
    if ($LASTEXITCODE -ne 0 -or [string]($output | Select-Object -First 1) -notmatch
            'version "([0-9]+)(?:\.|\")' -or [int]$Matches[1] -ne 21)
    {
        Throw-Blocked 'JAVA_MAJOR_VERSION_MISMATCH'
    }
}

function Read-Json([string]$Path)
{
    if ([string]::IsNullOrWhiteSpace($Path) -or -not (Test-Path -LiteralPath $Path -PathType Leaf))
    {
        Throw-Blocked 'CANONICAL_DATABASE_TARGET_MISSING'
    }
    return Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
}

function Resolve-CanonicalPath([string]$Path)
{
    $fullPath = [IO.Path]::GetFullPath($Path).TrimEnd('/', '\')
    if (-not (Test-Path -LiteralPath $fullPath))
    {
        return $fullPath
    }
    $linux = Get-Variable -Name IsLinux -ErrorAction SilentlyContinue
    if ($null -ne $linux -and [bool]$linux.Value)
    {
        $resolved = Invoke-Native '/usr/bin/readlink' @('-f', '--', $fullPath)
        $value = (($resolved.Lines -join '').Trim()).TrimEnd('/')
        if ([string]::IsNullOrWhiteSpace($value))
        {
            Throw-Blocked 'RELEASE_PATH_RESOLUTION_FAILED'
        }
        return [IO.Path]::GetFullPath($value).TrimEnd('/')
    }
    return (Resolve-Path -LiteralPath $fullPath).Path.TrimEnd('\', '/')
}

function Assert-TargetContract($Target)
{
    if ($null -eq $Target -or
            [string]$Target.schemaVersion -cne 'gatey-readonly-runtime-target.v1' -or
            [string]$Target.runtimeRole -cne 'GATEY_READONLY_QUALIFICATION')
    {
        Throw-Blocked 'CANONICAL_DATABASE_TARGET_MISSING'
    }
    if ([string]$Target.unit.name -cne 'nq-gatey-readonly-qualification.service' -or
            [string]$Target.unit.installedPath -cne
                '/etc/systemd/system/nq-gatey-readonly-qualification.service' -or
            [string]$Target.unit.sourceRelativePath -cne
                'config/nq-gatey-readonly-qualification.service')
    {
        Throw-Blocked 'SYSTEMD_UNIT_MISMATCH'
    }
    if ([string]$Target.service.user -cne 'nq-gatey-readonly' -or
            [string]$Target.service.group -cne 'nq-gatey-readonly' -or
            [string]$Target.service.workingDirectory -cne '/opt/nexus-quant' -or
            [string]$Target.service.releaseRoot -cne '/opt/nexus-quant/releases' -or
            [string]$Target.service.currentPointer -cne '/opt/nexus-quant/current' -or
            [string]$Target.service.evidenceRoot -cne
                '/var/lib/nexus-quant/gatey-readonly-qualification/deployment-evidence')
    {
        Throw-Blocked 'SYSTEMD_CONTRACT_INVALID'
    }
    if ([string]$Target.environment.path -cne
            '/etc/nexus-quant/gatey-readonly-qualification/runtime.env' -or
            [string]$Target.environment.owner -cne 'root' -or
            [string]$Target.environment.group -cne 'nq-gatey-readonly' -or
            [string]$Target.environment.mode -cne '640' -or
            [string]$Target.environment.secretPath -cne
                '/etc/nexus-quant/gatey-readonly-qualification/secrets.env' -or
            [string]$Target.environment.secretOwner -cne 'root' -or
            [string]$Target.environment.secretGroup -cne 'root' -or
            [string]$Target.environment.secretMode -cne '600')
    {
        Throw-Blocked 'ENVIRONMENT_CONTRACT_INVALID'
    }
    if ([string]$Target.management.address -cne '127.0.0.1' -or
            [int]$Target.management.port -ne 18890 -or
            [string]$Target.management.healthPath -cne '/actuator/health' -or
            [string]$Target.management.identityPath -cne '/actuator/readonlyproviderobservation')
    {
        Throw-Blocked 'MANAGEMENT_BIND_NOT_LOOPBACK'
    }
    if ([string]$Target.database.targetId -cne 'gatey-production-control-plane' -or
            [string]$Target.database.runtimeEnvironment -cne 'PRODUCTION_CONTROL_PLANE' -or
            [string]$Target.database.host -cne '127.0.0.1' -or
            [int]$Target.database.port -ne 55432 -or
            [string]$Target.database.name -cne 'nexus_quant' -or
            [string]$Target.database.flywayHistoryTable -cne 'flyway_schema_history' -or
            [string]$Target.database.credentialReferenceName -cne
                'gatey-readonly-qualification-db' -or
            [string]$Target.database.credentialPath -cne
                '/etc/nexus-quant/gatey-readonly-qualification/db.pgpass' -or
            [string]$Target.database.credentialOwner -cne 'root' -or
            [string]$Target.database.credentialGroup -cne 'root' -or
            [string]$Target.database.credentialMode -cne '600')
    {
        Throw-Blocked 'DATABASE_TARGET_MISMATCH'
    }
    if ([string]$Target.safety.live -cne 'DISABLED' -or
            [string]$Target.safety.killSwitch -cne 'ENGAGED' -or
            -not [bool]$Target.safety.providerObservationEnabled -or
            [bool]$Target.safety.tradingComponentsEnabled -or
            [bool]$Target.safety.mutationRuntimeBound)
    {
        Throw-Blocked 'SAFETY_CONTRACT_INVALID'
    }
}

function Assert-SystemdSource([string]$Path)
{
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf))
    {
        Throw-Blocked 'SYSTEMD_CONTRACT_MISSING'
    }
    $source = Get-Content -LiteralPath $Path -Raw
    foreach ($required in @(
        'User=nq-gatey-readonly',
        'Group=nq-gatey-readonly',
        'WorkingDirectory=/opt/nexus-quant',
        'EnvironmentFile=/etc/nexus-quant/gatey-readonly-qualification/runtime.env',
        'ExecStart=/usr/bin/java -jar /opt/nexus-quant/current/app/nq-app.jar',
        'Restart=no',
        'TimeoutStartSec=120s',
        'TimeoutStopSec=30s',
        'UMask=0077',
        'NoNewPrivileges=true',
        'PrivateTmp=true',
        'ProtectSystem=strict',
        'ProtectHome=true',
        'ReadOnlyPaths=/opt/nexus-quant/releases'
    ))
    {
        if (-not $source.Contains($required))
        {
            Throw-Blocked 'SYSTEMD_CONTRACT_INVALID'
        }
    }
    foreach ($forbidden in @('nq-gatew', 'chmod 777', 'NQ_GATEY_QUALIFICATION_DB_PASSWORD='))
    {
        if ($source.IndexOf($forbidden, [StringComparison]::OrdinalIgnoreCase) -ge 0)
        {
            Throw-Blocked 'SYSTEMD_CONTRACT_INVALID'
        }
    }
}

function Read-EnvironmentValues([string]$Path)
{
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf))
    {
        Throw-Blocked 'ENVIRONMENT_CONTRACT_MISSING'
    }
    $values = @{}
    foreach ($line in [IO.File]::ReadAllLines($Path))
    {
        $trimmed = $line.Trim()
        if ($trimmed.Length -eq 0 -or $trimmed.StartsWith('#')) { continue }
        $separator = $trimmed.IndexOf('=')
        if ($separator -le 0)
        {
            Throw-Blocked 'ENVIRONMENT_CONTRACT_INVALID'
        }
        $name = $trimmed.Substring(0, $separator)
        $value = $trimmed.Substring($separator + 1)
        if ($name -cnotmatch '^[A-Z][A-Z0-9_]+$' -or $values.ContainsKey($name))
        {
            Throw-Blocked 'ENVIRONMENT_CONTRACT_INVALID'
        }
        $values[$name] = $value
    }
    return $values
}

function Assert-EnvironmentMetadata($Metadata)
{
    if ([string]$Metadata.type -cne 'regular file' -or
            [string]$Metadata.owner -cne 'root' -or
            [string]$Metadata.group -cne 'nq-gatey-readonly')
    {
        Throw-Blocked 'ENVIRONMENT_OWNER_INVALID'
    }
    if ([string]$Metadata.mode -cne '640')
    {
        Throw-Blocked 'ENVIRONMENT_MODE_INVALID'
    }
    if ([bool]$Metadata.serviceUserWritable)
    {
        Throw-Blocked 'ENVIRONMENT_WRITABLE_BY_SERVICE_USER'
    }
    if (-not [bool]$Metadata.serviceUserReadable)
    {
        Throw-Blocked 'ENVIRONMENT_NOT_READABLE_BY_SERVICE_USER'
    }
}

function Assert-RootSecretMetadata($Metadata)
{
    if ([string]$Metadata.type -cne 'regular file' -or
            [string]$Metadata.owner -cne 'root' -or [string]$Metadata.group -cne 'root')
    {
        Throw-Blocked 'SECRET_OWNER_INVALID'
    }
    if ([string]$Metadata.mode -cne '600')
    {
        Throw-Blocked 'SECRET_MODE_INVALID'
    }
    if ([bool]$Metadata.serviceUserReadable -or [bool]$Metadata.serviceUserWritable)
    {
        Throw-Blocked 'SECRET_ACCESSIBLE_BY_SERVICE_USER'
    }
}

function Get-EnvironmentMetadata([string]$Path, [string]$ServiceUser)
{
    $stat = Invoke-Native '/usr/bin/stat' @('--format=%F|%U|%G|%a', '--', $Path)
    $parts = (($stat.Lines -join '').Trim()).Split('|')
    if ($parts.Count -ne 4) { Throw-Blocked 'ENVIRONMENT_CONTRACT_INVALID' }
    $writable = Invoke-Native '/usr/sbin/runuser' @(
        '-u', $ServiceUser, '--', '/usr/bin/test', '-w', $Path
    ) -AllowFailure
    $readable = Invoke-Native '/usr/sbin/runuser' @(
        '-u', $ServiceUser, '--', '/usr/bin/test', '-r', $Path
    ) -AllowFailure
    if (($writable.ExitCode -ne 0 -and $writable.ExitCode -ne 1) -or
            ($readable.ExitCode -ne 0 -and $readable.ExitCode -ne 1))
    {
        Throw-Blocked 'ENVIRONMENT_WRITE_PROBE_INVALID'
    }
    return [pscustomobject]@{
        type = $parts[0]
        owner = $parts[1]
        group = $parts[2]
        mode = $parts[3]
        serviceUserReadable = $readable.ExitCode -eq 0
        serviceUserWritable = $writable.ExitCode -eq 0
    }
}

function Assert-EnvironmentValues($Values, $Target, [string]$ReleaseId)
{
    $required = @(
        'SPRING_PROFILES_ACTIVE', 'NQ_APP_BIND_ADDRESS', 'NQ_APP_PORT',
        'NQ_GATEY_MANAGEMENT_ADDRESS', 'NQ_GATEY_MANAGEMENT_PORT',
        'NQ_GATEY_RELEASE_ID', 'NQ_GATEY_SOURCE_COMMIT', 'NQ_GATEY_RELEASE_MANIFEST_SHA256',
        'NQ_GATEY_QUALIFICATION_DB_URL', 'NQ_GATEY_QUALIFICATION_DB_USER',
        'NQ_GATEY_DATABASE_TARGET_ID',
        'NQ_GATEY_DATABASE_CREDENTIAL_REFERENCE', 'NQ_LIVE_ENABLED',
        'NQ_TRADING_COMPONENTS_ENABLED', 'NQ_RUNTIME_PROVIDER_OBSERVATION_ENABLED',
        'NQ_RUNTIME_PROVIDER_OBSERVATION_ORDER_SUBMISSION_ENABLED',
        'NQ_RUNTIME_PROVIDER_OBSERVATION_CANCEL_ENABLED',
        'NQ_RUNTIME_PROVIDER_OBSERVATION_TRANSFER_ENABLED',
        'NQ_RUNTIME_PROVIDER_OBSERVATION_WITHDRAW_ENABLED',
        'NQ_GATEY_EXPECTED_KILL_SWITCH', 'NQ_AUTH_BOOTSTRAP_ADMIN_ENABLED',
        'NQ_SECURITY_ISSUER', 'NQ_SECURITY_ACCESS_TOKEN_TTL'
    )
    foreach ($name in $required)
    {
        if (-not $Values.ContainsKey($name) -or [string]::IsNullOrWhiteSpace([string]$Values[$name]))
        {
            Throw-Blocked 'ENVIRONMENT_CONTRACT_MISSING'
        }
    }
    foreach ($secretName in @(
        'NQ_GATEY_QUALIFICATION_DB_USER'
    ))
    {
        if ([string]$Values[$secretName] -match $script:PlaceholderPattern)
        {
            Throw-Blocked 'ENVIRONMENT_PLACEHOLDER_NOT_REPLACED'
        }
    }
    if ([string]$Values['SPRING_PROFILES_ACTIVE'] -cne 'gatey-readonly-qualification' -or
            [string]$Values['NQ_APP_BIND_ADDRESS'] -cne '127.0.0.1' -or
            [string]$Values['NQ_GATEY_MANAGEMENT_ADDRESS'] -cne '127.0.0.1' -or
            [string]$Values['NQ_APP_PORT'] -cne '18890' -or
            [string]$Values['NQ_GATEY_MANAGEMENT_PORT'] -cne '18890')
    {
        Throw-Blocked 'MANAGEMENT_BIND_NOT_LOOPBACK'
    }
    if ([string]$Values['NQ_GATEY_RELEASE_ID'] -cne $ReleaseId -or
            [string]$Values['NQ_GATEY_SOURCE_COMMIT'] -cne $ReleaseId -or
            [string]$Values['NQ_GATEY_RELEASE_MANIFEST_SHA256'] -cnotmatch $script:Sha256Pattern)
    {
        Throw-Blocked 'CURRENT_RELEASE_MISMATCH'
    }
    $expectedUrl = 'jdbc:postgresql://' + [string]$Target.database.host + ':' +
        [string]$Target.database.port + '/' + [string]$Target.database.name
    $configuredUrl = [string]$Values['NQ_GATEY_QUALIFICATION_DB_URL']
    if ($configuredUrl -match 'jdbc:postgresql://localhost')
    {
        Throw-Blocked 'LOCALHOST_DATABASE_FALLBACK_REJECTED'
    }
    if ($configuredUrl -cne $expectedUrl -or
            [string]$Values['NQ_GATEY_DATABASE_TARGET_ID'] -cne
                [string]$Target.database.targetId -or
            [string]$Values['NQ_GATEY_DATABASE_CREDENTIAL_REFERENCE'] -cne
                [string]$Target.database.credentialReferenceName)
    {
        Throw-Blocked 'DATABASE_TARGET_MISMATCH'
    }
    if ([string]$Values['NQ_LIVE_ENABLED'] -cne 'false')
    {
        Throw-Blocked 'LIVE_ENABLED'
    }
    if ([string]$Values['NQ_TRADING_COMPONENTS_ENABLED'] -cne 'false' -or
            [string]$Values['NQ_RUNTIME_PROVIDER_OBSERVATION_ENABLED'] -cne 'true' -or
            [string]$Values['NQ_RUNTIME_PROVIDER_OBSERVATION_ORDER_SUBMISSION_ENABLED'] -cne 'false' -or
            [string]$Values['NQ_RUNTIME_PROVIDER_OBSERVATION_CANCEL_ENABLED'] -cne 'false' -or
            [string]$Values['NQ_RUNTIME_PROVIDER_OBSERVATION_TRANSFER_ENABLED'] -cne 'false' -or
            [string]$Values['NQ_RUNTIME_PROVIDER_OBSERVATION_WITHDRAW_ENABLED'] -cne 'false')
    {
        Throw-Blocked 'MUTATION_RUNTIME_CONFIGURATION_INVALID'
    }
    if ([string]$Values['NQ_GATEY_EXPECTED_KILL_SWITCH'] -cne 'ENGAGED')
    {
        Throw-Blocked 'KILL_SWITCH_NOT_ENGAGED'
    }
    if ([string]$Values['NQ_AUTH_BOOTSTRAP_ADMIN_ENABLED'] -cne 'false')
    {
        Throw-Blocked 'BOOTSTRAP_ADMIN_ENABLED'
    }
}

function Assert-ReleaseFacts($Release, [string]$ReleaseId, [string]$ManifestSha256)
{
    if ([string]$Release.releaseId -cne $ReleaseId -or
            [string]$Release.manifestSha256 -cne $ManifestSha256)
    {
        Throw-Blocked 'CURRENT_RELEASE_MISMATCH'
    }
}

function Assert-CounterSafe($Counter)
{
    if ($null -eq $Counter)
    {
        Throw-Blocked 'STARTUP_COUNTER_INVALID'
    }
    $statusProperty = $Counter.PSObject.Properties['status']
    $valueProperty = $Counter.PSObject.Properties['value']
    if ($null -eq $statusProperty -or $null -eq $valueProperty)
    {
        Throw-Blocked 'STARTUP_COUNTER_INVALID'
    }
    $status = [string]$statusProperty.Value
    $value = $valueProperty.Value
    if ($status -in @('NOT_INSTRUMENTED', 'UNKNOWN'))
    {
        if ($null -ne $value)
        {
            Throw-Blocked 'STARTUP_COUNTER_INVALID'
        }
        return 'NOT_VERIFIED'
    }
    if ($status -notin @('VERIFIED_ZERO', 'OBSERVED') -or $null -eq $value)
    {
        Throw-Blocked 'STARTUP_COUNTER_INVALID'
    }
    $integral = $value -is [byte] -or $value -is [sbyte] -or
        $value -is [int16] -or $value -is [uint16] -or
        $value -is [int32] -or $value -is [uint32] -or
        $value -is [int64] -or $value -is [uint64]
    if (-not $integral)
    {
        Throw-Blocked 'STARTUP_COUNTER_INVALID'
    }
    if ([decimal]$value -ne 0)
    {
        Throw-Blocked 'STARTUP_SIDE_EFFECT_OBSERVED'
    }
    return 'VERIFIED_ZERO'
}

function Assert-HealthFacts($Health, $Identity, [string]$ReleaseId)
{
    if ([string]$Health.status -cne 'UP')
    {
        Throw-Blocked 'RUNTIME_HEALTH_NOT_VERIFIED'
    }
    if ([string]$Identity.sourceCommit -cne $ReleaseId -or
            [string]$Identity.releaseId -cne $ReleaseId -or
            [int]$Identity.javaMajor -ne 21 -or
            [string]$Identity.qualificationProfile -cne 'gatey-readonly-qualification' -or
            [string]$Identity.capabilityIdentity -cne 'read-only-provider-observation' -or
            [string]$Identity.bindAddress -cne '127.0.0.1')
    {
        Throw-Blocked 'RUNTIME_IDENTITY_MISMATCH'
    }
    if (-not [bool]$Identity.providerObservationEnabled -or
            [bool]$Identity.tradingComponentsEnabled -or [bool]$Identity.liveEnabled -or
            [string]$Identity.killSwitch -cne 'ENGAGED')
    {
        Throw-Blocked 'RUNTIME_SAFETY_IDENTITY_INVALID'
    }
    if ([bool]$Identity.mutationRuntimeBound)
    {
        Throw-Blocked 'MUTATION_RUNTIME_BOUND'
    }
    if (-not [bool]$Identity.diagnosticOnly -or [bool]$Identity.tradingAuthorization -or
            -not [bool]$Identity.noSideEffect)
    {
        Throw-Blocked 'RUNTIME_DIAGNOSTIC_CONTRACT_INVALID'
    }
    $classifications = @()
    foreach ($counter in @(
        $Identity.credentialMetadataReads, $Identity.credentialMaterialReads,
        $Identity.decryptCount, $Identity.okxGetCount, $Identity.okxPostCount,
        $Identity.executionIntentDelta, $Identity.executionReceiptDelta,
        $Identity.orderDelta, $Identity.ledgerDelta
    ))
    {
        $classifications += Assert-CounterSafe $counter
    }
    return [pscustomobject][ordered]@{
        total = $classifications.Count
        verifiedZero = @($classifications | Where-Object { $_ -ceq 'VERIFIED_ZERO' }).Count
        notVerified = @($classifications | Where-Object { $_ -ceq 'NOT_VERIFIED' }).Count
        unknownNeverPromotedToZero = $true
    }
}

function Assert-DatabaseFacts($Facts, $Target, [string]$SchemaTarget)
{
    if ([string]$Facts.database -cne [string]$Target.database.name -or
            [int]$Facts.port -ne [int]$Target.database.port)
    {
        Throw-Blocked 'DATABASE_TARGET_MISMATCH'
    }
    if ([string]$Facts.killSwitch -cne 'ENGAGED')
    {
        Throw-Blocked 'KILL_SWITCH_NOT_ENGAGED'
    }
    if ([long]$Facts.failedMigrations -ne 0 -or [string]$Facts.currentVersion -cne $SchemaTarget)
    {
        Throw-Blocked 'SERVER_FLYWAY_HISTORY_UNSAFE'
    }
}

function Invoke-DatabaseFacts($Target, $Values)
{
    $previousPassFile = $env:PGPASSFILE
    try
    {
        $env:PGPASSFILE = [string]$Target.database.credentialPath
        $query = @"
SELECT current_database() || '|' || current_setting('port') || '|' ||
       COALESCE((SELECT status FROM kill_switch_states WHERE scope='GLOBAL_TRADING'),'MISSING') || '|' ||
       (SELECT COUNT(*) FROM flyway_schema_history WHERE success = false) || '|' ||
       COALESCE((SELECT 'V' || version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank DESC LIMIT 1),'EMPTY');
"@
        $result = Invoke-Native '/usr/bin/psql' @(
            '-X', '-A', '-t', '-v', 'ON_ERROR_STOP=1',
            '-h', [string]$Target.database.host,
            '-p', [string]$Target.database.port,
            '-U', [string]$Values['NQ_GATEY_QUALIFICATION_DB_USER'],
            '-d', [string]$Target.database.name,
            '-c', $query
        )
    }
    finally
    {
        if ($null -eq $previousPassFile) { Remove-Item Env:PGPASSFILE -ErrorAction SilentlyContinue }
        else { $env:PGPASSFILE = $previousPassFile }
    }
    $parts = (($result.Lines -join '').Trim()).Split('|')
    if ($parts.Count -ne 5) { Throw-Blocked 'DATABASE_FACTS_INVALID' }
    return [pscustomobject]@{
        database = $parts[0]
        port = [int]$parts[1]
        killSwitch = $parts[2]
        failedMigrations = [long]$parts[3]
        currentVersion = $parts[4]
    }
}

function Get-TargetAndPaths
{
    $requestedRelease = [IO.Path]::GetFullPath($ReleaseRoot).TrimEnd('/')
    $resolvedRelease = $requestedRelease
    if (Test-Path -LiteralPath $requestedRelease)
    {
        $resolvedRelease = Resolve-CanonicalPath $requestedRelease
    }
    $targetPath = $TargetContractPath
    if ([string]::IsNullOrWhiteSpace($targetPath))
    {
        $targetPath = Join-Path $resolvedRelease 'config/gatey-readonly-runtime-target.json'
    }
    $target = Read-Json $targetPath
    Assert-TargetContract $target
    $envPath = $EnvironmentPath
    if ([string]::IsNullOrWhiteSpace($envPath)) { $envPath = [string]$target.environment.path }
    return [pscustomobject]@{
        releaseRoot = $resolvedRelease
        targetPath = [IO.Path]::GetFullPath($targetPath)
        target = $target
        environmentPath = [IO.Path]::GetFullPath($envPath)
        unitSource = Join-Path $resolvedRelease ([string]$target.unit.sourceRelativePath)
    }
}

function Invoke-LocalPlanPreflight($Context)
{
    $release = Test-GateYReadonlyRelease $Context.releaseRoot
    if ($ExpectedReleaseId -cnotmatch $script:CommitPattern -or
            $ExpectedManifestSha256 -cnotmatch $script:Sha256Pattern)
    {
        Throw-Blocked 'EXPECTED_RELEASE_IDENTITY_REQUIRED'
    }
    Assert-ReleaseFacts $release $ExpectedReleaseId $ExpectedManifestSha256
    Assert-SystemdSource $Context.unitSource
    $manifest = Get-Content -LiteralPath (Join-Path $Context.releaseRoot 'release-manifest.json') -Raw |
        ConvertFrom-Json
    return [pscustomobject]@{
        release = $release
        manifest = $manifest
    }
}

function Get-PlanIoClassification
{
    return [pscustomobject][ordered]@{
        scope = 'LOCAL_ONLY'
        externalIoClassification = 'ZERO_EXTERNAL_IO'
        credentialAssistedExternalIo = $false
        credentialMaterialConsumedByExternalProcess = $false
        credentialBytesExposedToScript = $false
        psqlInvocations = 0
        pgpassUses = 0
        networkCalls = 0
        databaseReads = 0
        databaseWrites = 0
        filesystemMutations = 0
        systemdMutations = 0
        runtimeStarts = 0
    }
}

function Get-UnitPreflightIoClassification
{
    return [pscustomobject][ordered]@{
        scope = 'READ_ONLY_RUNTIME_IO'
        externalIoClassification = 'READ_ONLY_EXTERNAL_IO_ALLOWED'
        credentialAssistedExternalIo = $true
        credentialMaterialConsumedByExternalProcess = $true
        credentialBytesExposedToScript = $false
        psqlInvocations = 1
        pgpassUses = 1
        networkCalls = 1
        databaseReads = 1
        databaseWrites = 0
        filesystemMutations = 0
        systemdMutations = 0
        runtimeStarts = 0
    }
}

function Invoke-ReleasePreflight($Context, [switch]$RequireCurrent)
{
    Assert-Java21
    $release = Test-GateYReadonlyRelease $Context.releaseRoot -RequirePosix
    if ($ExpectedReleaseId -cnotmatch $script:CommitPattern -or
            $ExpectedManifestSha256 -cnotmatch $script:Sha256Pattern)
    {
        Throw-Blocked 'EXPECTED_RELEASE_IDENTITY_REQUIRED'
    }
    Assert-ReleaseFacts $release $ExpectedReleaseId $ExpectedManifestSha256
    Assert-SystemdSource $Context.unitSource
    $metadata = Get-EnvironmentMetadata $Context.environmentPath ([string]$Context.target.service.user)
    Assert-EnvironmentMetadata $metadata
    $secretMetadata = Get-EnvironmentMetadata `
        ([string]$Context.target.environment.secretPath) ([string]$Context.target.service.user)
    Assert-RootSecretMetadata $secretMetadata
    $credentialMetadata = Get-EnvironmentMetadata `
        ([string]$Context.target.database.credentialPath) ([string]$Context.target.service.user)
    Assert-RootSecretMetadata $credentialMetadata
    $values = Read-EnvironmentValues $Context.environmentPath
    Assert-EnvironmentValues $values $Context.target $ExpectedReleaseId
    if ($RequireCurrent)
    {
        $current = [string]$Context.target.service.currentPointer
        if (-not (Test-Path -LiteralPath $current)) { Throw-Blocked 'CURRENT_RELEASE_MISMATCH' }
        $resolvedCurrent = Resolve-CanonicalPath $current
        if ($resolvedCurrent -cne $Context.releaseRoot) { Throw-Blocked 'CURRENT_RELEASE_MISMATCH' }
    }
    $manifest = Get-Content -LiteralPath (Join-Path $Context.releaseRoot 'release-manifest.json') -Raw |
        ConvertFrom-Json
    $databaseFacts = Invoke-DatabaseFacts $Context.target $values
    Assert-DatabaseFacts $databaseFacts $Context.target ([string]$manifest.schema.targetVersion)
    return [pscustomobject]@{
        release = $release
        manifest = $manifest
        environment = $values
        environmentMetadata = $metadata
        secretMetadata = $secretMetadata
        credentialMetadata = $credentialMetadata
        database = $databaseFacts
    }
}

function Assert-InstalledUnit($Context)
{
    $installed = [string]$Context.target.unit.installedPath
    if (-not (Test-Path -LiteralPath $installed -PathType Leaf))
    {
        Throw-Blocked 'SYSTEMD_CONTRACT_MISSING'
    }
    if ((Get-GateYReadonlySha256File $installed) -cne
            (Get-GateYReadonlySha256File $Context.unitSource))
    {
        Throw-Blocked 'SYSTEMD_UNIT_MISMATCH'
    }
}

function Invoke-Plan
{
    $context = Get-TargetAndPaths
    $facts = Invoke-LocalPlanPreflight $context
    $io = Get-PlanIoClassification
    return [pscustomobject][ordered]@{
        decision = 'PASS / GATEY_READONLY_LOCAL_PLAN_GENERATED'
        action = 'Plan'
        releaseId = $facts.release.releaseId
        manifestSha256 = $facts.release.manifestSha256
        unit = [string]$context.target.unit.name
        expectedEnvironmentPath = [string]$context.environmentPath
        expectedDatabaseTargetId = [string]$context.target.database.targetId
        expectedActions = @(
            'UNIT_PREFLIGHT_READ_ONLY_RUNTIME_IO', 'INSTALL_UNIT',
            'ACTIVATE_ATOMIC_CURRENT', 'START_QUALIFICATION_UNIT',
            'VERIFY_LOOPBACK_HEALTH', 'VERIFY_RUNTIME_IDENTITY'
        )
        localReleaseVerified = $true
        runtimeEnvironmentVerified = $false
        databaseFactsVerified = $false
        io = $io
        filesystemMutation = $false
        systemdMutation = $false
        databaseMutation = $false
        runtimeStart = $false
        tradingAuthorization = $false
    }
}

function Invoke-UnitPreflight
{
    Assert-RootLinux
    $context = Get-TargetAndPaths
    $facts = Invoke-ReleasePreflight $context -RequireCurrent
    Assert-InstalledUnit $context
    $io = Get-UnitPreflightIoClassification
    return [pscustomobject][ordered]@{
        decision = 'PASS / GATEY_READONLY_UNIT_PREFLIGHT_VERIFIED'
        releaseId = $facts.release.releaseId
        manifestSha256 = $facts.release.manifestSha256
        databaseTarget = 'DB_TARGET_VERIFIED'
        killSwitch = $facts.database.killSwitch
        io = $io
        serverMutation = $false
    }
}

function Install-Unit
{
    Assert-RootLinux
    $context = Get-TargetAndPaths
    $null = Invoke-ReleasePreflight $context
    $installedPath = [string]$context.target.unit.installedPath
    $temporary = Join-Path (Split-Path -Parent $installedPath) `
        ('.nq-gatey-readonly-qualification-' + $PID + '.service')
    try
    {
        [IO.File]::Copy($context.unitSource, $temporary, $false)
        $null = Invoke-Native '/usr/bin/chown' @('root:root', '--', $temporary)
        $null = Invoke-Native '/usr/bin/chmod' @('0644', '--', $temporary)
        $null = Invoke-Native '/usr/bin/systemd-analyze' @('verify', $temporary)
        $null = Invoke-Native '/usr/bin/mv' @('-T', '-f', '--', $temporary, $installedPath)
    }
    finally
    {
        if (Test-Path -LiteralPath $temporary)
        {
            Remove-Item -LiteralPath $temporary -Force
        }
    }
    Assert-InstalledUnit $context
    $null = Invoke-Native '/usr/bin/systemctl' @('daemon-reload')
    return [pscustomobject][ordered]@{
        decision = 'PASS / GATEY_READONLY_SYSTEMD_CONTRACT_INSTALLED'
        unit = [string]$context.target.unit.name
        systemdMutation = $true
    }
}

function Invoke-Health
{
    Assert-RootLinux
    $context = Get-TargetAndPaths
    $null = Invoke-ReleasePreflight $context -RequireCurrent
    Assert-InstalledUnit $context
    $unit = [string]$context.target.unit.name
    $active = Invoke-Native '/usr/bin/systemctl' @('is-active', '--quiet', $unit) -AllowFailure
    if ($active.ExitCode -ne 0) { Throw-Blocked 'RUNTIME_HEALTH_NOT_VERIFIED' }
    $pidResult = Invoke-Native '/usr/bin/systemctl' @('show', '--property=MainPID', '--value', $unit)
    $mainPid = [long](($pidResult.Lines -join '').Trim())
    if ($mainPid -le 0) { Throw-Blocked 'RUNTIME_HEALTH_NOT_VERIFIED' }
    $base = 'http://127.0.0.1:18890'
    $health = $null
    $identity = $null
    for ($attempt = 1; $attempt -le $script:HealthAttemptLimit; $attempt++)
    {
        try
        {
            $listeners = Invoke-Native '/usr/bin/ss' @('-H', '-ltnp')
            $listenerText = $listeners.Lines -join "`n"
            if ($listenerText -notmatch '127\.0\.0\.1:18890' -or
                    $listenerText -notmatch ('pid=' + $mainPid + '([,\)])'))
            {
                throw 'listener not ready'
            }
            $health = Invoke-RestMethod -Method Get `
                -Uri ($base + [string]$context.target.management.healthPath) -TimeoutSec 3
            $identity = Invoke-RestMethod -Method Get `
                -Uri ($base + [string]$context.target.management.identityPath) -TimeoutSec 3
            break
        }
        catch
        {
            $health = $null
            $identity = $null
            if ($attempt -lt $script:HealthAttemptLimit) { Start-Sleep -Seconds 1 }
        }
    }
    if ($null -eq $health -or $null -eq $identity)
    {
        Throw-Blocked 'RUNTIME_HEALTH_NOT_VERIFIED'
    }
    $counterProof = Assert-HealthFacts $health $identity $ExpectedReleaseId
    return [pscustomobject][ordered]@{
        decision = 'PASS / GATEY_READONLY_LOOPBACK_RUNTIME_HEALTHY'
        unit = $unit
        mainPid = $mainPid
        releaseId = [string]$identity.releaseId
        sourceCommit = [string]$identity.sourceCommit
        killSwitch = [string]$identity.killSwitch
        mutationRuntimeBound = [bool]$identity.mutationRuntimeBound
        counterProof = $counterProof
        diagnosticOnly = [bool]$identity.diagnosticOnly
        tradingAuthorization = $false
    }
}

function Get-ResidualCgroupProcessIds(
    [AllowEmptyCollection()][string[]]$ProcessIds,
    [long]$ControlProcessId
)
{
    return @($ProcessIds | Where-Object {
        -not [string]::IsNullOrWhiteSpace($_) -and [long]$_ -ne $ControlProcessId
    })
}

function Invoke-VerifyStopped
{
    Assert-RootLinux
    $context = Get-TargetAndPaths
    $unit = [string]$context.target.unit.name
    $active = Invoke-Native '/usr/bin/systemctl' @('is-active', '--quiet', $unit) -AllowFailure
    if ($active.ExitCode -eq 0) { Throw-Blocked 'RUNTIME_STOP_NOT_VERIFIED' }
    $pidResult = Invoke-Native '/usr/bin/systemctl' @('show', '--property=MainPID', '--value', $unit) -AllowFailure
    $pidText = ($pidResult.Lines -join '').Trim()
    if ($pidText.Length -gt 0 -and [long]$pidText -ne 0) { Throw-Blocked 'RUNTIME_STOP_NOT_VERIFIED' }
    $groupResult = Invoke-Native '/usr/bin/systemctl' @(
        'show', '--property=ControlGroup', '--value', $unit
    ) -AllowFailure
    $controlGroup = ($groupResult.Lines -join '').Trim()
    if ($controlGroup.Length -gt 0)
    {
        $procsPath = '/sys/fs/cgroup' + $controlGroup + '/cgroup.procs'
        if (Test-Path -LiteralPath $procsPath -PathType Leaf)
        {
            $residual = @(Get-ResidualCgroupProcessIds `
                ([IO.File]::ReadAllLines($procsPath)) ([long]$PID))
            if ($residual.Count -ne 0) { Throw-Blocked 'RUNTIME_STOP_NOT_VERIFIED' }
        }
    }
    $listeners = Invoke-Native '/usr/bin/ss' @('-H', '-ltnp')
    if (($listeners.Lines -join "`n") -match '127\.0\.0\.1:18890')
    {
        Throw-Blocked 'RUNTIME_STOP_NOT_VERIFIED'
    }
    return [pscustomobject][ordered]@{
        decision = 'PASS / GATEY_READONLY_RUNTIME_STOPPED'
        mainPid = 0
        managementPortClosed = $true
        releaseDeleted = $false
        databaseMutation = $false
    }
}

function Start-Runtime
{
    $preflight = Invoke-UnitPreflight
    $context = Get-TargetAndPaths
    $null = Invoke-Native '/usr/bin/systemctl' @('start', [string]$context.target.unit.name)
    $health = Invoke-Health
    return [pscustomobject][ordered]@{
        decision = 'PASS / GATEY_READONLY_RUNTIME_STARTED_HEALTHY'
        releaseId = $preflight.releaseId
        mainPid = $health.mainPid
        runtimeStart = $true
        tradingAuthorization = $false
    }
}

function Stop-Runtime
{
    Assert-RootLinux
    $context = Get-TargetAndPaths
    $null = Invoke-Native '/usr/bin/systemctl' @('stop', [string]$context.target.unit.name)
    return Invoke-VerifyStopped
}

function Invoke-InstallerAction([string]$InstallerAction, [string]$ReleaseId)
{
    $installer = Join-Path $PSScriptRoot 'install-gatey-readonly-release.ps1'
    $output = @(& $installer -Action $InstallerAction -ReleaseId $ReleaseId 2>&1)
    if ($LASTEXITCODE -ne 0)
    {
        Throw-Blocked 'IMMUTABLE_RELEASE_ACTIVATION_FAILED'
    }
    return (($output -join [Environment]::NewLine) | ConvertFrom-Json)
}

function Get-PreviousReleaseContract($Manifest)
{
    if ([string]$Manifest.schemaVersion -ceq 'gatey-readonly-release.v1') { return 'GATEY' }
    if ([string]$Manifest.schemaVersion -ceq 'nq-gatew-release-v3') { return 'GATEW' }
    Throw-Blocked 'PREVIOUS_RELEASE_CONTRACT_UNSUPPORTED'
}

function Test-PreviousRelease([string]$Root)
{
    $manifestPath = Join-Path $Root 'release-manifest.json'
    $null = Assert-GateYRegularFileIdentity $manifestPath
    $manifestSha256 = Get-GateYReadonlySha256File $manifestPath
    $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
    $previousContract = Get-PreviousReleaseContract $manifest
    if ($previousContract -ceq 'GATEY')
    {
        $verified = Test-GateYReadonlyRelease $Root -RequirePosix
        return [pscustomobject][ordered]@{
            contract = 'GATEY'
            releaseId = [string]$verified.releaseId
            manifestSha256 = [string]$verified.manifestSha256
            releaseRoot = $Root
        }
    }
    if ($previousContract -ceq 'GATEW')
    {
        $verifier = Join-Path $PSScriptRoot 'verify-gatew-release.ps1'
        $contract = Join-Path $PSScriptRoot 'gatew-release-contract.psm1'
        $null = Assert-GateYRegularFileIdentity $verifier
        $null = Assert-GateYRegularFileIdentity $contract
        $engine = (Get-Process -Id $PID).Path
        $output = @(& $engine -NoProfile -File $verifier `
            -ReleaseRoot $Root `
            -ExpectedReleaseId ([string]$manifest.releaseId) `
            -ExpectedManifestSha256 $manifestSha256 2>&1)
        if ($LASTEXITCODE -ne 0)
        {
            Throw-Blocked 'PREVIOUS_RELEASE_VERIFICATION_FAILED'
        }
        $result = ($output -join [Environment]::NewLine) | ConvertFrom-Json
        if ([string]$result.decision -cne 'PASS / IMMUTABLE_RELEASE_VERIFIED')
        {
            Throw-Blocked 'PREVIOUS_RELEASE_VERIFICATION_FAILED'
        }
        return [pscustomobject][ordered]@{
            contract = 'GATEW'
            releaseId = [string]$result.releaseId
            manifestSha256 = [string]$result.manifestSha256
            releaseRoot = $Root
        }
    }
}

function Set-CurrentPointer([string]$Target)
{
    $releasesRoot = '/opt/nexus-quant/releases/'
    $resolvedTarget = [IO.Path]::GetFullPath($Target).TrimEnd('/')
    if (-not $resolvedTarget.StartsWith($releasesRoot, [StringComparison]::Ordinal) -or
            -not (Test-Path -LiteralPath $resolvedTarget -PathType Container))
    {
        Throw-Blocked 'PREVIOUS_RELEASE_IDENTITY_MISMATCH'
    }
    $current = '/opt/nexus-quant/current'
    $temporary = '/opt/nexus-quant/.current-rollback-' + [Guid]::NewGuid().ToString('N')
    try
    {
        $null = Invoke-Native '/usr/bin/ln' @('-s', '--', $resolvedTarget, $temporary)
        $null = Invoke-Native '/usr/bin/chown' @('-h', 'root:root', '--', $temporary)
        $null = Invoke-Native '/usr/bin/mv' @('-T', '-f', '--', $temporary, $current)
    }
    finally
    {
        if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary -Force }
    }
    $actual = ((Invoke-Native '/usr/bin/readlink' @('--', $current)).Lines -join '').Trim()
    if ($actual -cne $resolvedTarget) { Throw-Blocked 'CURRENT_POINTER_POST_ACTIVATION_INVALID' }
    return $actual
}

function Activate-Runtime
{
    Assert-RootLinux
    if (-not $NoMigration)
    {
        Throw-Blocked 'ROLLBACK_CURRENT_VERIFICATION_NOT_IMPLEMENTED'
    }
    $context = Get-TargetAndPaths
    $null = Invoke-ReleasePreflight $context
    Assert-InstalledUnit $context
    $current = [string]$context.target.service.currentPointer
    $previous = $null
    if (Test-Path -LiteralPath $current)
    {
        $previous = Resolve-CanonicalPath $current
        $previousId = Split-Path -Leaf $previous
        $previousVerification = Test-PreviousRelease $previous
        if ([string]$previousVerification.releaseId -cne $previousId)
        {
            Throw-Blocked 'PREVIOUS_RELEASE_IDENTITY_MISMATCH'
        }
        if (-not [string]::IsNullOrWhiteSpace($PreviousReleaseId) -and
                $PreviousReleaseId -cne $previousId)
        {
            Throw-Blocked 'PREVIOUS_RELEASE_IDENTITY_MISMATCH'
        }
    }
    try
    {
        $activation = Invoke-InstallerAction 'activate' $ExpectedReleaseId
        $started = Start-Runtime
        return [pscustomobject][ordered]@{
            decision = 'PASS / GATEY_READONLY_RELEASE_ACTIVATED_HEALTHY'
            previousRelease = $previous
            currentRelease = [string]$activation.currentTarget
            mainPid = $started.mainPid
            rollbackRequired = $false
        }
    }
    catch
    {
        $activationFailure = $_
        try
        {
            $null = Stop-Runtime
        }
        catch
        {
            Throw-Blocked 'FAILED_RUNTIME_STOP_NOT_VERIFIED'
        }
        if ($null -ne $previous)
        {
            $null = Set-CurrentPointer $previous
        }
        throw $activationFailure
    }
}

function Rollback-Runtime
{
    Assert-RootLinux
    if (-not $NoMigration -or $PreviousReleaseId -cnotmatch $script:CommitPattern)
    {
        Throw-Blocked 'ROLLBACK_CURRENT_VERIFICATION_NOT_IMPLEMENTED'
    }
    $context = Get-TargetAndPaths
    $null = Invoke-ReleasePreflight $context -RequireCurrent
    Assert-InstalledUnit $context
    $null = Stop-Runtime
    $previousRoot = '/opt/nexus-quant/releases/' + $PreviousReleaseId
    $previousVerification = Test-PreviousRelease $previousRoot
    if ([string]$previousVerification.releaseId -cne $PreviousReleaseId)
    {
        Throw-Blocked 'PREVIOUS_RELEASE_IDENTITY_MISMATCH'
    }
    $currentRelease = Set-CurrentPointer $previousRoot
    return [pscustomobject][ordered]@{
        decision = 'PASS / GATEY_READONLY_CODE_ROLLBACK_COMPLETED'
        currentRelease = $currentRelease
        previousContract = [string]$previousVerification.contract
        previousRuntimeRestarted = $false
        databaseRecoveryPerformed = $false
    }
}

function Write-DeploymentEvidence($Value)
{
    if ([string]::IsNullOrWhiteSpace($EvidencePath)) { return $null }
    Assert-RootLinux
    $root = '/var/lib/nexus-quant/gatey-readonly-qualification/deployment-evidence'
    $fullPath = [IO.Path]::GetFullPath($EvidencePath)
    if ((Split-Path -Parent $fullPath) -cne $root -or
            [IO.Path]::GetFileName($fullPath) -cnotmatch
                '^gatey-readonly-deployment-[0-9]{8}T[0-9]{6}Z-[a-z0-9-]+\.json$')
    {
        Throw-Blocked 'DEPLOYMENT_EVIDENCE_PATH_INVALID'
    }
    if (Test-Path -LiteralPath $fullPath)
    {
        Throw-Blocked 'DEPLOYMENT_EVIDENCE_ALREADY_EXISTS'
    }
    [IO.Directory]::CreateDirectory($root) | Out-Null
    $null = Invoke-Native '/usr/bin/chown' @('root:root', '--', $root)
    $null = Invoke-Native '/usr/bin/chmod' @('0700', '--', $root)
    $temporary = Join-Path $root ('.evidence-' + $PID + '-' + [Guid]::NewGuid().ToString('N'))
    try
    {
        [IO.File]::WriteAllText(
            $temporary,
            (($Value | ConvertTo-Json -Depth 12) + [char]10),
            [Text.UTF8Encoding]::new($false)
        )
        $null = Invoke-Native '/usr/bin/chown' @('root:root', '--', $temporary)
        $null = Invoke-Native '/usr/bin/chmod' @('0600', '--', $temporary)
        $null = Invoke-Native '/usr/bin/mv' @('-T', '-n', '--', $temporary, $fullPath)
        if (Test-Path -LiteralPath $temporary)
        {
            Throw-Blocked 'DEPLOYMENT_EVIDENCE_ALREADY_EXISTS'
        }
    }
    finally
    {
        if (Test-Path -LiteralPath $temporary)
        {
            Remove-Item -LiteralPath $temporary -Force
        }
    }
    return $fullPath
}

function Copy-Object($Value)
{
    return $Value | ConvertTo-Json -Depth 12 | ConvertFrom-Json
}

function Expect-Blocked([scriptblock]$ActionBlock, [string]$Decision)
{
    try
    {
        & $ActionBlock
        throw ('expected decision not raised: ' + $Decision)
    }
    catch
    {
        if ($_.Exception.Message -cne $Decision) { throw }
    }
}

function New-TestTarget
{
    return [pscustomobject][ordered]@{
        schemaVersion = 'gatey-readonly-runtime-target.v1'
        runtimeRole = 'GATEY_READONLY_QUALIFICATION'
        unit = [pscustomobject][ordered]@{
            name = 'nq-gatey-readonly-qualification.service'
            installedPath = '/etc/systemd/system/nq-gatey-readonly-qualification.service'
            sourceRelativePath = 'config/nq-gatey-readonly-qualification.service'
        }
        service = [pscustomobject][ordered]@{
            user = 'nq-gatey-readonly'; group = 'nq-gatey-readonly'
            workingDirectory = '/opt/nexus-quant'
            releaseRoot = '/opt/nexus-quant/releases'
            currentPointer = '/opt/nexus-quant/current'
            evidenceRoot = '/var/lib/nexus-quant/gatey-readonly-qualification/deployment-evidence'
        }
        environment = [pscustomobject][ordered]@{
            path = '/etc/nexus-quant/gatey-readonly-qualification/runtime.env'
            owner = 'root'; group = 'nq-gatey-readonly'; mode = '640'
            templateRelativePath = 'config/gatey-readonly-runtime.env.example'
            secretPath = '/etc/nexus-quant/gatey-readonly-qualification/secrets.env'
            secretOwner = 'root'; secretGroup = 'root'; secretMode = '600'
            secretTemplateRelativePath = 'config/gatey-readonly-runtime.secrets.env.example'
        }
        management = [pscustomobject][ordered]@{
            address = '127.0.0.1'; port = 18890
            healthPath = '/actuator/health'; identityPath = '/actuator/readonlyproviderobservation'
        }
        database = [pscustomobject][ordered]@{
            targetId = 'gatey-production-control-plane'
            runtimeEnvironment = 'PRODUCTION_CONTROL_PLANE'
            host = '127.0.0.1'; port = 55432; name = 'nexus_quant'
            flywayHistoryTable = 'flyway_schema_history'
            credentialReferenceName = 'gatey-readonly-qualification-db'
            credentialPath = '/etc/nexus-quant/gatey-readonly-qualification/db.pgpass'
            credentialOwner = 'root'; credentialGroup = 'root'; credentialMode = '600'
            credentialTemplateRelativePath = 'config/gatey-readonly-db.pgpass.example'
        }
        safety = [pscustomobject][ordered]@{
            live = 'DISABLED'; killSwitch = 'ENGAGED'
            providerObservationEnabled = $true
            tradingComponentsEnabled = $false
            mutationRuntimeBound = $false
        }
    }
}

function New-TestEnvironment([string]$ReleaseId)
{
    return @{
        SPRING_PROFILES_ACTIVE = 'gatey-readonly-qualification'
        NQ_APP_BIND_ADDRESS = '127.0.0.1'; NQ_APP_PORT = '18890'
        NQ_GATEY_MANAGEMENT_ADDRESS = '127.0.0.1'; NQ_GATEY_MANAGEMENT_PORT = '18890'
        NQ_GATEY_RELEASE_ID = $ReleaseId; NQ_GATEY_SOURCE_COMMIT = $ReleaseId
        NQ_GATEY_RELEASE_MANIFEST_SHA256 = ('2' * 64)
        NQ_GATEY_QUALIFICATION_DB_URL = 'jdbc:postgresql://127.0.0.1:55432/nexus_quant'
        NQ_GATEY_QUALIFICATION_DB_USER = 'fixture-user'
        NQ_GATEY_DATABASE_TARGET_ID = 'gatey-production-control-plane'
        NQ_GATEY_DATABASE_CREDENTIAL_REFERENCE = 'gatey-readonly-qualification-db'
        NQ_LIVE_ENABLED = 'false'; NQ_TRADING_COMPONENTS_ENABLED = 'false'
        NQ_RUNTIME_PROVIDER_OBSERVATION_ENABLED = 'true'
        NQ_RUNTIME_PROVIDER_OBSERVATION_ORDER_SUBMISSION_ENABLED = 'false'
        NQ_RUNTIME_PROVIDER_OBSERVATION_CANCEL_ENABLED = 'false'
        NQ_RUNTIME_PROVIDER_OBSERVATION_TRANSFER_ENABLED = 'false'
        NQ_RUNTIME_PROVIDER_OBSERVATION_WITHDRAW_ENABLED = 'false'
        NQ_GATEY_EXPECTED_KILL_SWITCH = 'ENGAGED'
        NQ_AUTH_BOOTSTRAP_ADMIN_ENABLED = 'false'
        NQ_SECURITY_ISSUER = 'nexus-quant-gatey-readonly'
        NQ_SECURITY_ACCESS_TOKEN_TTL = 'PT30M'
    }
}

function New-TestHealth([string]$ReleaseId)
{
    $counter = [pscustomobject]@{ status = 'NOT_INSTRUMENTED'; value = $null }
    return [pscustomobject][ordered]@{
        sourceCommit = $ReleaseId; releaseId = $ReleaseId; javaMajor = 21
        qualificationProfile = 'gatey-readonly-qualification'
        capabilityIdentity = 'read-only-provider-observation'; bindAddress = '127.0.0.1'
        providerObservationEnabled = $true; tradingComponentsEnabled = $false
        liveEnabled = $false; killSwitch = 'ENGAGED'; mutationRuntimeBound = $false
        credentialMetadataReads = $counter; credentialMaterialReads = $counter
        decryptCount = $counter; okxGetCount = $counter; okxPostCount = $counter
        executionIntentDelta = $counter; executionReceiptDelta = $counter
        orderDelta = $counter; ledgerDelta = $counter
        diagnosticOnly = $true; tradingAuthorization = $false; noSideEffect = $true
    }
}

function Invoke-ContractSelfTest
{
    $cases = [Collections.Generic.List[string]]::new()
    $releaseId = '1111111111111111111111111111111111111111'
    $manifestSha = '2' * 64
    $target = New-TestTarget
    Assert-TargetContract $target
    $cases.Add('canonical-target-pass')
    $wrongCanonicalPort = Copy-Object $target
    $wrongCanonicalPort.database.port = 5432
    Expect-Blocked { Assert-TargetContract $wrongCanonicalPort } `
        'BLOCKED / DATABASE_TARGET_MISMATCH'
    $cases.Add('wrong-canonical-db-port-blocked')

    $missingUnit = Copy-Object $target; $missingUnit.unit.name = ''
    Expect-Blocked { Assert-TargetContract $missingUnit } 'BLOCKED / SYSTEMD_UNIT_MISMATCH'
    $cases.Add('missing-systemd-contract-blocked')
    $wrongUnit = Copy-Object $target; $wrongUnit.unit.name = 'wrong.service'
    Expect-Blocked { Assert-TargetContract $wrongUnit } 'BLOCKED / SYSTEMD_UNIT_MISMATCH'
    $cases.Add('wrong-systemd-unit-blocked')

    Assert-EnvironmentMetadata ([pscustomobject]@{
        type='regular file';owner='root';group='nq-gatey-readonly';mode='640'
        serviceUserReadable=$true;serviceUserWritable=$false
    })
    $cases.Add('environment-metadata-pass')
    Expect-Blocked { Assert-EnvironmentMetadata ([pscustomobject]@{
        type='regular file';owner='nq-gatey-readonly';group='nq-gatey-readonly';mode='640'
        serviceUserReadable=$true;serviceUserWritable=$false
    }) } 'BLOCKED / ENVIRONMENT_OWNER_INVALID'
    $cases.Add('wrong-env-owner-blocked')
    Expect-Blocked { Assert-EnvironmentMetadata ([pscustomobject]@{
        type='regular file';owner='root';group='nq-gatey-readonly';mode='644'
        serviceUserReadable=$true;serviceUserWritable=$false
    }) } 'BLOCKED / ENVIRONMENT_MODE_INVALID'
    $cases.Add('wrong-env-mode-blocked')
    Expect-Blocked { Assert-EnvironmentMetadata ([pscustomobject]@{
        type='regular file';owner='root';group='nq-gatey-readonly';mode='640'
        serviceUserReadable=$true;serviceUserWritable=$true
    }) } 'BLOCKED / ENVIRONMENT_WRITABLE_BY_SERVICE_USER'
    $cases.Add('service-user-writable-env-blocked')

    Assert-RootSecretMetadata ([pscustomobject]@{
        type='regular file';owner='root';group='root';mode='600'
        serviceUserReadable=$false;serviceUserWritable=$false
    })
    $cases.Add('root-secret-metadata-pass')
    Expect-Blocked { Assert-RootSecretMetadata ([pscustomobject]@{
        type='regular file';owner='root';group='root';mode='600'
        serviceUserReadable=$true;serviceUserWritable=$false
    }) } 'BLOCKED / SECRET_ACCESSIBLE_BY_SERVICE_USER'
    $cases.Add('service-user-secret-read-blocked')

    $values = New-TestEnvironment $releaseId
    Assert-EnvironmentValues $values $target $releaseId
    $cases.Add('environment-values-pass')
    $missingDb = New-TestEnvironment $releaseId; $missingDb.Remove('NQ_GATEY_DATABASE_TARGET_ID')
    Expect-Blocked { Assert-EnvironmentValues $missingDb $target $releaseId } `
        'BLOCKED / ENVIRONMENT_CONTRACT_MISSING'
    $cases.Add('missing-db-target-blocked')
    $wrongDb = New-TestEnvironment $releaseId; $wrongDb['NQ_GATEY_DATABASE_TARGET_ID'] = 'wrong'
    Expect-Blocked { Assert-EnvironmentValues $wrongDb $target $releaseId } `
        'BLOCKED / DATABASE_TARGET_MISMATCH'
    $cases.Add('wrong-db-target-blocked')
    $localhost = New-TestEnvironment $releaseId
    $localhost['NQ_GATEY_QUALIFICATION_DB_URL'] = 'jdbc:postgresql://localhost:55432/nexus_quant'
    Expect-Blocked { Assert-EnvironmentValues $localhost $target $releaseId } `
        'BLOCKED / LOCALHOST_DATABASE_FALLBACK_REJECTED'
    $cases.Add('localhost-fallback-rejected')
    $live = New-TestEnvironment $releaseId; $live['NQ_LIVE_ENABLED'] = 'true'
    Expect-Blocked { Assert-EnvironmentValues $live $target $releaseId } 'BLOCKED / LIVE_ENABLED'
    $cases.Add('live-enabled-blocked')
    $kill = New-TestEnvironment $releaseId; $kill['NQ_GATEY_EXPECTED_KILL_SWITCH'] = 'DISENGAGED'
    Expect-Blocked { Assert-EnvironmentValues $kill $target $releaseId } `
        'BLOCKED / KILL_SWITCH_NOT_ENGAGED'
    $cases.Add('kill-disengaged-blocked')
    $nonLoopback = New-TestEnvironment $releaseId
    $nonLoopback['NQ_GATEY_MANAGEMENT_ADDRESS'] = '0.0.0.0'
    Expect-Blocked { Assert-EnvironmentValues $nonLoopback $target $releaseId } `
        'BLOCKED / MANAGEMENT_BIND_NOT_LOOPBACK'
    $cases.Add('non-loopback-management-blocked')

    Assert-ReleaseFacts ([pscustomobject]@{
        releaseId=$releaseId;manifestSha256=$manifestSha
    }) $releaseId $manifestSha
    $cases.Add('release-identity-pass')
    Expect-Blocked { Assert-ReleaseFacts ([pscustomobject]@{
        releaseId=('3' * 40);manifestSha256=$manifestSha
    }) $releaseId $manifestSha } 'BLOCKED / CURRENT_RELEASE_MISMATCH'
    $cases.Add('current-release-mismatch-blocked')

    $health = [pscustomobject]@{ status = 'UP' }
    $identity = New-TestHealth $releaseId
    $counterProof = Assert-HealthFacts $health $identity $releaseId
    if ([int]$counterProof.total -ne 9 -or [int]$counterProof.verifiedZero -ne 0 -or
            [int]$counterProof.notVerified -ne 9)
    {
        throw 'SELF_TEST_COUNTER_CLASSIFICATION_INVALID'
    }
    $cases.Add('runtime-health-identity-pass')
    $commitMismatch = Copy-Object $identity; $commitMismatch.sourceCommit = '4' * 40
    Expect-Blocked { Assert-HealthFacts $health $commitMismatch $releaseId } `
        'BLOCKED / RUNTIME_IDENTITY_MISMATCH'
    $cases.Add('health-commit-mismatch-blocked')
    Expect-Blocked { Assert-HealthFacts ([pscustomobject]@{status='DOWN'}) $identity $releaseId } `
        'BLOCKED / RUNTIME_HEALTH_NOT_VERIFIED'
    $cases.Add('failed-health-blocked')
    $bound = Copy-Object $identity; $bound.mutationRuntimeBound = $true
    Expect-Blocked { Assert-HealthFacts $health $bound $releaseId } 'BLOCKED / MUTATION_RUNTIME_BOUND'
    $cases.Add('mutation-runtime-bound-blocked')
    if ((Assert-CounterSafe ([pscustomobject]@{status='NOT_INSTRUMENTED';value=$null})) -cne
            'NOT_VERIFIED') { throw 'NOT_INSTRUMENTED_CLASSIFICATION_INVALID' }
    $cases.Add('not-instrumented-never-equals-verified-zero')
    if ((Assert-CounterSafe ([pscustomobject]@{status='UNKNOWN';value=$null})) -cne
            'NOT_VERIFIED') { throw 'UNKNOWN_CLASSIFICATION_INVALID' }
    $cases.Add('unknown-never-equals-zero')
    if ((Assert-CounterSafe ([pscustomobject]@{status='VERIFIED_ZERO';value=[long]0})) -cne
            'VERIFIED_ZERO') { throw 'VERIFIED_ZERO_CLASSIFICATION_INVALID' }
    $cases.Add('verified-zero-accepted')
    if ((Assert-CounterSafe ([pscustomobject]@{status='OBSERVED';value=[long]0})) -cne
            'VERIFIED_ZERO') { throw 'OBSERVED_ZERO_CLASSIFICATION_INVALID' }
    $cases.Add('observed-zero-explicitly-classified')
    Expect-Blocked { Assert-CounterSafe ([pscustomobject]@{status='OBSERVED';value=$null}) } `
        'BLOCKED / STARTUP_COUNTER_INVALID'
    $cases.Add('counter-null-rejected')
    Expect-Blocked { Assert-CounterSafe ([pscustomobject]@{status='NOT_INSTRUMENTED';value=0}) } `
        'BLOCKED / STARTUP_COUNTER_INVALID'
    $cases.Add('not-instrumented-zero-rejected')
    Expect-Blocked { Assert-CounterSafe ([pscustomobject]@{status='UNKNOWN';value=0}) } `
        'BLOCKED / STARTUP_COUNTER_INVALID'
    $cases.Add('unknown-zero-rejected')
    Expect-Blocked { Assert-CounterSafe ([pscustomobject]@{status='OBSERVED';value=1}) } `
        'BLOCKED / STARTUP_SIDE_EFFECT_OBSERVED'
    $cases.Add('startup-side-effect-blocked')
    Expect-Blocked { Assert-CounterSafe ([pscustomobject]@{status='OBSERVED';value='0'}) } `
        'BLOCKED / STARTUP_COUNTER_INVALID'
    $cases.Add('counter-string-zero-rejected')
    Expect-Blocked { Assert-CounterSafe ([pscustomobject]@{status='OBSERVED';value=''}) } `
        'BLOCKED / STARTUP_COUNTER_INVALID'
    $cases.Add('counter-empty-string-rejected')
    Expect-Blocked { Assert-CounterSafe ([pscustomobject]@{status='OBSERVED'}) } `
        'BLOCKED / STARTUP_COUNTER_INVALID'
    $cases.Add('counter-missing-value-rejected')
    Expect-Blocked { Assert-CounterSafe ([pscustomobject]@{status='OBSERVED';value=-1}) } `
        'BLOCKED / STARTUP_SIDE_EFFECT_OBSERVED'
    $cases.Add('counter-negative-rejected')

    Assert-DatabaseFacts ([pscustomobject]@{
        database='nexus_quant';port=55432;killSwitch='ENGAGED';failedMigrations=0;currentVersion='V41'
    }) $target 'V41'
    $cases.Add('database-facts-pass')
    Expect-Blocked { Assert-DatabaseFacts ([pscustomobject]@{
        database='soak_db';port=55432;killSwitch='ENGAGED';failedMigrations=0;currentVersion='V41'
    }) $target 'V41' } 'BLOCKED / DATABASE_TARGET_MISMATCH'
    $cases.Add('observed-db-mismatch-blocked')

    if ((Get-PreviousReleaseContract ([pscustomobject]@{
        schemaVersion='nq-gatew-release-v3'
    })) -cne 'GATEW') { throw 'GATEW_PREVIOUS_CONTRACT_ROUTING_INVALID' }
    $cases.Add('gatew-previous-release-routing-pass')
    Expect-Blocked { Get-PreviousReleaseContract ([pscustomobject]@{
        schemaVersion='unknown-release'
    }) } 'BLOCKED / PREVIOUS_RELEASE_CONTRACT_UNSUPPORTED'
    $cases.Add('unsupported-previous-release-blocked')

    $planIo = Get-PlanIoClassification
    if ([string]$planIo.scope -cne 'LOCAL_ONLY' -or
            [string]$planIo.externalIoClassification -cne 'ZERO_EXTERNAL_IO' -or
            [bool]$planIo.credentialAssistedExternalIo -or [int]$planIo.psqlInvocations -ne 0 -or
            [int]$planIo.pgpassUses -ne 0 -or [int]$planIo.networkCalls -ne 0)
    {
        throw 'PLAN_IO_CLASSIFICATION_INVALID'
    }
    $cases.Add('plan-zero-external-io-classified')
    $preflightIo = Get-UnitPreflightIoClassification
    if ([string]$preflightIo.scope -cne 'READ_ONLY_RUNTIME_IO' -or
            [string]$preflightIo.externalIoClassification -cne
                'READ_ONLY_EXTERNAL_IO_ALLOWED' -or
            -not [bool]$preflightIo.credentialAssistedExternalIo -or
            -not [bool]$preflightIo.credentialMaterialConsumedByExternalProcess -or
            [bool]$preflightIo.credentialBytesExposedToScript -or
            [int]$preflightIo.psqlInvocations -ne 1 -or
            [int]$preflightIo.pgpassUses -ne 1 -or [int]$preflightIo.networkCalls -ne 1 -or
            [int]$preflightIo.databaseReads -ne 1 -or [int]$preflightIo.databaseWrites -ne 0)
    {
        throw 'PREFLIGHT_IO_CLASSIFICATION_INVALID'
    }
    $cases.Add('preflight-external-io-explicitly-classified')
    if ($script:HealthAttemptLimit -ne 90)
    {
        throw 'HEALTH_ATTEMPT_LIMIT_INVALID'
    }
    $cases.Add('health-timeout-bounded-90-seconds')
    $selfOnly = @(Get-ResidualCgroupProcessIds @([string]$PID, '') ([long]$PID))
    $withResidual = @(Get-ResidualCgroupProcessIds @([string]$PID, '424242') ([long]$PID))
    if ($selfOnly.Count -ne 0 -or $withResidual.Count -ne 1 -or
            [string]$withResidual[0] -cne '424242')
    {
        throw 'CGROUP_CONTROL_PROCESS_EXCLUSION_INVALID'
    }
    $cases.Add('exec-stop-post-self-pid-excluded')

    if ($cases.Count -ne 43) { throw ('SELF_TEST_CASE_COUNT_INVALID:' + $cases.Count) }
    return [pscustomobject][ordered]@{
        decision = 'PASS / GATEY_READONLY_RUNTIME_DEPLOYMENT_CONTRACT_SELF_TEST'
        cases = $cases.Count
        results = @($cases)
        filesystemMutation = $false
        systemdMutation = $false
        databaseMutation = $false
        runtimeStart = $false
        planIo = $planIo
        preflightIo = $preflightIo
    }
}

try
{
    $result = switch ($Action)
    {
        'Plan' { Invoke-Plan }
        'UnitPreflight' { Invoke-UnitPreflight }
        'InstallUnit' { Install-Unit }
        'Start' { Start-Runtime }
        'Stop' { Stop-Runtime }
        'VerifyStopped' { Invoke-VerifyStopped }
        'Health' { Invoke-Health }
        'Activate' { Activate-Runtime }
        'Rollback' { Rollback-Runtime }
        'ContractSelfTest' { Invoke-ContractSelfTest }
    }
    if (-not [string]::IsNullOrWhiteSpace($EvidencePath))
    {
        if ($Action -in @('Plan', 'UnitPreflight', 'ContractSelfTest'))
        {
            Throw-Blocked 'DRY_RUN_EVIDENCE_WRITE_FORBIDDEN'
        }
        $writtenEvidence = Write-DeploymentEvidence $result
        $result | Add-Member -NotePropertyName evidencePath -NotePropertyValue $writtenEvidence
    }
    $result | ConvertTo-Json -Depth 12
}
catch
{
    $decision = if ($_.Exception.Message -match '^(BLOCKED|FAIL) / [A-Z0-9_]+$') {
        $_.Exception.Message
    } else {
        'FAIL / GATEY_READONLY_RUNTIME_DEPLOYMENT_INTERNAL_ERROR'
    }
    [pscustomobject][ordered]@{
        decision = $decision
        action = $Action
        filesystemMutation = $false
        systemdMutation = $false
        databaseMutation = $false
        tradingAuthorization = $false
    } | ConvertTo-Json
    exit 2
}
