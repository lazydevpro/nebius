# Rebuild execution — 2026-10-04

Baseline: local Git commit `70e7771`. Existing bilingual APK/evidence preserved. No remote publication. User authorized implementation of the reviewed plan, live top-of-home statistics and several original UI mockups.

## Current contracts and ownership

- Root: MainActivity, original shared visual primitives, Home integration, caregiver setup protection, existing floating assistant/guard, request safety and integration tests.
- Platform writer: new status/Home-role/app-catalog/detail components and platform resources; resolves version-specific APIs before code.
- Communication writer: new family/recipient/composition/native-contact components and communication resources; no incomplete default Phone/SMS role registration.
- Daily-tools writer: new alarm/SOS/notification-center components and daily resources; no actual outgoing communication during tests.
- Each implementation receives independent critique after integration. One writer per file; production changes stay distinct from generated concept images.

## Root component contract

Keep Android API 26 minimum with guarded newer calls; use the platform component's explicit Home-selection route and verify selection on resume. Repeated normal Home intents reset the primary screen. All mockup numbers and portraits are illustrative; production status reads real device state. Native Views provide scalable, screen-reader-labeled targets, original drawn icons, spacing and contrast. Core Home is offline; missing family configuration remains visible.

Configuration unlock is local accidental-change protection, not device-wide confinement. Use salted password derivation, no hardcoded/default PIN, session relock, and an owner-accessible recovery route. Retain atomic text/switch/photo drafts. Do not lock users out of Home or Android emergency communication. Do not claim another app's internal controls are disabled by an overlay.

Voice starts only after an explicit action. Existing classifier can propose allowlisted routes, not arbitrary gestures/sends. Screen content is not uploaded by this build. Retain exact recipient/content review and no automatic retry after an uncertain external handoff. Money input must reject ambiguous, signed, overprecision and conflicting expressions instead of truncating them. Actual payments remain outside the executor.

## Verification required

Build/lint, parser/domain tests, existing regression reconciliation, new Home/status/navigation tests, independent functional and accessibility critique, exact-APK installation on authorized Poco when available, screenshot inspection and real status/default-role checks. Real camera/voice/network toggles, default-role consent, reference suite parity, WhatsApp account setup and supported adapter tests remain distinct physical/user gates. No row in the 251-item inventory is accepted because a tile exists.

## Pending at start

Full reference-menu audit and paid-feature entitlements; public distribution policy qualification; WhatsApp installation/account; APIs/devices beyond Poco; real phone/SMS handler completeness; full passive-guidance and autonomous assistant evidence; intended-user usability and 24-hour lifecycle acceptance. These remain visible while implementation proceeds.

## Verified checkpoint — 2026-10-04 06:29 UTC

The selected visual direction is integrated into native Views and installed on the Poco: one compact clock/date/battery/connection card, no VPN or filler instructions, grouped family faces, teal assistant, colored action tiles and compact Settings/More. User corrections drove two real-device spacing iterations. The independent visual review accepted the compact card; final screenshots show the complete footer above system navigation. Fictional generated portraits/status values were never seeded into the app.

New components are integrated: Home-role request/singleTask return, live status/details, app catalog/search/favorites/hiding, protected caregiver setup, canonical family directory, reviewed external call/message handoffs and drafts, optional contacts/keypad, alarms, SOS preparation, opt-in notification archive, explicit assistant and floating navigation helper. The backend has per-device credentials and bounded request/inference controls.

Independent critic found eight concrete integration defects and one overlay stop defect; writers repaired them and the critic rechecked source. Final build/lint passed; 38 Poco instrumentation tests passed; 35 mocked backend tests passed. APK and installed hash agree. See `../BUILD_STATUS.md` for exact evidence and limits.

Scope not completed: default Phone/SMS handlers/in-call/inbox lifecycle; full 251-row reference suite; family slots beyond three; configurable Home pages/widgets/full preference coverage; supported guided WhatsApp adapter and autonomous assistant execution; deployment/signing; intended-user and overnight lifecycle acceptance. App handoffs do not count as these capabilities. Default Home selection and WhatsApp account setup remain user-controlled prerequisites.
