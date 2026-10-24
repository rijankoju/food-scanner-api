# Starts Postgres (Docker) and the backend with JDK 25. Keys come from .env.
# Usage: .\run.ps1            (from the food-scanner-api-server folder, in PowerShell)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

# Use the portable JDK 25 if it is installed.
$jdk25 = Join-Path $HOME '.jdks\jdk-25.0.4.1+1'
if (Test-Path $jdk25) {
    $env:JAVA_HOME = $jdk25
}

if (-not (Test-Path '.env')) {
    Write-Warning 'No .env file yet: copy .env.example to .env and add your GEMINI_API_KEY.'
}

docker compose up -d postgres
if ($LASTEXITCODE -ne 0) { throw 'Could not start Postgres. Is Docker Desktop running?' }
& .\mvnw.cmd spring-boot:run @args
