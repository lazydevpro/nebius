$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$pocoAdb = "$env:LOCALAPPDATA\Microsoft\WinGet\Packages\Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe\platform-tools\adb.exe"
if (-not (Test-Path $pocoAdb)) { $pocoAdb = (Get-Command adb -ErrorAction Stop).Source }
$pocoSerial = '100.79.216.34:5555'
& $pocoAdb connect $pocoSerial
if ($LASTEXITCODE -ne 0) { throw 'Poco connection failed. Check Tailscale and ADB.' }
$pocoModel = & $pocoAdb -s $pocoSerial shell getprop ro.product.model
if ($LASTEXITCODE -ne 0 -or $pocoModel -notmatch 'POCO|Poco|POCOPHONE|F1') { throw "Unexpected or unavailable target: $pocoModel" }
if (-not (Test-Path 'dist\maa-debug.apk')) { throw 'Build the APK first with build.ps1.' }
& $pocoAdb -s $pocoSerial install -r 'dist\maa-debug.apk'
if ($LASTEXITCODE -ne 0) { throw 'APK install failed.' }
& $pocoAdb -s $pocoSerial shell am start -n in.family.maa/.MainActivity
if ($LASTEXITCODE -ne 0) { throw 'App launch failed.' }
