[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [switch]$ConfirmDisposable,

    [ValidateSet('postgres:16-alpine')]
    [string]$PostgresImage = 'postgres:16-alpine'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

# GateW-4 restore drill accepts only local, randomly named, volume-free disposable PostgreSQL.
# Host, database, and credential parameters are intentionally unavailable.
if (-not $ConfirmDisposable) {
    throw 'ConfirmDisposable is required'
}

$profiles = @(
    $env:SPRING_PROFILES_ACTIVE,
    $env:NQ_ENVIRONMENT,
    $env:NQ_PROFILE
) -join ','
if ($profiles -match '(?i)(^|[,;\s])(prod|production)([,;\s]|$)') {
    throw 'production profile is forbidden for the GateW-4 restore drill'
}

$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$artifactRoot = Join-Path $repo 'artifacts'
$runId = [Guid]::NewGuid().ToString('N')
$database = "nq_gatew4_disposable_$runId"
$user = 'nq_gatew4_drill'
$password = [Guid]::NewGuid().ToString('N')
$sourceContainer = "nq-gatew4-disposable-source-$runId"
$restoreContainer = "nq-gatew4-disposable-restore-$runId"
$runDirectory = Join-Path $artifactRoot "gatew4-restore-tmp-$runId"
$dumpPath = Join-Path $runDirectory 'gatew4.dump'

function Assert-LastExitCode([string]$operation) {
    if ($LASTEXITCODE -ne 0) {
        throw "$operation failed with exit code $LASTEXITCODE"
    }
}

function Assert-DisposableContainerName([string]$name) {
    if ($name -notmatch '^nq-gatew4-disposable-(source|restore)-[0-9a-f]{32}$') {
        throw 'refusing to operate on a non-disposable container name'
    }
}

function Start-DisposablePostgres([string]$name) {
    Assert-DisposableContainerName $name
    $containerId = docker run --detach --name $name `
        --env "POSTGRES_DB=$database" `
        --env "POSTGRES_USER=$user" `
        --env "POSTGRES_PASSWORD=$password" `
        --publish '127.0.0.1::5432' `
        $PostgresImage
    Assert-LastExitCode "start disposable PostgreSQL $name"
    if ([string]::IsNullOrWhiteSpace($containerId)) {
        throw 'Docker did not return a container id'
    }

    for ($attempt = 1; $attempt -le 30; $attempt++) {
        docker exec $name pg_isready --username $user --dbname $database *> $null
        if ($LASTEXITCODE -eq 0) {
            return
        }
        Start-Sleep -Seconds 1
    }
    throw "disposable PostgreSQL $name did not become ready"
}

function Stop-DisposableContainer([string]$name) {
    Assert-DisposableContainerName $name
    $exists = docker ps --all --quiet --filter "name=^/$name$"
    Assert-LastExitCode 'list disposable containers'
    if (-not [string]::IsNullOrWhiteSpace(($exists | Out-String).Trim())) {
        docker rm --force $name *> $null
        Assert-LastExitCode "remove disposable container $name"
    }
}

function Get-LoopbackPort([string]$name) {
    Assert-DisposableContainerName $name
    $mapping = docker port $name '5432/tcp'
    Assert-LastExitCode "read disposable PostgreSQL port for $name"
    if ($mapping -notmatch '^127\.0\.0\.1:(\d+)$') {
        throw 'Docker returned a non-loopback or invalid PostgreSQL port mapping'
    }
    return $Matches[1]
}

function Invoke-Scalar([string]$name, [string]$sql) {
    Assert-DisposableContainerName $name
    $value = docker exec --env "PGPASSWORD=$password" $name `
        psql --username $user --dbname $database --no-psqlrc `
        --set ON_ERROR_STOP=1 --tuples-only --no-align --command $sql
    Assert-LastExitCode 'run restore verification query'
    return ($value | Out-String).Trim()
}

$savedEnvironment = @{}
$drillEnvironmentNames = @(
    'CI',
    'NQ_NO_OUTBOUND',
    'NQ_AI_ENABLED',
    'NQ_DH_RUNTIME_ENABLED',
    'NQ_REAL_EXCHANGE_ENABLED',
    'NQ_GATEW4_RESTORE_DRILL_REQUIRED',
    'NQ_GATEW4_RESTORE_DB_URL',
    'NQ_GATEW4_RESTORE_DB_USER',
    'NQ_GATEW4_RESTORE_DB_PASSWORD'
)
foreach ($name in $drillEnvironmentNames) {
    $savedEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}

try {
    docker image inspect $PostgresImage *> $null
    Assert-LastExitCode 'inspect pinned local PostgreSQL image'

    New-Item -ItemType Directory -Path $runDirectory -Force | Out-Null
    Start-DisposablePostgres $sourceContainer
    $sourcePort = Get-LoopbackPort $sourceContainer

    $env:CI = 'true'
    $env:NQ_NO_OUTBOUND = 'true'
    $env:NQ_AI_ENABLED = 'false'
    $env:NQ_DH_RUNTIME_ENABLED = 'false'
    $env:NQ_REAL_EXCHANGE_ENABLED = 'false'
    $env:NQ_GATEW4_RESTORE_DRILL_REQUIRED = 'true'
    $env:NQ_GATEW4_RESTORE_DB_URL = "jdbc:postgresql://127.0.0.1:$sourcePort/$database"
    $env:NQ_GATEW4_RESTORE_DB_USER = $user
    $env:NQ_GATEW4_RESTORE_DB_PASSWORD = $password

    & mvn -f (Join-Path $repo 'backend\pom.xml') `
        -pl nq-app -am `
        '-Dtest=GateW4RestoreDrillPreparePostgresIntegrationTest' `
        '-Dsurefire.failIfNoSpecifiedTests=false' test
    Assert-LastExitCode 'prepare disposable database with Flyway V1-V35'

    docker exec --env "PGPASSWORD=$password" $sourceContainer `
        pg_dump --username $user --dbname $database --format custom `
        --no-owner --no-privileges --file /tmp/gatew4.dump
    Assert-LastExitCode 'create disposable PostgreSQL backup'
    docker cp "${sourceContainer}:/tmp/gatew4.dump" $dumpPath *> $null
    Assert-LastExitCode 'copy disposable PostgreSQL backup'
    if (-not (Test-Path -LiteralPath $dumpPath) -or (Get-Item -LiteralPath $dumpPath).Length -le 0) {
        throw 'backup file was not created'
    }

    Stop-DisposableContainer $sourceContainer
    Start-DisposablePostgres $restoreContainer
    docker cp $dumpPath "${restoreContainer}:/tmp/gatew4.dump" *> $null
    Assert-LastExitCode 'copy backup into restore container'
    docker exec --env "PGPASSWORD=$password" $restoreContainer `
        pg_restore --username $user --dbname $database `
        --no-owner --no-privileges --exit-on-error /tmp/gatew4.dump
    Assert-LastExitCode 'restore disposable PostgreSQL backup'

    $flywayVersion = Invoke-Scalar $restoreContainer `
        "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1;"
    $migrationCount = Invoke-Scalar $restoreContainer `
        "SELECT COUNT(*) FROM flyway_schema_history WHERE success;"
    $killSwitchStatus = Invoke-Scalar $restoreContainer `
        "SELECT status FROM kill_switch_states WHERE scope = 'GLOBAL_TRADING';"
    $killSwitchEvents = Invoke-Scalar $restoreContainer `
        "SELECT COUNT(*) FROM kill_switch_events WHERE scope = 'GLOBAL_TRADING';"
    $reviewFixture = Invoke-Scalar $restoreContainer `
        "SELECT COUNT(*) FROM validation_review_cases WHERE id = '00000000-0000-0000-0000-000000004004';"
    $constraints = Invoke-Scalar $restoreContainer `
        "SELECT COUNT(*) FROM pg_constraint WHERE conname IN ('fk_kill_switch_events_scope','uq_kill_switch_events_scope_version','chk_validation_review_cases_time_order');"

    if ($flywayVersion -ne '35' -or [int]$migrationCount -ne 35) {
        throw 'restored Flyway history is incomplete'
    }
    if ($killSwitchStatus -ne 'ENGAGED' -or [int]$killSwitchEvents -lt 1) {
        throw 'restored kill switch safety facts are incomplete'
    }
    if ([int]$reviewFixture -ne 1 -or [int]$constraints -ne 3) {
        throw 'restored review fixture or schema constraints are incomplete'
    }

    Write-Output 'PASS / GATEW4_DISPOSABLE_BACKUP_RESTORE_PROVEN'
    Write-Output "FLYWAY_VERSION=$flywayVersion"
    Write-Output "MIGRATIONS=$migrationCount"
    Write-Output "KILL_SWITCH_STATUS=$killSwitchStatus"
    Write-Output "KILL_SWITCH_EVENTS=$killSwitchEvents"
    Write-Output "REVIEW_FIXTURE=$reviewFixture"
    Write-Output "CONSTRAINTS=$constraints"
} finally {
    foreach ($name in $drillEnvironmentNames) {
        [Environment]::SetEnvironmentVariable($name, $savedEnvironment[$name], 'Process')
    }
    Stop-DisposableContainer $sourceContainer
    Stop-DisposableContainer $restoreContainer

    if (Test-Path -LiteralPath $runDirectory) {
        $resolvedArtifacts = (Resolve-Path -LiteralPath $artifactRoot).Path
        $resolvedRun = (Resolve-Path -LiteralPath $runDirectory).Path
        if (-not $resolvedRun.StartsWith($resolvedArtifacts + [IO.Path]::DirectorySeparatorChar)) {
            throw 'refusing to remove restore artifacts outside the repository artifacts directory'
        }
        Remove-Item -LiteralPath $resolvedRun -Recurse -Force
    }
}
