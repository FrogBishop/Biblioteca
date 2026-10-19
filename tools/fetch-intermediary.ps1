# Downloads a Fabric intermediary jar that the Fabric Maven keeps timing out on.
# The jar is placed in local-repo/ and build.gradle.kts uses that directory as a
# fallback repository. Chunked Range requests are used because a single long
# connection to the CDN stalls after a few hundred kilobytes on some networks.
param(
    [Parameter(Mandatory = $true)]
    [string]$Version
)

$ErrorActionPreference = 'Stop'
$dir = Join-Path $PSScriptRoot "..\local-repo\net\fabricmc\intermediary\$Version"
New-Item -ItemType Directory -Force $dir | Out-Null

$base = "https://maven.fabricmc.net/net/fabricmc/intermediary/$Version"
$jar = Join-Path $dir "intermediary-$Version-v2.jar"
$pom = Join-Path $dir "intermediary-$Version.pom"

if (-not (Test-Path $pom)) {
    & curl.exe -4 -sS -L --max-time 120 -o $pom "$base/intermediary-$Version.pom"
}

$head = & curl.exe -4 -sS -I -H "Range: bytes=0-0" --max-time 60 "$base/intermediary-$Version-v2.jar"
$range = ($head | Select-String 'content-range:\s*bytes\s+0-0/(\d+)').Matches[0].Groups[1].Value
if (-not $range) { throw "Could not read the size of intermediary-$Version-v2.jar" }
$total = [long]$range

$step = 100000
$chunks = @()
for ($start = 0; $start -lt $total; $start += $step) {
    $end = [Math]::Min($start + $step - 1, $total - 1)
    $chunk = Join-Path $dir ("chunk_{0}.bin" -f $start)
    $want = $end - $start + 1
    for ($try = 0; $try -lt 10; $try++) {
        if ((Test-Path $chunk) -and (Get-Item $chunk).Length -eq $want) { break }
        & curl.exe -4 -sS -L --max-time 120 -r "$start-$end" -o $chunk "$base/intermediary-$Version-v2.jar"
    }
    if (-not (Test-Path $chunk) -or (Get-Item $chunk).Length -ne $want) {
        throw "chunk $start failed"
    }
    $chunks += $chunk
}

$list = ($chunks | ForEach-Object { '"' + $_ + '"' }) -join '+'
$combined = Join-Path $dir 'combined.bin'
cmd /c copy /b $list $combined | Out-Null
Move-Item -Force $combined $jar
Remove-Item $chunks -Force
Write-Output "$jar ($((Get-Item $jar).Length) bytes)"
