[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][switch]$ConfirmDisposable,
    [ValidateSet('postgres:16-alpine')][string]$PostgresImage = 'postgres:16-alpine'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if (-not $ConfirmDisposable) { throw 'ConfirmDisposable is required' }
$profiles = @($env:SPRING_PROFILES_ACTIVE,$env:NQ_ENVIRONMENT,$env:NQ_PROFILE) -join ','
if ($profiles -match '(?i)(^|[,;\s])(prod|production)([,;\s]|$)') { throw 'production profile forbidden' }

$repo=(Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$runId=[Guid]::NewGuid().ToString('N')
$container="nq-gatey5-disposable-worker-$runId"
$database="nq_fake_worker_$runId"
$user='fake_worker'; $password='fake-worker-disposable-only'
$runDirectory=Join-Path $repo "artifacts\gatey5-worker-tmp-$runId"
$store=Join-Path $repo "artifacts\fake-venue-store-tmp-$runId.properties"
$health=Join-Path $repo "artifacts\fake-worker-health-tmp-$runId.properties"
$releaseA=Join-Path $runDirectory 'release-a.properties'; $releaseB=Join-Path $runDirectory 'release-b.properties'
$classpathFile=Join-Path $runDirectory 'classpath.txt'
$backupPath=Join-Path $runDirectory 'worker-runtime.dump'
$migrationLocation='filesystem:'+(Join-Path $repo 'backend\nq-infra\src\main\resources\db\migration').Replace('\','/')
$flywayClasses=Join-Path $runDirectory 'flyway'
$fakeProcess=$null
$script:artifactDigest=$null
$script:releaseManifestDigests=@{}

function Assert-Exit([string]$operation) { if ($LASTEXITCODE -ne 0) { throw "$operation failed: $LASTEXITCODE" } }
function Assert-Container { if ($container -cnotmatch '^nq-gatey5-disposable-worker-[0-9a-f]{32}$') { throw 'unsafe container name' } }
function Invoke-Sql([string]$sql) {
    $value=docker exec -e "PGPASSWORD=$password" $container psql -U $user -d $database --no-psqlrc -At -c $sql
    Assert-Exit 'query disposable database'; return ($value|Out-String).Trim()
}
function Get-Intent([int]$number) { return ('70000000-0000-0000-0000-{0:d12}' -f $number) }
function Write-Release([string]$path,[string]$release,[string]$worker) {
    $created=(Get-Date).ToUniversalTime().ToString('o')
    [IO.File]::WriteAllLines($path,@("releaseId=$release","workerIdentity=$worker",("artifactDigest=$script:artifactDigest"),"createdAt=$created",'immutable=true'),[Text.UTF8Encoding]::new($false))
    (Get-Item -LiteralPath $path).IsReadOnly=$true
    $script:releaseManifestDigests[$release]=(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
}
function Invoke-Worker([int]$number,[string]$operation,[string]$crash,[string]$release='release-a',[string]$worker='worker-a') {
    $manifest=if($release -eq 'release-a'){$releaseA}else{$releaseB}
    $args=@('-Dnq.fake-worker.confirm-disposable=true',"-Dnq.fake-worker.repo-root=$repo",'-cp',$script:classpath,
        'com.guidinglight.nexusquant.app.livecontrol.executionworker.IsolatedFakeExecutionWorkerLauncher',
        "jdbc:postgresql://127.0.0.1:$script:dbPort/$database","http://127.0.0.1:$script:fakePort/",(Get-Intent $number),
        $operation,$worker,[Guid]::NewGuid().ToString(), '2',$manifest,$release,
        $script:releaseManifestDigests[$release],$health,$crash)
    $previousErrorAction=$ErrorActionPreference
    try {
        $ErrorActionPreference='Continue'
        $output=& java @args 2>&1 | Out-String
        $exitCode=$LASTEXITCODE
    } finally {
        $ErrorActionPreference=$previousErrorAction
    }
    Write-Verbose $output
    if($crash -eq 'NONE' -and $exitCode -ne 0){throw "worker operation failed intent=$number operation=$operation release=$release exit=$exitCode detail=$output"}
    return [int]$exitCode
}
function Metric([string]$client) {
    $body="clientOrderId=$client"
    $result=Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:$script:fakePort/v1/fake/metrics" -Body $body -ContentType 'application/x-www-form-urlencoded'
    $map=@{}; foreach($line in ($result -split "`n")){if($line -match '^([^=]+)=(.*)$'){$map[$Matches[1]]=$Matches[2]}}
    return $map
}
function Wait-Health([string]$expected,[Diagnostics.Process]$process=$null,[string]$errorLog=$null) {
    for($i=0;$i -lt 50;$i++){
        if((Test-Path $health) -and ((Get-Content -Raw $health) -match "(?m)^health=$expected`r?$")){return}
        if($process -and $process.HasExited){$detail=if($errorLog -and (Test-Path $errorLog)){(Get-Content -Raw $errorLog)}else{'no stderr'};throw "worker exited before $expected exit=$($process.ExitCode) detail=$detail"}
        Start-Sleep -Milliseconds 100
    }
    throw "worker health did not reach $expected"
}

try {
    Assert-Container; docker image inspect $PostgresImage *> $null; Assert-Exit 'inspect image'
    New-Item -ItemType Directory -Path $runDirectory -Force|Out-Null
    & mvn -f (Join-Path $repo 'backend\pom.xml') -pl nq-app -am -DskipTests clean install *> $null; Assert-Exit 'clean build backend worker reactor'
    $workerArtifact=Get-ChildItem -LiteralPath (Join-Path $repo 'backend\nq-app\target') -Filter 'nq-app-*.jar' | Where-Object { $_.Name -notmatch '\.original$' } | Select-Object -First 1
    if(-not $workerArtifact){throw 'worker artifact missing after build'}
    $script:artifactDigest=(Get-FileHash -LiteralPath $workerArtifact.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    & mvn -f (Join-Path $repo 'backend\nq-app\pom.xml') dependency:build-classpath "-Dmdep.outputFile=$classpathFile" *> $null; Assert-Exit 'classpath'
    $script:classpath=$workerArtifact.FullName+[IO.Path]::PathSeparator+(Get-Content -Raw $classpathFile).Trim()
    New-Item -ItemType Directory -Path $flywayClasses|Out-Null
    & javac -cp $script:classpath -d $flywayClasses (Join-Path $PSScriptRoot 'GateY5FlywayLauncher.java'); Assert-Exit 'compile Flyway launcher'

    docker run -d --name $container -e "POSTGRES_DB=$database" -e "POSTGRES_USER=$user" -e "POSTGRES_PASSWORD=$password" -p '127.0.0.1::5432' $PostgresImage|Out-Null
    Assert-Exit 'start database'
    for($i=0;$i -lt 30;$i++){docker exec $container pg_isready -U $user -d $database *> $null;if($LASTEXITCODE -eq 0){break};Start-Sleep 1}
    $mapping=docker port $container 5432/tcp;if($mapping -notmatch '^127\.0\.0\.1:(\d+)$'){throw 'unsafe DB mapping'};$script:dbPort=$Matches[1]
    & java -cp ($flywayClasses+[IO.Path]::PathSeparator+$script:classpath) gatey.GateY5FlywayLauncher "jdbc:postgresql://127.0.0.1:$script:dbPort/$database" $user $password $migrationLocation 38 *> $null;Assert-Exit 'Flyway V38'
    docker cp (Join-Path $PSScriptRoot 'gatey5-pre-fixture.sql') "${container}:/tmp/pre.sql" *> $null
    docker exec -e "PGPASSWORD=$password" $container psql -U $user -d $database --no-psqlrc -v ON_ERROR_STOP=1 -v users=10 -v roles=4 -v user_roles=20 -v accounts=10 -v strategy_runs=20 -v exchange_accounts=10 -v exchange_account_credentials=20 -v strategy_definitions=10 -v strategy_versions=20 -v research_configs=20 -v backtest_configs=20 -v backtest_runs=20 -v backtest_publish_records=20 -v orders=20 -f /tmp/pre.sql *> $null;Assert-Exit 'PRE fixture'
    & java -cp ($flywayClasses+[IO.Path]::PathSeparator+$script:classpath) gatey.GateY5FlywayLauncher "jdbc:postgresql://127.0.0.1:$script:dbPort/$database" $user $password $migrationLocation 39 *> $null;Assert-Exit 'Flyway V39'
    docker cp (Join-Path $PSScriptRoot 'gatey5-worker-fixture.sql') "${container}:/tmp/worker.sql" *> $null
    docker exec -e "PGPASSWORD=$password" $container psql -U $user -d $database --no-psqlrc -v ON_ERROR_STOP=1 -f /tmp/worker.sql *> $null;Assert-Exit 'worker fixture'

    $listener=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,0);$listener.Start();$script:fakePort=([Net.IPEndPoint]$listener.LocalEndpoint).Port;$listener.Stop()
    $fakeArgs=@('-Dnq.fake-worker.confirm-disposable=true',"-Dnq.fake-worker.repo-root=$repo",'-cp',$script:classpath,
        'com.guidinglight.nexusquant.app.livecontrol.executionworker.DisposableFakeVenueLauncher',$script:fakePort,$store)
    $fakeProcess=Start-Process java -ArgumentList $fakeArgs -PassThru -WindowStyle Hidden
    for($i=0;$i -lt 50;$i++){try{Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:$script:fakePort/health" -Body '' *> $null;break}catch{Start-Sleep -Milliseconds 100}}
    Write-Release $releaseA 'release-a' 'worker-a';Write-Release $releaseB 'release-b' 'worker-b'

    $results=[ordered]@{}
    $results.crashBeforeSend=(Invoke-Worker 1 EXECUTE AFTER_CLAIM);Start-Sleep 3;$results.crashBeforeSendRestart=(Invoke-Worker 1 EXECUTE NONE)
    $results.crashAfterSend=(Invoke-Worker 2 EXECUTE AFTER_SEND_STARTED);$results.crashAfterSendRecovery=(Invoke-Worker 2 RECONCILE NONE)
    $results.crashAfterMutation=(Invoke-Worker 3 EXECUTE AFTER_MUTATION);$results.crashAfterMutationRecovery=(Invoke-Worker 3 RECONCILE NONE)
    $results.receiptFailure=(Invoke-Worker 4 EXECUTE RECEIPT_FAILURE);$results.receiptFailureRecovery=(Invoke-Worker 4 RECONCILE NONE)

    Remove-Item $health -ErrorAction SilentlyContinue
    $duplicateArgs=@('-Dnq.fake-worker.confirm-disposable=true',"-Dnq.fake-worker.repo-root=$repo",'-cp',$script:classpath,'com.guidinglight.nexusquant.app.livecontrol.executionworker.IsolatedFakeExecutionWorkerLauncher',"jdbc:postgresql://127.0.0.1:$script:dbPort/$database","http://127.0.0.1:$script:fakePort/",(Get-Intent 5),'EXECUTE','worker-a',[Guid]::NewGuid().ToString(),'2',$releaseA,'release-a',$script:releaseManifestDigests['release-a'],$health,'WAIT_AFTER_CLAIM')
    $duplicateOut=Join-Path $runDirectory 'duplicate.out';$duplicateError=Join-Path $runDirectory 'duplicate.err'
    $duplicate=Start-Process java -ArgumentList $duplicateArgs -PassThru -WindowStyle Hidden -RedirectStandardOutput $duplicateOut -RedirectStandardError $duplicateError
    Wait-Health CLAIMED $duplicate $duplicateError;$results.duplicateContender=(Invoke-Worker 5 EXECUTE NONE);$duplicate.WaitForExit();$duplicate.Refresh();$results.duplicateWorker=$duplicate.ExitCode
    Start-Sleep 3;$results.duplicateRecovery=(Invoke-Worker 5 EXECUTE NONE)

    Remove-Item $health -ErrorAction SilentlyContinue
    $procArgs=@('-Dnq.fake-worker.confirm-disposable=true',"-Dnq.fake-worker.repo-root=$repo",'-cp',$script:classpath,'com.guidinglight.nexusquant.app.livecontrol.executionworker.IsolatedFakeExecutionWorkerLauncher',"jdbc:postgresql://127.0.0.1:$script:dbPort/$database","http://127.0.0.1:$script:fakePort/",(Get-Intent 6),'EXECUTE','worker-a',[Guid]::NewGuid().ToString(),'2',$releaseA,'release-a',$script:releaseManifestDigests['release-a'],$health,'WAIT_AFTER_CLAIM')
    $claimOut=Join-Path $runDirectory 'kill-claim.out';$claimError=Join-Path $runDirectory 'kill-claim.err'
    $p=Start-Process java -ArgumentList $procArgs -PassThru -WindowStyle Hidden -RedirectStandardOutput $claimOut -RedirectStandardError $claimError;Wait-Health CLAIMED $p $claimError
    Invoke-Sql "UPDATE kill_switch_states SET status='ENGAGED',version=version+1,reason_code='DRILL',source='DISPOSABLE_FIXTURE',updated_at=CURRENT_TIMESTAMP,updated_by='drill',trace_id='drill' WHERE scope='GLOBAL_TRADING';"|Out-Null
    $p.WaitForExit();$p.Refresh();$results.killAfterClaim=$p.ExitCode
    Invoke-Sql "UPDATE kill_switch_states SET status='DISENGAGED',version=version+1,reason_code='DRILL_RESET',source='DISPOSABLE_FIXTURE',updated_at=CURRENT_TIMESTAMP,updated_by='drill',trace_id='drill' WHERE scope='GLOBAL_TRADING';"|Out-Null
    Start-Sleep 3;$results.killAfterClaimRecovery=(Invoke-Worker 6 EXECUTE NONE)

    Remove-Item $health -ErrorAction SilentlyContinue
    $procArgs[7]=(Get-Intent 7);$procArgs[15]=$health;$procArgs[16]='WAIT_AFTER_SEND_STARTED'
    $sendOut=Join-Path $runDirectory 'kill-send.out';$sendError=Join-Path $runDirectory 'kill-send.err'
    $p=Start-Process java -ArgumentList $procArgs -PassThru -WindowStyle Hidden -RedirectStandardOutput $sendOut -RedirectStandardError $sendError;Wait-Health SEND_STARTED $p $sendError
    Invoke-Sql "UPDATE kill_switch_states SET status='ENGAGED',version=version+1,reason_code='DRILL',source='DISPOSABLE_FIXTURE',updated_at=CURRENT_TIMESTAMP,updated_by='drill',trace_id='drill' WHERE scope='GLOBAL_TRADING';"|Out-Null
    $p.WaitForExit();$p.Refresh();$results.killAfterSend=$p.ExitCode;$results.killAfterSendRecovery=(Invoke-Worker 7 RECONCILE NONE)

    Invoke-Sql "UPDATE kill_switch_states SET status='DISENGAGED',version=version+1,reason_code='ROLLBACK_A',source='DISPOSABLE_FIXTURE',updated_at=CURRENT_TIMESTAMP,updated_by='drill',trace_id='drill' WHERE scope='GLOBAL_TRADING';"|Out-Null
    $results.rollbackReleaseA=(Invoke-Worker 8 EXECUTE AFTER_MUTATION)
    Invoke-Sql "UPDATE kill_switch_states SET status='ENGAGED',version=version+1,reason_code='ROLLBACK',source='DISPOSABLE_FIXTURE',updated_at=CURRENT_TIMESTAMP,updated_by='drill',trace_id='drill' WHERE scope='GLOBAL_TRADING';"|Out-Null
    $results.rollbackReleaseB=(Invoke-Worker 8 RECONCILE NONE 'release-b' 'worker-b')

    Invoke-Sql "UPDATE kill_switch_states SET status='DISENGAGED',version=version+1,reason_code='INCIDENT_MATRIX',source='DISPOSABLE_FIXTURE',updated_at=CURRENT_TIMESTAMP,updated_by='drill',trace_id='drill' WHERE scope='GLOBAL_TRADING';"|Out-Null
    foreach($number in 9..12){$results["incidentCrash$number"]=(Invoke-Worker $number EXECUTE AFTER_MUTATION);$results["incidentRecovery$number"]=(Invoke-Worker $number RECONCILE NONE)}
    $results.restoreSendStartedCrash=(Invoke-Worker 13 EXECUTE AFTER_SEND_STARTED)
    $results.restoreUnknownCrash=(Invoke-Worker 14 EXECUTE AFTER_MUTATION)
    $results.restoreUnknownBeforeBackup=(Invoke-Worker 14 RECONCILE NONE)
    $preRestoreTemporal=Invoke-Sql "SELECT count(*) FILTER (WHERE state='SEND_STARTED')||'|'||count(*) FILTER (WHERE state='UNKNOWN')||'|'||count(*) FILTER (WHERE state IN ('SEND_SUCCEEDED','FAILED','CANCELLED','RECONCILED')) FROM execution_intents;"
    $terminalReceiptBefore=Invoke-Sql "SELECT count(*)||'|'||encode(digest(string_agg(receipt_id::text||':'||payload_digest,E'\n' ORDER BY receipt_id),'sha256'),'hex') FROM execution_receipts WHERE intent_id NOT IN ('70000000-0000-0000-0000-000000000013','70000000-0000-0000-0000-000000000014');"
    docker exec -e "PGPASSWORD=$password" $container pg_dump -U $user -d $database -Fc --no-owner --no-privileges -f /tmp/worker-runtime.dump;Assert-Exit 'backup worker runtime'
    docker cp "${container}:/tmp/worker-runtime.dump" $backupPath *> $null;Assert-Exit 'copy worker runtime backup'
    docker rm -f $container *> $null;Assert-Exit 'destroy source worker database'
    docker run -d --name $container -e "POSTGRES_DB=$database" -e "POSTGRES_USER=$user" -e "POSTGRES_PASSWORD=$password" -p '127.0.0.1::5432' $PostgresImage|Out-Null;Assert-Exit 'start restore database'
    for($i=0;$i -lt 30;$i++){docker exec $container pg_isready -U $user -d $database *> $null;if($LASTEXITCODE -eq 0){break};Start-Sleep 1}
    $mapping=docker port $container 5432/tcp;if($mapping -notmatch '^127\.0\.0\.1:(\d+)$'){throw 'unsafe restored DB mapping'};$script:dbPort=$Matches[1]
    docker cp $backupPath "${container}:/tmp/worker-runtime.dump" *> $null;Assert-Exit 'copy worker runtime restore'
    docker exec -e "PGPASSWORD=$password" $container pg_restore -U $user -d $database --no-owner --no-privileges --exit-on-error /tmp/worker-runtime.dump;Assert-Exit 'restore worker runtime'
    $postRestoreTemporal=Invoke-Sql "SELECT count(*) FILTER (WHERE state='SEND_STARTED')||'|'||count(*) FILTER (WHERE state='UNKNOWN')||'|'||count(*) FILTER (WHERE state IN ('SEND_SUCCEEDED','FAILED','CANCELLED','RECONCILED')) FROM execution_intents;"
    if($postRestoreTemporal -ne $preRestoreTemporal){throw "restore temporal mismatch pre=$preRestoreTemporal post=$postRestoreTemporal"}
    $results.restoreSendStartedRecovery=(Invoke-Worker 13 RECONCILE NONE)
    $results.restoreUnknownRecovery=(Invoke-Worker 14 RECONCILE NONE)
    $terminalReceiptAfter=Invoke-Sql "SELECT count(*)||'|'||encode(digest(string_agg(receipt_id::text||':'||payload_digest,E'\n' ORDER BY receipt_id),'sha256'),'hex') FROM execution_receipts WHERE intent_id NOT IN ('70000000-0000-0000-0000-000000000013','70000000-0000-0000-0000-000000000014');"
    if($terminalReceiptAfter -ne $terminalReceiptBefore){throw 'terminal receipt history changed across restore/recovery'}

    Invoke-Sql "UPDATE kill_switch_states SET status='ENGAGED',version=version+1,reason_code='DRILL_FINAL',source='DISPOSABLE_FIXTURE',updated_at=CURRENT_TIMESTAMP,updated_by='drill',trace_id='drill' WHERE scope='GLOBAL_TRADING';"|Out-Null
    $counters=@{};foreach($case in @('crash-before-send','crash-after-send','crash-after-mutation','receipt-failure','duplicate-worker','kill-after-claim','kill-after-send','rollback-release','partial-fill','late-fill','cancel-race','unknown-observation','restore-send-started','restore-unknown')){$counters[$case]=Metric $case}
    foreach($case in $counters.Keys){if([int]$counters[$case].mutationCallCount -gt 1){throw "blind retry: $case"}}
    foreach($name in @('crashBeforeSendRestart','crashAfterSendRecovery','crashAfterMutationRecovery','receiptFailureRecovery','duplicateRecovery','killAfterClaimRecovery','killAfterSendRecovery','rollbackReleaseB','incidentRecovery9','incidentRecovery10','incidentRecovery11','incidentRecovery12','restoreUnknownBeforeBackup','restoreSendStartedRecovery','restoreUnknownRecovery')){if([int]$results[$name] -ne 0){throw "required recovery failed: $name exit=$($results[$name])"}}
    foreach($case in @('crash-after-send','crash-after-mutation','receipt-failure','kill-after-send','rollback-release')){if([int]$counters[$case].recoveryQueryCount -ne 1){throw "query-only recovery missing: $case"}}
    foreach($case in @('crash-before-send','crash-after-mutation','receipt-failure','duplicate-worker','kill-after-claim','rollback-release')){if([int]$counters[$case].mutationCallCount -ne 1 -or [int]$counters[$case].remoteOrderCount -ne 1){throw "exact mutation proof failed: $case"}}
    if([int]$counters['crash-after-send'].mutationCallCount -ne 0 -or [int]$counters['kill-after-send'].mutationCallCount -ne 0){throw 'post-SEND_STARTED recovery performed mutation'}
    foreach($case in @('partial-fill','late-fill','cancel-race','unknown-observation','restore-unknown')){if([int]$counters[$case].mutationCallCount -ne 1 -or [int]$counters[$case].recoveryQueryCount -lt 1){throw "incident matrix count failed: $case"}}
    if([int]$counters['restore-send-started'].mutationCallCount -ne 0 -or [int]$counters['restore-send-started'].recoveryQueryCount -ne 1){throw 'restored SEND_STARTED was not query-only'}
    $finalKill=Invoke-Sql "SELECT status FROM kill_switch_states WHERE scope='GLOBAL_TRADING';"
    $states=Invoke-Sql "SELECT client_order_id||'|'||state||'|'||(SELECT count(*) FROM execution_receipts r WHERE r.intent_id=i.intent_id) FROM execution_intents i WHERE session_id='60000000-0000-0000-0000-000000000001' ORDER BY sequence;"
    [ordered]@{decision='PASS / ISOLATED_FAKE_WORKER_PROCESS_RESTORE_INCIDENTS_VERIFIED';processResults=$results;counters=$counters;states=@($states -split "`r?`n");restore=@{preTemporal=$preRestoreTemporal;postTemporal=$postRestoreTemporal;terminalReceiptHistory='IMMUTABLE';databaseDestroyedAndRestored=$true};kill=$finalKill;externalEgress=0;productionWrites=0}|ConvertTo-Json -Depth 8
} finally {
    if($fakeProcess -and -not $fakeProcess.HasExited){$fakeProcess.Kill();$fakeProcess.WaitForExit()}
    Assert-Container;$id=docker ps -aq --filter "name=^/$container$";if($id){docker rm -f $container *> $null}
    foreach($path in @($runDirectory,$store,$health)){if(Test-Path $path){$full=(Resolve-Path $path).Path;$root=(Resolve-Path (Join-Path $repo 'artifacts')).Path;if(-not $full.StartsWith($root+[IO.Path]::DirectorySeparatorChar)){throw 'unsafe cleanup'};Remove-Item -LiteralPath $full -Recurse -Force}}
}
