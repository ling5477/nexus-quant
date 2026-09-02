[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][switch]$ConfirmDisposable,
    [ValidateSet('Docker', 'Native', 'WslPg16')][string]$ExecutionMode = 'Docker',
    [string]$WslPostgresqlRoot = '/tmp/nq-pg16-runtime-attempt02/root',
    [string]$PostgresImage = 'postgres:16@sha256:f1c3376c26f2609ab9f29f71f824103fe2fcd8ee0346485cb6122a4f93df6f94',
    [string]$EvidenceRoot,
    [string]$ExpectedCommit
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if (-not $ConfirmDisposable) { throw 'BLOCKED / DISPOSABLE_CONFIRMATION_REQUIRED' }
if ((@($env:SPRING_PROFILES_ACTIVE, $env:NQ_ENVIRONMENT, $env:NQ_PROFILE) -join ',') -match
        '(?i)(^|[,;\s])(prod|production|live)([,;\s]|$)') {
    throw 'BLOCKED / PRODUCTION_OR_LIVE_PROFILE_FORBIDDEN'
}

$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$releaseModule = Join-Path $PSScriptRoot 'nq-canonical-release.psm1'
$databaseModule = Join-Path $PSScriptRoot 'nq-canonical-database.psm1'
Import-Module $releaseModule -Force -DisableNameChecking
Import-Module $databaseModule -Force -DisableNameChecking

$commit = if ([string]::IsNullOrWhiteSpace($ExpectedCommit)) {
    (& git -C $repo rev-parse HEAD 2>$null).Trim().ToLowerInvariant()
} else { $ExpectedCommit.ToLowerInvariant() }
if ($commit -cnotmatch '^[0-9a-f]{40}$') {
    throw 'BLOCKED / SOURCE_COMMIT_INVALID'
}
$head = (& git -C $repo rev-parse HEAD 2>$null).Trim().ToLowerInvariant()
if ($LASTEXITCODE -ne 0 -or $head -cne $commit) { throw 'BLOCKED / SOURCE_COMMIT_MISMATCH' }

$migration = Get-NqMigrationInventory (Join-Path $repo 'backend/nq-infra/src/main/resources/db/migration')
$runId = [Guid]::NewGuid().ToString('N')
$database = 'nq_canonical_restore'
$databaseUser = 'nq_restore_drill'
$databasePassword = [Guid]::NewGuid().ToString('N')
$source = "nq-canonical-source-$runId"
$target = "nq-canonical-target-$runId"
$failure = "nq-canonical-failure-$runId"
$evidence = if ([string]::IsNullOrWhiteSpace($EvidenceRoot)) {
    Join-Path $repo "artifacts/phase5b-current-schema-restore-$runId"
} else { [IO.Path]::GetFullPath($EvidenceRoot) }
$dumpPath = Join-Path $evidence 'current-schema.dump'
$metadataPath = Join-Path $evidence 'current-schema.metadata.json'
$negativeRoot = Join-Path $evidence 'negative'
$launcherRoot = Join-Path $evidence 'launcher'
$classpathFile = Join-Path $evidence 'runtime-classpath.txt'
$nativeClusterRoot = Join-Path ([IO.Path]::GetTempPath()) "nq-canonical-pg-$runId"
$nativeClusterStarted = $false
$nativePort = 0
$wslClusterRoot = "/tmp/nq-canonical-pg-$runId"
$wslBinRoot = "$WslPostgresqlRoot/usr/lib/postgresql/16/bin"
$wslLibraryRoot = "$WslPostgresqlRoot/usr/lib/x86_64-linux-gnu"

function Invoke-WslPg([string]$Tool, [string[]]$Arguments, [switch]$AllowFailure) {
    $lines = @(& wsl.exe -d Ubuntu -- env "LD_LIBRARY_PATH=$wslLibraryRoot" "$wslBinRoot/$Tool" @Arguments 2>&1)
    $code = [int]$LASTEXITCODE
    if (-not $AllowFailure -and $code -ne 0) {
        $detail = (@($lines | Select-Object -Last 12) -join ' | ')
        throw "FAIL / WSL_PG16_$($Tool.ToUpperInvariant())_FAILED / $detail"
    }
    return [pscustomobject]@{ ExitCode=$code; Lines=$lines }
}

function Convert-ToWslPath([string]$Path) {
    $full=[IO.Path]::GetFullPath($Path)
    if($full-cnotmatch '^([A-Za-z]):\\(.+)$'){throw 'BLOCKED / WSL_PATH_INVALID'}
    '/mnt/'+$Matches[1].ToLowerInvariant()+'/'+$Matches[2].Replace('\','/')
}

function Get-NativeDatabaseName([string]$Name) {
    Assert-ContainerName $Name
    $kind = if ($Name.Contains('-source-')) { 'source' } elseif ($Name.Contains('-target-')) { 'target' } else { 'failure' }
    return "nq_${kind}_$($runId.Substring(0, 16))"
}

function Start-NativeCluster {
    if ($ExecutionMode -notin @('Native','WslPg16')) { return }
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    try {
        $listener.Start()
        $script:nativePort = ([Net.IPEndPoint]$listener.LocalEndpoint).Port
    } finally { $listener.Stop() }
    if ($ExecutionMode -ceq 'WslPg16') {
        $null = Invoke-WslPg 'initdb' @('--pgdata',$wslClusterRoot,'--username',$databaseUser,'--auth=trust','--encoding=UTF8','--no-locale')
        $startResult = Invoke-WslPg 'pg_ctl' @('--pgdata',$wslClusterRoot,'--log',"$wslClusterRoot/postgres.log",'--options',"-p $nativePort -h 127.0.0.1 -k $wslClusterRoot",'--wait','start') -AllowFailure
        if($startResult.ExitCode-ne0){
            $log=@(& wsl.exe -d Ubuntu -- tail -n 20 "$wslClusterRoot/postgres.log" 2>&1)
            throw ('FAIL / WSL_PG16_PG_CTL_FAILED / '+($log-join' | '))
        }
        $script:nativeClusterStarted = $true
        return
    }
    $lines = @(& initdb --pgdata $nativeClusterRoot --username $databaseUser --auth=trust --encoding=UTF8 --no-locale 2>&1)
    if ($LASTEXITCODE -ne 0) { throw 'FAIL / DISPOSABLE_NATIVE_INITDB_FAILED' }
    $nativeLog = Join-Path $nativeClusterRoot 'postgres-disposable.log'
    $pgCtlOutput = Join-Path $nativeClusterRoot 'pg-ctl-start.out'
    $pgCtlError = Join-Path $nativeClusterRoot 'pg-ctl-start.err'
    $pgCtl = (Get-Command pg_ctl -ErrorAction Stop).Source
    $process = Start-Process -FilePath $pgCtl -ArgumentList @(
        '--pgdata', $nativeClusterRoot, '--log', $nativeLog,
        '--options', "`"-p $nativePort -h 127.0.0.1`"", '--wait', 'start'
    ) -RedirectStandardOutput $pgCtlOutput -RedirectStandardError $pgCtlError `
        -WindowStyle Hidden -PassThru
    $process.WaitForExit()
    if ($process.ExitCode -ne 0) { throw 'FAIL / DISPOSABLE_NATIVE_POSTGRES_START_FAILED' }
    $script:nativeClusterStarted = $true
}

function Stop-NativeCluster {
    if ($ExecutionMode -notin @('Native','WslPg16')) { return }
    if ($ExecutionMode -ceq 'WslPg16') {
        if ($nativeClusterStarted) { $null=Invoke-WslPg 'pg_ctl' @('--pgdata',$wslClusterRoot,'--mode','immediate','--wait','stop') -AllowFailure; $script:nativeClusterStarted=$false }
        if ($wslClusterRoot -cnotmatch '^/tmp/nq-canonical-pg-[0-9a-f]{32}$') { throw 'BLOCKED / WSL_CLUSTER_CLEANUP_PATH_INVALID' }
        & wsl.exe -d Ubuntu -- rm -rf -- $wslClusterRoot
        if ($LASTEXITCODE -ne 0) { throw 'FAIL / WSL_CLUSTER_CLEANUP_FAILED' }
        return
    }
    if ($nativeClusterStarted -and (Test-Path -LiteralPath $nativeClusterRoot -PathType Container)) {
        & pg_ctl --pgdata $nativeClusterRoot --mode immediate --wait stop *> $null
        $script:nativeClusterStarted = $false
    }
    if (Test-Path -LiteralPath $nativeClusterRoot -PathType Container) {
        $resolved = [IO.Path]::GetFullPath($nativeClusterRoot)
        $prefix = Join-Path ([IO.Path]::GetFullPath([IO.Path]::GetTempPath())) 'nq-canonical-pg-'
        if (-not $resolved.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase) -or
                (Split-Path -Leaf $resolved) -cnotmatch '^nq-canonical-pg-[0-9a-f]{32}$') {
            throw 'BLOCKED / NATIVE_CLUSTER_CLEANUP_PATH_INVALID'
        }
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}

function Assert-ContainerName([string]$Name) {
    if ($Name -cnotmatch '^nq-canonical-(source|target|failure)-[0-9a-f]{32}$') {
        throw 'BLOCKED / NON_DISPOSABLE_CONTAINER_NAME'
    }
}

function Invoke-Docker([string[]]$Arguments, [switch]$AllowFailure) {
    $lines = @(& docker @Arguments 2>&1)
    $code = [int]$LASTEXITCODE
    if (-not $AllowFailure -and $code -ne 0) { throw 'FAIL / DISPOSABLE_DOCKER_COMMAND_FAILED' }
    return [pscustomobject]@{ ExitCode = $code; Lines = $lines }
}

function Start-Postgres([string]$Name) {
    Assert-ContainerName $Name
    if ($ExecutionMode -in @('Native','WslPg16')) {
        $db = Get-NativeDatabaseName $Name
        if ($ExecutionMode -ceq 'WslPg16') { $null=Invoke-WslPg 'createdb' @('--host','127.0.0.1','--port',[string]$nativePort,'--username',$databaseUser,$db); return }
        $lines = @(& createdb --host 127.0.0.1 --port $nativePort --username $databaseUser $db 2>&1)
        if ($LASTEXITCODE -ne 0) { throw 'FAIL / DISPOSABLE_NATIVE_DATABASE_CREATE_FAILED' }
        return
    }
    $result = Invoke-Docker @('run', '--detach', '--name', $Name,
        '--env', "POSTGRES_DB=$database", '--env', "POSTGRES_USER=$databaseUser",
        '--env', "POSTGRES_PASSWORD=$databasePassword", '--publish', '127.0.0.1::5432', $PostgresImage)
    if ($result.Lines.Count -eq 0) { throw 'FAIL / DISPOSABLE_POSTGRES_START_FAILED' }
    for ($attempt = 1; $attempt -le 60; $attempt++) {
        $ready = Invoke-Docker @('exec', $Name, 'pg_isready', '--username', $databaseUser, '--dbname', $database) -AllowFailure
        if ($ready.ExitCode -eq 0) { return }
        Start-Sleep -Seconds 1
    }
    throw 'FAIL / DISPOSABLE_POSTGRES_NOT_READY'
}

function Stop-Postgres([string]$Name) {
    Assert-ContainerName $Name
    if ($ExecutionMode -in @('Native','WslPg16')) {
        if ($nativeClusterStarted) {
            $db = Get-NativeDatabaseName $Name
            if ($ExecutionMode -ceq 'WslPg16') { $null=Invoke-WslPg 'dropdb' @('--host','127.0.0.1','--port',[string]$nativePort,'--username',$databaseUser,'--if-exists','--force',$db) -AllowFailure; return }
            & dropdb --host 127.0.0.1 --port $nativePort --username $databaseUser `
                --if-exists --force $db *> $null
        }
        return
    }
    $listed = Invoke-Docker @('ps', '--all', '--quiet', '--filter', "name=^/$Name$") -AllowFailure
    if ($listed.ExitCode -eq 0 -and -not [string]::IsNullOrWhiteSpace(($listed.Lines -join '').Trim())) {
        $null = Invoke-Docker @('rm', '--force', $Name)
    }
}

function Get-Port([string]$Name) {
    if ($ExecutionMode -in @('Native','WslPg16')) { return [string]$nativePort }
    $result = Invoke-Docker @('port', $Name, '5432/tcp')
    $line = ($result.Lines -join '').Trim()
    if ($line -cnotmatch '^127\.0\.0\.1:([0-9]+)$') { throw 'BLOCKED / NON_LOOPBACK_POSTGRES_MAPPING' }
    return $Matches[1]
}

function Invoke-Scalar([string]$Name, [string]$Sql) {
    if ($ExecutionMode -in @('Native','WslPg16')) {
        $db = Get-NativeDatabaseName $Name
        if ($ExecutionMode -ceq 'WslPg16') {
            $result=Invoke-WslPg 'psql' @('--host','127.0.0.1','--port',[string]$nativePort,'--username',$databaseUser,'--dbname',$db,'--no-psqlrc','--set','ON_ERROR_STOP=1','--tuples-only','--no-align','--command',$Sql)
            return ($result.Lines -join "`n").Trim()
        }
        $lines = @(& psql --host 127.0.0.1 --port $nativePort --username $databaseUser `
            --dbname $db --no-psqlrc --set ON_ERROR_STOP=1 --tuples-only --no-align --command $Sql 2>&1)
        if ($LASTEXITCODE -ne 0) { throw 'FAIL / DISPOSABLE_NATIVE_SQL_FAILED' }
        return ($lines -join "`n").Trim()
    }
    $result = Invoke-Docker @('exec', '--env', "PGPASSWORD=$databasePassword", $Name,
        'psql', '--username', $databaseUser, '--dbname', $database, '--no-psqlrc',
        '--set', 'ON_ERROR_STOP=1', '--tuples-only', '--no-align', '--command', $Sql)
    return ($result.Lines -join "`n").Trim()
}

function Invoke-Flyway([string]$Action, [string]$Name, [switch]$AllowFailure) {
    $port = Get-Port $Name
    $dependencies = [IO.File]::ReadAllText($classpathFile, [Text.Encoding]::UTF8).Trim()
    $classpath = $launcherRoot + [IO.Path]::PathSeparator +
        (Join-Path $repo 'backend/nq-app/target/classes') + [IO.Path]::PathSeparator +
        (Join-Path $repo 'backend/nq-infra/target/classes') + [IO.Path]::PathSeparator + $dependencies
    $location = 'filesystem:' + (Join-Path $repo 'backend/nq-infra/src/main/resources/db/migration')
    $databaseName = if ($ExecutionMode -in @('Native','WslPg16')) { Get-NativeDatabaseName $Name } else { $database }
    $lines = @(& java -cp $classpath nqcanonical.NqCanonicalFlywayLauncher $Action `
        "jdbc:postgresql://127.0.0.1:$port/$databaseName" $databaseUser $databasePassword `
        $location $migration.targetVersion 2>&1)
    $code = [int]$LASTEXITCODE
    if (-not $AllowFailure -and $code -ne 0) { throw "FAIL / FLYWAY_$($Action.ToUpperInvariant())_FAILED" }
    return [pscustomobject]@{ ExitCode = $code; Lines = $lines }
}

function Get-Canary([string]$Name) {
    return Invoke-Scalar $Name @"
SELECT concat_ws('|',
  (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE'),
  (SELECT COUNT(*) FROM pg_constraint c JOIN pg_namespace n ON n.oid=c.connamespace WHERE n.nspname='public'),
  (SELECT COUNT(*) FROM pg_indexes WHERE schemaname='public'),
  (SELECT COUNT(*) FROM flyway_schema_history WHERE success),
  (SELECT 'V' || version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1),
  (SELECT COUNT(*) FROM roles WHERE role_code='NQ_CANONICAL_RESTORE_CANARY'));
"@
}

function Invoke-Smoke([string]$Name) {
    $databaseName = if ($ExecutionMode -in @('Native','WslPg16')) { Get-NativeDatabaseName $Name } else { $database }
    $url = "jdbc:postgresql://127.0.0.1:$(Get-Port $Name)/$databaseName"
    $commands = [Collections.Generic.List[object]]::new()
    $commands.Add([string[]]@('-f', (Join-Path $repo 'backend/pom.xml'), '-pl', 'nq-infra', '-am', 'test',
            '-Dtest=JdbcRepositoryPostgresSmokeTest', '-Dsurefire.failIfNoSpecifiedTests=false',
            '-Dnq.postgres.smoke.required=true', "-Dnq.postgres.smoke.url=$url",
            "-Dnq.postgres.smoke.user=$databaseUser", "-Dnq.postgres.smoke.password=$databasePassword"))
    $commands.Add([string[]]@('-f', (Join-Path $repo 'backend/pom.xml'), '-pl', 'nq-app', '-am', 'test',
            '-Dtest=NqAppContextPostgresSmokeTest', '-Dsurefire.failIfNoSpecifiedTests=false',
            '-Dnq.app.context.smoke.required=true', "-Dnq.app.context.smoke.url=$url",
            "-Dnq.app.context.smoke.user=$databaseUser", "-Dnq.app.context.smoke.password=$databasePassword"))
    foreach ($arguments in $commands) {
        $lines = @(& mvn @arguments 2>&1)
        if ($LASTEXITCODE -ne 0) {
            $safe = (($lines -join "`n").Replace($databasePassword, '<redacted>') -split "`n" | Select-Object -Last 40) -join "`n"
            throw "FAIL / RESTORED_DATABASE_SMOKE_FAILED`n$safe"
        }
    }
}

function Expect-Rejected([scriptblock]$Action, [string]$Name) {
    try { & $Action; throw "NEGATIVE_CASE_ACCEPTED / $Name" } catch {
        if ($_.Exception.Message -like 'NEGATIVE_CASE_ACCEPTED*') { throw }
    }
    return $Name
}

foreach ($name in @($source, $target, $failure)) { Stop-Postgres $name }
try {
    Write-Output 'STAGE / PREPARE_DISPOSABLE_RUNTIME'
    [IO.Directory]::CreateDirectory($negativeRoot) | Out-Null
    [IO.Directory]::CreateDirectory($launcherRoot) | Out-Null
    if ($ExecutionMode -ceq 'Docker') {
        $null = Invoke-Docker @('image', 'inspect', $PostgresImage)
    } else {
        Start-NativeCluster
    }

    Write-Output 'STAGE / PREPARE_FLYWAY_CLASSPATH'
    $mavenLines = @(& mvn -f (Join-Path $repo 'backend/pom.xml') -DskipTests install 2>&1)
    if ($LASTEXITCODE -ne 0) { throw 'FAIL / RESTORE_DRILL_BACKEND_BUILD_FAILED' }
    $mavenLines = @(& mvn -f (Join-Path $repo 'backend/nq-app/pom.xml') -DskipTests `
        dependency:build-classpath "-Dmdep.outputFile=$classpathFile" 2>&1)
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $classpathFile -PathType Leaf)) {
        throw 'FAIL / RESTORE_DRILL_CLASSPATH_FAILED'
    }
    & javac -cp ([IO.File]::ReadAllText($classpathFile).Trim()) -d $launcherRoot `
        (Join-Path $PSScriptRoot 'NqCanonicalFlywayLauncher.java')
    if ($LASTEXITCODE -ne 0) { throw 'FAIL / RESTORE_DRILL_LAUNCHER_COMPILE_FAILED' }

    Write-Output 'STAGE / MIGRATE_SOURCE_TO_CURRENT'
    Start-Postgres $source
    $serverVersionNumber = Invoke-Scalar $source 'SHOW server_version_num;'
    if ($serverVersionNumber -cnotmatch '^([0-9]+)$') { throw 'BLOCKED / POSTGRESQL_VERSION_OBSERVATION_INVALID' }
    $serverMajor = [Math]::Floor([int64]$Matches[1] / 10000)
    if ($ExecutionMode -ceq 'Docker') {
        $dumpToolIdentity = ((Invoke-Docker @('exec',$source,'pg_dump','--version')).Lines -join ' ').Trim()
        $restoreToolIdentity = ((Invoke-Docker @('exec',$source,'pg_restore','--version')).Lines -join ' ').Trim()
    } elseif ($ExecutionMode -ceq 'WslPg16') {
        $dumpToolIdentity = ((Invoke-WslPg 'pg_dump' @('--version')).Lines -join ' ').Trim()
        $restoreToolIdentity = ((Invoke-WslPg 'pg_restore' @('--version')).Lines -join ' ').Trim()
    } else {
        $dumpToolIdentity = ((& pg_dump --version) -join ' ').Trim()
        $restoreToolIdentity = ((& pg_restore --version) -join ' ').Trim()
    }
    if ($dumpToolIdentity -cnotmatch 'PostgreSQL\) ([0-9]+)\.' -or [int]$Matches[1] -ne 16) { throw 'BLOCKED / UNSUPPORTED_POSTGRESQL_MAJOR' }
    $backupToolMajor = [int]$Matches[1]
    if ($restoreToolIdentity -cnotmatch 'PostgreSQL\) ([0-9]+)\.' -or [int]$Matches[1] -ne 16 -or $serverMajor -ne 16) { throw 'BLOCKED / UNSUPPORTED_POSTGRESQL_MAJOR' }
    $restoreToolMajor = [int]$Matches[1]
    $null = Invoke-Flyway 'migrate' $source
    $actualTarget = Invoke-Scalar $source "SELECT 'V' || version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1;"
    if ($actualTarget -cne [string]$migration.targetVersion) { throw 'FAIL / ACTUAL_SCHEMA_TARGET_MISMATCH' }
    $null = Invoke-Scalar $source @"
INSERT INTO roles(role_code, description)
VALUES ('NQ_CANONICAL_RESTORE_CANARY', 'non-secret disposable restore proof')
ON CONFLICT (role_code) DO UPDATE SET description=EXCLUDED.description;
"@
    $sourceCanary = Get-Canary $source

    Write-Output 'STAGE / CREATE_AND_VERIFY_BACKUP'
    if ($ExecutionMode -ceq 'Native') {
        $sourceDatabase = Get-NativeDatabaseName $source
        $lines = @(& pg_dump --host 127.0.0.1 --port $nativePort --username $databaseUser `
            --dbname $sourceDatabase --format custom --no-owner --no-privileges --file $dumpPath 2>&1)
        if ($LASTEXITCODE -ne 0) { throw 'FAIL / DISPOSABLE_NATIVE_BACKUP_FAILED' }
        $toolIdentity = ((& pg_dump --version) -join ' ').Trim()
    } elseif ($ExecutionMode -ceq 'WslPg16') {
        $sourceDatabase = Get-NativeDatabaseName $source
        $wslDumpPath = Convert-ToWslPath $dumpPath
        $null = Invoke-WslPg 'pg_dump' @('--host','127.0.0.1','--port',[string]$nativePort,'--username',$databaseUser,
            '--dbname',$sourceDatabase,'--format','custom','--no-owner','--no-privileges','--file',$wslDumpPath)
        $toolIdentity = $dumpToolIdentity
    } else {
        $null = Invoke-Docker @('exec', '--env', "PGPASSWORD=$databasePassword", $source,
            'pg_dump', '--username', $databaseUser, '--dbname', $database, '--format', 'custom',
            '--no-owner', '--no-privileges', '--file', '/tmp/current-schema.dump')
        $null = Invoke-Docker @('cp', "${source}:/tmp/current-schema.dump", $dumpPath)
        $toolIdentity = ((Invoke-Docker @('exec', $source, 'pg_dump', '--version')).Lines -join ' ').Trim()
    }
    $null = Write-NqCanonicalBackupMetadata $dumpPath $metadataPath $migration.targetVersion `
        "disposable:${runId}:source" $toolIdentity $serverMajor $backupToolMajor $restoreToolMajor $commit
    $backup = Test-NqCanonicalBackup $dumpPath $metadataPath $migration.targetVersion $commit 16

    Write-Output 'STAGE / RESTORE_AND_VALIDATE_CURRENT_SCHEMA'
    Start-Postgres $target
    if ($ExecutionMode -ceq 'Native') {
        $targetDatabase = Get-NativeDatabaseName $target
        $lines = @(& pg_restore --host 127.0.0.1 --port $nativePort --username $databaseUser `
            --dbname $targetDatabase --no-owner --no-privileges --exit-on-error $dumpPath 2>&1)
        if ($LASTEXITCODE -ne 0) { throw 'FAIL / DISPOSABLE_NATIVE_RESTORE_FAILED' }
    } elseif ($ExecutionMode -ceq 'WslPg16') {
        $targetDatabase = Get-NativeDatabaseName $target
        $wslDumpPath = Convert-ToWslPath $dumpPath
        $null = Invoke-WslPg 'pg_restore' @('--host','127.0.0.1','--port',[string]$nativePort,'--username',$databaseUser,
            '--dbname',$targetDatabase,'--no-owner','--no-privileges','--exit-on-error',$wslDumpPath)
    } else {
        $null = Invoke-Docker @('cp', $dumpPath, "${target}:/tmp/current-schema.dump")
        $null = Invoke-Docker @('exec', '--env', "PGPASSWORD=$databasePassword", $target,
            'pg_restore', '--username', $databaseUser, '--dbname', $database, '--no-owner',
            '--no-privileges', '--exit-on-error', '/tmp/current-schema.dump')
    }
    $null = Invoke-Flyway 'validate' $target
    $targetCanary = Get-Canary $target
    if ($targetCanary -cne $sourceCanary) { throw 'FAIL / POST_RESTORE_SCHEMA_OR_DATA_CANARY_MISMATCH' }
    Write-Output 'STAGE / RUN_REPOSITORY_AND_APP_SMOKE'
    Invoke-Smoke $target

    Write-Output 'STAGE / RUN_BACKUP_AND_RESTORE_NEGATIVE_CASES'
    $negativeCases = [Collections.Generic.List[string]]::new()
    $tampered = Join-Path $negativeRoot 'tampered.dump'
    [IO.File]::Copy($dumpPath, $tampered)
    $stream = [IO.File]::Open($tampered, [IO.FileMode]::Append, [IO.FileAccess]::Write)
    try { $stream.WriteByte(0) } finally { $stream.Dispose() }
    $negativeCases.Add((Expect-Rejected { Test-NqCanonicalBackup $tampered $metadataPath $migration.targetVersion $commit 16 } 'tampered-backup'))

    $truncated = Join-Path $negativeRoot 'truncated.dump'
    $bytes = [IO.File]::ReadAllBytes($dumpPath)
    [IO.File]::WriteAllBytes($truncated, $bytes[0..([Math]::Max(0, [Math]::Floor($bytes.Length / 3)))])
    $negativeCases.Add((Expect-Rejected { Test-NqCanonicalBackup $truncated $metadataPath $migration.targetVersion $commit 16 } 'truncated-backup'))

    $wrongMetadata = Join-Path $negativeRoot 'wrong-schema.metadata.json'
    $wrong = Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json
    $wrong.sourceSchemaVersion = 'V999999'
    [IO.File]::WriteAllText($wrongMetadata, ($wrong | ConvertTo-Json -Depth 8 -Compress), [Text.UTF8Encoding]::new($false))
    $negativeCases.Add((Expect-Rejected { Test-NqCanonicalBackup $dumpPath $wrongMetadata $migration.targetVersion $commit 16 } 'wrong-schema-target'))

    $wrongMajorMetadata = Join-Path $negativeRoot 'wrong-major.metadata.json'
    $wrongMajor = Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json
    $wrongMajor.postgresqlServerMajor = 17
    $wrongMajor.backupToolMajor = 17
    $wrongMajor.restoreToolMajor = 17
    [IO.File]::WriteAllText($wrongMajorMetadata, ($wrongMajor | ConvertTo-Json -Depth 8 -Compress), [Text.UTF8Encoding]::new($false))
    $negativeCases.Add((Expect-Rejected { Test-NqCanonicalBackup $dumpPath $wrongMajorMetadata $migration.targetVersion $commit 16 } 'wrong-postgresql-major'))

    Start-Postgres $failure
    if ($ExecutionMode -ceq 'Native') {
        $failureDatabase = Get-NativeDatabaseName $failure
        $failedLines = @(& pg_restore --host 127.0.0.1 --port $nativePort --username $databaseUser `
            --dbname $failureDatabase --no-owner --no-privileges --exit-on-error $truncated 2>&1)
        $failedRestoreCode = [int]$LASTEXITCODE
    } elseif ($ExecutionMode -ceq 'WslPg16') {
        $failureDatabase = Get-NativeDatabaseName $failure
        $wslTruncated = Convert-ToWslPath $truncated
        $failedRestore = Invoke-WslPg 'pg_restore' @('--host','127.0.0.1','--port',[string]$nativePort,'--username',$databaseUser,
            '--dbname',$failureDatabase,'--no-owner','--no-privileges','--exit-on-error',$wslTruncated) -AllowFailure
        $failedRestoreCode = $failedRestore.ExitCode
    } else {
        $null = Invoke-Docker @('cp', $truncated, "${failure}:/tmp/truncated.dump")
        $failedRestore = Invoke-Docker @('exec', '--env', "PGPASSWORD=$databasePassword", $failure,
            'pg_restore', '--username', $databaseUser, '--dbname', $database, '--no-owner',
            '--no-privileges', '--exit-on-error', '/tmp/truncated.dump') -AllowFailure
        $failedRestoreCode = $failedRestore.ExitCode
    }
    if ($failedRestoreCode -eq 0) { throw 'NEGATIVE_CASE_ACCEPTED / restore-command-failure' }
    $negativeCases.Add('restore-command-failure')

    $null = Invoke-Scalar $target "DELETE FROM roles WHERE role_code='NQ_CANONICAL_RESTORE_CANARY';"
    $negativeCases.Add((Expect-Rejected { if ((Get-Canary $target) -cne $sourceCanary) { throw 'REJECTED' } } 'post-restore-validation-mismatch'))
    $null = Invoke-Scalar $target @"
INSERT INTO roles(role_code, description)
VALUES ('NQ_CANONICAL_RESTORE_CANARY', 'non-secret disposable restore proof');
"@
    $null = Invoke-Scalar $target 'DROP TABLE flyway_schema_history;'
    $missingHistory = Invoke-Flyway 'validate' $target -AllowFailure
    if ($missingHistory.ExitCode -eq 0) { throw 'NEGATIVE_CASE_ACCEPTED / missing-flyway-history' }
    $negativeCases.Add('missing-flyway-history')

    $receipt = [pscustomobject][ordered]@{
        schemaVersion = 'nq-current-schema-restore-proof.v1'
        decision = 'PASS / CURRENT_SCHEMA_BACKUP_RESTORE_PROVEN'
        sourceCommit = $commit
        actualLatestMigration = $migration.targetVersion
        migrationCount = @($migration.migrations).Count
        migrationInventorySha256 = $migration.inventorySha256
        backupSha256 = $backup.backupSha256
        backupSize = $backup.backupSize
        backupFormat = 'POSTGRESQL_CUSTOM'
        backupToolIdentity = $backup.toolIdentity
        restoreToolIdentity = $restoreToolIdentity
        postgresqlServerMajor = $serverMajor
        backupToolMajor = $backupToolMajor
        restoreToolMajor = $restoreToolMajor
        postgresqlServerVersionNumber = $serverVersionNumber
        sourceDatabaseIdentity = $backup.databaseIdentity
        sourceCanary = $sourceCanary
        restoredCanary = $targetCanary
        flywayValidate = 'PASS'
        pendingMigrations = 0
        repositorySmoke = 'PASS'
        applicationContextSmoke = 'PASS'
        negativeCases = @($negativeCases)
        productionAccess = 'NONE'
        createdAt = [DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ')
    }
    $receiptPath = Join-Path $evidence 'restore-proof.json'
    [IO.File]::WriteAllText($receiptPath, ($receipt | ConvertTo-Json -Depth 12), [Text.UTF8Encoding]::new($false))
    $receipt
} finally {
    Write-Output 'STAGE / CLEANUP_DISPOSABLE_RUNTIME'
    foreach ($name in @($failure, $target, $source)) { Stop-Postgres $name }
    Stop-NativeCluster
}
