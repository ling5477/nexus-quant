#requires -Version 7.4
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$JarPath,
    [Parameter(Mandatory)][ValidateSet('NQ_SELF_HOSTED','NQ_PUBLIC_DEMO')][string]$Distribution
)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression
$jar=[IO.Compression.ZipFile]::OpenRead([IO.Path]::GetFullPath($JarPath))
try {
    $identities=@($jar.Entries | Where-Object { $_.FullName -in @('META-INF/nq-distribution.properties','BOOT-INF/classes/META-INF/nq-distribution.properties') })
    if ($identities.Count -ne 1) { throw 'Missing or ambiguous artifact distribution identity' }
    $identity=$identities[0]
    $reader=[IO.StreamReader]::new($identity.Open())
    try { $text=$reader.ReadToEnd() } finally { $reader.Dispose() }
    if ($text.Trim() -cne "distribution=$Distribution") { throw 'Artifact distribution mismatch' }
    $demo=@($jar.Entries | Where-Object { $_.FullName -match '^BOOT-INF/lib/nq-public-demo-[^/]+\.jar$' })
    if ($Distribution -ceq 'NQ_SELF_HOSTED') {
        if ($demo.Count -ne 0) { throw 'Self-Hosted must not include nq-public-demo' }
        if (@($jar.Entries | Where-Object { $_.FullName -like 'BOOT-INF/classes/com/guidinglight/nexusquant/demo/*' }).Count -ne 0) {
            throw 'Self-Hosted application contains Demo implementation classes'
        }
        foreach ($library in @($jar.Entries | Where-Object { $_.FullName -match '^BOOT-INF/lib/nq-[^/]+\.jar$' })) {
            $memory=[IO.MemoryStream]::new()
            try {
                $stream=$library.Open()
                try { $stream.CopyTo($memory) } finally { $stream.Dispose() }
                $memory.Position=0
                $nested=[IO.Compression.ZipArchive]::new($memory,[IO.Compression.ZipArchiveMode]::Read,$true)
                try {
                    if (@($nested.Entries | Where-Object { $_.FullName -like 'com/guidinglight/nexusquant/demo/*' }).Count -ne 0) {
                        throw 'Self-Hosted library contains Demo implementation classes'
                    }
                } finally { $nested.Dispose() }
            } finally { $memory.Dispose() }
        }
    } else {
        if ($demo.Count -ne 1) { throw 'Public Demo module missing or ambiguous' }
        $memory=[IO.MemoryStream]::new()
        try {
            $stream=$demo[0].Open()
            try { $stream.CopyTo($memory) } finally { $stream.Dispose() }
            $memory.Position=0
            $nested=[IO.Compression.ZipArchive]::new($memory,[IO.Compression.ZipArchiveMode]::Read,$true)
            try {
                foreach ($controller in @('DemoRegistrationController.class','PlatformController.class')) {
                    if (@($nested.Entries | Where-Object { $_.Name -ceq $controller }).Count -ne 1) { throw ('Required Demo controller missing: '+$controller) }
                }
            } finally { $nested.Dispose() }
        } finally { $memory.Dispose() }
    }
} finally { $jar.Dispose() }
[ordered]@{distribution=$Distribution; jar=(Resolve-Path -LiteralPath $JarPath).Path; sha256=(Get-FileHash -LiteralPath $JarPath -Algorithm SHA256).Hash.ToLowerInvariant(); moduleIsolation='PASS'} | ConvertTo-Json
