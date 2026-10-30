$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = "$env:LOCALAPPDATA\Android\jdk-17"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
if (-not (Test-Path "$env:JAVA_HOME\bin\java.exe")) { throw 'Set JAVA_HOME to an installed JDK 17.' }
Set-Location $PSScriptRoot
$sdkPath = $env:ANDROID_HOME.Replace('\','/').Replace(':','\:')
Set-Content -LiteralPath 'local.properties' -Value "sdk.dir=$sdkPath"
& .\gradlew.bat assembleDebug lintDebug --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Android build failed' }
$version = "1.0.2"
New-Item -ItemType Directory -Force 'dist' | Out-Null
Get-ChildItem -Path 'dist' -Filter '*.apk' | Remove-Item -Force -ErrorAction SilentlyContinue
Copy-Item -LiteralPath 'app\build\outputs\apk\debug\app-debug.apk' -Destination "dist\maa-v$version.apk"

$oneDriveBuilds = "$env:USERPROFILE\OneDrive\MaaBuilds"
if (Test-Path "$env:USERPROFILE\OneDrive") {
    New-Item -ItemType Directory -Force $oneDriveBuilds | Out-Null
    Get-ChildItem -Path $oneDriveBuilds -Filter '*.apk' | Remove-Item -Force -ErrorAction SilentlyContinue
    Copy-Item -LiteralPath 'app\build\outputs\apk\debug\app-debug.apk' -Destination "$oneDriveBuilds\maa-v$version.apk"
}
