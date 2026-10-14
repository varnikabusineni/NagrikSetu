# Starts the Nagrik Setu frontend on http://localhost:5173. Keep this window open.
# Usage:  powershell -ExecutionPolicy Bypass -File scripts\windows\start-frontend.ps1
. (Join-Path $PSScriptRoot 'common.ps1')

Write-Step 'Checking prerequisites'
$node = Get-NodeMajorVersion
if ($node -lt 18) {
    Write-Fail 'Node.js 18 or newer is required. Install: winget install OpenJS.NodeJS.LTS, then open a NEW PowerShell window.'
    exit 1
}
Write-Ok "Node.js $node"
if (Test-Port 8080) { Write-Ok 'Backend is running on port 8080' }
else { Write-Warn 'The backend is not running yet. Start it first (start-backend.ps1), otherwise sign-in will fail.' }

# npm.cmd avoids PowerShell's "running scripts is disabled" error for npm.ps1.
$npm = 'npm'
if ($OnWindows) { $npm = 'npm.cmd' }

Set-Location $FrontendDir
if (-not (Test-Path (Join-Path $FrontendDir 'node_modules'))) {
    Write-Step 'Installing frontend packages (first run only)'
    & $npm install --no-audit --no-fund
    if ($LASTEXITCODE -ne 0) { Write-Fail 'npm install failed. Check your internet connection and try again.'; exit 1 }
}

Write-Step 'Starting the frontend. Open http://localhost:5173 in your browser. Press Ctrl+C to stop.'
& $npm run dev
exit $LASTEXITCODE
