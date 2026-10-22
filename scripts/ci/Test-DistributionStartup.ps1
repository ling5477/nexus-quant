#requires -Version 7.4
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$SelfHostedJar,
    [Parameter(Mandatory)][string]$PublicDemoJar
)
$ErrorActionPreference='Stop'
$selfJar=(Resolve-Path -LiteralPath $SelfHostedJar).Path
$demoJar=(Resolve-Path -LiteralPath $PublicDemoJar).Path
$probeDirectory=Join-Path ([IO.Path]::GetTempPath()) ('nq-distribution-startup-'+[guid]::NewGuid().ToString('N'))
[void](New-Item -ItemType Directory -Path $probeDirectory)
function Test-StartupRejection([string]$Jar,[string[]]$Overrides,[string]$Expected) {
    $start=[Diagnostics.ProcessStartInfo]::new()
    $start.FileName=(Get-Command java -CommandType Application -ErrorAction Stop | Select-Object -First 1).Source
    $start.WorkingDirectory=$probeDirectory; $start.UseShellExecute=$false; $start.CreateNoWindow=$true
    $start.RedirectStandardOutput=$true; $start.RedirectStandardError=$true
    foreach($key in @($start.Environment.Keys)) {
        if($key.StartsWith('NQ_',[StringComparison]::OrdinalIgnoreCase) -or $key.StartsWith('SPRING_',[StringComparison]::OrdinalIgnoreCase) -or
                $key -in @('JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS')) {
            [void]$start.Environment.Remove($key)
        }
    }
    foreach($key in @('NQ_LIVE_ENABLED','NQ_REAL_PROVIDER_ENABLED','NQ_REAL_CLIENT_ENABLED','NQ_REAL_EXCHANGE_ENABLED',
            'NQ_OKX_WS_ENABLED','NQ_BINANCE_WS_ENABLED','NQ_OKX_RECOVERY_ENABLED','NQ_AI_ENABLED','NQ_DH_RUNTIME_ENABLED')) {
        $start.Environment[$key]='false'
    }
    foreach($key in @('NQ_OKX_API_KEY','NQ_OKX_API_SECRET','NQ_OKX_API_PASSPHRASE','NQ_BINANCE_API_KEY','NQ_BINANCE_API_SECRET')) {
        $start.Environment[$key]=''
    }
    $start.Environment['NQ_OKX_ENV']='dome'; $start.Environment['NQ_BINANCE_ENV']='dome'
    $start.Environment.Remove('NQ_DISTRIBUTION') | Out-Null
    $defaults=@('--spring.profiles.active=ci','--spring.main.web-application-type=none',
            '--spring.main.banner-mode=off','--logging.level.root=ERROR',
            '--spring.datasource.url=jdbc:postgresql://127.0.0.1:1/distribution_guard',
            '--spring.flyway.enabled=false','--nq.instrument.catalog-sync.enabled=false',
            '--nq.okx.recovery.enabled=false','--nq.auth.bootstrap-admin.enabled=false')
    foreach($override in $Overrides) {
        $key=$override.Substring(0,$override.IndexOf('=')+1)
        $defaults=@($defaults | Where-Object {-not $_.StartsWith($key)})
    }
    foreach($argument in @('-jar',$Jar) + $defaults + $Overrides) {
        $start.ArgumentList.Add($argument)
    }
    $process=[Diagnostics.Process]::new(); $process.StartInfo=$start
    try {
        [void]$process.Start()
        $stdout=$process.StandardOutput.ReadToEndAsync(); $stderr=$process.StandardError.ReadToEndAsync()
        if(-not $process.WaitForExit(45000)) { $process.Kill($true); throw ('Startup rejection timed out: '+$Expected) }
        $process.WaitForExit()
        if($process.ExitCode -eq 0 -or -not ($stdout.Result+$stderr.Result).Contains($Expected)) {
            throw ('Startup did not reject with expected guard: '+$Expected)
        }
        [ordered]@{jarSha256=(Get-FileHash -LiteralPath $Jar -Algorithm SHA256).Hash.ToLowerInvariant(); guard=$Expected; exitCode=$process.ExitCode; result='PASS'}
    } finally { $process.Dispose() }
}
$results=@(
    Test-StartupRejection $selfJar @('--nq.distribution=NQ_PUBLIC_DEMO') 'DISTRIBUTION_RUNTIME_MISMATCH'
    Test-StartupRejection $selfJar @('--nq.demo.registration-enabled=true') 'SELF_HOSTED_DEMO_CONFIGURATION_FORBIDDEN'
    Test-StartupRejection $demoJar @('--nq.distribution=NQ_SELF_HOSTED') 'DISTRIBUTION_RUNTIME_MISMATCH'
    Test-StartupRejection $demoJar @('--nq.env-safety.live-enabled=true') 'PUBLIC_DEMO_UNSAFE_CONFIGURATION'
    Test-StartupRejection $demoJar @('--nq.auth.bootstrap-admin.enabled=true') 'PUBLIC_DEMO_UNSAFE_CONFIGURATION'
    Test-StartupRejection $demoJar @('--spring.profiles.active=local') 'PUBLIC_DEMO_UNSAFE_PROFILE'
)
$results | ConvertTo-Json -Depth 4
