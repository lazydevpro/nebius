# Build status — 4 October 2026

## Current rebuild preview

- Version `0.2.0-preview` (code 2), installed on the authorized Poco F1 over its existing Tailscale ADB bridge without clearing data.
- Compact shared clock/date/battery/connection card; no Home VPN label or explanatory paragraphs. Original colored tiles, one family panel, English/Hindi, Settings and More fit the Poco viewport. Secondary tools are under More.
- Added local caregiver PIN, Home-role request, app catalog/search/favorites/hiding, family recipient review and drafts, optional contacts/keypad, alarms, opt-in notification history, explicit local/cloud assistant and floating navigation helper. Full BIG-suite parity and supported WhatsApp guidance are unfinished.
- `assembleDebug assembleDebugAndroidTest lintDebug` passed. Lint: **0 errors, 71 warnings**; chiefly unused resources, draw/layout allocations, localization suggestions, compatibility/dependency notices and synchronous preference writes. No lint baseline or suppression was added.
- **38 Android instrumentation tests passed on the Poco** in 55.47 seconds. Includes home bounds in both languages, compact status and large-font layout, modal/Home behavior, existing bilingual drafts/QR navigation, communication identity/drafts, amount parsing, alarm calculations/storage, notification retention, app search and assistant cancellation-state tests. These do not prove real alarm wake/ring, microphone/provider cancellation, network inference or external-app delivery.
- **35 backend tests passed**, using loopback sockets and mocked upstream inference, without real credentials or external inference.
- Current artifact: `dist/maa-debug.apk`; SHA-256 **BD4A8BEAA7D3A9640DC5974CC72F0CB0BC09BDA6A7270C5C251432C8EF3026E4**. The installed `base.apk` hash matches. It is debug-signed and not a production release.
- Actual screenshots: `verification/poco-rebuild-home-en.png` and `verification/poco-rebuild-home-hi.png`. First English automated capture caught a window transition; replaced by a settled ADB capture of the same installed APK. Hindi capture inspected. No Maa fatal exception in the inspected crash buffer.
- Local logs: `verification/rebuild-build.txt`, `verification/instrumentation-rebuild-poco.txt`, `verification/backend-rebuild-tests.txt`. Earlier failed iterations are retained. Device evidence is git-ignored because it may contain personal data.
- Maa's default Home selection is still pending; Android resolved Home to its chooser. WhatsApp remains absent. Physical consent/permissions, real voice/camera, alarms after idle/reboot, actual notification actions, reference-suite audit and intended-user trials remain open.

## Previous prototype checkpoint — 3 October 2026

## Hindi and English

- Added a visible English / हिन्दी switch to every Maa screen. Hindi remains the
  default; the preference survives activity recreation and reopening the app.
- All 97 interface, voice, dialog, help and QR request strings have both language
  resources. Names and other user-entered text are preserved.
- Changing language preserves unsaved settings, switch drafts and QR amounts.
- Speech input/output uses hi-IN or en-IN. Missing TTS voices leave text prompts
  available. Local voice commands and the backend prompt accept both languages.
- The floating help button and its notification update when the language changes.
- Both languages are included in app bundles, independently of device language.
- Current APK: `dist/maa-debug.apk`.
- APK SHA-256: `AE5ED95396015156CA3A65E1B6D2807292F1D8DFA61FAF369F8CBEA3F6897E21`.
- Build and lint passed (zero errors, five nonfatal warnings); ten backend tests
  passed. English/Hindi resource keys and format arguments were checked for parity.
- Current Android runtime verification is incomplete. The first test attempt was
  aborted by system-server watchdog restarts. The existing test AVD was restarted
  at 432x768 / 168 dpi with two CPU cores, preserving data. Maa launched and the
  Hindi home screen was inspected. The rerun encountered window-focus failures in
  its first two tests, then the session was interrupted without a final result.
  No complete passing emulator run is claimed. The installer uses ADB push installation.
- Production audit: not ready; see `PRODUCTION_AUDIT.md`. After the user enabled
  and authorized the ADB bridge, the bilingual APK was installed on the Poco F1
  (Android 10 / API 29). Its installed SHA-256 matches the artifact.
- All six current Android regression cases passed on the Poco in two runs
  (`instrumentation-poco.txt`: five; `instrumentation-poco-qr.txt`: one). QR fixtures
  were used only after confirming the fresh installation had no existing QR photos.
  The cache was empty afterward. Phone animation settings remain unchanged.
- The Hindi home screen was inspected and no Maa fatal exception was found in the
  available crash buffer. Real speech/camera/WhatsApp/overlay acceptance remains open.
- Poco speech quality, real camera/WhatsApp/sharing and live Nebius remain unverified.

## Earlier recovery and stability work


- Family settings drafts, switches and QR amounts now survive activity recreation.
- Choosing a contact photo preserves unsaved settings fields instead of silently
  saving them. Text fields and switches are committed by Save.
- The help flow has large Hindi buttons. The floating help intent returns Home
  from another Maa screen.
- Navigation and leaving the app invalidate pending voice and inference results.
- QR retakes use a separate capture file; cancellation preserves the previous photo.
  Photo previews are decoded at a bounded size.
- Backend URL validation now rejects incomplete URLs, credentials, query strings
  and fragments. Malformed gateway requests produce an error response.
- Added five Android regression cases and `test-emulator.ps1`.
- Created a separate `maa-test-api35` AVD using the installed API 35 image.
  It booted and installed/launched Maa. The Windows host is itself a VM; first
  boot was slow and System UI initially showed an ANR dialog. Reducing the display
  to 432x768 at 168 dpi made interactive checks practical.
- Home-screen layout was inspected; a light-status-bar contrast fix was applied.
- System and keyboard insets now surround the scroll view. This fixes automatic
  scrolling that could place a settings field under the navigation bar.
- All five Android instrumentation tests passed on `maa-test-api35` using the
  final APK. See `verification/instrumentation.txt` (`OK (5 tests)`).
- Build and lint passed (zero errors, 15 warnings); ten backend tests passed.
- Final Home and Help screenshots/hierarchies are saved under `verification/`.
  No Maa fatal exception was found in the emulator crash log. The headless emulator
  was stopped after verification; the dedicated AVD is retained for future tests.
- APK SHA-256: `F9FBE1D7DEA144E591648042B77CD8DDF8A62F6584C6D2004C039BCDED98FE1A`.
- At that earlier checkpoint Poco was offline. Physical-device checks, actual Hindi speech, WhatsApp,
  real camera/share delivery and live Nebius inference remain unverified.

This is a prototype APK. Reliable family-media automation and caregiver
delivery/acknowledgement remain future work; see the README for current limits.
