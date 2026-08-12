# Start the Luminara backend.
#   .\run.ps1            -> http://0.0.0.0:8000  (reachable from the emulator at 10.0.2.2:8000)
#   .\run.ps1 -Reload    -> auto-reload for development
param([switch]$Reload)

$ErrorActionPreference = "Stop"
Set-Location -Path $PSScriptRoot

if (-not (Test-Path ".env")) {
    Copy-Item ".env.example" ".env"
    Write-Host "Created .env from .env.example — add your keys, then restart." -ForegroundColor Yellow
}

$args = @("-m", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000")
if ($Reload) { $args += "--reload" }

Write-Host "Luminara backend -> http://localhost:8000  (docs at /docs)" -ForegroundColor Cyan
python @args
