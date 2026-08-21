[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ReportPath
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$proofAlgorithm = "shadow-report-proof-v1"

function Assert-ReportCondition([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "REPORT_PROOF_INVALID: $Message" }
}

function Assert-UniqueJsonProperties([System.Text.Json.JsonElement]$Element, [string]$Location) {
    if ($Element.ValueKind -eq [System.Text.Json.JsonValueKind]::Object) {
        $seen = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
        foreach ($property in $Element.EnumerateObject()) {
            if (-not $seen.Add($property.Name)) {
                throw "REPORT_PROOF_INVALID: duplicate JSON property at $Location/$($property.Name)"
            }
            Assert-UniqueJsonProperties $property.Value "$Location/$($property.Name)"
        }
    }
    elseif ($Element.ValueKind -eq [System.Text.Json.JsonValueKind]::Array) {
        $index = 0
        foreach ($item in $Element.EnumerateArray()) {
            Assert-UniqueJsonProperties $item "$Location/$index"
            $index++
        }
    }
}

function Get-Sha256Utf8([string]$Text) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [Text.UTF8Encoding]::new($false).GetBytes($Text)
        return ([BitConverter]::ToString($sha.ComputeHash($bytes))).Replace("-", "").ToLowerInvariant()
    }
    finally { $sha.Dispose() }
}

try {
    $resolvedReport = (Resolve-Path -LiteralPath $ReportPath).Path
    Assert-ReportCondition (Test-Path -LiteralPath $resolvedReport -PathType Leaf) "report file is missing"

    $raw = [IO.File]::ReadAllBytes($resolvedReport)
    Assert-ReportCondition (-not ($raw.Length -ge 3 -and $raw[0] -eq 0xef -and $raw[1] -eq 0xbb -and $raw[2] -eq 0xbf)) "UTF-8 BOM is forbidden"
    try { $text = [Text.UTF8Encoding]::new($false, $true).GetString($raw) }
    catch { throw "REPORT_PROOF_INVALID: report is not strict UTF-8" }
    $normalizedText = $text.Replace("`r`n", "`n")
    Assert-ReportCondition (-not $normalizedText.Contains("`r")) "bare CR is forbidden"

    $jsonOptions = [System.Text.Json.JsonDocumentOptions]::new()
    $jsonOptions.AllowTrailingCommas = $false
    $jsonOptions.CommentHandling = [System.Text.Json.JsonCommentHandling]::Disallow
    try { $document = [System.Text.Json.JsonDocument]::Parse($normalizedText, $jsonOptions) }
    catch { throw "REPORT_PROOF_INVALID: report is not valid JSON" }
    try {
        Assert-ReportCondition ($document.RootElement.ValueKind -eq [System.Text.Json.JsonValueKind]::Object) "report root must be an object"
        Assert-UniqueJsonProperties $document.RootElement '$'
        $violationsElement = $document.RootElement.GetProperty("violations")
        Assert-ReportCondition ($violationsElement.ValueKind -eq [System.Text.Json.JsonValueKind]::Array) "violations must be an array"
    }
    catch [System.Collections.Generic.KeyNotFoundException] {
        throw "REPORT_PROOF_INVALID: violations array is missing"
    }
    finally { $document.Dispose() }

    $report = $normalizedText | ConvertFrom-Json -Depth 20
    Assert-ReportCondition ($report.PSObject.Properties.Name -contains 'schema_version') "schema_version is missing"
    Assert-ReportCondition (@('2.0.0', '3.0.0') -contains [string]$report.schema_version) "unsupported report schema"

    $violations = @($report.violations)
    $countProperty = if ([string]$report.schema_version -eq '2.0.0') { 'current_violation_count' } else { 'current_count' }
    Assert-ReportCondition ($report.PSObject.Properties.Name -contains $countProperty) "$countProperty is missing"
    $declaredCount = [int64]0
    Assert-ReportCondition ([int64]::TryParse([string]$report.$countProperty, [ref]$declaredCount)) "$countProperty must be an integer"
    Assert-ReportCondition ($declaredCount -eq $violations.Count) "$countProperty does not match violations"

    $identities = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    $projection = [Collections.Generic.List[object]]::new()
    foreach ($finding in $violations) {
        foreach ($field in @('rule_id', 'path', 'classification', 'fingerprint')) {
            Assert-ReportCondition ($finding.PSObject.Properties.Name -contains $field) "finding field $field is missing"
            Assert-ReportCondition ($finding.$field -is [string] -and -not [string]::IsNullOrWhiteSpace([string]$finding.$field)) "finding field $field must be a non-blank string"
        }
        Assert-ReportCondition ([string]$finding.rule_id -match '^[A-Z][A-Z0-9-]+$') "rule_id is invalid"
        Assert-ReportCondition ([string]$finding.classification -match '^[A-Z][A-Z0-9_]+$') "classification is invalid"
        Assert-ReportCondition ([string]$finding.fingerprint -match '^[0-9a-f]{24}$') "fingerprint is invalid"
        $path = [string]$finding.path
        Assert-ReportCondition (-not $path.Contains('\')) "finding path must use '/' separators"
        $segments = @($path.Split('/'))
        Assert-ReportCondition (-not [IO.Path]::IsPathRooted($path) -and @($segments | Where-Object { $_ -eq '' -or $_ -eq '.' -or $_ -eq '..' }).Count -eq 0) "finding path is not repository-relative"

        $identity = "$($finding.rule_id)`0$path`0$($finding.fingerprint)"
        Assert-ReportCondition ($identities.Add($identity)) "duplicate or ambiguous finding identity"
        $projection.Add([pscustomobject][ordered]@{
            rule_id = [string]$finding.rule_id
            path = $path
            classification = [string]$finding.classification
            fingerprint = [string]$finding.fingerprint
        })
    }

    $items = [object[]]$projection.ToArray()
    $comparison = [Comparison[object]]{
        param($left, $right)
        foreach ($field in @('rule_id', 'path', 'fingerprint', 'classification')) {
            # baseline 既有 deterministic projection 由 PowerShell culture sort 生成；
            # v1 将其收口为显式 InvariantCulture，并用 ordinal 处理 culture-equal tie。
            $result = [StringComparer]::InvariantCulture.Compare([string]$left.$field, [string]$right.$field)
            if ($result -ne 0) { return $result }
            $result = [StringComparer]::Ordinal.Compare([string]$left.$field, [string]$right.$field)
            if ($result -ne 0) { return $result }
        }
        return 0
    }
    [Array]::Sort($items, $comparison)

    # 复用 baseline deterministic_content_sha256 的稳定四字段 JSON projection；
    # 顶层时间、输出路径、平台和展示字段不属于 finding proof identity。
    $canonicalJson = ConvertTo-Json -InputObject $items -Depth 5 -Compress
    $proofHash = Get-Sha256Utf8 $canonicalJson

    Write-Output 'SHADOW_REPORT_PROOF_RESULT=PASS'
    Write-Output "REPORT_PROOF_ALGORITHM=$proofAlgorithm"
    Write-Output "REPORT_PROOF_SCHEMA=$($report.schema_version)"
    Write-Output "REPORT_PROOF_FINDING_COUNT=$($items.Count)"
    Write-Output "REPORT_PROOF_SHA256=$proofHash"
    exit 0
}
catch {
    $message = $_.Exception.Message
    Write-Output 'SHADOW_REPORT_PROOF_RESULT=FAIL'
    if ($message.StartsWith('REPORT_PROOF_INVALID:', [StringComparison]::Ordinal)) {
        [Console]::Error.WriteLine($message)
        exit 2
    }
    [Console]::Error.WriteLine("REPORT_PROOF_EXECUTION_FAILED: $message")
    exit 3
}
