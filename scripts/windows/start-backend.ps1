# Starts the Nagrik Setu backend (Spring Boot) on http://localhost:8080. Keep this window open.
# Usage:  powershell -ExecutionPolicy Bypass -File scripts\windows\start-backend.ps1
. (Join-Path $PSScriptRoot 'common.ps1')

Write-Step 'Checking prerequisites'
$java = Get-JavaMajorVersion
if ($java -lt 21) {
    if ($java -eq 0) { Write-Fail 'Java was not found. Install: winget install EclipseAdoptium.Temurin.21.JDK, then open a NEW PowerShell window.' }
    else { Write-Fail "Java $java found but 21 or newer is required. Install: winget install EclipseAdoptium.Temurin.21.JDK" }
    exit 1
}
Write-Ok "Java $java"
if (-not (Test-Port 5432)) {
    Write-Fail 'PostgreSQL is not running on port 5432. Start the "postgresql-x64-..." service in Windows Services.'
    exit 1
}
Write-Ok 'PostgreSQL is running'
if (Test-Port 8080) {
    Write-Fail 'Port 8080 is already in use. If the backend is already running in another window, you do not need to start it again.'
    exit 1
}

# Defaults match setup-database.ps1. Override by setting these variables before running the script.
if (-not $env:NAGRIK_DB_USERNAME) { $env:NAGRIK_DB_USERNAME = 'nagrik' }
if (-not $env:NAGRIK_DB_PASSWORD) { $env:NAGRIK_DB_PASSWORD = 'nagrik' }

Write-Step 'Starting the backend (the first run downloads Maven and libraries; this can take a few minutes)'
Write-Host '    Wait for "Started NagrikSetuApplication", then start the frontend in a second window.'
Write-Host '    Press Ctrl+C in this window to stop the backend.'
Set-Location $BackendDir
if ($OnWindows) { & .\mvnw.cmd spring-boot:run } else { & ./mvnw spring-boot:run }
exit $LASTEXITCODE
