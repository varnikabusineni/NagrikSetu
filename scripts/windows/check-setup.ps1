# Checks that everything needed to run Nagrik Setu is installed and running.
# Usage:  powershell -ExecutionPolicy Bypass -File scripts\windows\check-setup.ps1
. (Join-Path $PSScriptRoot 'common.ps1')
$ErrorActionPreference = 'Continue'
$problems = 0

Write-Step 'Java (needs 21 or newer)'
$java = Get-JavaMajorVersion
if ($java -ge 21) { Write-Ok "Java $java" }
elseif ($java -eq 0) { Write-Fail 'Java not found. Install it: winget install EclipseAdoptium.Temurin.21.JDK   (then open a NEW PowerShell window)'; $problems++ }
else { Write-Fail "Java $java found, but 21+ is required. Install: winget install EclipseAdoptium.Temurin.21.JDK"; $problems++ }

Write-Step 'Node.js (needs 18 or newer)'
$node = Get-NodeMajorVersion
if ($node -ge 18) { Write-Ok "Node.js $node" }
elseif ($node -eq 0) { Write-Fail 'Node.js not found. Install it: winget install OpenJS.NodeJS.LTS   (then open a NEW PowerShell window)'; $problems++ }
else { Write-Fail "Node.js $node found, but 18+ is required. Install: winget install OpenJS.NodeJS.LTS"; $problems++ }

Write-Step 'PostgreSQL'
$psql = Find-Psql
if ($psql) { Write-Ok "psql found: $psql" } else { Write-Warn 'psql not found. Install PostgreSQL 16 or 17 from https://www.postgresql.org/download/windows/' }
if (Test-Port 5432) { Write-Ok 'PostgreSQL is accepting connections on port 5432' }
else { Write-Fail 'Nothing is listening on port 5432. Start PostgreSQL: Windows key -> "Services" -> postgresql-x64-16 -> Start'; $problems++ }

Write-Step 'Ports'
if (Test-Port 8080) { Write-Warn 'Port 8080 is in use (fine if that is the Nagrik Setu backend already running)' } else { Write-Ok 'Port 8080 is free for the backend' }
if (Test-Port 5173) { Write-Warn 'Port 5173 is in use (fine if that is the Nagrik Setu frontend already running)' } else { Write-Ok 'Port 5173 is free for the frontend' }

Write-Host ''
if ($problems -eq 0) { Write-Host 'Everything needed is installed. Next: run setup-database.ps1 (first time only), then start-backend.ps1 and start-frontend.ps1.' -ForegroundColor Green }
else { Write-Host "$problems problem(s) found. Fix them and run this check again." -ForegroundColor Red; exit 1 }
