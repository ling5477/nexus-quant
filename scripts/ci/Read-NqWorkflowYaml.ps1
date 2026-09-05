[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string] $WorkflowPath,
    # Canonical CI uses Maven's standard local repository. An explicit existing cache is permitted
    # for offline tooling/tests; this reader never resolves, installs or downloads dependencies.
    [string] $MavenRepositoryRoot = (Join-Path ([Environment]::GetFolderPath('UserProfile')) '.m2/repository')
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
[xml] $parentPom = Get-Content -LiteralPath (Join-Path $repo 'backend/pom.xml') -Raw
$bootVersion = [string]$parentPom.project.properties.'spring.boot.version'
if ($bootVersion -cnotmatch '^[0-9]+\.[0-9]+\.[0-9]+$') { throw 'YAML_PARSER_BOM_IDENTITY_INVALID' }
$bomPath = Join-Path $MavenRepositoryRoot "org/springframework/boot/spring-boot-dependencies/$bootVersion/spring-boot-dependencies-$bootVersion.pom"
if (-not (Test-Path -LiteralPath $bomPath -PathType Leaf)) {
    throw 'YAML_PARSER_DEPENDENCY_UNAVAILABLE / prepare existing backend dependencies before ContractOnly'
}
[xml] $bom = Get-Content -LiteralPath $bomPath -Raw
$yamlVersion = [string]$bom.project.properties.'snakeyaml.version'
if ($yamlVersion -cnotmatch '^[0-9]+\.[0-9]+(?:\.[0-9]+)?$') { throw 'YAML_PARSER_BOM_IDENTITY_INVALID' }
$parserJar = Join-Path $MavenRepositoryRoot "org/yaml/snakeyaml/$yamlVersion/snakeyaml-$yamlVersion.jar"
if (-not (Test-Path -LiteralPath $parserJar -PathType Leaf)) {
    throw 'YAML_PARSER_DEPENDENCY_UNAVAILABLE / prepare existing backend dependencies before ContractOnly'
}
$java = (Get-Command java -CommandType Application -ErrorAction Stop | Select-Object -First 1).Source
$source = Join-Path $PSScriptRoot 'NqWorkflowYaml.java'
# Java 21 source launcher compiles in memory; no generated class/cache is trusted or retained.
try { $output = @(& $java -cp $parserJar $source ([IO.Path]::GetFullPath($WorkflowPath)) 2>&1) }
catch { throw 'YAML_SEMANTIC_PARSE_REJECTED / parser process failed' }
if ($LASTEXITCODE -ne 0) { throw 'YAML_SEMANTIC_PARSE_REJECTED / parser process failed' }
try { $parsed = ($output -join "`n") | ConvertFrom-Json } catch { throw 'YAML_SEMANTIC_PARSE_REJECTED / invalid parser transport' }
if ($null -eq $parsed -or $null -eq $parsed.PSObject.Properties['document'] -or
    $null -eq $parsed.PSObject.Properties['actions']) { throw 'YAML_SEMANTIC_PARSE_REJECTED / missing parser model' }
return $parsed
