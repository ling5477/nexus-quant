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
if ($profiles -match '(?i)(^|[,;\s])(prod|production)([,;\s]|$)') { throw 'production profile forbidden' }

$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$artifactRoot = Join-Path $repo 'artifacts'
$runId = [Guid]::NewGuid().ToString('N')
$source = "nq-gatey5-disposable-post-source-$runId"
$restore = "nq-gatey5-disposable-post-restore-$runId"
$database = "nq_gatey5_post_$runId"
$databaseUser = 'nq_gatey5_post'
$databasePassword = [Guid]::NewGuid().ToString('N')
$runDirectory = Join-Path $artifactRoot "gatey5-post-tmp-$runId"
$dumpPath = Join-Path $runDirectory 'post-v39.dump'
$toolClasses = Join-Path $repo 'backend\target\gatey5-tooling'
$classpathFile = Join-Path $repo 'backend\nq-app\target\gatey5-classpath.txt'
$migrationLocation = 'filesystem:' + (Join-Path $repo 'backend\nq-infra\src\main\resources\db\migration').Replace('\', '/')

$pre = if ($Scale -eq 'Full') {
    [ordered]@{ users=1000;roles=32;user_roles=4000;accounts=2000;strategy_runs=250000;exchange_accounts=2000;exchange_account_credentials=6000;strategy_definitions=2000;strategy_versions=20000;research_configs=20000;backtest_configs=50000;backtest_runs=500000;backtest_publish_records=100000;orders=2500000 }
} else {
    [ordered]@{ users=10;roles=4;user_roles=20;accounts=10;strategy_runs=20;exchange_accounts=10;exchange_account_credentials=20;strategy_definitions=10;strategy_versions=20;research_configs=20;backtest_configs=20;backtest_runs=20;backtest_publish_records=20;orders=20 }
}
$post = if ($Scale -eq 'Full') {
    [ordered]@{ users=1000;orders=2500000;exchange_accounts=2000;risk_limit_sets=1000;live_sessions=5000;live_session_events=150000;events_per_session=30;operator_approvals=15000;approvals_per_session=3;execution_intents=2000000;intents_per_session=400;execution_receipts=6000000;receipts_per_intent=3 }
} else {
    [ordered]@{ users=10;orders=20;exchange_accounts=10;risk_limit_sets=2;live_sessions=5;live_session_events=15;events_per_session=3;operator_approvals=15;approvals_per_session=3;execution_intents=40;intents_per_session=8;execution_receipts=120;receipts_per_intent=3 }
}

function Assert-Exit([string]$Operation) { if ($LASTEXITCODE -ne 0) { throw "$Operation failed: $LASTEXITCODE" } }
function Assert-Name([string]$Name) { if ($Name -cnotmatch '^nq-gatey5-disposable-post-(source|restore)-[0-9a-f]{32}$') { throw 'unsafe container name' } }
function Start-Db([string]$Name) {
    Assert-Name $Name
    docker run -d --name $Name -e "POSTGRES_DB=$database" -e "POSTGRES_USER=$databaseUser" `
        -e "POSTGRES_PASSWORD=$databasePassword" -p '127.0.0.1::5432' $PostgresImage | Out-Null
    Assert-Exit 'start disposable database'
    for ($i=0; $i -lt 30; $i++) { docker exec $Name pg_isready -U $databaseUser -d $database *> $null; if ($LASTEXITCODE -eq 0) { return }; Start-Sleep 1 }
    throw 'database not ready'
}
function Stop-Db([string]$Name) {
    Assert-Name $Name
    $id = docker ps -aq --filter "name=^/$Name$"
    if ($id) { docker rm -f $Name *> $null; Assert-Exit 'remove disposable database' }
}
function Get-Port([string]$Name) {
    $mapping = docker port $Name 5432/tcp
    if ($mapping -notmatch '^127\.0\.0\.1:(\d+)$') { throw 'non-loopback mapping' }
    return $Matches[1]
}
function Invoke-Flyway([string]$Name, [string]$Target) {
    $dependencies = (Get-Content -Raw $classpathFile).Trim()
    & java -cp ($toolClasses + [IO.Path]::PathSeparator + $dependencies) gatey.GateY5FlywayLauncher `
        "jdbc:postgresql://127.0.0.1:$(Get-Port $Name)/$database" $databaseUser $databasePassword `
        $migrationLocation $Target *> $null
    Assert-Exit "Flyway $Target"
}
function Invoke-Fixture([string]$Name, [string]$File, [System.Collections.IDictionary]$Variables) {
    docker cp $File "${Name}:/tmp/fixture.sql" *> $null; Assert-Exit 'copy fixture'
    $arguments = @('exec','-e',"PGPASSWORD=$databasePassword",$Name,'psql','-U',$databaseUser,'-d',$database,'--no-psqlrc','-v','ON_ERROR_STOP=1')
    foreach ($entry in $Variables.GetEnumerator()) { $arguments += @('-v',"$($entry.Key)=$($entry.Value)") }
    $arguments += @('-f','/tmp/fixture.sql')
    & docker @arguments *> $null; Assert-Exit 'run fixture'
}
function Invoke-Scalar([string]$Name, [string]$Sql) {
    $value = docker exec -e "PGPASSWORD=$databasePassword" $Name psql -U $databaseUser -d $database --no-psqlrc -At -c $Sql
    Assert-Exit 'query disposable database'
    return ($value | Out-String).Trim()
}

try {
    docker image inspect $PostgresImage *> $null; Assert-Exit 'inspect pinned image'
    New-Item -ItemType Directory -Path $runDirectory -Force | Out-Null
    & mvn -f (Join-Path $repo 'backend\pom.xml') -DskipTests install *> $null; Assert-Exit 'build backend'
    & mvn -f (Join-Path $repo 'backend\nq-app\pom.xml') dependency:build-classpath "-Dmdep.outputFile=$classpathFile" *> $null; Assert-Exit 'classpath'
    $dependencies = (Get-Content -Raw $classpathFile).Trim()
    New-Item -ItemType Directory -Path $toolClasses -Force | Out-Null
    & javac -cp $dependencies -d $toolClasses (Join-Path $PSScriptRoot 'GateY5FlywayLauncher.java'); Assert-Exit 'compile launcher'

    Start-Db $source
    Invoke-Flyway $source '38'
    Invoke-Fixture $source (Join-Path $PSScriptRoot 'gatey5-pre-fixture.sql') $pre
    Invoke-Flyway $source '39'
    Invoke-Fixture $source (Join-Path $PSScriptRoot 'gatey5-post-fixture.sql') $post

    $expectedTotal = ($pre.Values | Measure-Object -Sum).Sum + [long]$pre.backtest_publish_records + `
        [long]$post.risk_limit_sets + [long]$post.live_sessions + [long]$post.live_session_events + `
        [long]$post.operator_approvals + [long]$post.execution_intents + [long]$post.execution_receipts
    $actualTotal = [long](Invoke-Scalar $source @"
SELECT sum(c) FROM (
 SELECT count(*) c FROM users UNION ALL SELECT count(*) FROM roles UNION ALL SELECT count(*) FROM user_roles
 UNION ALL SELECT count(*) FROM accounts UNION ALL SELECT count(*) FROM strategy_runs
 UNION ALL SELECT count(*) FROM exchange_accounts UNION ALL SELECT count(*) FROM exchange_account_credentials
 UNION ALL SELECT count(*) FROM strategy_definitions UNION ALL SELECT count(*) FROM strategy_versions
 UNION ALL SELECT count(*) FROM research_configs UNION ALL SELECT count(*) FROM backtest_configs
 UNION ALL SELECT count(*) FROM backtest_runs UNION ALL SELECT count(*) FROM backtest_publish_records
 UNION ALL SELECT count(*) FROM strategy_release_admission_state UNION ALL SELECT count(*) FROM orders
 UNION ALL SELECT count(*) FROM risk_limit_sets UNION ALL SELECT count(*) FROM live_sessions
 UNION ALL SELECT count(*) FROM live_session_events UNION ALL SELECT count(*) FROM operator_approvals
 UNION ALL SELECT count(*) FROM execution_intents UNION ALL SELECT count(*) FROM execution_receipts
) rows;
"@)
    if ($actualTotal -ne $expectedTotal) { throw "POST total mismatch actual=$actualTotal expected=$expectedTotal" }
    if ([long](Invoke-Scalar $source "SELECT count(*) FROM live_sessions WHERE state NOT IN ('REJECTED','FAILED','KILLED','LIVE_RECONCILED');") -ne 0) { throw 'non-terminal fixture session exists' }
    if ([long](Invoke-Scalar $source "SELECT count(*) FROM execution_receipts;") -ne 3 * [long]$post.execution_intents) { throw 'receipt ratio mismatch' }

    $logicalDigest = Invoke-Scalar $source @"
SELECT encode(digest(string_agg(table_name||':'||rows||':'||minimum||':'||maximum,E'\n' ORDER BY table_name),'sha256'),'hex') FROM (
 SELECT 'risk_limit_sets' table_name,count(*) rows,min(risk_limit_set_id::text) minimum,max(risk_limit_set_id::text) maximum FROM risk_limit_sets
 UNION ALL SELECT 'live_sessions',count(*),min(session_id::text),max(session_id::text) FROM live_sessions
 UNION ALL SELECT 'live_session_events',count(*),min(event_id::text),max(event_id::text) FROM live_session_events
 UNION ALL SELECT 'operator_approvals',count(*),min(approval_id::text),max(approval_id::text) FROM operator_approvals
 UNION ALL SELECT 'execution_intents',count(*),min(intent_id::text),max(intent_id::text) FROM execution_intents
 UNION ALL SELECT 'execution_receipts',count(*),min(receipt_id::text),max(receipt_id::text) FROM execution_receipts
) facts;
"@
    $sizes = Invoke-Scalar $source "SELECT relname||'|'||pg_relation_size(oid)||'|'||pg_indexes_size(oid)||'|'||pg_total_relation_size(oid) FROM pg_class WHERE relnamespace='public'::regnamespace AND relname IN ('risk_limit_sets','live_sessions','live_session_events','operator_approvals','execution_intents','execution_receipts') ORDER BY relname;"

    docker exec -e "PGPASSWORD=$databasePassword" $source pg_dump -U $databaseUser -d $database -Fc --no-owner --no-privileges -f /tmp/post.dump
    Assert-Exit 'backup POST fixture'
    docker cp "${source}:/tmp/post.dump" $dumpPath *> $null; Assert-Exit 'copy POST backup'
    Stop-Db $source
    Start-Db $restore
    docker cp $dumpPath "${restore}:/tmp/post.dump" *> $null; Assert-Exit 'copy restore backup'
    docker exec -e "PGPASSWORD=$databasePassword" $restore pg_restore -U $databaseUser -d $database --no-owner --no-privileges --exit-on-error /tmp/post.dump
    Assert-Exit 'restore POST fixture'
    $restoredTotal = [long](Invoke-Scalar $restore "SELECT (SELECT count(*) FROM live_sessions)+(SELECT count(*) FROM operator_approvals)+(SELECT count(*) FROM risk_limit_sets)+(SELECT count(*) FROM execution_intents)+(SELECT count(*) FROM execution_receipts)+(SELECT count(*) FROM orders)+(SELECT count(*) FROM audit_logs)+(SELECT count(*) FROM ledger_entries);")
    $expectedRestoreSubset = [long]$post.live_sessions + [long]$post.operator_approvals + [long]$post.risk_limit_sets + [long]$post.execution_intents + [long]$post.execution_receipts + [long]$pre.orders
    if ($restoredTotal -ne $expectedRestoreSubset) { throw 'restored fact subset mismatch' }
    $temporal = Invoke-Scalar $restore "SELECT count(*) FILTER (WHERE state='SEND_STARTED')||'|'||count(*) FILTER (WHERE state='UNKNOWN')||'|'||count(*) FILTER (WHERE state IN ('SEND_SUCCEEDED','FAILED','CANCELLED','RECONCILED')) FROM execution_intents;"

    [ordered]@{
        decision='PASS / GATEY5_POST_FIXTURE_BACKUP_RESTORE_VERIFIED'
        scale=$Scale; rows=$actualTotal; logicalDigest=$logicalDigest
        relationSizes=@($sizes -split "`r?`n"); restoredSubsetRows=$restoredTotal
        restoredTemporalStates=$temporal; allSessionsTerminal=$true; receiptRatio=3
        externalEgress=0; productionWrites=0
    } | ConvertTo-Json -Depth 6
} finally {
    Stop-Db $source; Stop-Db $restore
    if (Test-Path $runDirectory) {
        $root=(Resolve-Path $artifactRoot).Path; $target=(Resolve-Path $runDirectory).Path
        if (-not $target.StartsWith($root + [IO.Path]::DirectorySeparatorChar)) { throw 'unsafe cleanup path' }
        Remove-Item -LiteralPath $target -Recurse -Force
    }
}
