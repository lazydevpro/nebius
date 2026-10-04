# Independent code and UX audit for the Maa launcher expansion

Audit date: 2026-10-03. This is a planning audit of the checked-out source and saved evidence, not a production certification. No app code, phone settings, or user data were changed during this audit. No live provider calls or new device tests were performed.

## Assessment

Maa is a small, useful launcher prototype with bilingual navigation, three family contact slots, voice intent routing, payment-request preparation, and experimental assistance services. It is not yet a complete launcher product, a floating AI assistant, or an enforced family-only interface.

The best path is to preserve its narrow, safe behaviors and localization work while replacing the monolithic UI and experimental restriction architecture. A launcher can simplify entry points; that alone does not constrain what happens after an unrestricted third-party app, dialer, or chooser opens. “Family-only WhatsApp” therefore needs a feasibility gate and a precise, demonstrated boundary before becoming an acceptance claim.

The manifest already declares `MAIN` / `HOME` / `DEFAULT` (`app/src/main/AndroidManifest.xml:17`). The missing work is default-Home onboarding, role/status handling, reliable Home reentry, launcher content, and actual device verification. Describing this as starting from an app with no Home declaration would be inaccurate.

## Evidence and limits

- Independently calculated `dist/maa-debug.apk` SHA256: `AE5ED95396015156CA3A65E1B6D2807292F1D8DFA61FAF369F8CBEA3F6897E21`.
- Inspected the successful saved Poco output: `docs/verification/instrumentation-poco.txt:6` reports five tests, and `docs/verification/instrumentation-poco-qr.txt:6` reports one test. These are six successful cases across two runs, not evidence of a single six-case execution.
- Inspected `docs/verification/poco-home-hi.png` and its XML hierarchy. The captured unconfigured Home is spacious, uses large controls, and has no apparent overlap in that state. It does not prove configured-contact layouts, enlarged fonts, screen-reader behavior, or independent use by a low-literacy person.
- Read all six production Kotlin files, the manifest/resources, the complete instrumentation suite, and the Python gateway/tests. No Android JVM unit-test source tree exists in this snapshot.
- Ten backend cases exist in `backend/test_server.py`. This audit inspected them; it did not rerun them or independently reassert the prior build/lint result.
- The workspace was not a Git repository when inspected. Establish an immutable source baseline before parallel implementation so code, test logs, and APKs can be associated reliably.

The coordinator is updating historical status documents separately. Their older connection/test narratives were not used as the basis for the findings below.

## Findings that block the expanded product contract

### P1: Family-only access has no enforcement boundary

`MainActivity.kt:193-195` launches WhatsApp's normal entry activity. `MainActivity.kt:205-210` launches an unrestricted Android share chooser. `MainActivity.kt:188-191` hands a configured number to the system dialer. None of those paths restrict the later recipient or destination inside the external app.

`WhatsAppGuard.kt:18` recognizes a fixed list of Hindi/English tab labels. Lines 35-40 stop traversal after 600 nodes or 12 matches, identify links using simple visible-text patterns, and skip large rectangles. Lines 29-30 remove masks when no suitable root is available. Lines 54-55 draw rectangular overlays; they do not implement an allowlist of family conversation identities or actions. The guard never inspects a conversation's allowed recipient. A contact with a malicious link image, an unlabeled link, an unrecognized language, or a newly structured WhatsApp screen falls outside the current heuristic.

The notification filter is also not an identity boundary. `FamilyNotifications.kt:13-18` compares the notification title with user-entered names. Calls and ongoing notifications bypass filtering at line 15. An unknown sender with a matching visible name can pass; a family summary or changed display name can be dismissed. The present experimental and notification disclosures are appropriately modest (`res/values/strings.xml:54-55,89`); do not remove them while leaving the implementation unchanged.

**Required decision:** distinguish a simplified launcher, guided third-party app access, and any managed-device restriction mode. Each needs its own promise. Even restricting the allowed app list does not by itself enforce a recipient allowlist within WhatsApp. Strict family-only use must be proved for every supported entry path or provided through an owned family communication surface. If the WhatsApp feasibility spike cannot satisfy the strict contract, the product must show that mode as unavailable rather than silently opening the unrestricted app.

### P1: Caregiver settings are a click-through notice

`MainActivity.kt:173-175` opens configuration after a normal confirmation dialog. It provides no caregiver authentication. The same screen edits phone numbers, backend destination/token, notification allowlist, and guard state (`MainActivity.kt:286-312`). An accidental or unauthorized visit can change the family recipient and disable assistance. Photo selection saves immediately even if the rest of the draft is abandoned (`MainActivity.kt:226`).

**Required gate:** caregiver setup and all configuration entry points require a defined unlock flow with relock on timeout/background/restart, secure local credential handling, and a usable recovery path. Treat a caregiver PIN as protection against unintended changes, not proof of device-wide tamper resistance. Configuration must commit atomically or clearly expose which edits take effect immediately.

### P1: Spoken money amounts can change meaning

`MainActivity.kt:337-340` takes the first substring matching `[0-9]+(?:\.[0-9]{1,2})?`. Independently reproducing that expression gave:

| Transcript | Current match |
| --- | --- |
| `1,000 rupees` | `1` |
| `₹1,250.50` | `1` |
| `-50 rupees` | `50` |
| `125.999 rupees` | `125.99` |
| `not 100, 200` | `100` |
| `one hundred rupees` | no match |

Typed/QR-supplied values use `BigDecimal` and a positive/range check but no two-decimal-place rule (`MainActivity.kt:262-263`). The code does not execute payments, and its request confirmation says so (`MainActivity.kt:266`; `strings.xml:83-86`). Those are valuable safety constraints, but they do not make an incorrectly interpreted request acceptable.

**Required gate:** parse a whole supported monetary expression into integer minor units; explicitly support Indian/Western grouping, Hindi digits, and the intended spoken-word vocabulary. Reject negatives, overprecision, multiple/conflicting amounts, overflow, and ambiguous input. Read back the exact amount and recipient before an explicit confirmation. No ambiguous transcript may silently become a valid request.

### P1: The floating service is a Home button, not an AI assistant

`HelpOverlayService.kt:42` only starts `MainActivity`. Its fixed 140-by-72 dp window has no drag/reposition, selected-app scope, interaction state, contextual task UI, or microphone controls (`HelpOverlayService.kt:40-45`). It returns `START_NOT_STICKY` at line 48. There is no persisted enabled-state restoration or boot receiver in the manifest. The manifest's description refers to selected apps, but the service itself does not filter foreground apps.

**Required gate:** implement explicit idle/listening/thinking/confirmation/error states, a large stable escape-to-Home action, user-controlled placement, and lifecycle/permission health handling. Microphone access must remain explicit. Only typed, allowlisted capabilities may be proposed; UI text and model responses must never directly authorize arbitrary touches, recipients, calls, or payments.

### P2: Launcher responsiveness and recovery have unbounded work

Home/contact photos use synchronous `ImageView.setImageURI` without a thumbnail budget (`MainActivity.kt:153,183`). Camera output is copied over the current request photo without exception handling around the copy (`MainActivity.kt:228-230`). `showQr` decodes the image and runs ZXing on the UI thread (`MainActivity.kt:241-253`). Large, slow, invalid, or revoked photo sources and storage failures are not covered by tests.

The simple file provider has a useful two-path allowlist (`PhotoProvider.kt:14-16`), but every request reuses `shop.jpg`. A production handoff should use a unique immutable request attachment, narrowly scoped grants, and explicit retention/cleanup. Reusing one mutable filename complicates proving that a later recipient reads the intended request image.

**Required gate:** bounded thumbnails and background decode, cancellable work, atomic attachment replacement, missing/revoked-file recovery, and measured Home responsiveness on the Poco. A decode or backend failure must not disable Home or family calling.

### P2: Spoken guidance and accessibility need behavioral verification

`openWhatsApp` calls `speak` and immediately calls `launch`, which stops TTS through `cancelPendingVoice` (`MainActivity.kt:94-97,195,197`). Thus its spoken “choose your family chat” instruction can be cut off at the handoff. The visible status field is a plain `TextView`, without an explicit live-region/announcement strategy (`MainActivity.kt:133`). Contact photos and names are separate clickable views (`MainActivity.kt:151-155`), which can produce redundant focus stops and ambiguous spoken actions.

The captured Home uses the same green treatment for all tasks and emoji-plus-text labels. “Photos / videos” opens full WhatsApp rather than a family-media viewer (`MainActivity.kt:171,193-195`; `strings.xml:10`). Missing contacts are simply absent from Home (`MainActivity.kt:147-149`), while the contacts screen still creates three buttons (`MainActivity.kt:180-185`). There is no setup-complete state that assures a person can reach family before relying on the launcher.

**Required gate:** task-specific imagery and family faces, consistent action labels, correct screen-reader semantics, usable focus order, explicit error recovery, and completion of real tasks by low-literacy users. Large pixels alone do not prove usability.

### P2: The backend is a good bounded classifier, not a production service

Preserve the action allowlist and validation (`backend/server.py:8-16`), constant-time token comparison (`:58-60`), request size/transcript limits (`:62-70`), inference timeout (`:34`), and absence of transcript logging (`:41-42`). The Android client also discards stale results using generation checks (`MainActivity.kt:349-360`).

The gateway has no inbound socket deadline, concurrency cap, rate limit, per-device token lifecycle, or deployment health model; `rfile.read(size)` can wait for an incomplete body (`backend/server.py:65`), and `ThreadingHTTPServer` is used directly (`:84`). Its loopback binding reduces exposure in this prototype; deployment controls are still required before exposing it. The app stores the bearer token with the other preferences (`MainActivity.kt:282,312,355`). Network failure yields a button fallback message, not an attempted local reclassification (`:360`).

**Required gate:** authenticated per-device provisioning, bounded ingress and inference capacity, controlled token rotation, HTTPS deployment, privacy-preserving operational telemetry, and local availability of all core launcher functions.

## Architecture: reuse, extract, replace

| Area | Reuse | Replace or extend |
| --- | --- | --- |
| Home registration | Existing Home intent filter; bilingual app identity | Dedicated Home lifecycle, default-role onboarding/status, app catalog, chosen apps/shortcuts, install/remove/change handling |
| Senior UI | Large-target sizing intent, cream/green base palette, family faces, simple Home escape | The 371-line `MainActivity` mixes screens, domain logic, persistence, speech, network, and camera. Split typed screen state, view rendering, and action execution; create a tested design system |
| Localization | `AppLanguage`, Hindi/English strings, no language splitting, current language persistence tests | Accessible action labels, missing vocabulary, locale-sensitive amounts/dates, voice availability setup, translated recovery flows |
| Contacts/settings | Existing data as migration input; photo/name personalization | Fixed three-slot preferences and unprotected settings. Introduce a versioned caregiver configuration repository, atomic drafts, verified contact identities, and a settings lock |
| Call/payment boundaries | System dialer final control; explicit request confirmation; no payment execution | Recipient-scoped policy, consistent confirmation/readback, exact money parser, durable request state, safe attachments |
| Voice/AI | Recognition/TTS availability checks, cancellation generation, server action allowlist | Activity-owned callbacks and ad hoc thread. Use lifecycle-aware speech/inference controllers, typed actions, deterministic policy validation, and an assistant state machine |
| Overlay | Service/window creation and language-update behavior as a small technical spike | Fixed button and lifecycle behavior; add permission health, scoped visibility, placement, assistant UI, and measured recovery |
| WhatsApp guard | Existing screen-observation code for experimentation and explicit limitation language | Label/rectangle masking as enforcement. Build a supported-version adapter only if the feasibility gate proves the promised tasks and boundaries |
| Notifications | Opt-in model, no message-body persistence, calls preserved | Display-name filtering as a trust model; use a demonstrated identity strategy or retain narrowly described optional quieting |
| Photo provider | Private storage, non-exported provider, URI path allowlist | Mutable shared filenames, lifecycle/grant/cleanup handling; use unique immutable request attachments |
| Tests/build | Existing regression cases, Kotlin/Gradle build, backend validation tests | Add independently asserted domain tests, external-intent interception, UIAutomator/device tests, accessibility checks, capability and failure matrices, reproducible release artifacts |

Suggested domain boundaries: `HomeState`, `CaregiverConfiguration`, `TrustedContact`, `AllowedApp`, `AssistantState`, `AssistantAction`, `ActionPolicy`, `MoneyAmount`, `PaymentRequest`, `PermissionHealth`, and per-app `GuidedAccessAdapter`. Names are illustrative. The architectural requirement is that a renderer or model cannot bypass policy, not a particular UI library choice.

## Existing test names versus actual proof

| Case | What it actually proves | What it does not prove |
| --- | --- | --- |
| `settingsDraftSurvivesActivityRecreationWithoutSaving` (`NavigationTest.kt:38`) | Name/phone/switch draft survives language change/recreation without committing checked fields | Photo cancellation atomicity, caregiver authentication, valid-save migration, full process death |
| `languageSwitchPersistsAcrossRecreationAndRelaunch` (`:62`) | Tested labels and language preference change in both directions | Audible voice quality, every screen/dialog, system app language, all font scales |
| `familyHelpSupportsLargeButtonsAndBackNavigation` (`:84`) | Help controls are displayed; contacts and Back navigation work | Any assertion of target dimensions, contrast, motor accessibility, or low-literacy success |
| `helpOverlayIntentReturnsToHomeFromSettings` (`:96`) | Explicit activity intent with the overlay's flags returns Home | Overlay service startup, permission handling, placement, app switching, service death, actual visible overlay |
| `malformedBackendAddressCannotBeSaved` (`:110`) | One incomplete HTTPS URL is rejected in both languages | Full URL/security matrix, real TLS, authentication, network cancellation, provisioning |
| `qrAmountSurvivesActivityRecreation` (`:124`) | Injected camera-result fixture preserves amount and displays/cancels request confirmation | Real camera, QR decoding, recognition, parsed amount accuracy, recipient selection, delivery, payment |

The QR fixture is a generated white JPEG and invokes `onActivityResult` by reflection (`NavigationTest.kt:131-139`). The backend HTTP tests mock `infer` (`backend/test_server.py:38-65`); they do not establish provider/model performance or live inference accuracy.

## Implementation phases and pass/fail gates

### Phase 0 — Freeze the contract and source baseline

Create a verified feature matrix against the chosen BIG Launcher product/version using primary product evidence and observed behavior. Distinguish launcher functionality, any companion phone/SMS products, and optional features. Do not use “complete parity” as an unbounded slogan. Record every row as present, to implement, platform-dependent, or deliberately substituted with an agreed equivalent.

Pass only when every requested feature has an observable behavior, a test, an owner, and a defined operating mode. Tie source revision, dependency versions, APK hash, device/OS, test identities, and results together. No feature counts as done because a similarly named button exists.

### Phase 1 — Extract the core and make Home dependable

Preserve existing behavior while introducing explicit state/actions, configuration migrations, bounded image work, default-Home setup, and app launch/catalog handling. Add clock/date/battery and the required launcher content from the parity matrix. Support offline Home and caregiver recovery.

Pass: all current regressions remain green; 50 repeated Home/app/Back transitions produce one predictable Home state; default Home survives reboot, cold start, update, and process death on the Poco; another launcher can be restored through the intended caregiver path. Proposed starting performance budgets: Home interactive within 2 seconds cold / 500 ms warm on the Poco, visible response to a tap within 100 ms, no ANR during the scenario suite. Measure and record rather than assume those budgets.

### Phase 2 — Complete senior journeys and protect configuration

Implement the agreed contact/call/media/message/SOS/app-access journeys with photographs, clear outcomes, large controls, language-specific spoken feedback, and caregiver settings protection. Any SOS behavior needs a concrete delivery/call contract and honest state; opening a chooser is not delivery.

Pass: primary actions at least 64 dp with adequate separation; secondary actions at least 48 dp; all screens usable at the chosen maximum supported font/display scale, including 200% text where supported; standard contrast checks pass; TalkBack announces each action/recipient once in a coherent order; no actionable control is hidden by bars, keyboard, or overlay. After caregiver setup, at least 4 of 5 representative low-literacy participants complete each core journey without step-by-step coaching, with zero wrong-recipient or unintended-send events. Treat a real observed safety failure as a release blocker even if aggregate success passes.

### Phase 3 — Prove guided access before expanding it

Build one complete supported WhatsApp family journey, including returning Home, before attempting a generic cross-app assistant. Test exact app versions/languages and identify every entry/escape path: launcher, notification, Recents, deep link, share target, search, unknown chat/call, contact/group profile, links, media viewer, keyboard, rotation, and accessibility-service loss.

Pass: every advertised action succeeds in its supported configuration; unsupported screen/version/permission loss produces a visible recoverable state. A strict mode passes only if every tested disallowed path is denied and an unknown state cannot expose unrestricted interaction. An overlay that usually covers a button does not satisfy this gate. Device-level app allowlisting and within-app family recipient restriction need separate tests and claims.

### Phase 4 — Add the floating assistant and safe request flows

Add the explicit assistant state machine, speech/controller integration, deterministic action policy, exact amount parsing, recipient confirmations, cancellation, and network fallback. Keep local core actions available when AI is unavailable.

Pass: language-specific transcript corpus covers natural phrases and adversarial/ambiguous amounts; no unsupported action executes; zero stale actions after navigating away or cancelling; one user confirmation causes at most one external action; no call/message/payment is inferred to have completed from an intent launch. Test mic denied/permanently denied, recognizer/TTS absent, disconnect/timeout, malformed/oversized replies, duplicate taps, and process death. Verify actual sound/microphone behavior on the Poco with human observation.

### Phase 5 — Production and long-running reliability

Harden provisioning/backend, permission-health guidance, service recovery, updates/data migrations, privacy controls, and release signing. Complete the remaining parity rows and an actual caregiver onboarding/recovery walkthrough.

Pass: all release-blocking findings closed with evidence; a 24-hour Poco soak plus reboot, lock/unlock, battery restriction, permission revoke/regrant, app update, and WhatsApp update/rejection scenarios produce no silent loss of a claimed protection. Run a stable supported Android-version matrix in addition to Android 10. Preserve the tested APK hash and signed-release verification. An unavailable environment is recorded as unverified, never converted into a pass.

## Independent engineering and review loop

1. The coordinator writes a small ticket with user-visible behavior, explicit scope, allowed external actions, failure cases, and acceptance evidence. Every ticket links to the parity/safety matrix.
2. One writer owns production edits for that ticket. Parallel specialists may research or review isolated areas; they do not make overlapping edits in the same files. Use source-control isolation when genuinely parallel implementation is needed.
3. A functional reviewer receives the requirement and resulting code/artifact without the writer's self-verdict. A second reviewer checks accessibility, misuse paths, and failure recovery. Both provide reproducible observations and exact source/test references, not general reassurance.
4. The verifier runs independent checks against the exact built artifact. Use fake/intercepted external intents and a dedicated test account/contact for automation; actual calls or message sends require the explicit task authorization applicable to those actions. Do not treat launching a chooser, mocked inference, or an injected JPEG as proof of a real external integration.
5. The coordinator maintains one issue ledger: severity, reproduction, owner, fix revision, regression check, device evidence, and disposition. All critical/high issues and disputed acceptance claims return to the writer. The reviewer who found a defect verifies the fix.
6. Advance only when the ticket's measurable gates pass and both reviewers have no unresolved release blockers. Preserve failed evidence as well as passes. Re-run broader checks only when code changes or newly discovered risks justify them; do not use repeated easy tests to substitute for missing hard ones.
7. Run a separate end-to-end review after integration. No component reviewer can certify the complete restricted-mode or parity claim from unit tests alone. Final acceptance must include the intended user and caregiver on the real target phone.

Release evidence should be indexed by behavior, not test count: what the person did, what the app did, what was observed, which artifact/device was used, and what remains unverified.
