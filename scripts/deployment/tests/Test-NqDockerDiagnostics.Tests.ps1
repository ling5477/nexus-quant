[CmdletBinding()]
param([switch]$UncaughtFailure)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$drill = Join-Path $PSScriptRoot '../Invoke-NqCanonicalRestoreDrill.ps1'
$tokens = $null
$errors = $null
$ast = [Management.Automation.Language.Parser]::ParseFile($drill, [ref]$tokens, [ref]$errors)
if ($errors.Count -ne 0) { throw 'FAIL / RESTORE_SCRIPT_PARSE' }
# 只载入生产脚本的两个诊断函数，负例不启动 Docker、数据库或 Maven。
foreach ($name in @('Protect-DockerDiagnostic', 'Invoke-Docker')) {
    $definitions = @($ast.FindAll({ param($node)
        $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -ceq $name
    }, $true))
    if ($definitions.Count -ne 1) { throw 'FAIL / DIAGNOSTIC_FUNCTION_IDENTITY' }
    . ([scriptblock]::Create($definitions[0].Extent.Text))
}
$script:dockerStage = 'CLEANUP_DISPOSABLE_RUNTIME'
$databasePassword = 'diagnostic-fixture-' + 'private-value'
$environmentFixture = 'diagnostic-env-' + 'private-value'
$shell = (Get-Process -Id $PID).Path
$fixture = Join-Path ([IO.Path]::GetTempPath()) ('nq-docker-diagnostic-' + [Guid]::NewGuid().ToString('N') + '.ps1')
$script:fixtureExit = 23
$script:fixtureStderr = $true
function docker {
    & $shell -NoProfile -ExecutionPolicy Bypass -File $fixture -Code $script:fixtureExit -WithStderr ([int]$script:fixtureStderr)
    # native 退出码写入全局自动变量；script 副本会在嵌套 harness 中遮蔽后续成功值。
    # fake 仅把本次真实退出码传给调用者，不保留跨 case 的 script 状态。
    Set-Variable -Name LASTEXITCODE -Value $global:LASTEXITCODE -Scope 1
}
function Assert-Diagnostic([bool]$Condition, [string]$Name) {
    if (-not $Condition) { throw "FAIL / DOCKER_DIAGNOSTIC_REGRESSION / $Name" }
}
try {
    [IO.File]::WriteAllText($fixture, @'
param([int]$Code, [int]$WithStderr)
if ($WithStderr) {
    [Console]::Error.WriteLine('Error response from daemon: removal failed')
    [Console]::Error.WriteLine('diagnostic-fixture-' + 'private-value')
    [Console]::Error.WriteLine('diagnostic-env-' + 'private-value')
    [Console]::Error.WriteLine('postgresql://fixture:private@localhost/test')
    [Console]::Error.WriteLine('API_KEY=fixture-key-value')
}
[Console]::Out.WriteLine('fixture-stdout')
exit $Code
'@, [Text.UTF8Encoding]::new($false))
    $arguments = @('rm', '--force', '--env', "FIXTURE=$environmentFixture", 'fixture-container')
    if ($UncaughtFailure) {
        $null = Invoke-Docker -Arguments $arguments -Operation REMOVE_CONTAINER
        throw 'FAIL / NONZERO_ACCEPTED'
    }
    $caught = ''
    try { $null = Invoke-Docker -Arguments $arguments -Operation REMOVE_CONTAINER } catch { $caught = $_.Exception.Message }
    Assert-Diagnostic ($caught.StartsWith('FAIL / DISPOSABLE_DOCKER_COMMAND_FAILED / DISPOSABLE_DOCKER_FAILURE ')) 'failure-preserved'
    $record = ($caught -split 'DISPOSABLE_DOCKER_FAILURE ', 2)[1] | ConvertFrom-Json
    Assert-Diagnostic ($record.operation -ceq 'REMOVE_CONTAINER' -and $record.stage -ceq 'CLEANUP_DISPOSABLE_RUNTIME' -and $record.exitCode -eq 23) 'failure-identity'
    Assert-Diagnostic ($record.stderrAvailable -and $record.sanitizedStderr.Contains('removal failed')) 'stderr-retained'
    foreach ($value in @($databasePassword, $environmentFixture, 'fixture:private', 'fixture-key-value')) {
        Assert-Diagnostic (-not $caught.Contains($value)) 'secret-absent'
    }
    $script:fixtureStderr = $false
    $caught = ''
    try { $null = Invoke-Docker -Arguments $arguments -Operation REMOVE_CONTAINER } catch { $caught = $_.Exception.Message }
    $record = ($caught -split 'DISPOSABLE_DOCKER_FAILURE ', 2)[1] | ConvertFrom-Json
    Assert-Diagnostic (-not $record.stderrAvailable -and $record.sanitizedStderr -ceq '' -and $record.exitCode -eq 23) 'stderr-unavailable'
    $result = Invoke-Docker -Arguments $arguments -Operation WAIT_POSTGRES_READY -AllowFailure
    Assert-Diagnostic ($result.ExitCode -eq 23) 'existing-explicit-failure-probe-preserved'
    $script:fixtureExit = 0
    $result = Invoke-Docker -Arguments $arguments -Operation READ_PORT_MAPPING
    Assert-Diagnostic ($result.ExitCode -eq 0 -and $result.Lines[0] -ceq 'fixture-stdout') 'success-output-preserved'
    Write-Output 'PASS / NEGATIVE_THEN_POSITIVE_ISOLATION / negative=23 / positive=0'
    $ErrorActionPreference = 'Continue'
    $output = @(& $shell -NoProfile -ExecutionPolicy Bypass -File $PSCommandPath -UncaughtFailure 2>&1)
    $code = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    $text = $output -join "`n"
    Assert-Diagnostic ($code -ne 0 -and $text.Contains('DISPOSABLE_DOCKER_FAILURE')) 'uncaught-process-fails'
    foreach ($value in @($databasePassword, $environmentFixture, 'fixture:private', 'fixture-key-value')) {
        Assert-Diagnostic (-not $text.Contains($value)) 'process-output-secret-absent'
    }
    Write-Output 'PASS / DOCKER_DIAGNOSTIC_REGRESSION / native-stderr / nonzero / operation / stage / exitCode / redaction / empty-stderr / success / process-boundary'
} finally {
    if (Test-Path -LiteralPath $fixture) { Remove-Item -LiteralPath $fixture -Force }
}
