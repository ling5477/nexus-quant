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

# CANONICAL_CONFIG_HASH_START
function Get-CanonicalConfigurationHashAlgorithm {
    return "git-canonical-v1"
}

function ConvertTo-CanonicalUInt32Bytes([uint32]$Value) {
    return ,([byte[]]@(
        [byte](($Value -shr 24) -band 0xff),
        [byte](($Value -shr 16) -band 0xff),
        [byte](($Value -shr 8) -band 0xff),
        [byte]($Value -band 0xff)
    ))
}

function ConvertTo-CanonicalUInt64Bytes([uint64]$Value) {
    return ,([byte[]]@(
        [byte](($Value -shr 56) -band 0xff),
        [byte](($Value -shr 48) -band 0xff),
        [byte](($Value -shr 40) -band 0xff),
        [byte](($Value -shr 32) -band 0xff),
        [byte](($Value -shr 24) -band 0xff),
        [byte](($Value -shr 16) -band 0xff),
        [byte](($Value -shr 8) -band 0xff),
        [byte]($Value -band 0xff)
    ))
}

function Write-CanonicalFrameBytes([IO.Stream]$Stream, [byte[]]$Bytes) {
    if ($Bytes.Length -gt 0) { $Stream.Write($Bytes, 0, $Bytes.Length) }
}

function Get-CanonicalConfigurationTextBytes([string]$FullPath, [string]$RelativePath) {
    $raw = [IO.File]::ReadAllBytes($FullPath)
    if ($raw.Length -ge 3 -and $raw[0] -eq 0xef -and $raw[1] -eq 0xbb -and $raw[2] -eq 0xbf) {
        throw "CONFIG_INVALID: UTF-8 BOM is forbidden in canonical configuration input: $RelativePath"
    }
    $strictUtf8 = [Text.UTF8Encoding]::new($false, $true)
    try { $text = $strictUtf8.GetString($raw) }
    catch { throw "CONFIG_INVALID: invalid UTF-8 in canonical configuration input: $RelativePath" }
    $canonical = $text.Replace("`r`n", "`n")
    if ($canonical.Contains("`r")) {
        throw "CONFIG_INVALID: bare CR is forbidden in canonical configuration input: $RelativePath"
    }
    return ,([Text.UTF8Encoding]::new($false).GetBytes($canonical))
}

function Get-CanonicalConfigurationInputPaths([string]$RootPath) {
    $standards = Join-Path $RootPath "docs\standards\java"
    $overlays = @(@("nq-java-domain-overlay.md", "dh-java-domain-overlay.md") | Where-Object {
        Test-Path -LiteralPath (Join-Path $standards $_) -PathType Leaf
    })
    if ($overlays.Count -ne 1) { throw "CONFIG_INVALID: exactly one domain overlay is required" }
    return @(
        "docs/standards/java/common-java-engineering-standard.md",
        "docs/standards/java/java-platform-profile.md",
        "docs/standards/java/spring-platform-profile.md",
        "docs/standards/java/architecture-overlay.md",
        "docs/standards/java/$($overlays[0])",
        "docs/standards/java/alibaba-huangshan-rule-mapping.yaml",
        "docs/standards/java/java-rule-exceptions.yaml",
        "docs/standards/java/java-shadow-scope.json",
        "docs/standards/java/platform-profile.json",
        "scripts/java-standard/invoke-java-shadow-scan.ps1"
    )
}

function Get-CanonicalConfigurationHash([string]$RootPath, [string[]]$RelativePaths) {
    $root = [IO.Path]::GetFullPath($RootPath).TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)
    $normalized = New-Object System.Collections.Generic.List[string]
    $seen = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($inputPath in $RelativePaths) {
        $path = ([string]$inputPath).Replace("\", "/")
        $segments = @($path.Split('/'))
        if ([string]::IsNullOrWhiteSpace($path) -or [IO.Path]::IsPathRooted($path) -or
            $segments.Count -eq 0 -or @($segments | Where-Object { $_ -eq "" -or $_ -eq "." -or $_ -eq ".." }).Count -gt 0) {
            throw "CONFIG_INVALID: invalid canonical configuration path: $inputPath"
        }
        if (-not $seen.Add($path)) { throw "CONFIG_INVALID: duplicate canonical configuration path: $path" }
        $normalized.Add($path)
    }
    $paths = [string[]]$normalized.ToArray()
    [Array]::Sort($paths, [StringComparer]::Ordinal)

    $stream = [IO.MemoryStream]::new()
    try {
        $utf8 = [Text.UTF8Encoding]::new($false)
        $magic = [Text.Encoding]::ASCII.GetBytes("NQDH-SHADOW-CONFIG")
        $algorithm = $utf8.GetBytes((Get-CanonicalConfigurationHashAlgorithm))
        Write-CanonicalFrameBytes $stream (ConvertTo-CanonicalUInt32Bytes ([uint32]$magic.Length))
        Write-CanonicalFrameBytes $stream $magic
        Write-CanonicalFrameBytes $stream (ConvertTo-CanonicalUInt32Bytes ([uint32]$algorithm.Length))
        Write-CanonicalFrameBytes $stream $algorithm
        Write-CanonicalFrameBytes $stream (ConvertTo-CanonicalUInt32Bytes ([uint32]$paths.Length))
        foreach ($path in $paths) {
            $nativePath = $path.Replace([char]'/', [IO.Path]::DirectorySeparatorChar)
            $fullPath = [IO.Path]::GetFullPath((Join-Path $root $nativePath))
            $comparison = if ([IO.Path]::DirectorySeparatorChar -eq [char]'\') { [StringComparison]::OrdinalIgnoreCase } else { [StringComparison]::Ordinal }
            if (-not $fullPath.StartsWith($root + [IO.Path]::DirectorySeparatorChar, $comparison) -or
                -not (Test-Path -LiteralPath $fullPath -PathType Leaf)) {
                throw "CONFIG_INVALID: missing or escaping canonical configuration input: $path"
            }
            $pathBytes = $utf8.GetBytes($path)
            $contentBytes = Get-CanonicalConfigurationTextBytes $fullPath $path
            Write-CanonicalFrameBytes $stream (ConvertTo-CanonicalUInt32Bytes ([uint32]$pathBytes.Length))
            Write-CanonicalFrameBytes $stream $pathBytes
            Write-CanonicalFrameBytes $stream (ConvertTo-CanonicalUInt64Bytes ([uint64]$contentBytes.Length))
            Write-CanonicalFrameBytes $stream $contentBytes
        }
        $sha = [Security.Cryptography.SHA256]::Create()
        try { return ([BitConverter]::ToString($sha.ComputeHash($stream.ToArray()))).Replace("-", "").ToLowerInvariant() }
        finally { $sha.Dispose() }
    }
    finally { $stream.Dispose() }
}
# CANONICAL_CONFIG_HASH_END

try {
    $required = @(
        "README.md", "common-java-engineering-standard.md", "java-platform-profile.md", "spring-platform-profile.md", "architecture-overlay.md",
        "alibaba-huangshan-rule-mapping.yaml", "songshan-to-huangshan-diff.yaml",
        "java-rule-exceptions.yaml", "java-shadow-scope.json", "platform-profile.json", "source-provenance.json", "source-history.json", "shadow-baseline.json"
    )
    foreach ($name in $required) { Assert-Condition (Test-Path -LiteralPath (Join-Path $standardRoot $name) -PathType Leaf) "CONFIG_INVALID" "missing docs/standards/java/$name" }
    $overlays = @(@("nq-java-domain-overlay.md", "dh-java-domain-overlay.md") | Where-Object { Test-Path -LiteralPath (Join-Path $standardRoot $_) -PathType Leaf })
    Assert-Condition ($overlays.Count -eq 1) "CONFIG_INVALID" "exactly one domain overlay is required"
    $configInputPaths = Get-CanonicalConfigurationInputPaths $repoRoot
    $configurationHashAlgorithm = Get-CanonicalConfigurationHashAlgorithm
    $currentCanonicalConfigurationHash = Get-CanonicalConfigurationHash $repoRoot $configInputPaths

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
    try { [xml]$pomXml = $pomText }
    catch { throw "CONFIG_INVALID: root Maven POM is not valid XML" }
    $qualityProfilePresent = @($pomXml.SelectNodes("//*[local-name()='profile']") | Where-Object {
        $idNode = $_.SelectSingleNode("./*[local-name()='id']")
        $null -ne $idNode -and $idNode.InnerText.Trim() -ceq 'quality'
    }).Count -gt 0
    $qualityProfileStatus = if ($qualityProfilePresent) { 'AVAILABLE' } else { 'NOT_AVAILABLE' }
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
    $baselineAlgorithmProperty = $baseline.PSObject.Properties['configuration_hash_algorithm']
    Assert-Condition ($null -ne $baselineAlgorithmProperty -and [string]$baselineAlgorithmProperty.Value -eq $configurationHashAlgorithm) "BASELINE_SCHEMA_INVALID" "baseline configuration hash algorithm mismatch"
    Assert-Condition ($baseline.configuration_sha256 -match '^[0-9a-f]{64}$') "BASELINE_SCHEMA_INVALID" "baseline config hash invalid"
    Assert-Condition ($baseline.configuration_sha256 -eq $currentCanonicalConfigurationHash) "BASELINE_CONFIGURATION_HASH_MISMATCH" "baseline=$($baseline.configuration_sha256) current=$currentCanonicalConfigurationHash"
    Assert-Condition ($baseline.baseline_head -match '^[0-9a-f]{40}$') "BASELINE_SCHEMA_INVALID" "baseline HEAD identity invalid"
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

    $v40VerifierPath = Join-Path $PSScriptRoot 'verify-v40-migration-git-blob.ps1'
    Assert-Condition (Test-Path -LiteralPath $v40VerifierPath -PathType Leaf) "CONFIG_INVALID" "V40 exact Git blob verifier is missing"
    $currentPwsh = [Diagnostics.Process]::GetCurrentProcess().MainModule.FileName
    $v40VerifierOutput = @(& $currentPwsh -NoProfile -File $v40VerifierPath -RepositoryRoot $repoRoot 2>&1 | ForEach-Object { $_.ToString() })
    Assert-Condition ($LASTEXITCODE -eq 0 -and $v40VerifierOutput -contains 'V40_GIT_BLOB_CONTRACT=PASS') "CONFIG_INVALID" "V40 exact Git blob contract failed"

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
    Write-Output "BASELINE_HEAD=$($baseline.baseline_head)"
    Write-Output "CONFIGURATION_HASH_ALGORITHM=$configurationHashAlgorithm"
    Write-Output "CURRENT_CANONICAL_CONFIG_HASH=$currentCanonicalConfigurationHash"
    Write-Output "CONFIG_HASH_INPUTS=$($configInputPaths -join ',')"
    Write-Output "QUALITY_PROFILE=$qualityProfileStatus"
    Write-Output 'CURRENT_ACTIVE_SONGSHAN_INPUT_COUNT=0'
    Write-Output 'SONGSHAN_MAPPING_STATUS=HISTORY_ONLY'
    $v40VerifierOutput | Write-Output
    Write-Output "COMMON_STANDARD_SHA256=$commonHash"
    exit 0
}
catch {
    $message = $_.Exception.Message
    Write-Output 'GOVERNANCE_CHECKER_RESULT=FAIL'
    if ($message -match '^(CONFIG_INVALID|MAPPING_INVALID|PLATFORM_PROFILE_INVALID|BASELINE_SCHEMA_INVALID|BASELINE_CONFIGURATION_HASH_MISMATCH|RULE_ID_COLLISION):') { [Console]::Error.WriteLine($message); exit 2 }
    [Console]::Error.WriteLine("CHECKER_EXECUTION_FAILED: $message"); exit 3
}
