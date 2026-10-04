param([string]$Serial = 'emulator-5554')

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$env:JAVA_HOME = "$env:LOCALAPPDATA\Android\jdk-17"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$testAdb = "$env:ANDROID_HOME\platform-tools\adb.exe"

if ($Serial -notmatch '^emulator-\d+$') { throw 'These automated tests use photo fixtures. Run them on an emulator.' }
$testBoot = & $testAdb -s $Serial shell getprop sys.boot_completed
if ($LASTEXITCODE -ne 0 -or ($testBoot -join '').Trim() -ne '1') { throw 'Start an emulator and wait for Android to finish booting before running this script.' }

& .\gradlew.bat assembleDebug assembleDebugAndroidTest lintDebug --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Android build or lint failed.' }
New-Item -ItemType Directory -Force dist,docs\verification | Out-Null
Copy-Item -LiteralPath 'app\build\outputs\apk\debug\app-debug.apk' -Destination 'dist\maa-debug.apk'

& $testAdb -s $Serial install --no-streaming -r 'dist\maa-debug.apk'
if ($LASTEXITCODE -ne 0) { throw 'App installation failed.' }
& $testAdb -s $Serial install --no-streaming -r 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
if ($LASTEXITCODE -ne 0) { throw 'Test installation failed.' }
$testAnimationScales = @{}
foreach ($testScale in @('window_animation_scale','transition_animation_scale','animator_duration_scale')) {
    $testAnimationScales[$testScale] = ((& $testAdb -s $Serial shell settings get global $testScale) -join '').Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Unable to read emulator animation settings.' }
}
try {
    foreach ($testScale in $testAnimationScales.Keys) {
        & $testAdb -s $Serial shell settings put global $testScale 0
        if ($LASTEXITCODE -ne 0) { throw 'Unable to configure emulator test animations.' }
    }
    $testOutput = & $testAdb -s $Serial shell am instrument -w -e disableAnalytics true 'in.family.maa.test/androidx.test.runner.AndroidJUnitRunner' 2>&1
    $testExit = $LASTEXITCODE
} finally {
    foreach ($testScale in $testAnimationScales.Keys) {
        if ($testAnimationScales[$testScale] -eq 'null') { & $testAdb -s $Serial shell settings delete global $testScale | Out-Null }
        else { & $testAdb -s $Serial shell settings put global $testScale $testAnimationScales[$testScale] }
    }
}
$testOutput | Set-Content -LiteralPath 'docs\verification\instrumentation.txt' -Encoding utf8
$testOutput
if ($testExit -ne 0 -or ($testOutput -join "`n") -notmatch 'OK \(\d+ tests?\)') { throw 'Device tests did not pass. See docs/verification/instrumentation.txt.' }
