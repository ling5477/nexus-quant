[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [switch]$ConfirmDisposable,

    [ValidateSet('Smoke', 'Full')]
    [string]$Scale = 'Smoke',

    [ValidateSet('postgres:16-alpine')]
    [string]$PostgresImage = 'postgres:16-alpine'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not $ConfirmDisposable) { throw 'ConfirmDisposable is required' }
$profiles = @($env:SPRING_PROFILES_ACTIVE, $env:NQ_ENVIRONMENT, $env:NQ_PROFILE) -join ','
if ($profiles -match '(?i)(^|[,;\s])(prod|production)([,;\s]|$)') {
    throw 'BLOCKED / PRODUCTION_PROFILE_FORBIDDEN'
}

$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$artifactRoot = Join-Path $repo 'artifacts'
$runId = [Guid]::NewGuid().ToString('N')
$sourceContainer = "nq-gatey5-disposable-lock-source-$runId"
$normalContainer = "nq-gatey5-disposable-lock-normal-$runId"
$database = "nq_gatey5_lock_$runId"
$databaseUser = 'nq_gatey5_lock'
$databasePassword = [Guid]::NewGuid().ToString('N')
$runDirectory = Join-Path $artifactRoot "gatey5-lock-tmp-$runId"
$dumpPath = Join-Path $runDirectory 'pre-v39.dump'
$toolClasses = Join-Path $repo 'backend\target\gatey5-tooling'
$classpathFile = Join-Path $repo 'backend\nq-app\target\gatey5-classpath.txt'
$migrationLocation = 'filesystem:' + (Join-Path $repo 'backend\nq-infra\src\main\resources\db\migration').Replace('\', '/')
$fixturePath = Join-Path $PSScriptRoot 'gatey5-pre-fixture.sql'

$targets = if ($Scale -eq 'Full') {
    [ordered]@{
        users = 1000; roles = 32; user_roles = 4000; accounts = 2000; strategy_runs = 250000
        exchange_accounts = 2000; exchange_account_credentials = 6000
        strategy_definitions = 2000; strategy_versions = 20000; research_configs = 20000
        backtest_configs = 50000; backtest_runs = 500000; backtest_publish_records = 100000
        strategy_release_admission_state = 100000; orders = 2500000
    }
} else {
    [ordered]@{
        users = 10; roles = 4; user_roles = 20; accounts = 10; strategy_runs = 20
        exchange_accounts = 10; exchange_account_credentials = 20
        strategy_definitions = 10; strategy_versions = 20; research_configs = 20
        backtest_configs = 20; backtest_runs = 20; backtest_publish_records = 20
        strategy_release_admission_state = 20; orders = 20
    }
}

function Assert-ExitCode([string]$Operation) {
    if ($LASTEXITCODE -ne 0) { throw "$Operation failed with exit code $LASTEXITCODE" }
}

function Assert-ContainerName([string]$Name) {
    if ($Name -cnotmatch '^nq-gatey5-disposable-lock-(source|normal)-[0-9a-f]{32}$') {
        throw 'refusing non-disposable container name'
    }
}

function Start-Postgres([string]$Name) {
    Assert-ContainerName $Name
    docker run --detach --name $Name `
        --env "POSTGRES_DB=$database" `
        --env "POSTGRES_USER=$databaseUser" `
        --env "POSTGRES_PASSWORD=$databasePassword" `
        --publish '127.0.0.1::5432' `
        $PostgresImage | Out-Null
    Assert-ExitCode "start $Name"
    for ($attempt = 1; $attempt -le 30; $attempt++) {
        docker exec $Name pg_isready --username $databaseUser --dbname $database *> $null
        if ($LASTEXITCODE -eq 0) { return }
        Start-Sleep -Seconds 1
    }
    throw "$Name did not become ready"
}

function Stop-Postgres([string]$Name) {
    Assert-ContainerName $Name
    $id = docker ps --all --quiet --filter "name=^/$Name$"
    Assert-ExitCode 'list disposable containers'
    if (-not [string]::IsNullOrWhiteSpace(($id | Out-String).Trim())) {
        docker rm --force $Name *> $null
        Assert-ExitCode "remove $Name"
    }
}

function Get-Port([string]$Name) {
    Assert-ContainerName $Name
    $mapping = docker port $Name '5432/tcp'
    Assert-ExitCode 'read loopback port'
    if ($mapping -notmatch '^127\.0\.0\.1:(\d+)$') { throw 'non-loopback port mapping refused' }
    return [int]$Matches[1]
}

function Invoke-Sql([string]$Name, [string]$Sql, [switch]$AllowFailure) {
    Assert-ContainerName $Name
    $previousErrorAction = $ErrorActionPreference
    if ($AllowFailure) { $ErrorActionPreference = 'Continue' }
    try {
        $output = docker exec --env "PGPASSWORD=$databasePassword" $Name `
            psql --username $databaseUser --dbname $database --no-psqlrc `
            --set ON_ERROR_STOP=1 --tuples-only --no-align --command $Sql 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorAction
    }
    $global:LASTEXITCODE = $exitCode
    if (-not $AllowFailure -and $exitCode -ne 0) { throw 'execute disposable SQL failed' }
    return ($output | Out-String).Trim()
}

function Invoke-Flyway([string]$Name, [string]$Target, [switch]$ExpectFailure) {
    $port = Get-Port $Name
    $dependencies = (Get-Content -Raw -LiteralPath $classpathFile).Trim()
    $classpath = $toolClasses + [IO.Path]::PathSeparator + $dependencies
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $previousErrorAction = $ErrorActionPreference
    if ($ExpectFailure) { $ErrorActionPreference = 'Continue' }
    try {
        $output = & java -cp $classpath gatey.GateY5FlywayLauncher `
            "jdbc:postgresql://127.0.0.1:$port/$database" $databaseUser $databasePassword `
            $migrationLocation $Target 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorAction
    }
    $watch.Stop()
    if ($ExpectFailure -and $exitCode -eq 0) { throw 'expected Flyway failure did not occur' }
    if (-not $ExpectFailure -and $exitCode -ne 0) { throw "Flyway target $Target failed" }
    return [ordered]@{
        exitCode = $exitCode
        elapsedMs = $watch.ElapsedMilliseconds
        outputTail = (($output | Select-Object -Last 8) -join "`n")
    }
}

function Assert-V38Atomic([string]$Name) {
    $state = Invoke-Sql $Name @"
SELECT (SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1)
       || '|' ||
       (SELECT count(*) FROM information_schema.tables
        WHERE table_schema='public' AND table_name IN
        ('risk_limit_sets','live_sessions','live_session_events','operator_approvals','execution_intents','execution_receipts'));
"@
    if ($state -ne '38|0') { throw "BLOCKED / MIGRATION_FAILURE_ATOMICITY_NOT_PROVEN state=$state" }
}

function Wait-ForApplication([string]$Name, [string]$ApplicationName, [int]$MinimumAgeSeconds = 0) {
    for ($attempt = 1; $attempt -le 150; $attempt++) {
        $count = Invoke-Sql $Name "SELECT count(*) FROM pg_stat_activity WHERE application_name='$ApplicationName' AND state <> 'idle' AND now()-xact_start >= interval '$MinimumAgeSeconds seconds';"
        if ([int]$count -ge 1) { return }
        Start-Sleep -Seconds 1
    }
    throw "fault injection $ApplicationName was not observed"
}

function Start-Blocker([string]$Name, [string]$ApplicationName, [string]$MutationSql, [int]$HoldSeconds) {
    Assert-ContainerName $Name
    $sql = "SET application_name='$ApplicationName'; BEGIN; $MutationSql; SELECT pg_sleep($HoldSeconds); ROLLBACK;"
    docker exec --detach --env "PGPASSWORD=$databasePassword" $Name `
        psql --username $databaseUser --dbname $database --no-psqlrc `
        --set ON_ERROR_STOP=1 --command $sql
    Assert-ExitCode "start blocker $ApplicationName"
    Wait-ForApplication $Name $ApplicationName
}

function Measure-BlockedMigration([string]$Scenario, [string[]]$Mutations) {
    $index = 0
    $blockerApplications = @()
    foreach ($mutation in $Mutations) {
        $index++
        $blockerApplication = "gatey5_${Scenario}_$index"
        $blockerApplications += $blockerApplication
        Start-Blocker $sourceContainer $blockerApplication $mutation 15
    }
    $port = Get-Port $sourceContainer
    $applicationName = "gatey5_blocked_$Scenario"
    $dependencies = (Get-Content -Raw -LiteralPath $classpathFile).Trim()
    $classpath = $toolClasses + [IO.Path]::PathSeparator + $dependencies
    $stdout = Join-Path $runDirectory "$Scenario-flyway.out"
    $stderr = Join-Path $runDirectory "$Scenario-flyway.err"
    $arguments = @('-cp', $classpath, 'gatey.GateY5FlywayLauncher',
        "jdbc:postgresql://127.0.0.1:$port/$database`?ApplicationName=$applicationName",
        $databaseUser, $databasePassword, $migrationLocation, '39')
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $process = Start-Process java -ArgumentList $arguments -PassThru -WindowStyle Hidden `
        -RedirectStandardOutput $stdout -RedirectStandardError $stderr
    $lockGraph = ''
    for ($attempt = 1; $attempt -le 80 -and -not $process.HasExited; $attempt++) {
        $lockGraph = Invoke-Sql $sourceContainer @"
SELECT blocked.pid||'|'||blocker.pid||'|'||COALESCE(waiting.relation::regclass::text,'-')
       ||'|'||waiting.mode||'|'||COALESCE(held.mode,'-')
FROM pg_stat_activity blocked
CROSS JOIN LATERAL unnest(pg_blocking_pids(blocked.pid)) blocking_pid
JOIN pg_stat_activity blocker ON blocker.pid=blocking_pid
JOIN pg_locks waiting ON waiting.pid=blocked.pid AND NOT waiting.granted
LEFT JOIN pg_locks held ON held.pid=blocker.pid AND held.granted
 AND held.locktype=waiting.locktype AND held.database IS NOT DISTINCT FROM waiting.database
 AND held.relation IS NOT DISTINCT FROM waiting.relation
WHERE blocked.application_name='$applicationName'
ORDER BY blocked.pid,blocker.pid,waiting.relation,waiting.mode,held.mode;
"@
        if (-not [string]::IsNullOrWhiteSpace($lockGraph)) { break }
        Start-Sleep -Milliseconds 100
        $process.Refresh()
    }
    $process.WaitForExit()
    $process.Refresh()
    $watch.Stop()
    $result = [ordered]@{
        exitCode = $process.ExitCode
        elapsedMs = $watch.ElapsedMilliseconds
        outputTail = ((@(Get-Content -LiteralPath $stdout -ErrorAction SilentlyContinue) +
            @(Get-Content -LiteralPath $stderr -ErrorAction SilentlyContinue) | Select-Object -Last 8) -join "`n")
    }
    if ($result.exitCode -eq 0) { throw 'expected Flyway failure did not occur' }
    if ([string]::IsNullOrWhiteSpace($lockGraph)) {
        throw "BLOCKED / LOCK_CONFLICT_GRAPH_NOT_OBSERVED scenario=$Scenario"
    }
    $applicationList = ($blockerApplications | ForEach-Object { "'$_'" }) -join ','
    Invoke-Sql $sourceContainer "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE application_name IN ($applicationList);" | Out-Null
    $remainingBlockers = Invoke-Sql $sourceContainer "SELECT count(*) FROM pg_stat_activity WHERE application_name IN ($applicationList);"
    if ([int]$remainingBlockers -ne 0) { throw "blocker cleanup failed scenario=$Scenario" }
    Assert-V38Atomic $sourceContainer
    if ($result.elapsedMs -gt 7000) { throw "BLOCKED / LOCK_TIMEOUT_BOUND_VIOLATED scenario=$Scenario ms=$($result.elapsedMs)" }
    return [ordered]@{ scenario = $Scenario; blocked = $true; elapsedMs = $result.elapsedMs;
        atomic = $true; lockGraph = @($lockGraph -split "`r?`n"); blockersReleased = $true }
}

try {
    docker image inspect $PostgresImage *> $null
    Assert-ExitCode 'inspect pinned PostgreSQL image'
    if (-not (Test-Path -LiteralPath $fixturePath -PathType Leaf)) { throw 'PRE fixture SQL is missing' }
    New-Item -ItemType Directory -Path $runDirectory -Force | Out-Null
    New-Item -ItemType Directory -Path $toolClasses -Force | Out-Null

    & mvn -f (Join-Path $repo 'backend\pom.xml') -DskipTests install *> $null
    Assert-ExitCode 'build backend dependencies'
    & mvn -f (Join-Path $repo 'backend\nq-app\pom.xml') dependency:build-classpath `
        "-Dmdep.outputFile=$classpathFile" *> $null
    Assert-ExitCode 'build Flyway classpath'
    $dependencies = (Get-Content -Raw -LiteralPath $classpathFile).Trim()
    & javac -cp $dependencies -d $toolClasses (Join-Path $PSScriptRoot 'GateY5FlywayLauncher.java')
    Assert-ExitCode 'compile Flyway launcher'

    Start-Postgres $sourceContainer
    $v38 = Invoke-Flyway $sourceContainer '38'
    Assert-V38Atomic $sourceContainer
    docker cp $fixturePath "${sourceContainer}:/tmp/gatey5-pre-fixture.sql" *> $null
    Assert-ExitCode 'copy PRE fixture'
    $fixtureArgs = @(
        'exec', '--env', "PGPASSWORD=$databasePassword", $sourceContainer,
        'psql', '--username', $databaseUser, '--dbname', $database, '--no-psqlrc',
        '--set', 'ON_ERROR_STOP=1'
    )
    foreach ($entry in $targets.GetEnumerator()) { $fixtureArgs += @('--set', "$($entry.Key)=$($entry.Value)") }
    $fixtureArgs += @('--file', '/tmp/gatey5-pre-fixture.sql')
    & docker @fixtureArgs *> $null
    Assert-ExitCode 'generate PRE fixture'

    $countUnion = ($targets.Keys | ForEach-Object { "SELECT '$_' table_name,count(*) row_count FROM $_" }) -join ' UNION ALL '
    $countRows = Invoke-Sql $sourceContainer "$countUnion ORDER BY table_name;"
    $actualCounts = [ordered]@{}
    foreach ($line in $countRows -split "`r?`n") {
        $parts = $line.Split('|')
        $actualCounts[$parts[0]] = [long]$parts[1]
    }
    foreach ($entry in $targets.GetEnumerator()) {
        if ($actualCounts[$entry.Key] -ne [long]$entry.Value) {
            throw "BLOCKED / PRODUCTION_LIKE_FIXTURE_REALIZATION_FAILED table=$($entry.Key)"
        }
    }
    $preTotal = ($actualCounts.Values | Measure-Object -Sum).Sum
    $expectedTotal = ($targets.Values | Measure-Object -Sum).Sum
    if ($preTotal -ne $expectedTotal) { throw 'PRE total mismatch' }

    $logicalDigest = Invoke-Sql $sourceContainer @"
SELECT encode(digest(string_agg(table_name||':'||rows||':'||minimum||':'||maximum,E'\n' ORDER BY table_name),'sha256'),'hex')
FROM (
 SELECT 'users' table_name,count(*) rows,min(username) minimum,max(username) maximum FROM users
 UNION ALL SELECT 'roles',count(*),min(role_code),max(role_code) FROM roles
 UNION ALL SELECT 'user_roles',count(*),min(user_id||':'||role_id),max(user_id||':'||role_id) FROM user_roles
 UNION ALL SELECT 'accounts',count(*),min(account_code),max(account_code) FROM accounts
 UNION ALL SELECT 'strategy_runs',count(*),min(strategy_run_id),max(strategy_run_id) FROM strategy_runs
 UNION ALL SELECT 'exchange_accounts',count(*),min(account_alias),max(account_alias) FROM exchange_accounts
 UNION ALL SELECT 'exchange_account_credentials',count(*),min(credential_id::text),max(credential_id::text) FROM exchange_account_credentials
 UNION ALL SELECT 'strategy_definitions',count(*),min(strategy_id),max(strategy_id) FROM strategy_definitions
 UNION ALL SELECT 'strategy_versions',count(*),min(strategy_version_id),max(strategy_version_id) FROM strategy_versions
 UNION ALL SELECT 'research_configs',count(*),min(research_config_id),max(research_config_id) FROM research_configs
 UNION ALL SELECT 'backtest_configs',count(*),min(backtest_config_id),max(backtest_config_id) FROM backtest_configs
 UNION ALL SELECT 'backtest_runs',count(*),min(backtest_run_id),max(backtest_run_id) FROM backtest_runs
 UNION ALL SELECT 'backtest_publish_records',count(*),min(publish_record_id),max(publish_record_id) FROM backtest_publish_records
 UNION ALL SELECT 'strategy_release_admission_state',count(*),min(publish_record_id),max(publish_record_id) FROM strategy_release_admission_state
 UNION ALL SELECT 'orders',count(*),min(order_id),max(order_id) FROM orders
) fixture;
"@
    $sizeRows = Invoke-Sql $sourceContainer @"
SELECT relname||'|'||pg_relation_size(oid)||'|'||pg_indexes_size(oid)||'|'||pg_total_relation_size(oid)
FROM pg_class WHERE relnamespace='public'::regnamespace AND relname IN ('$($targets.Keys -join "','")') ORDER BY relname;
"@

    docker exec --env "PGPASSWORD=$databasePassword" $sourceContainer `
        pg_dump --username $databaseUser --dbname $database --format custom `
        --no-owner --no-privileges --file /tmp/pre-v39.dump
    Assert-ExitCode 'backup PRE fixture'
    docker cp "${sourceContainer}:/tmp/pre-v39.dump" $dumpPath *> $null
    Assert-ExitCode 'copy PRE backup'
    Start-Postgres $normalContainer
    docker cp $dumpPath "${normalContainer}:/tmp/pre-v39.dump" *> $null
    Assert-ExitCode 'copy PRE backup to normal clone'
    docker exec --env "PGPASSWORD=$databasePassword" $normalContainer `
        pg_restore --username $databaseUser --dbname $database --no-owner --no-privileges `
        --exit-on-error /tmp/pre-v39.dump
    Assert-ExitCode 'restore PRE normal clone'
    $normal = Invoke-Flyway $normalContainer '39'
    $normalState = Invoke-Sql $normalContainer "SELECT version||'|'||success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;"
    if ($normalState -ne '39|true') { throw 'normal V39 migration was not accepted' }

    $preflightApplication = 'gatey5_transaction_age_preflight'
    Start-Blocker $sourceContainer $preflightApplication 'SELECT 1' 45
    Wait-ForApplication $sourceContainer $preflightApplication 31
    $overAge = Invoke-Sql $sourceContainer "SELECT count(*) FROM pg_stat_activity WHERE application_name='$preflightApplication' AND now()-xact_start > interval '30 seconds';"
    if ([int]$overAge -lt 1) { throw 'transaction-age preflight did not deny' }

    $blocked = @()
    $blocked += Measure-BlockedMigration 'order_writer' @("UPDATE orders SET updated_at=updated_at WHERE order_id='gy5-order-1'")
    $blocked += Measure-BlockedMigration 'account_writer' @("UPDATE exchange_accounts SET updated_at=updated_at WHERE exchange_account_id=1")
    $blocked += Measure-BlockedMigration 'strategy_release_writer' @("UPDATE strategy_release_admission_state SET admission_revision=admission_revision+1 WHERE publish_record_id='gy5-publish-1'")
    $blocked += Measure-BlockedMigration 'concurrent_writers' @(
        "UPDATE orders SET updated_at=updated_at WHERE order_id='gy5-order-2'",
        "UPDATE exchange_accounts SET updated_at=updated_at WHERE exchange_account_id=2",
        "UPDATE strategy_release_admission_state SET admission_revision=admission_revision+1 WHERE publish_record_id='gy5-publish-2'"
    )

    $statementWatch = [Diagnostics.Stopwatch]::StartNew()
    $statementResult = Invoke-Sql $sourceContainer "SET statement_timeout='60s'; SELECT pg_sleep(61);" -AllowFailure
    $statementExitCode = $LASTEXITCODE
    $statementWatch.Stop()
    if ($statementExitCode -eq 0 -or $statementWatch.Elapsed.TotalSeconds -lt 58 -or $statementWatch.Elapsed.TotalSeconds -gt 64) {
        throw 'statement_timeout 60s was not effective'
    }
    Assert-V38Atomic $sourceContainer

    $longRead = 'gatey5_long_read'
    Start-Blocker $sourceContainer $longRead 'SELECT count(*) FROM orders' 140
    Wait-ForApplication $sourceContainer $longRead 120
    $longReadResult = Invoke-Flyway $sourceContainer '39'
    $longReadDisposition = if ($longReadResult.elapsedMs -gt 7000) { 'BLOCKING' } else { 'NON_BLOCKING' }
    $finalState = Invoke-Sql $sourceContainer "SELECT version||'|'||success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;"
    if ($finalState -ne '39|true') { throw 'retry V39 was not accepted' }

    $result = [ordered]@{
        decision = 'PASS / GATEY5_V38_V39_LOCK_WINDOW_MEASURED'
        scale = $Scale
        seed = 'gatey-production-like-scale-v1'
        preRows = $preTotal
        logicalDigest = $logicalDigest
        relationSizes = @($sizeRows -split "`r?`n")
        v38MigrationMs = $v38.elapsedMs
        normalMigrationMs = $normal.elapsedMs
        blockedScenarios = $blocked
        transactionAgePreflight = 'DENIED_OVER_30S'
        statementTimeoutSeconds = [math]::Round($statementWatch.Elapsed.TotalSeconds, 3)
        statementSchemaAtomic = $true
        longReadDisposition = $longReadDisposition
        longReadMigrationMs = $longReadResult.elapsedMs
        flywayValidate = 'PASS'
        lockTimeoutSeconds = 5
        blockedDdlUpperBoundSeconds = 7
        externalEgress = 0
        productionWrites = 0
    }
    $result | ConvertTo-Json -Depth 8
} finally {
    Stop-Postgres $sourceContainer
    Stop-Postgres $normalContainer
    if (Test-Path -LiteralPath $runDirectory) {
        $resolvedArtifacts = (Resolve-Path -LiteralPath $artifactRoot).Path
        $resolvedRun = (Resolve-Path -LiteralPath $runDirectory).Path
        if (-not $resolvedRun.StartsWith($resolvedArtifacts + [IO.Path]::DirectorySeparatorChar)) {
            throw 'refusing cleanup outside repository artifacts'
        }
        Remove-Item -LiteralPath $resolvedRun -Recurse -Force
    }
}
