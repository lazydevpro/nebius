$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = "$env:LOCALAPPDATA\Android\jdk-17"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
if (-not (Test-Path "$env:JAVA_HOME\bin\java.exe")) { throw 'Set JAVA_HOME to an installed JDK 17.' }
Set-Location $PSScriptRoot
$sdkPath = $env:ANDROID_HOME.Replace('\','/').Replace(':','\:')
Set-Content -LiteralPath 'local.properties' -Value "sdk.dir=$sdkPath"
& .\gradlew.bat assembleDebug lintDebug --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Android build failed' }
New-Item -ItemType Directory -Force 'dist' | Out-Null
Copy-Item -LiteralPath 'app\build\outputs\apk\debug\app-debug.apk' -Destination 'dist\maa-debug.apk'
