# Maa — a Hindi and English phone home for my mom

An Android launcher for someone who recognises family faces and uses speed dial,
but cannot comfortably read smartphone menus. Package: `in.family.maa`.
Android 8+; physical target: Poco F1.

The current app is version **0.2.0-preview** and is not production ready. The researched
[rebuild plan](docs/PRODUCT_PLAN.md) covers complete BIG-suite parity, dependable
Home behavior, an original accessible interface and bounded guided-app assistance.
See the [feature inventory](docs/research/BIG_LAUNCHER_PARITY.md) and
[independent code audit](docs/research/INDEPENDENT_CODE_AUDIT.md) for scope and gaps.

The rebuild adds the selected compact Home design, live device status, caregiver
PIN, app search/favorites/hiding, family message drafts, alarms, opt-in notification
history and an explicit assistant with a floating navigation helper. Alarms,
notifications and Shop QR are under **More**. These features do not establish full
BIG-suite parity or reliable control inside WhatsApp. Original design concepts
are in `output/ui-mockups`; actual device evidence stays local in `docs/verification`.

## Build on this Windows machine

Run `./build.ps1` from PowerShell. It uses the installed Java 17 and Android SDK,
builds the debug APK, runs Android lint, and copies the result to
`dist/maa-debug.apk`. Android Studio is not required to be open.
The standard Gradle wrapper is included. For other machines, install JDK 17 and
Android SDK 36, set `local.properties`, then run `gradlew assembleDebug lintDebug`.

## Install on the Poco

Run `./install-poco.ps1` once the phone is online; it checks the model before
installing and preserves app data. Equivalent commands:

```powershell
$adb = "$env:LOCALAPPDATA\Microsoft\WinGet\Packages\Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe\platform-tools\adb.exe"
& $adb connect 100.79.216.34:5555
& $adb devices -l
& $adb -s 100.79.216.34:5555 shell getprop ro.product.model
& $adb -s 100.79.216.34:5555 install -r dist/maa-debug.apk
& $adb -s 100.79.216.34:5555 shell am start -n in.family.maa/.MainActivity
```

Keep ADB on the existing private Tailscale network. No port forwarding.

## Configure once as a family member

Open Settings; add up to three names, numbers and photos. Contacts use the
system dialler, preserving its final call control. Choose Maa as the default Home
app in Android settings if desired. All family buttons still work without the AI
backend. Tap **English** or **हिन्दी** at the top of Home to switch
languages. Hindi is the default; the choice is saved across app restarts. The
interface, spoken prompts, shared request text and floating help button use the
selected language. Contact names and other text you enter stay as written.
Switching preserves unfinished settings and the entered QR amount. This changes
Maa only; Android permission screens and other apps keep their own language.

Speech recognition uses Hindi (India) or English (India), matching the switch.
The phone needs a recognition service and a TTS voice for that language. If the
TTS voice is unavailable, prompts remain visible and the buttons still work.

Unfinished family settings survive activity recreation and returning from the
photo picker. Text fields and switches take effect only after pressing Save;
choosing a contact photo stays in the draft until Save. The help screen provides
large buttons for choosing a family contact or opening the message share chooser.

The floating help button requires “display over other apps” permission. Return to
Maa settings after granting it and press the enable button again. It offers Home,
family and assistant routes;
the microphone runs only after the voice button is pressed. The service can be
stopped from Maa settings. Some apps prevent overlays, and MIUI may need manual
background settings. Validate those on the Poco rather than assuming them.

Optional notification access allows quieting WhatsApp notifications whose titles
do not exactly match the configured trusted names/group names (one per line).
Save the list and enable the switch. This is **not spam detection** or message
blocking. Group summaries and variations in display names can be quieted too;
test before relying on it. This quieting feature does not retain message content.
Separately, the Notifications screen can opt into local notification history
(up to 100 entries for seven days). Turning history off clears it. Notification
actions are available only while their live notification handles remain valid.

An experimental, separately opt-in accessibility screen guard masks UI nodes in
WhatsApp whose exact Hindi/English labels match Updates/Communities/Channels/Status
or whose visible text contains an HTTP link. It does not tap underlying controls
or retain screen content. Enable the switch, save, and grant Maa's screen guard
accessibility access. It is a prototype, not reliable protection: unsupported
labels, embedded links, images, screen changes and UI-tree omissions can bypass
it. Test placement and removal on the actual installed WhatsApp before enabling
it for your mom. The masks open Maa for help when pressed. No general-purpose
autonomous accessibility actions are implemented.

## Voice and live Nemotron

Hindi and English commands route locally to family, messages, apps, alarms or Home.
Speech begins on an explicit tap and the transcript is reviewed before submission.
Cloud classification requires separate opt-in for the configured HTTPS endpoint.

The backend uses standard-library Python. Set `NEBIUS_API_KEY`,
`NEBIUS_MODEL` (an available NVIDIA Nemotron model ID), and `MAA_DEVICE_TOKENS`
(a JSON map of device IDs to random tokens of at least 24 characters), then run
`python backend/server.py`. Legacy `MAA_ACCESS_TOKEN` additionally requires
`MAA_ALLOW_LEGACY_TOKEN=1`. See [backend limits and deployment requirements](docs/implementation/BACKEND_CONTRACT.md).
It binds to localhost port 8080. Put it behind an authenticated HTTPS deployment
with rate limits before connecting the phone. Enter that HTTPS base URL and the
app-specific access token in family settings. Never enter the Nebius API key in
the APK or commit it. `NEBIUS_BASE_URL` defaults to
`https://api.tokenfactory.nebius.com/v1` and may be overridden for your account.

Only submitted text, after cloud opt-in, is sent to the configured backend and then Nebius.
The backend validates the action against a fixed allowlist, drops extra fields,
and does not log transcripts. It cannot run arbitrary commands or pay anyone.
The app token is stored in private app preferences; this prototype has not yet
implemented a production credential provisioning system. No sponsor credentials
are included and live inference has not been validated.

Run backend checks with `python -m unittest discover -s backend -v`.

## Shop QR requests

Capture a full-resolution photo through the system camera. The app attempts local
QR decoding and recognises UPI pay URIs, displaying the supplied amount when
present. A QR-provided merchant name is untrusted. Speak or type the amount;
recognised digit strings (including Devanagari digits) are supported. Confirm the
amount before sharing the photo and request text through Android's share chooser.
The caregiver must verify the merchant and pay using their own payment app.
The app never marks a chooser launch as delivery or payment completion.
The entered amount survives activity recreation. Retaking a photo uses a separate
capture file so cancelling the camera keeps the previous request photo intact.

## Development checks

Run `python -m unittest discover -s backend -v` for the local gateway tests.
Run `./test-emulator.ps1` once an Android emulator is fully booted to build, lint,
install and run the six Android regression tests. Pass `-Serial emulator-5556`
if needed. The script accepts emulator serials only because the QR test uses a
temporary camera-result fixture. It writes its result to
`docs/verification/instrumentation.txt`.

The Android tests cover both language directions, persistence after recreation and
relaunch, draft settings and QR amounts across language changes, English payment
confirmation, help navigation, returning Home from the help intent, and rejecting
malformed backend addresses in both languages.
They do not verify speech recognition, camera hardware, WhatsApp, real sharing or
live inference. Continue using the Poco acceptance checklist for those features.

## Current boundaries

This first APK does not yet provide a simplified WhatsApp media viewer, reliable
complete in-app button/link protection, automated chat navigation, SMS blocking, fraud
classification, automatic caregiver delivery, caregiver acknowledgement tracking,
or a production hosted backend. WhatsApp opens its normal interface. These are
remaining parts of the wider concept, not capabilities of this APK.

Physical verification on Poco is required for Hindi/English speech, photos, camera/QR,
sharing, notification access, overlays, background reliability and real WhatsApp.
The dedicated device is the primary acceptance target. Build success alone is
insufficient. See `docs/TESTING.md` for the task loop and acceptance checks.

## Sources used for build and integration

- [Android build plugin compatibility](https://developer.android.com/build/releases/about-agp)
- [Android Java versions](https://developer.android.com/build/jdks)
- [Android in-app language packaging](https://developer.android.com/guide/app-bundle/configure-base#handling_language_changes)
- [Nebius inference API](https://api.tokenfactory.nebius.com/docs)

Original code: MIT. ZXing core: Apache-2.0; included as a Maven dependency.
