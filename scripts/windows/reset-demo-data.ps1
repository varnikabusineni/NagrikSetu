# Deletes ALL Nagrik Setu data and uploaded photos so the demo starts fresh.
# The backend re-creates the tables and demo data the next time it starts.
# Usage:  powershell -ExecutionPolicy Bypass -File scripts\windows\reset-demo-data.ps1
param([string]$AppUser = 'nagrik')
. (Join-Path $PSScriptRoot 'common.ps1')

if (Test-Port 8080) {
    Write-Fail 'The backend is running. Stop it first (Ctrl+C in its window), then run this script again.'
    exit 1
}
$answer = Read-Host 'This deletes every complaint, account change and uploaded photo in your local Nagrik Setu. Type RESET to continue'
if ($answer -ne 'RESET') { Write-Host 'Cancelled.'; exit 0 }

$psql = Find-Psql
if (-not $psql) { Write-Fail 'psql.exe was not found.'; exit 1 }
Request-PostgresPassword

Write-Step 'Recreating the nagrik_setu database'
Invoke-Psql $psql 'DROP DATABASE IF EXISTS nagrik_setu WITH (FORCE)' | Out-Null
Invoke-Psql $psql "CREATE DATABASE nagrik_setu OWNER $AppUser ENCODING 'UTF8' TEMPLATE template0" | Out-Null
Write-Ok 'Database recreated'

Write-Step 'Deleting uploaded photos'
$storage = Join-Path (Join-Path $BackendDir 'storage') 'evidence'
if (Test-Path $storage) { Remove-Item -Recurse -Force $storage; Write-Ok "Deleted $storage" } else { Write-Ok 'No uploaded photos to delete' }

Write-Host ''
Write-Host 'Done. Start the backend again; it will load fresh demo data.' -ForegroundColor Green
