[CmdletBinding()]
param(
    [string]$OutputPath = "artifacts/java-shadow/shadow-report.json",
    [string]$BaselinePath = "docs/standards/java/shadow-baseline.json",
    [switch]$UpdateBaseline
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$checkerVersion = "2.0.0-powershell-lexical"
$rulesetVersion = "huangshan-platform-2.0.0"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path

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

function Get-Sha256Text([string]$Text) {
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        return ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($Text)))).Replace("-", "").ToLowerInvariant()
    }
    finally { $sha.Dispose() }
}

function Get-RepoPath([string]$FullName) {
    return $FullName.Substring($repoRoot.Length + 1).Replace("\", "/")
}

function Resolve-RepoFile([string]$Path) {
    $resolved = if ([IO.Path]::IsPathRooted($Path)) { [IO.Path]::GetFullPath($Path) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $Path)) }
    if (-not $resolved.StartsWith($repoRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw "CONFIG_INVALID: path escapes repository: $Path"
    }
    return $resolved
}

function Get-JavaLexicalLines([string[]]$Lines) {
    $result = New-Object System.Collections.Generic.List[string]
    $inBlock = $false
    $inTextBlock = $false
    foreach ($line in $Lines) {
        $builder = [Text.StringBuilder]::new()
        $inString = $false
        $inChar = $false
        $escaped = $false
        for ($i = 0; $i -lt $line.Length; $i++) {
            $ch = $line[$i]
            $next = if ($i + 1 -lt $line.Length) { $line[$i + 1] } else { [char]0 }
            $next2 = if ($i + 2 -lt $line.Length) { $line[$i + 2] } else { [char]0 }
            if ($inBlock) {
                if ($ch -eq '*' -and $next -eq '/') { $null = $builder.Append('  '); $i++; $inBlock = $false } else { $null = $builder.Append(' ') }
                continue
            }
            if ($inTextBlock) {
                if ($ch -eq '"' -and $next -eq '"' -and $next2 -eq '"') { $null = $builder.Append('   '); $i += 2; $inTextBlock = $false } else { $null = $builder.Append(' ') }
                continue
            }
            if ($inString) {
                $null = $builder.Append(' ')
                if ($escaped) { $escaped = $false }
                elseif ($ch -eq '\') { $escaped = $true }
                elseif ($ch -eq '"') { $inString = $false }
                continue
            }
            if ($inChar) {
                $null = $builder.Append(' ')
                if ($escaped) { $escaped = $false }
                elseif ($ch -eq '\') { $escaped = $true }
                elseif ($ch -eq "'") { $inChar = $false }
                continue
            }
            if ($ch -eq '/' -and $next -eq '/') { $null = $builder.Append(' ' * ($line.Length - $i)); break }
            if ($ch -eq '/' -and $next -eq '*') { $null = $builder.Append('  '); $i++; $inBlock = $true; continue }
            if ($ch -eq '"' -and $next -eq '"' -and $next2 -eq '"') { $null = $builder.Append('   '); $i += 2; $inTextBlock = $true; continue }
            if ($ch -eq '"') { $null = $builder.Append(' '); $inString = $true; continue }
            if ($ch -eq "'") { $null = $builder.Append(' '); $inChar = $true; continue }
            $null = $builder.Append($ch)
        }
        $result.Add($builder.ToString())
    }
    return @($result)
}

function Get-InvocationArgumentCount([string]$Text, [int]$OpenParenIndex) {
    $depth = 0
    $commas = 0
    for ($i = $OpenParenIndex; $i -lt $Text.Length; $i++) {
        $ch = $Text[$i]
        if ($ch -eq '(') { $depth++ }
        elseif ($ch -eq ')') {
            $depth--
            if ($depth -eq 0) { return $commas + 1 }
        }
        elseif ($ch -eq ',' -and $depth -eq 1) { $commas++ }
    }
    return -1
}

try {
    $standardsRoot = Join-Path $repoRoot "docs\standards\java"
    $overlayCandidates = @(@(
        Join-Path $standardsRoot "nq-java-domain-overlay.md"
        Join-Path $standardsRoot "dh-java-domain-overlay.md"
    ) | Where-Object { Test-Path -LiteralPath $_ })
    if ($overlayCandidates.Count -ne 1) { throw "CONFIG_INVALID: exactly one domain overlay is required" }

    $scopePath = Join-Path $standardsRoot "java-shadow-scope.json"
    $platformPath = Join-Path $standardsRoot "platform-profile.json"
    $mappingPath = Join-Path $standardsRoot "alibaba-huangshan-rule-mapping.yaml"
    $configInputPaths = Get-CanonicalConfigurationInputPaths $repoRoot
    $configurationHashAlgorithm = Get-CanonicalConfigurationHashAlgorithm

    $scope = Get-Content -LiteralPath $scopePath -Raw -Encoding UTF8 | ConvertFrom-Json
    $platform = Get-Content -LiteralPath $platformPath -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($scope.schema_version -ne "1.0.0") { throw "CONFIG_INVALID: unsupported architecture scope schema" }
    if ($platform.schema_version -ne "1.0.0" -or $platform.java.status -ne "CONSISTENT" -or $platform.spring.status -ne "CONSISTENT") { throw "PLATFORM_PROFILE_INVALID: inconsistent platform profile" }
    if ([int]$platform.java.compiler_release -lt 17) { throw "PLATFORM_PROFILE_INVALID: unsupported compiler release" }

    $configurationHash = Get-CanonicalConfigurationHash $repoRoot $configInputPaths
    $excludedSegments = @($scope.excluded_segments)
    $productionRoots = @($scope.production_source_roots)
    $timeRestrictedPrefixes = @($scope.time_restricted_prefixes)
    $mainMarker = [string]$scope.production_source_marker
    $testMarker = if ($scope.PSObject.Properties.Name -contains 'test_source_marker') { [string]$scope.test_source_marker } else { "/src/test/java/" }

    $changedJava = @{}
    foreach ($statusLine in @(git -C $repoRoot status --short --untracked-files=all -- '*.java')) {
        if ($LASTEXITCODE -ne 0) { throw "CHECKER_EXECUTION_FAILED: git status failed" }
        if ($statusLine.Length -ge 4) { $changedJava[$statusLine.Substring(3).Trim('"').Replace('\', '/')] = $true }
    }
    $currentBaselineHead = (git -C $repoRoot rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0 -or $currentBaselineHead -notmatch '^[0-9a-f]{40}$') { throw "CHECKER_EXECUTION_FAILED: current HEAD resolution failed" }
    $rulesetExpansionRuleIds = @(
        "JAVA-LEGACY-DATE-IN-DOMAIN", "JAVA-RAW-THREAD", "JAVA-UNMANAGED-EXECUTOR", "JAVA-COMMON-POOL-ASYNC",
        "SPRING-FIELD-INJECTION", "SPRING-LEGACY-JAVAX-PERSISTENCE", "SPRING-LEGACY-JAVAX-VALIDATION", "SPRING-LEGACY-JAVAX-SERVLET",
        "SPRING-TRANSACTION-PRIVATE-METHOD", "SPRING-DEPRECATED-TEST-ANNOTATION"
    )

    $findings = New-Object System.Collections.Generic.List[object]
    $occurrences = @{}
    function Add-Finding([string]$RuleId, [string]$LegacyRuleId, [string]$Severity, [string]$ArchitectureScope, [string]$Path, [int]$LineNumber, [string]$Summary, [string]$EvidenceLine) {
        $normalized = ($EvidenceLine.Trim() -replace '\s+', ' ')
        $base = "$RuleId|$Path|$normalized"
        if (-not $occurrences.ContainsKey($base)) { $occurrences[$base] = 0 }
        $occurrences[$base]++
        $fingerprint = (Get-Sha256Text "$base|$($occurrences[$base])").Substring(0, 24)
        $legacyFingerprint = $null
        if ($LegacyRuleId) { $legacyFingerprint = (Get-Sha256Text "$LegacyRuleId|$Path|$normalized|$($occurrences[$base])").Substring(0, 24) }
        $findings.Add([pscustomobject]@{
            rule_id = $RuleId
            legacy_rule_id = $LegacyRuleId
            path = $Path
            symbol_or_line = "line:$LineNumber"
            summary = $Summary
            severity = $Severity
            architecture_scope = $ArchitectureScope
            fingerprint = $fingerprint
            legacy_fingerprint = $legacyFingerprint
            checker_version = $checkerVersion
            ruleset_version = $rulesetVersion
            configuration_sha256 = $configurationHash
        })
    }

    $javaFiles = Get-ChildItem -LiteralPath $repoRoot -Recurse -File -Filter "*.java" | Where-Object {
        $relative = Get-RepoPath $_.FullName
        $segments = $relative.Split('/')
        $inRoot = @($productionRoots | Where-Object { $relative.StartsWith($_ + "/", [StringComparison]::Ordinal) }).Count -gt 0
        $inRoot -and ($relative.Contains($mainMarker) -or $relative.Contains($testMarker)) -and -not ($segments | Where-Object { $excludedSegments -contains $_ })
    } | Sort-Object { Get-RepoPath $_.FullName }

    foreach ($javaFile in $javaFiles) {
        $relative = Get-RepoPath $javaFile.FullName
        $rawLines = [IO.File]::ReadAllLines($javaFile.FullName)
        $lexLines = Get-JavaLexicalLines $rawLines
        $isMain = $relative.Contains($mainMarker)
        $isTest = $relative.Contains($testMarker)
        $isTimeRestricted = @($timeRestrictedPrefixes | Where-Object { $relative.StartsWith($_, [StringComparison]::Ordinal) }).Count -gt 0

        for ($i = 0; $i -lt $rawLines.Length; $i++) {
            $raw = $rawLines[$i]
            $lex = $lexLines[$i]
            $lineNo = $i + 1
            if ($isMain -and $isTimeRestricted -and $lex -match '\b(System\.currentTimeMillis|Instant\.now|LocalDateTime\.now|ZonedDateTime\.now)\s*\(') { Add-Finding "JAVA-TIME-DIRECT-READ" "JAVA-COMMON-TIME-001" "P2" "DOMAIN_APPLICATION" $relative $lineNo "受限 domain/application 层直接读取系统时间" $raw }
            if ($isMain -and $isTimeRestricted -and $lex -match '\bnew\s+Date\s*\(|\bCalendar\.getInstance\s*\(') { Add-Finding "JAVA-LEGACY-DATE-IN-DOMAIN" $null "P2" "DOMAIN_APPLICATION" $relative $lineNo "受限层使用 legacy 日期类型读取当前时间" $raw }
            if ($isMain -and $lex -match '\bnew\s+Thread\s*\(') { Add-Finding "JAVA-RAW-THREAD" $null "P1" "PRODUCTION" $relative $lineNo "生产代码直接创建 raw Thread" $raw }
            if ($isMain -and $lex -match '\bExecutors\.(newCachedThreadPool|newFixedThreadPool|newSingleThreadExecutor|newScheduledThreadPool|newSingleThreadScheduledExecutor|newWorkStealingPool)\s*\(') { $legacy = if ($matches[1] -eq 'newCachedThreadPool') { "JAVA-COMMON-CONCURRENCY-001" } else { $null }; Add-Finding "JAVA-UNMANAGED-EXECUTOR" $legacy "P1" "PRODUCTION" $relative $lineNo "生产代码创建 unmanaged executor，需由架构 owner 复核生命周期与边界" $raw }
            if ($isMain -and $raw -match '\bnew\s+BigDecimal\s*\(\s*(?:[-+]?\d+\.\d+[dDfF]?|[^)]*\.doubleValue\s*\(\s*\))') { Add-Finding "JAVA-COMMON-NUMERIC-001" $null "P1" "PRODUCTION" $relative $lineNo "BigDecimal 可能通过二进制浮点构造" $raw }
            if ($isMain -and $raw -match '\bSystem\.(out|err)\.') { Add-Finding "JAVA-COMMON-LOG-002" $null "P2" "PRODUCTION" $relative $lineNo "生产代码使用 System.out/System.err" $raw }
            if ($isMain -and $raw -match '(?i)\b(log|logger)\.(trace|debug|info|warn|error)\s*\([^;]*(api.?key|secret|passphrase|private.?key|mnemonic|cookie|authorization)') { Add-Finding "JAVA-COMMON-SECRET-001" $null "P1" "PRODUCTION" $relative $lineNo "日志语句可能包含敏感字段" $raw }
            if ($isMain -and $raw -match '(?i)\bselect\s+\*\s+from\b') { Add-Finding "JAVA-COMMON-SQL-001" $null "P2" "PRODUCTION" $relative $lineNo "Java SQL 使用 SELECT *" $raw }
            if ($isMain -and $lex -match '\bprintStackTrace\s*\(') { Add-Finding "JAVA-COMMON-EXCEPTION-001" $null "P1" "PRODUCTION" $relative $lineNo "生产代码仅调用 printStackTrace" $raw }
            if ($isMain -and $lex -match '\b(?:private|protected|public)\s+(?:final\s+)?Optional\s*<[^>]+>\s+\w+\s*[;,) ]') { Add-Finding "JAVA-COMMON-OPTIONAL-001" $null "P3" "PRODUCTION" $relative $lineNo "生产代码可能将 Optional 用作字段或参数" $raw }
            if ($lex -match '^\s*import\s+javax\.persistence\.') { Add-Finding "SPRING-LEGACY-JAVAX-PERSISTENCE" $null "P1" "SPRING_SOURCE" $relative $lineNo "使用已迁移的 javax.persistence namespace" $raw }
            if ($lex -match '^\s*import\s+javax\.validation\.') { Add-Finding "SPRING-LEGACY-JAVAX-VALIDATION" $null "P1" "SPRING_SOURCE" $relative $lineNo "使用已迁移的 javax.validation namespace" $raw }
            if ($lex -match '^\s*import\s+javax\.servlet\.') { Add-Finding "SPRING-LEGACY-JAVAX-SERVLET" $null "P1" "SPRING_SOURCE" $relative $lineNo "使用已迁移的 javax.servlet namespace" $raw }
            if ($isTest -and $lex -match '^\s*import\s+org\.springframework\.boot\.test\.mock\.mockito\.(MockBean|SpyBean)\s*;') { Add-Finding "SPRING-DEPRECATED-TEST-ANNOTATION" $null "P2" "TEST" $relative $lineNo "测试使用当前平台已弃用的 Spring Boot Mockito Bean annotation" $raw }
            if ($isMain -and $lex -match '@Autowired\b') {
                for ($j = $i + 1; $j -lt [Math]::Min($i + 6, $lexLines.Length); $j++) {
                    $candidate = $lexLines[$j].Trim()
                    if (-not $candidate -or $candidate.StartsWith('@')) { continue }
                    if ($candidate -match '^(private|protected|public)\s+(?:static\s+)?(?:final\s+)?[\w<>, ?\.\[\]]+\s+\w+\s*;' -and $candidate -notmatch '\(') { Add-Finding "SPRING-FIELD-INJECTION" $null "P2" "SPRING_COMPONENT" $relative ($j + 1) "Spring component 使用 field injection" $rawLines[$j] }
                    break
                }
            }
            if ($isMain -and $lex -match '@Transactional\b') {
                for ($j = $i + 1; $j -lt [Math]::Min($i + 8, $lexLines.Length); $j++) {
                    $candidate = $lexLines[$j].Trim()
                    if (-not $candidate -or $candidate.StartsWith('@')) { continue }
                    if ($candidate -match '^private\s+.*\(') { Add-Finding "SPRING-TRANSACTION-PRIVATE-METHOD" $null "P1" "SPRING_COMPONENT" $relative ($j + 1) "private method 上的 @Transactional 不经过 Spring proxy" $rawLines[$j] }
                    break
                }
            }
        }

        if ($isMain) {
            $lexText = $lexLines -join "`n"
            foreach ($match in [regex]::Matches($lexText, '\bCompletableFuture\.(supplyAsync|runAsync)\s*\(')) {
                $open = $lexText.IndexOf('(', $match.Index)
                if ($open -ge 0 -and (Get-InvocationArgumentCount $lexText $open) -eq 1) {
                    $lineNo = ([regex]::Matches($lexText.Substring(0, $match.Index), "`n")).Count + 1
                    Add-Finding "JAVA-COMMON-POOL-ASYNC" $null "P1" "PRODUCTION" $relative $lineNo "CompletableFuture async 调用未提供项目管理 executor" $rawLines[$lineNo - 1]
                }
            }
        }
    }
    $findings = @($findings | Sort-Object rule_id, path, fingerprint)

    $baselineFullPath = Resolve-RepoFile $BaselinePath
    $previousBaseline = $null
    if (Test-Path -LiteralPath $baselineFullPath -PathType Leaf) { $previousBaseline = Get-Content -LiteralPath $baselineFullPath -Raw -Encoding UTF8 | ConvertFrom-Json }
    $previousFingerprints = @{}
    if ($previousBaseline) { foreach ($item in @($previousBaseline.violations)) { $previousFingerprints[$item.fingerprint] = $true } }

    $classified = @($findings | ForEach-Object {
        $classification = if ($changedJava.ContainsKey($_.path)) { "NEW_CODE_FINDING" } elseif ($rulesetExpansionRuleIds -contains $_.rule_id) { "RULESET_EXPANSION_FINDING" } else { "EXISTING_BASELINE_FINDING" }
        [pscustomobject]@{
            rule_id = $_.rule_id; path = $_.path; symbol_or_line = $_.symbol_or_line; summary = $_.summary; severity = $_.severity; architecture_scope = $_.architecture_scope; classification = $classification; fingerprint = $_.fingerprint; checker_version = $_.checker_version; ruleset_version = $_.ruleset_version; configuration_sha256 = $_.configuration_sha256
        }
    })

    if ($UpdateBaseline) {
        $provenance = Get-Content -LiteralPath (Join-Path $standardsRoot "source-provenance.json") -Raw -Encoding UTF8 | ConvertFrom-Json
        $attempt01SummaryPath = Join-Path $repoRoot "docs\evidence\java-engineering-standard\attempt-01\shadow-scan-summary.json"
        if (-not (Test-Path -LiteralPath $attempt01SummaryPath -PathType Leaf)) { throw "BASELINE_SCHEMA_INVALID: attempt-01 Shadow summary missing" }
        $attempt01Summary = Get-Content -LiteralPath $attempt01SummaryPath -Raw -Encoding UTF8 | ConvertFrom-Json
        $lineagePreviousCount = [int]$attempt01Summary.baseline_violation_count
        $projection = @($classified | ForEach-Object { [pscustomobject]@{ rule_id = $_.rule_id; path = $_.path; classification = $_.classification; fingerprint = $_.fingerprint } })
        $baseline = [pscustomobject]@{
            schema_version = "2.0.0"; previous_ruleset_version = "songshan-shadow-1.0.0"; current_ruleset_version = $rulesetVersion; baseline_head = $currentBaselineHead; checker_version = $checkerVersion; configuration_hash_algorithm = $configurationHashAlgorithm; configuration_sha256 = $configurationHash; generated_at_utc = $provenance.retrieved_at_utc; deterministic_content_sha256 = Get-Sha256Text ($projection | ConvertTo-Json -Depth 5 -Compress); excluded_paths = @($excludedSegments); previous_count = $lineagePreviousCount; current_count = $classified.Count; existing_baseline_count = @($classified | Where-Object { $_.classification -eq 'EXISTING_BASELINE_FINDING' }).Count; ruleset_expansion_count = @($classified | Where-Object { $_.classification -eq 'RULESET_EXPANSION_FINDING' }).Count; new_code_count = @($classified | Where-Object { $_.classification -eq 'NEW_CODE_FINDING' }).Count; violation_count = $classified.Count; violations = $classified
        }
        [IO.Directory]::CreateDirectory((Split-Path $baselineFullPath -Parent)) | Out-Null
        [IO.File]::WriteAllText($baselineFullPath, ($baseline | ConvertTo-Json -Depth 8) + "`n", [Text.UTF8Encoding]::new($false))
        $previousBaseline = $baseline
    }

    if (-not $previousBaseline) { throw "BASELINE_SCHEMA_INVALID: missing baseline" }
    if ($previousBaseline.schema_version -ne "2.0.0" -or $previousBaseline.current_ruleset_version -ne $rulesetVersion) { throw "BASELINE_SCHEMA_INVALID: baseline ruleset/schema mismatch" }
    $baselineAlgorithmProperty = $previousBaseline.PSObject.Properties['configuration_hash_algorithm']
    if ($null -eq $baselineAlgorithmProperty -or [string]$baselineAlgorithmProperty.Value -ne $configurationHashAlgorithm) { throw "BASELINE_SCHEMA_INVALID: baseline configuration hash algorithm mismatch" }
    if ($previousBaseline.configuration_sha256 -ne $configurationHash) { throw "BASELINE_CONFIGURATION_HASH_MISMATCH: baseline=$($previousBaseline.configuration_sha256) current=$configurationHash" }
    $baselineByFingerprint = @{}; foreach ($item in @($previousBaseline.violations)) { $baselineByFingerprint[$item.fingerprint] = $item.classification }
    $reported = @($findings | ForEach-Object {
        $classification = if ($baselineByFingerprint.ContainsKey($_.fingerprint)) { [string]$baselineByFingerprint[$_.fingerprint] } elseif ($changedJava.ContainsKey($_.path)) { "NEW_CODE_FINDING" } else { "RULESET_EXPANSION_FINDING" }
        [pscustomobject]@{ rule_id = $_.rule_id; path = $_.path; symbol_or_line = $_.symbol_or_line; summary = $_.summary; severity = $_.severity; architecture_scope = $_.architecture_scope; classification = $classification; is_in_baseline = $baselineByFingerprint.ContainsKey($_.fingerprint); is_new_code = $classification -eq 'NEW_CODE_FINDING'; fingerprint = $_.fingerprint; checker_version = $_.checker_version; ruleset_version = $_.ruleset_version; configuration_sha256 = $_.configuration_sha256 }
    })
    $existingCount = @($reported | Where-Object { $_.classification -eq 'EXISTING_BASELINE_FINDING' }).Count
    $expansionCount = @($reported | Where-Object { $_.classification -eq 'RULESET_EXPANSION_FINDING' }).Count
    $newCodeCount = @($reported | Where-Object { $_.classification -eq 'NEW_CODE_FINDING' }).Count
    $status = if ($reported.Count) { "VIOLATION_FOUND" } else { "PASS" }
    $report = [pscustomobject]@{
        schema_version = "2.0.0"; status = $status; checker_version = $checkerVersion; ruleset_version = $rulesetVersion; baseline_head = $previousBaseline.baseline_head; huangshan_source_ref = "6c59c8c36ecd8722c712d5685b8c3822c1c8b030"; java_platform = "release-$($platform.java.compiler_release)"; spring_platform = "boot-$($platform.spring.boot)_framework-$($platform.spring.framework)"; configuration_hash_algorithm = $configurationHashAlgorithm; configuration_sha256 = $configurationHash; current_violation_count = $reported.Count; baseline_violation_count = [int]$previousBaseline.violation_count; existing_baseline_count = $existingCount; ruleset_expansion_count = $expansionCount; new_code_violation_count = $newCodeCount; report_artifact = $OutputPath.Replace("\", "/"); violations = $reported
    }
    $outputFullPath = Resolve-RepoFile $OutputPath
    [IO.Directory]::CreateDirectory((Split-Path $outputFullPath -Parent)) | Out-Null
    [IO.File]::WriteAllText($outputFullPath, ($report | ConvertTo-Json -Depth 8) + "`n", [Text.UTF8Encoding]::new($false))

    Write-Output "SHADOW_CHECKER_RESULT=$status"
    Write-Output "JAVA_PLATFORM=release-$($platform.java.compiler_release)"
    Write-Output "SPRING_PLATFORM=boot-$($platform.spring.boot)_framework-$($platform.spring.framework)"
    Write-Output "HUANGSHAN_RULESET_IDENTITY=$($report.huangshan_source_ref)"
    Write-Output "RULESET_VERSION=$rulesetVersion"
    Write-Output "BASELINE_HEAD=$($previousBaseline.baseline_head)"
    Write-Output "CONFIGURATION_HASH_ALGORITHM=$configurationHashAlgorithm"
    Write-Output "CONFIG_HASH_INPUTS=$($configInputPaths -join ',')"
    Write-Output "EXISTING_BASELINE_COUNT=$existingCount"
    Write-Output "RULESET_EXPANSION_COUNT=$expansionCount"
    Write-Output "NEW_CODE_VIOLATION_COUNT=$newCodeCount"
    Write-Output "CONFIGURATION_SHA256=$configurationHash"
    Write-Output "REPORT_ARTIFACT=$($OutputPath.Replace('\', '/'))"
    exit 0
}
catch {
    $message = $_.Exception.Message
    if ($message -match '^(CONFIG_INVALID|PLATFORM_PROFILE_INVALID|MAPPING_INVALID|BASELINE_SCHEMA_INVALID|BASELINE_CONFIGURATION_HASH_MISMATCH|RULE_ID_COLLISION):') { [Console]::Error.WriteLine($message); exit 2 }
    [Console]::Error.WriteLine("CHECKER_EXECUTION_FAILED: $message")
    exit 3
}
