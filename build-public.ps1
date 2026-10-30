# Builds the APK for judges. It holds no Nebius key: AI requests go through the gateway,
# using MAA_PUBLIC_TOKEN and MAA_GATEWAY_URL from secrets.properties.
$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = "$env:LOCALAPPDATA\Android\jdk-17"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
Set-Location $PSScriptRoot
if (-not (Select-String -LiteralPath 'secrets.properties' -Pattern '^MAA_PUBLIC_TOKEN=.+')) { throw 'Add MAA_PUBLIC_TOKEN to secrets.properties.' }
& .\gradlew.bat assembleDebug -PmaaPublicBuild --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Android build failed' }

# Refuse to hand out an APK that contains the key or the phone's own token.
$apk = (Resolve-Path 'app\build\outputs\apk\debug\app-debug.apk').Path
$private = (Select-String -LiteralPath 'secrets.properties' -Pattern '^(NEBIUS_API_KEY|MAA_DEVICE_TOKEN)=(.+)$').Matches | ForEach-Object { $_.Groups[2].Value.Trim() }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead($apk)
try {
    foreach ($entry in $zip.Entries) {
        $stream = $entry.Open()
        $bytes = New-Object IO.MemoryStream
        $stream.CopyTo($bytes)
        $stream.Dispose()
        $text = [Text.Encoding]::GetEncoding(28591).GetString($bytes.ToArray())
        foreach ($value in $private) { if ($text.Contains($value)) { throw "Private value found in $($entry.FullName)" } }
    }
} finally { $zip.Dispose() }

New-Item -ItemType Directory -Force 'dist' | Out-Null
Copy-Item -LiteralPath $apk -Destination 'dist\maa-test-build.apk' -Force
Write-Output 'dist\maa-test-build.apk has no private values.'
