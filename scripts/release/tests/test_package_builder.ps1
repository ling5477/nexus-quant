#requires -Version 7.4
[CmdletBinding()]
param([string]$HistoricalPackageRoot, [string]$EvidenceDirectory)
$ErrorActionPreference = 'Stop'
$builder = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../release/build-package.ps1'))
$tokens = $null; $parseErrors = $null
$ast = [Management.Automation.Language.Parser]::ParseFile($builder, [ref]$tokens, [ref]$parseErrors)
if ($parseErrors.Count) { throw 'Builder syntax invalid' }
$guards = @($ast.FindAll({ param($node) $node -is [Management.Automation.Language.IfStatementAst] -and $node.Extent.Text.StartsWith('if ($PostgresImage -or') }, $false))
if ($guards.Count -ne 1) { throw 'Postgres input guard missing or ambiguous' }
$guard = [scriptblock]::Create($guards[0].Extent.Text)
foreach ($case in @(
    @{PostgresImage='postgres:16.15'; ExpectedPostgresConfigDigest=('sha256:'+('a'*64)); Architectures=@('amd64')},
    @{PostgresImage=('sha256:'+('a'*64)); ExpectedPostgresConfigDigest=''; Architectures=@('amd64')},
    @{PostgresImage=('sha256:'+('a'*64)); ExpectedPostgresConfigDigest=('sha256:'+('b'*64)); Architectures=@('amd64','arm64')}
)) {
    $PostgresImage=$case.PostgresImage; $ExpectedPostgresConfigDigest=$case.ExpectedPostgresConfigDigest; $Architectures=$case.Architectures
    $rejected=$false; try { . $guard } catch { $rejected=$_.Exception.Message -eq 'Postgres reuse requires an immutable image ID, expected config digest and one architecture' }
    if (-not $rejected) { throw 'Unsafe Postgres reuse input accepted' }
}
$PostgresImage='sha256:'+('a'*64); $ExpectedPostgresConfigDigest='sha256:'+('b'*64); $Architectures=@('amd64'); . $guard
$output=Join-Path ([IO.Path]::GetTempPath()) ('nq-package-rejection-'+[guid]::NewGuid().ToString('N'))
$rejected=$false
try { & $builder -OutputDirectory $output -Architectures amd64 } catch { $rejected=$_.Exception.Message -eq 'Build from a verified immutable release-source export' }
if (-not $rejected -or (Test-Path -LiteralPath $output)) { throw 'Worktree build must fail before creating package output' }
Write-Host 'BUILD_INPUT_NEGATIVES_PASS invalidReuse=3 worktreeRejected=true'
if ($HistoricalPackageRoot) {
    if (-not $EvidenceDirectory -or -not (Test-Path -LiteralPath $EvidenceDirectory -PathType Container)) { throw 'Existing task evidence directory required' }
    # 只调用真实构建器函数验证历史镜像的归档协议；不生成候选来源或 Package Manifest。
    foreach ($function in $ast.FindAll({ param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] },$false)) { . ([scriptblock]::Create($function.Extent.Text)) }
    $manifest=@{}; foreach ($line in Get-Content -LiteralPath (Join-Path $HistoricalPackageRoot 'package-amd64.env')) { $pair=$line.Split('=',2); $manifest[$pair[0]]=$pair[1] }
    if ((Hash (Join-Path $HistoricalPackageRoot 'package-amd64.env')) -cne 'f0beba4f37481c85ff1677e8c546f10deaeba57fb5144f69f5d7edcd839ef5db') { throw 'Historical trusted package identity mismatch' }
    $refs=@{}; $version='readiness-contract-20261028'; $arch='amd64'
    foreach ($component in @('backend','frontend')) {
        $tag="nexusquant/$component`:$version"
        [void](Invoke-PackageDocker @('tag',$manifest[$component.ToUpperInvariant()+'_IMAGE'],$tag) 30)
        $refs[$component]=Image-Identity $tag $arch
    }
    $PostgresImage=$manifest.POSTGRES_IMAGE; $ExpectedPostgresConfigDigest=$manifest.POSTGRES_CONFIG_DIGEST; $Architectures=@('amd64'); . $guard
    $postgresRef=$PostgresImage
    $selection=@($ast.FindAll({param($node) $node -is [Management.Automation.Language.IfStatementAst] -and $node.Extent.Text.StartsWith('if (-not $postgresRef)')},$true))
    if ($selection.Count -ne 1) { throw 'Postgres selection missing' }
    . ([scriptblock]::Create($selection[0].Extent.Text))
    $refs.postgres=Image-Identity $postgresRef $arch
    $archive=Join-Path $EvidenceDirectory 'historical-images-contract.tar'
    [void](Invoke-PackageDocker @('save','-o',$archive,$refs.backend.tag,$refs.frontend.tag,$postgresRef) 120)
    Archive-ImageIdentities $archive $refs $arch
    $comparison=@($ast.FindAll({param($node) $node -is [Management.Automation.Language.IfStatementAst] -and $node.Extent.Text.Contains('Postgres configuration differs')},$true))
    if ($comparison.Count -ne 1) { throw 'Postgres config guard missing' }
    . ([scriptblock]::Create($comparison[0].Extent.Text))
    $ExpectedPostgresConfigDigest='sha256:'+('0'*64); $rejected=$false
    try { . ([scriptblock]::Create($comparison[0].Extent.Text)) } catch { $rejected=$_.Exception.Message -eq 'Postgres configuration differs from trusted upgrade baseline' }
    if (-not $rejected) { throw 'Wrong Postgres config accepted' }
    Write-Host ('IMMUTABLE_POSTGRES_ARCHIVE_CONTRACT_PASS config='+$refs.postgres.configDigest+' wrongConfigRejected=true')
}
