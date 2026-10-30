# Builds the APK for judges. It holds no Nebius key: AI requests go through the gateway,
# using MAA_PUBLIC_TOKEN and MAA_GATEWAY_URL from secrets.properties.
$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = "$env:LOCALAPPDATA\Android\jdk-17"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
Set-Location $PSScriptRoot
if (-not (Select-String -LiteralPath 'secrets.properties' -Pattern '^MAA_PUBLIC_TOKEN=.+')) { throw 'Add MAA_PUBLIC_TOKEN to secrets.properties.' }
& .\gradlew.bat assembleDebug -PmaaPublicBuild --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Android build failed' }

# Refuse to hand out an APK that contains the key.
$apk = (Resolve-Path 'app\build\outputs\apk\debug\app-debug.apk').Path
$key = (Select-String -LiteralPath 'secrets.properties' -Pattern '^NEBIUS_API_KEY=(.+)$').Matches[0].Groups[1].Value.Trim()
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead($apk)
try {
    foreach ($entry in $zip.Entries) {
        $stream = $entry.Open()
        $bytes = New-Object IO.MemoryStream
        $stream.CopyTo($bytes)
        $stream.Dispose()
        if ([Text.Encoding]::GetEncoding(28591).GetString($bytes.ToArray()).Contains($key)) { throw "Nebius key found in $($entry.FullName)" }
    }
} finally { $zip.Dispose() }

New-Item -ItemType Directory -Force 'dist' | Out-Null
Copy-Item -LiteralPath $apk -Destination 'dist\maa-test-build.apk' -Force
Write-Output 'dist\maa-test-build.apk has no Nebius key.'
