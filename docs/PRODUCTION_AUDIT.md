# Maa production-readiness audit — 3 October 2026

**Verdict: not production ready.** The current artifact is a development prototype.
This review includes source inspection, APK metadata/signature inspection, existing
build/lint/backend results, a bounded local gateway probe, and a Poco connection
attempt followed by six passing physical-device regression checks. It is not a
complete real-world acceptance test or penetration test.

## Release blockers

1. **P1 — Spoken payment amounts are parsed incorrectly.**
   [MainActivity.kt](C:/Users/techtorm/Documents/hackathon/nebius/app/src/main/java/in/family/maa/MainActivity.kt:338) takes the first matching digit substring.
   `1,000 rupees` becomes `1`; `-50 rupees` becomes `50`; `100.999 rupees` becomes
   `100.99`. The later positive-value check accepts these transformed values.
   Confirmation and family review reduce impact but do not make the parser correct.
   The examples were reproduced using the exact regex in a local probe, not a live
   speech engine. Normalize supported grouping formats and validate the entire
   amount; reject negative, ambiguous and excessive-precision input. Add Hindi and
   English amount regression tests before approving the payment-request flow.

2. **P1 — The distributed APK enables debugging and uses the Android debug key.**
   `aapt2` reports `android:debuggable=true`; `apksigner` reports
   `C=US, O=Android, CN=Android Debug`. [build.ps1](C:/Users/techtorm/Documents/hackathon/nebius/build.ps1:8) only builds
   and distributes `assembleDebug`. The current Gradle file has no production
   signing setup. Prepare a separately signed, non-debuggable release artifact,
   protect its signing key, and validate update/install behavior. Do not uninstall
   the existing app to work around a future signing-key mismatch without preserving
   the user's data and agreeing a migration approach.

3. **P1 — The AI gateway is a development server without production request controls.**
   [server.py](C:/Users/techtorm/Documents/hackathon/nebius/backend/server.py:65) reads an authenticated request body
   with no socket deadline; a local single-connection probe confirmed the accepted
   socket timeout is `None`. [server.py](C:/Users/techtorm/Documents/hackathon/nebius/backend/server.py:84) starts an
   unbounded threaded HTTP server. There is no implemented rate limit, concurrency
   cap, per-device credential lifecycle, or inference spending limit. One shared
   bearer token is compared at line 59. Binding to localhost is appropriate for
   development, but the repository does not provide the production HTTPS edge or
   deployment described in its README. Add those controls and a supported server
   runtime before exposing this service. No live Nebius call was made in this audit.

4. **P1 — Full device acceptance remains incomplete despite passing regressions.**
   Six Android regression cases now pass on the real Poco F1 (Android 10): language
   switching/relaunch, draft preservation, help navigation, the Home help intent,
   backend address validation, and QR amount preservation/English confirmation.
   Build/lint and ten backend checks also pass. Camera hardware, spoken recognition
   and audibility, WhatsApp layouts, actual sharing, overlay recovery, default-Home
   behavior and MIUI background persistence still need acceptance tests. The QR
   regression uses a temporary image, not camera hardware. Earlier emulator runs
   were interrupted by system-server crashes and window-focus failures; the phone
   results are the current successful regression evidence.

## Additional reliability issues

- **P2 — Contact photos are loaded without bounded thumbnails.**
  [MainActivity.kt](C:/Users/techtorm/Documents/hackathon/nebius/app/src/main/java/in/family/maa/MainActivity.kt:153) and line 183 pass saved image URIs directly to
  `ImageView.setImageURI` while creating the launcher/contact screen. No thumbnail
  sampling, background load or image-size budget is implemented here. A large or
  slow image provider can delay the core launcher UI; memory failure is an
  unverified risk, not a reproduced crash. Use bounded thumbnails with placeholders
  and test large images and revoked photo access on the Poco.
- **P2 — Camera-result file failures can escape the activity callback.**
  [MainActivity.kt](C:/Users/techtorm/Documents/hackathon/nebius/app/src/main/java/in/family/maa/MainActivity.kt:230) copies and overwrites the previous QR photo
  without handling I/O failure. Full storage or an unreadable capture can raise an
  exception, and failure after overwrite begins can damage the previous photo.
  Use a temporary replacement and error handling; test storage failure while
  preserving the current request.
- **P2 — Floating help is not yet verified as a dependable recovery control.**
  [HelpOverlayService.kt](C:/Users/techtorm/Documents/hackathon/nebius/app/src/main/java/in/family/maa/HelpOverlayService.kt:48)
  returns `START_NOT_STICKY`, and no recovery/onboarding flow handles a killed
  service. The enable action announces success before the service's window-add
  outcome is known. Confirm permissions, actual visibility, service loss and MIUI
  background behavior before relying on this button for a nontechnical user.

## Product scope and positives

WhatsApp opens its normal interface. The guard masks a limited set of labels and
HTTP-looking nodes; notification quieting trusts display titles, not verified
sender identity. Neither is comprehensive spam/link protection. Help/payment
requests use Android's chooser with no delivery acknowledgement. These limits are
already disclosed in the README, but the broader family-media/caregiver experience
is not complete.

Useful safeguards already present: no direct call or payment execution; explicit
payment-request confirmation; allowlisted backend actions; request body/transcript
size limits; HTTPS-only app backend configuration; disabled backups; no transcript
logging; restricted photo-provider paths; and invalidation of stale voice results.

## Poco verification result

- Connected to `100.79.216.34:5555` over Tailscale after the user enabled the
  bridge and approved this computer's debugging key.
- Verified `POCO F1`, Android 10, API 29, 1080x2246 at 440 dpi.
- Maa was not installed before this check. Installed the current bilingual APK
  and test APK; the installed app's SHA-256 matches the build artifact.
- Five navigation/language/settings tests passed in 32.53 seconds. The QR
  regression passed separately in 5.501 seconds after verifying there were no
  pre-existing Maa QR photos. Its temporary files were removed/restored by the
  test; the app cache was empty afterward.
- The phone rejected ADB changes to global animations and generic injected taps.
  All original animation values were checked and remain unchanged. In-app
  instrumentation worked without changing those restrictions.
- Hindi home layout was visually inspected; language switching in both directions
  and recreation/relaunch were checked by the tests. English screen layout has not
  yet received a separate screenshot review.
- No Maa fatal exception was found in the available crash buffer. This is a
  bounded observation, not proof of long-term stability.
- Maa is installed and was left on its home screen. No final call, outgoing
  message or payment was initiated; no unrelated application was uninstalled.

## Evidence and release gates

- APK: `dist/maa-debug.apk`, SHA-256
  `AE5ED95396015156CA3A65E1B6D2807292F1D8DFA61FAF369F8CBEA3F6897E21`.
- Lint: zero errors, five warnings (`app/build/reports/lint-results-debug.txt`).
- Poco results: `docs/verification/instrumentation-poco.txt` and
  `docs/verification/instrumentation-poco-qr.txt`; image `poco-home-hi.png`.
- Local probes: `docs/verification/production-audit-probes.json` (mocked inference,
  one local connection, no live credentials or phone data).
- Emulator evidence: `docs/verification/startup.png`,
  `instrumentation-emulator-startup-abort.txt`, `emulator-startup-watchdog.txt`.
- Exit criteria: correct amount parsing; non-debuggable signed release build;
  production backend controls; a completed passing bilingual regression run;
  Poco acceptance with Hindi/English speech, real camera and permissions; and
  explicitly scoped claims for WhatsApp protection and caregiver delivery.

Official references: [Android release preparation](https://developer.android.com/studio/publish/preparing),
[Python HTTP server limitations](https://docs.python.org/3/library/http.server.html),
and [ADB TCP connection setup](https://developer.android.com/tools/adb#wireless).
