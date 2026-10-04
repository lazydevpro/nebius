# Device acceptance checklist

2026-10-04 current `0.3.0-preview` result: **42/42 Android instrumentation tests passed on the Poco F1**. Build/lint passed (0 errors, 76 warnings). The unchanged backend previously passed **35 mocked local tests**. See `BUILD_STATUS.md` for the exact installed artifact and evidence. Passing automated tests does not complete the physical checklist.

Regression coverage includes Home viewport fit in both languages with no title/language/footer controls, the largest Ask Asha CTA below the tiles, one compact status card, large-font status layout, clock long-press confirmation and accessibility labeling, Home dismissing settings gates, English/Asha onboarding defaults and unsaved recreation, assistant-name normalization, trusted voice-token replay/spoof rejection, canonical recipient validation and stale identity, per-recipient/channel drafts, bounded money grammar, alarm timing/storage, opt-in notification retention, app search recreation and stale floating-helper stop/start events. Tests preserve pre-existing app preferences and QR fixtures. No actual call, message, payment or cloud inference was initiated.

Build → install/update → launch → interact → observe screenshot/hierarchy/logs →
fix → repeat. Prefer the Poco F1 at `100.79.216.34:5555`; verify its model first.
Never reset it, uninstall unrelated apps, or expose ADB publicly.

1. Home: readable large targets; no overlap with real notch or navigation bars.
   Hold the clock, confirm Open settings, then switch English / हिन्दी; restart and confirm the choice persists.
   Check long English labels with large font, translated dialogs and share text.
   Contact names, unsaved fields and QR amounts must survive switching.
2. Family settings: persist contacts and photos after restart. Empty contacts
   explain the missing setup; valid contacts open the correct dialler number.
3. Hindi and English voice: grant/deny microphone; recogniser absent; network absent; unknown
   request; repeated taps; leave app while awaiting inference. No stale action.
4. Backend: real Nemotron response; wrong token; timeout; malformed output;
   unsupported action. No automatic payment or arbitrary execution.
5. Media: WhatsApp installed/absent; return Home from system navigation.
6. Floating help: enable permission; return and start; use across apps; stop;
   rotate; MIUI background handling; confirm underlying controls remain usable.
   Change Maa language while the help button is active; its label and notification
   must update immediately.
7. Notifications: only after opt-in; exact family matches retained; other title
   quieted; ongoing calls retained; switch off restores normal behavior. Summaries
   and spelling variations must be checked explicitly.
8. QR: full photo captured; valid UPI; unreadable/non-UPI; amount absent;
   Hindi digits; cancel; reject zero/negative/oversized amount; choose recipient;
   verify photo received. Share cancellation never becomes a success claim.
9. Lifecycle: resume after camera/dialler/share; Back returns Home; device lock;
   restart app; large font. Inspect crashes and ANRs.
10. Experimental screen guard: opt-in off/on; Hindi/English supported labels;
    HTTP link text; no mask outside WhatsApp; scrolling moves/removes masks;
    taps on a mask open Maa; unsupported links remain an explicit limitation.

Use `adb install -r` to preserve data. Do not clear data without a test-specific
reason. Physical haptic/audio quality requires direct user observation.

## Automated regression checks

`./test-emulator.ps1` builds and installs both APKs on a fully booted emulator,
runs the Android instrumentation suite, and fails unless the runner reports a
passing result. It temporarily disables system animations for Espresso and
restores their previous values when the run finishes. Current cases:

- Unsaved contact fields and the guard switch survive language switching and
  activity recreation, without writing the draft into family preferences.
- English is the default; language selection lives in onboarding/settings.
  The choice survives recreation and relaunch. Home has no language selector.
- Onboarding defaults to English/Asha; language and name drafts survive recreation,
  and Start persists them. The completed setup does not return on relaunch.
- Home's clock requires a long press and confirmation to access settings; a
  normal tap does nothing. Home intents dismiss the pending confirmation.
- Assistant names are normalized and bounded. Voice-entry tokens cannot be
  forged, replayed, resumed after cancellation or restored to restart recognition.
- The help screen offers large buttons, opens contacts and returns Home on Back.
- The intent used by the floating help button returns Home from settings.
- An incomplete backend URL cannot be saved in either language.
- An entered QR amount survives switching both ways and recreation after a
  simulated camera result. The English confirmation shows the amount and
  explicitly identifies the action as a request, not a payment.

The tests restore the original language preference. The QR test backs up and
restores its cache files. It does not exercise a real
camera or initiate a payment/share. The script intentionally targets emulators;
the Poco remains the physical acceptance device.

Backend checks: `python -m unittest discover -s backend -v`. The 35 cases include
malformed JSON shapes, invalid tokens, action validation, request bounds and inference failures.

## Wider concept still to implement

- A tested simplified family-media journey.
- Supported WhatsApp screen detection and explicit button/link protection.
- Acknowledged caregiver help delivery and request state tracking.
- Meaningful fraud review rather than a generic “unknown = suspicious” rule.
- Judge-ready hosted inference and English-subtitled demonstration.
