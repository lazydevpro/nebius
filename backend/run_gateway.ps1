param(
    [string]$ApiKey = "",
    [string]$Model = "",
    [string]$DeviceToken = "",
    [int]$Port = 0
)

# 1. Load backend\.env or .env if present
$envFiles = @("backend\.env", ".env", "$PSScriptRoot\.env")
foreach ($f in $envFiles) {
    if (Test-Path $f) {
        Get-Content $f | ForEach-Object {
            $line = $_.Trim()
            if ($line -and -not $line.StartsWith('#') -and $line.Contains('=')) {
                $parts = $line.Split('=', 2)
                $k = $parts[0].Trim()
                $v = $parts[1].Trim().Trim('"', "'")
                if ($k -and -not [System.Environment]::GetEnvironmentVariable($k)) {
                    [System.Environment]::SetEnvironmentVariable($k, $v)
                }
            }
        }
        break
    }
}

# 2. Resolve values with priority: CLI parameter > Environment variable > Default
if (-not $ApiKey) {
    $ApiKey = $env:NEBIUS_API_KEY
}
if (-not $Model) {
    $Model = if ($env:NEBIUS_MODEL) { $env:NEBIUS_MODEL } else { "nvidia/Nemotron-3.5-Super-VL" }
}
if (-not $DeviceToken) {
    $DeviceToken = if ($env:MAA_DEVICE_TOKEN) { $env:MAA_DEVICE_TOKEN } else { throw 'Set MAA_DEVICE_TOKEN in backend/.env.' }
}
if ($Port -le 0) {
    $Port = if ($env:PORT) { [int]$env:PORT } else { 8080 }
}

if (-not $ApiKey) {
    Write-Host "==========================================================" -ForegroundColor Red
    Write-Host " NEBIUS_API_KEY is not configured!" -ForegroundColor Red
    Write-Host "==========================================================" -ForegroundColor Red
    Write-Host "Please paste your Nebius Token Factory API key into: " -ForegroundColor Yellow
    Write-Host "  backend\.env" -ForegroundColor White
    Write-Host "  (or pass -ApiKey '<YOUR_KEY>' on the command line)" -ForegroundColor Gray
    Write-Host ""
    exit 1
}

$env:NEBIUS_API_KEY = $ApiKey
$env:NEBIUS_MODEL = $Model
$env:NEBIUS_BASE_URL = if ($env:NEBIUS_BASE_URL) { $env:NEBIUS_BASE_URL } else { "https://api.tokenfactory.nebius.com/v1" }
if (-not $env:MAA_DEVICE_TOKENS) {
    $env:MAA_DEVICE_TOKENS = "{`"poco`":`"$DeviceToken`"}"
}
$env:PORT = $Port

Write-Host "==========================================================" -ForegroundColor Green
Write-Host " Starting Nebius Token Factory Intent Gateway" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green
Write-Host "  Model:        $Model" -ForegroundColor Cyan
Write-Host "  Base URL:     $env:NEBIUS_BASE_URL" -ForegroundColor Cyan
Write-Host "  Port:         $Port" -ForegroundColor Cyan
Write-Host "  Device Token: $DeviceToken" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Green
Write-Host ""
python backend/server.py
