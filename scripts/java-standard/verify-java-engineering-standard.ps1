[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$standardRoot = Join-Path $repoRoot "docs\standards\java"

function Assert-Condition([bool]$Condition, [string]$Type, [string]$Message) {
    if (-not $Condition) { throw "$Type`: $Message" }
}

function Get-Sha256Text([string]$Text) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($Text)))).Replace("-", "").ToLowerInvariant() }
    finally { $sha.Dispose() }
}

try {
    $required = @(
        "README.md", "common-java-engineering-standard.md", "java-platform-profile.md", "spring-platform-profile.md", "architecture-overlay.md",
        "alibaba-huangshan-rule-mapping.yaml", "alibaba-songshan-rule-mapping.yaml", "songshan-to-huangshan-diff.yaml",
        "java-rule-exceptions.yaml", "java-shadow-scope.json", "platform-profile.json", "source-provenance.json", "source-history.json", "shadow-baseline.json"
    )
    foreach ($name in $required) { Assert-Condition (Test-Path -LiteralPath (Join-Path $standardRoot $name) -PathType Leaf) "CONFIG_INVALID" "missing docs/standards/java/$name" }
    $overlays = @(@("nq-java-domain-overlay.md", "dh-java-domain-overlay.md") | Where-Object { Test-Path -LiteralPath (Join-Path $standardRoot $_) -PathType Leaf })
    Assert-Condition ($overlays.Count -eq 1) "CONFIG_INVALID" "exactly one domain overlay is required"

    $provenance = Get-Content -LiteralPath (Join-Path $standardRoot "source-provenance.json") -Raw -Encoding UTF8 | ConvertFrom-Json
    Assert-Condition ($provenance.schema_version -eq "2.0.0") "CONFIG_INVALID" "unsupported provenance schema"
    Assert-Condition ($provenance.edition -eq "Huangshan" -and $provenance.status -eq "CURRENT_EXTERNAL_REFERENCE") "CONFIG_INVALID" "Huangshan must be current external reference"
    Assert-Condition ($provenance.official_repository -eq "https://github.com/alibaba/p3c") "CONFIG_INVALID" "untrusted source repository"
    Assert-Condition ($provenance.source_ref -match '^[0-9a-f]{40}$' -and $provenance.source_blob_identity -match '^[0-9a-f]{40}$' -and $provenance.source_sha256 -match '^[0-9a-f]{64}$') "CONFIG_INVALID" "invalid Huangshan identities"
    Assert-Condition ([int]$provenance.source_rule_count -eq 319) "CONFIG_INVALID" "unexpected Huangshan rule count"
    $history = Get-Content -LiteralPath (Join-Path $standardRoot "source-history.json") -Raw -Encoding UTF8 | ConvertFrom-Json
    $songshan = @($history.lineage | Where-Object { $_.edition -eq 'Songshan' })
    $huangshan = @($history.lineage | Where-Object { $_.edition -eq 'Huangshan' })
    Assert-Condition ($songshan.Count -eq 1 -and $songshan[0].status -eq 'SUPERSEDED' -and $songshan[0].superseded_by -eq 'Huangshan 1.7.1') "CONFIG_INVALID" "Songshan lineage invalid"
    Assert-Condition ($huangshan.Count -eq 1 -and $huangshan[0].status -eq 'CURRENT_EXTERNAL_REFERENCE' -and $huangshan[0].source_sha256 -eq $provenance.source_sha256) "CONFIG_INVALID" "Huangshan lineage invalid"

    $platform = Get-Content -LiteralPath (Join-Path $standardRoot "platform-profile.json") -Raw -Encoding UTF8 | ConvertFrom-Json
    Assert-Condition ($platform.schema_version -eq '1.0.0') "PLATFORM_PROFILE_INVALID" "unsupported platform schema"
    Assert-Condition ($platform.java.status -eq 'CONSISTENT' -and $platform.spring.status -eq 'CONSISTENT') "PLATFORM_PROFILE_INVALID" "platform authority conflict"
    Assert-Condition ([int]$platform.java.compiler_release -eq [int]$platform.java.ci_version) "PLATFORM_PROFILE_INVALID" "compiler release differs from CI Java"
    Assert-Condition (-not [bool]$platform.java.preview_enabled -and -not [bool]$platform.java.virtual_threads_enabled) "PLATFORM_PROFILE_INVALID" "task must not enable preview or virtual threads"
    foreach ($version in @($platform.spring.boot, $platform.spring.framework, $platform.testing.junit_jupiter, $platform.testing.mockito, $platform.database.postgresql_driver, $platform.database.flyway)) { Assert-Condition ($version -match '^\d+\.\d+\.\d+') "PLATFORM_PROFILE_INVALID" "invalid effective dependency version $version" }
    $pomPath = if (Test-Path -LiteralPath (Join-Path $repoRoot 'backend\pom.xml')) { Join-Path $repoRoot 'backend\pom.xml' } else { Join-Path $repoRoot 'dh-bom\pom.xml' }
    $pomText = Get-Content -LiteralPath $pomPath -Raw -Encoding UTF8
    $javaMatch = [regex]::Match($pomText, '<maven\.compiler\.release>(?:\$\{java\.version\}|(\d+))</maven\.compiler\.release>')
    Assert-Condition ($javaMatch.Success) "PLATFORM_PROFILE_INVALID" "compiler release declaration missing"
    $declaredRelease = if ($javaMatch.Groups[1].Success) { [int]$javaMatch.Groups[1].Value } else { $jv = [regex]::Match($pomText, '<java\.version>(\d+)</java\.version>'); Assert-Condition $jv.Success "PLATFORM_PROFILE_INVALID" "java.version missing"; [int]$jv.Groups[1].Value }
    Assert-Condition ($declaredRelease -eq [int]$platform.java.compiler_release) "PLATFORM_PROFILE_INVALID" "profile compiler release differs from POM"
    $bootMatch = [regex]::Match($pomText, '<spring\.boot\.version>([^<]+)</spring\.boot\.version>')
    if (-not $bootMatch.Success) { $bootMatch = [regex]::Match($pomText, '(?s)<parent>.*?<artifactId>spring-boot-starter-parent</artifactId>\s*<version>([^<]+)</version>') }
    Assert-Condition ($bootMatch.Success -and $bootMatch.Groups[1].Value -eq [string]$platform.spring.boot) "PLATFORM_PROFILE_INVALID" "Spring Boot profile differs from POM"
    $ciText = Get-Content -LiteralPath (Join-Path $repoRoot '.github\workflows\ci.yml') -Raw -Encoding UTF8
    $ciJavaPattern = 'java-version:\s*[''"]?' + [regex]::Escape([string]$platform.java.ci_version) + '[''"]?'
    Assert-Condition ($ciText -match $ciJavaPattern) "PLATFORM_PROFILE_INVALID" "CI Java does not match platform profile"

    $songshanMapping = Get-Content -LiteralPath (Join-Path $standardRoot "alibaba-songshan-rule-mapping.yaml") -Raw -Encoding UTF8
    Assert-Condition ($songshanMapping -match '(?m)^status:\s*"SUPERSEDED"\s*$') "MAPPING_INVALID" "Songshan mapping still appears current"
    $mapping = Get-Content -LiteralPath (Join-Path $standardRoot "alibaba-huangshan-rule-mapping.yaml") -Raw -Encoding UTF8
    Assert-Condition ($mapping -match '(?m)^status:\s*"CURRENT_EXTERNAL_REFERENCE"\s*$') "MAPPING_INVALID" "Huangshan mapping is not current"
    $sourceIds = @([regex]::Matches($mapping, '(?m)^\s*- source_rule_id:\s*"([^"\r\n]+)"') | ForEach-Object { $_.Groups[1].Value })
    Assert-Condition ($sourceIds.Count -eq 319 -and ($sourceIds | Sort-Object -Unique).Count -eq 319) "MAPPING_INVALID" "Huangshan mapping must contain 319 unique rules"
    $dispositions = @([regex]::Matches($mapping, '(?m)^\s+disposition:\s*"([A-Z_]+)"') | ForEach-Object { $_.Groups[1].Value })
    Assert-Condition ($dispositions.Count -eq 319) "MAPPING_INVALID" "each rule requires disposition"
    foreach ($d in $dispositions) { Assert-Condition (@('ADOPTED','MODIFIED','REJECTED','NOT_APPLICABLE') -contains $d) "MAPPING_INVALID" "invalid disposition $d" }
    foreach ($axis in @('java','spring','architecture')) {
        $count = ([regex]::Matches($mapping, "(?m)^\s{6}$axis`:\s*$")).Count
        Assert-Condition ($count -eq 319) "MAPPING_INVALID" "compatibility axis $axis incomplete"
    }
    $catalogIds = @([regex]::Matches($mapping, '(?m)^\s*- rule_id:\s*"([^"\r\n]+)"') | ForEach-Object { $_.Groups[1].Value })
    Assert-Condition ($catalogIds.Count -gt 0 -and ($catalogIds | Sort-Object -Unique).Count -eq $catalogIds.Count) "RULE_ID_COLLISION" "mapping catalog rule collision"
    $references = @([regex]::Matches($mapping, '(?m)^\s+project_rule_ids:\s*\["([^"\r\n]+)"\]') | ForEach-Object { $_.Groups[1].Value })
    foreach ($reference in $references) { Assert-Condition ($catalogIds -contains $reference) "MAPPING_INVALID" "unknown project rule $reference" }

    $diff = Get-Content -LiteralPath (Join-Path $standardRoot "songshan-to-huangshan-diff.yaml") -Raw -Encoding UTF8
    $diffHuangshanIds = @([regex]::Matches($diff, '(?m)^\s+huangshan_rule_id:\s*"([^"\r\n]+)"') | ForEach-Object { $_.Groups[1].Value })
    Assert-Condition ($diffHuangshanIds.Count -eq 319 -and ($diffHuangshanIds | Sort-Object -Unique).Count -eq 319) "MAPPING_INVALID" "lineage diff is incomplete"
    foreach ($requiredCount in @('UNCHANGED','MODIFIED','ADDED','REMOVED','RENUMBERED_OR_RELOCATED')) { Assert-Condition ($diff -match "(?m)^\s+$requiredCount`:\s+\d+\s*$") "MAPPING_INVALID" "diff count missing $requiredCount" }

    $ruleDocuments = @('common-java-engineering-standard.md','java-platform-profile.md','spring-platform-profile.md','architecture-overlay.md',$overlays[0])
    $documentedRuleIds = @()
    foreach ($doc in $ruleDocuments) { $text = Get-Content -LiteralPath (Join-Path $standardRoot $doc) -Raw -Encoding UTF8; $documentedRuleIds += @([regex]::Matches($text, '(?m)^\s*- `([A-Z][A-Z0-9-]+)`：') | ForEach-Object { $_.Groups[1].Value }) }
    Assert-Condition ($documentedRuleIds.Count -gt 0 -and ($documentedRuleIds | Sort-Object -Unique).Count -eq $documentedRuleIds.Count) "RULE_ID_COLLISION" "documented project rule collision"
    $allProjectRuleIds = @($catalogIds + $documentedRuleIds | Sort-Object -Unique)

    $exceptionsText = Get-Content -LiteralPath (Join-Path $standardRoot 'java-rule-exceptions.yaml') -Raw -Encoding UTF8
    Assert-Condition ($exceptionsText -match '(?m)^schema_version:\s*"1\.0\.0"\s*$') "CONFIG_INVALID" "invalid exception schema"
    if ($exceptionsText -notmatch '(?m)^exceptions:\s*\[\]\s*$') {
        $blocks = [regex]::Matches($exceptionsText, '(?ms)^\s*- exception_id:.*?(?=^\s*- exception_id:|\z)')
        foreach ($block in $blocks) {
            foreach ($field in @('exception_id','rule_id','scope','reason','authority_reference','created_at_utc','expires_when','new_usages_allowed')) { Assert-Condition ($block.Value -match "(?m)^\s+$field\s*:") "CONFIG_INVALID" "exception missing $field" }
            $ruleMatch = [regex]::Match($block.Value, '(?m)^\s+rule_id:\s*"([^"\r\n]+)"')
            Assert-Condition ($ruleMatch.Success -and $allProjectRuleIds -contains $ruleMatch.Groups[1].Value) "CONFIG_INVALID" "exception references unknown rule"
            Assert-Condition ($block.Value -match '(?m)^\s+new_usages_allowed:\s*false\s*$' -and $block.Value -notmatch '(?m)^\s+scope:\s*"?\*\*?"?\s*$') "CONFIG_INVALID" "unsafe exception"
        }
    }

    $skillName = if ($overlays[0] -eq 'nq-java-domain-overlay.md') { 'nq-java-engineering-standard' } else { 'dh-java-engineering-standard' }
    $skillPath = Join-Path $repoRoot ".agents\skills\$skillName\SKILL.md"
    Assert-Condition (Test-Path -LiteralPath $skillPath -PathType Leaf) "CONFIG_INVALID" "missing project Skill"
    $skillText = Get-Content -LiteralPath $skillPath -Raw -Encoding UTF8
    foreach ($reference in @('platform-profile.json','common-java-engineering-standard.md','java-platform-profile.md','spring-platform-profile.md','architecture-overlay.md',$overlays[0],'alibaba-huangshan-rule-mapping.yaml','java-rule-exceptions.yaml')) { Assert-Condition ($skillText.Contains($reference)) "CONFIG_INVALID" "Skill missing $reference" }
    Assert-Condition ($skillText -notmatch '\bJava\s+21\b|Spring Boot\s+3\.\d|Spring Framework\s+6\.\d') "PLATFORM_PROFILE_INVALID" "Skill hard-codes platform versions"
    Assert-Condition ($skillText -notmatch '\bGate[A-Z0-9-]+\b|\bStage-QDR-\d+\b') "CONFIG_INVALID" "Skill hard-codes current authority"

    $scope = Get-Content -LiteralPath (Join-Path $standardRoot 'java-shadow-scope.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    Assert-Condition ($scope.scanner_mode -eq 'JAVA_LEXICAL_STRUCTURE_AWARE' -and @($scope.production_source_roots).Count -gt 0 -and @($scope.time_restricted_prefixes).Count -gt 0) "CONFIG_INVALID" "architecture scope invalid"
    foreach ($source in @($scope.architecture_sources)) { Assert-Condition (Test-Path -LiteralPath (Join-Path $repoRoot $source) -PathType Leaf) "CONFIG_INVALID" "missing architecture source $source" }

    $baseline = Get-Content -LiteralPath (Join-Path $standardRoot 'shadow-baseline.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    Assert-Condition ($baseline.schema_version -eq '2.0.0' -and $baseline.current_ruleset_version -eq 'huangshan-platform-2.0.0') "BASELINE_SCHEMA_INVALID" "baseline schema/ruleset invalid"
    Assert-Condition ($baseline.configuration_sha256 -match '^[0-9a-f]{64}$') "BASELINE_SCHEMA_INVALID" "baseline config hash invalid"
    Assert-Condition ([int]$baseline.current_count -eq [int]$baseline.violation_count) "BASELINE_SCHEMA_INVALID" "baseline count mismatch"
    Assert-Condition ([int]$baseline.existing_baseline_count + [int]$baseline.ruleset_expansion_count + [int]$baseline.new_code_count -eq [int]$baseline.current_count) "BASELINE_SCHEMA_INVALID" "baseline classifications do not sum"
    $projection = @($baseline.violations | ForEach-Object { [pscustomobject]@{ rule_id = $_.rule_id; path = $_.path; classification = $_.classification; fingerprint = $_.fingerprint } })
    Assert-Condition ($baseline.deterministic_content_sha256 -eq (Get-Sha256Text ($projection | ConvertTo-Json -Depth 5 -Compress))) "BASELINE_SCHEMA_INVALID" "baseline deterministic hash mismatch"
    $actual = @($baseline.violations | ForEach-Object { $_.fingerprint }); $sorted = @($baseline.violations | Sort-Object rule_id,path,fingerprint | ForEach-Object { $_.fingerprint })
    Assert-Condition (($actual -join "`n") -eq ($sorted -join "`n")) "BASELINE_SCHEMA_INVALID" "baseline order nondeterministic"

    $governed = @(); $governed += @(Get-ChildItem -LiteralPath $standardRoot -File); $governed += @(Get-Item $skillPath); $governed += @(Get-ChildItem -LiteralPath $PSScriptRoot -File)
    foreach ($file in $governed) { $text = Get-Content -LiteralPath $file.FullName -Raw -Encoding UTF8; Assert-Condition ($text -notmatch '(?i)[A-Z]:[\\/](Users|project)[\\/]') "CONFIG_INVALID" "absolute path in $($file.Name)" }
    $links = @(); foreach ($scanRoot in @($standardRoot,(Split-Path $skillPath -Parent),$PSScriptRoot)) { $links += @(Get-ChildItem -LiteralPath $scanRoot -Recurse -Force | Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint }) }
    Assert-Condition ($links.Count -eq 0) "CONFIG_INVALID" "reparse point or cross-repository link found"
    Assert-Condition ($ciText.Contains('Java engineering standard Shadow') -and $ciText.Contains('invoke-java-shadow-scan.ps1') -and $ciText -notmatch '(?ms)Java engineering standard Shadow.*?continue-on-error:\s*true') "CONFIG_INVALID" "CI Shadow contract invalid"

    $commonHash = (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $standardRoot 'common-java-engineering-standard.md')).Hash.ToLowerInvariant()
    Write-Output 'GOVERNANCE_CHECKER_RESULT=PASS'
    Write-Output "JAVA_PLATFORM=release-$($platform.java.compiler_release)"
    Write-Output "SPRING_PLATFORM=boot-$($platform.spring.boot)_framework-$($platform.spring.framework)"
    Write-Output "HUANGSHAN_RULE_COUNT=$($sourceIds.Count)"
    Write-Output "MAPPING_CATALOG_RULE_COUNT=$($catalogIds.Count)"
    Write-Output "DOCUMENTED_PROJECT_RULE_COUNT=$($documentedRuleIds.Count)"
    Write-Output "BASELINE_EXISTING=$($baseline.existing_baseline_count)"
    Write-Output "BASELINE_RULESET_EXPANSION=$($baseline.ruleset_expansion_count)"
    Write-Output "BASELINE_NEW_CODE=$($baseline.new_code_count)"
    Write-Output "COMMON_STANDARD_SHA256=$commonHash"
    exit 0
}
catch {
    $message = $_.Exception.Message
    if ($message -match '^(CONFIG_INVALID|MAPPING_INVALID|PLATFORM_PROFILE_INVALID|BASELINE_SCHEMA_INVALID|RULE_ID_COLLISION):') { Write-Error $message; exit 2 }
    Write-Error "CHECKER_EXECUTION_FAILED: $message"; exit 3
}
