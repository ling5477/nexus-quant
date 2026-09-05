[CmdletBinding()]
param()
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$reader = Join-Path $repo 'scripts/ci/Read-NqWorkflowYaml.ps1'
$artifacts = Join-Path $repo 'artifacts'
$root = Join-Path $artifacts ('yaml-' + [Guid]::NewGuid().ToString('N').Substring(0,8))
New-Item -ItemType Directory -Path $root -Force | Out-Null
function Assert-Semantic([bool]$Condition, [string]$Message) { if (-not $Condition) { throw $Message } }
try {
    $equalities = 0
    foreach ($contract in @(
        @{ Key = 'continue-on-error'; Spellings = @('continue-on-error', '"continue-on-error"', '"continue\u002don-error"') },
        @{ Key = 'if'; Spellings = @('if', '"if"', '"\u0069f"') }
    )) {
        $keys = @()
        foreach ($spelling in $contract.Spellings) {
            $path = Join-Path $root 'equality.yml'
            [IO.File]::WriteAllText($path, "jobs:`n  backend:`n    ${spelling}: " + '${{ false }}' + "`n")
            $model = & $reader -WorkflowPath $path
            $properties = @($model.document.jobs.backend.PSObject.Properties)
            Assert-Semantic ($properties.Count -eq 1) 'Semantic equality fixture lost a mapping entry'
            $keys += $properties[0].Name
            Assert-Semantic ($properties[0].Name -ceq $contract.Key) 'Decoded YAML key differs from its plain spelling'
            Assert-Semantic ($properties[0].Value -ceq '${{ false }}') 'Expression must remain uninterpreted text'
        }
        Assert-Semantic (@($keys | Select-Object -Unique).Count -eq 1) 'Plain/quoted/escaped keys must be equal'
        $equalities++
        Write-Output "SEMANTIC_KEY_EQUALITY=$($contract.Key) plain=quoted=escaped"
    }
    # A positive nested mapping/sequence fixture protects the transport's structure and decoded run.
    $path = Join-Path $root 'structure.yml'
    [IO.File]::WriteAllText($path, 'on: [push]' + "`njobs:`n  backend:`n    steps:`n      - name: F008`n        `"r\u0075n`": `"echo\nhello`"`n")
    $model = & $reader -WorkflowPath $path
    Assert-Semantic ($model.document.jobs -is [pscustomobject]) 'jobs mapping was flattened'
    Assert-Semantic ($model.document.jobs.backend.steps -is [array]) 'steps sequence was flattened'
    Assert-Semantic ($model.document.jobs.backend.steps[0].run -ceq "echo`nhello") 'Decoded run key/value lost YAML semantics'
    Assert-Semantic ($null -ne $model.document.PSObject.Properties['on']) 'Workflow on key must remain on'

    $rejected = 0
    foreach ($invalid in @(
        'jobs: [',
        "jobs: {backend: {if: false, `"\u0069f`": false}}",
        "jobs: {backend: {continue-on-error: false, `"continue\u002don-error`": false}}",
        "jobs: {backend: {}, backend: {}}",
        "jobs: {backend: {<<: {if: false}}}",
        "value: &shared {if: false}`njobs: {backend: *shared}",
        "jobs: !!java.net.URL 'https://synthetic.invalid'",
        "jobs: {}`n---`njobs: {}"
    )) {
        $path = Join-Path $root 'invalid.yml'
        [IO.File]::WriteAllText($path, $invalid)
        $failed = $false
        try { & $reader -WorkflowPath $path | Out-Null }
        catch { $failed = $_.Exception.Message -like 'YAML_SEMANTIC_PARSE_REJECTED*' }
        Assert-Semantic $failed 'Parser error or unsupported YAML construct did not fail closed'
        $rejected++
    }
    $missingDependency = $false
    try { & $reader -WorkflowPath $path -MavenRepositoryRoot $root | Out-Null }
    catch { $missingDependency = $_.Exception.Message -like 'YAML_PARSER_DEPENDENCY_UNAVAILABLE*' }
    Assert-Semantic $missingDependency 'Missing offline parser dependency did not fail closed'
    Write-Output "YAML_SEMANTIC_TEST equalities=$equalities structure=PASS invalid-rejected=$rejected missing-dependency=REJECTED"
} finally {
    $resolved = (Resolve-Path -LiteralPath $root).Path
    $boundary = (Resolve-Path -LiteralPath $artifacts).Path + [IO.Path]::DirectorySeparatorChar
    if (-not $resolved.StartsWith($boundary)) { throw 'Refusing YAML test cleanup outside artifacts' }
    Remove-Item -LiteralPath $resolved -Recurse -Force
}
