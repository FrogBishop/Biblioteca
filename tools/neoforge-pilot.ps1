param(
    [ValidateSet('prepare', 'status')]
    [string]$Action = 'prepare',
    [switch]$Offline
)

$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$reportDir = Join-Path $repoRoot 'build/neoforge-pilot'
$statePath = Join-Path $reportDir 'state.json'
$logName = if ($Offline) { 'prepare-offline.log' } else { 'prepare.log' }
$logPath = Join-Path $reportDir $logName

if ($Action -eq 'status') {
    if (Test-Path -LiteralPath $statePath) { Get-Content -LiteralPath $statePath -Raw }
    else { Write-Output 'No recorded preparation run. Read NEOFORGE_PILOT.md.' }
    exit 0
}

[IO.Directory]::CreateDirectory($reportDir) | Out-Null
$state = [ordered]@{
    phase = 'dependency_preparation'
    status = 'running'
    minecraft = '26.3'
    neoforge = '26.3.0.98'
    moddevgradle = '2.0.148'
    startedUtc = [DateTime]::UtcNow.ToString('o')
    log = "build/neoforge-pilot/$logName"
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
    $pilotArguments = @('-p', (Join-Path $repoRoot 'platforms/neoforge-26.3'), 'preparePilot', '--console=plain', '--max-workers=1')
    if ($Offline) { $pilotArguments += '--offline' }
    & (Join-Path $repoRoot 'gradlew.bat') @pilotArguments *> $logPath
    $resultCode = $LASTEXITCODE
    $marker = Join-Path $repoRoot 'platforms/neoforge-26.3/build/pilot-environment.txt'
    if ($resultCode -eq 0 -and (Test-Path -LiteralPath $marker)) { $state.status = 'prepared' }
    else { $state.status = 'failed'; if ($resultCode -eq 0) { $resultCode = 1 } }
} catch {
    $state.status = 'failed'
    $state['error'] = $_.Exception.Message
} finally {
    Pop-Location
    $state.exitCode = $resultCode
    $state['finishedUtc'] = [DateTime]::UtcNow.ToString('o')
    Save-PilotState
}

Get-Content -LiteralPath $logPath -Tail 30 -ErrorAction SilentlyContinue
Write-Output "Pilot state: $($state.status). See NEOFORGE_PILOT.md and $statePath"
exit $resultCode
