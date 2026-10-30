param(
    [ValidateSet('prepare', 'verify', 'status')]
    [string]$Action = 'prepare',
    [switch]$Offline,
    [string]$NeoForgeVersion = '',
    [ValidatePattern('^[0-9A-Za-z][0-9A-Za-z._+-]*$')]
    [string]$ModVersion = '',
    [ValidateSet('1.21.11', '26.1', '26.1.1', '26.1.2', '26.2', '26.3')]
    [string]$MinecraftVersion = '26.3'
)

$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$reportRelative = if ($MinecraftVersion -eq '26.3') { 'build/neoforge-pilot' } else { "build/neoforge-$MinecraftVersion" }
$reportDir = Join-Path $repoRoot $reportRelative
$targetProject = Join-Path $repoRoot "platforms/neoforge-$MinecraftVersion"
$statePath = Join-Path $reportDir 'state.json'
$logName = if ($Offline) { "$Action-offline.log" } else { "$Action.log" }
$logPath = Join-Path $reportDir $logName

if ($Action -eq 'status') {
    if (Test-Path -LiteralPath $statePath) { Get-Content -LiteralPath $statePath -Raw }
    else { Write-Output "No recorded preparation run. Read platforms/neoforge-$MinecraftVersion/README.md." }
    exit 0
}

[IO.Directory]::CreateDirectory($reportDir) | Out-Null
$targetNeoVersion = $NeoForgeVersion
if (-not $targetNeoVersion) {
    $versionLine = Get-Content -LiteralPath (Join-Path $targetProject 'gradle.properties') |
        Where-Object { $_ -match '^neo_version=' } | Select-Object -First 1
    $targetNeoVersion = ($versionLine -split '=', 2)[1].Trim()
}
$state = [ordered]@{
    phase = $(if ($Action -eq 'prepare') { 'dependency_preparation' } else { 'neoforge_validation' })
    status = 'running'
    minecraft = $MinecraftVersion
    neoforge = $targetNeoVersion
    modVersionOverride = $ModVersion
    moddevgradle = '2.0.148'
    startedUtc = [DateTime]::UtcNow.ToString('o')
    log = "$reportRelative/$logName"
    exitCode = $null
}
function Save-PilotState {
    $temporaryState = Join-Path $reportDir 'state.json.tmp'
    [IO.File]::WriteAllText($temporaryState, ($state | ConvertTo-Json) + [Environment]::NewLine,
        [Text.UTF8Encoding]::new($false))
    Move-Item -LiteralPath $temporaryState -Destination $statePath -Force
}

# Write the checkpoint before launching Gradle. An interrupted run stays "running", never "passed".
Save-PilotState
$resultCode = 1
Push-Location $repoRoot
try {
    $pilotTask = if ($Action -eq 'prepare') { 'preparePilot' } else { 'build' }
    $pilotArguments = @('-p', $targetProject, $pilotTask, '--console=plain', '--max-workers=1')
    $pilotArguments += "-Pneo_version=$targetNeoVersion"
    if ($ModVersion) { $pilotArguments += "-PbibliotecaVersion=$ModVersion" }
    if ($Offline) { $pilotArguments += '--offline' }
    & (Join-Path $repoRoot 'gradlew.bat') @pilotArguments *> $logPath
    $resultCode = $LASTEXITCODE
    $marker = Join-Path $targetProject 'build/pilot-environment.txt'
    if ($resultCode -eq 0 -and (Test-Path -LiteralPath $marker)) {
        if ($Action -eq 'prepare') {
            $state.status = 'prepared'
        } else {
            $results = @(Get-Content -LiteralPath (Join-Path $reportDir 'validation/results.json') -Raw | ConvertFrom-Json)
            if ($results.Count -ne 4 -or @($results | Where-Object { -not $_.passed -or $_.minecraft -ne $MinecraftVersion -or $_.neoforge -ne $targetNeoVersion }).Count -ne 0) {
                throw 'Expected four successful NeoForge variant/side validations.'
            }
            foreach ($result in $results) {
                $artifactPath = Join-Path $repoRoot $result.jar
                if ((Get-FileHash -LiteralPath $artifactPath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $result.sha256) {
                    throw "Validation does not match the current artifact: $artifactPath"
                }
            }
            $state.status = 'verified'
            $state['validation'] = "$reportRelative/validation/results.json"
        }
    } else { $state.status = 'failed'; if ($resultCode -eq 0) { $resultCode = 1 } }
} catch {
    $state.status = 'failed'
    $resultCode = 1
    $state['error'] = $_.Exception.Message
    Add-Content -LiteralPath $logPath -Value $_.Exception.ToString()
} finally {
    Pop-Location
    $state.exitCode = $resultCode
    $state['finishedUtc'] = [DateTime]::UtcNow.ToString('o')
    Save-PilotState
}

Get-Content -LiteralPath $logPath -Tail 30 -ErrorAction SilentlyContinue
Write-Output "Pilot state: $($state.status). See platforms/neoforge-$MinecraftVersion/README.md and $reportRelative/state.json"
exit $resultCode
