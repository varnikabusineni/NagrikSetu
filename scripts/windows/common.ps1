# Shared helpers for the Nagrik Setu Windows scripts. Dot-sourced by the other scripts.
# Compatible with Windows PowerShell 5.1 and PowerShell 7.

$ErrorActionPreference = 'Stop'
$RepoRoot = (Resolve-Path (Join-Path (Join-Path $PSScriptRoot '..') '..')).Path
$BackendDir = Join-Path $RepoRoot 'backend'
$FrontendDir = Join-Path $RepoRoot 'frontend'
$OnWindows = ($env:OS -eq 'Windows_NT')

function Write-Step([string]$Message) { Write-Host "==> $Message" -ForegroundColor Cyan }
function Write-Ok([string]$Message) { Write-Host "    OK  $Message" -ForegroundColor Green }
function Write-Warn([string]$Message) { Write-Host "    !!  $Message" -ForegroundColor Yellow }
function Write-Fail([string]$Message) { Write-Host "    XX  $Message" -ForegroundColor Red }

# True if something is listening on 127.0.0.1:<port>.
function Test-Port([int]$Port) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $async = $client.BeginConnect('127.0.0.1', $Port, $null, $null)
        if (-not $async.AsyncWaitHandle.WaitOne(1500)) { return $false }
        $client.EndConnect($async)
        return $true
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

# Major Java version from `java -version` (0 if Java is not installed).
function Get-JavaMajorVersion {
    $cmd = Get-Command java -ErrorAction SilentlyContinue
    if (-not $cmd) { return 0 }
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $output = (& $cmd.Source -version 2>&1 | Out-String)
    $ErrorActionPreference = $previous
    if ($output -match 'version "(\d+)(\.(\d+))?') {
        $major = [int]$Matches[1]
        if ($major -eq 1 -and $Matches[3]) { $major = [int]$Matches[3] }
        return $major
    }
    return 0
}

# Major Node.js version (0 if Node is not installed).
function Get-NodeMajorVersion {
    $cmd = Get-Command node -ErrorAction SilentlyContinue
    if (-not $cmd) { return 0 }
    $v = (& $cmd.Source --version) -replace '^v', ''
    return [int]($v.Split('.')[0])
}

# Finds psql: first on PATH, then in the default PostgreSQL install folders (newest version first).
function Find-Psql {
    $cmd = Get-Command psql -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $roots = @("$env:ProgramFiles\PostgreSQL", "${env:ProgramFiles(x86)}\PostgreSQL")
    foreach ($root in $roots) {
        if ($root -and (Test-Path $root)) {
            $found = Get-ChildItem -Path $root -Directory -ErrorAction SilentlyContinue |
                Where-Object { Test-Path (Join-Path (Join-Path $_.FullName 'bin') 'psql.exe') } |
                Sort-Object { [int]($_.Name -replace '[^0-9].*$', '0') } -Descending |
                Select-Object -First 1
            if ($found) { return (Join-Path (Join-Path $found.FullName 'bin') 'psql.exe') }
        }
    }
    return $null
}

# Runs a SQL statement as the PostgreSQL superuser and returns trimmed output.
function Invoke-Psql([string]$Psql, [string]$Sql, [string]$Database = 'postgres', [string]$User = 'postgres') {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $output = & $Psql -h 127.0.0.1 -p 5432 -U $User -d $Database -v ON_ERROR_STOP=1 -tA -c $Sql 2>&1
    $code = $LASTEXITCODE
    $ErrorActionPreference = $previous
    $text = ($output | Where-Object { "$_" -notmatch 'Console code page' } | Out-String).Trim()
    if ($code -ne 0) { throw "psql failed: $text" }
    return $text
}

# Asks for the PostgreSQL superuser password once (unless PGPASSWORD is already set).
function Request-PostgresPassword {
    if ($env:PGPASSWORD) { return }
    $secure = Read-Host -AsSecureString 'Password for the PostgreSQL "postgres" user (the one you chose when installing PostgreSQL)'
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try {
        $env:PGPASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    }
}
