# Creates the Nagrik Setu database user and databases in a local PostgreSQL. Safe to run more than once.
# Usage:  powershell -ExecutionPolicy Bypass -File scripts\windows\setup-database.ps1
param(
    [string]$AppUser = 'nagrik',
    [string]$AppPassword = 'nagrik'
)
. (Join-Path $PSScriptRoot 'common.ps1')

Write-Step 'Looking for PostgreSQL'
$psql = Find-Psql
if (-not $psql) {
    Write-Fail 'psql.exe was not found. Install PostgreSQL 16 or 17 from https://www.postgresql.org/download/windows/'
    exit 1
}
Write-Ok $psql
if (-not (Test-Port 5432)) {
    Write-Fail 'PostgreSQL is not running on port 5432. Start the "postgresql-x64-..." service in Windows Services and try again.'
    exit 1
}

Request-PostgresPassword

Write-Step "Creating database user '$AppUser'"
$exists = Invoke-Psql $psql "SELECT 1 FROM pg_roles WHERE rolname = '$AppUser'"
if ($exists -eq '1') {
    Invoke-Psql $psql "ALTER ROLE $AppUser WITH LOGIN PASSWORD '$AppPassword'" | Out-Null
    Write-Ok "User '$AppUser' already existed; password set to the configured value"
} else {
    Invoke-Psql $psql "CREATE ROLE $AppUser WITH LOGIN PASSWORD '$AppPassword'" | Out-Null
    Write-Ok "User '$AppUser' created"
}

foreach ($db in @('nagrik_setu', 'nagrik_setu_test')) {
    Write-Step "Creating database '$db'"
    $exists = Invoke-Psql $psql "SELECT 1 FROM pg_database WHERE datname = '$db'"
    if ($exists -eq '1') {
        Write-Ok "'$db' already exists"
    } else {
        Invoke-Psql $psql "CREATE DATABASE $db OWNER $AppUser ENCODING 'UTF8' TEMPLATE template0" | Out-Null
        Write-Ok "'$db' created"
    }
}

Write-Host ''
Write-Host 'Database ready. Next: start the backend with scripts\windows\start-backend.ps1' -ForegroundColor Green
