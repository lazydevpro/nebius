# Maa Android guided mode: feasibility and acceptance gates

Research checked: 2026-10-03. Planning assumption: a private Poco F1 pilot first, followed by a separately reviewed public release; this sequencing remains subject to the user's preference. This document is research and proposed engineering contracts, not evidence that the described features are implemented or validated.

## Decision

Build Maa as an accessible Home app, family directory, and user-invoked assistant. Use official app entry points and a small number of tested accessibility adapters. Describe third-party UI simplification as **guided mode**, not isolation, a sandbox, or a guarantee that only family contacts are reachable.

An ordinary launcher plus overlays cannot enforce a complete contact-level boundary inside consumer WhatsApp. A strict family-only requirement needs cooperation from the communication application, a first-party communication service whose membership and actions Maa controls, or a supported app-specific management capability. Managed-device policy can strengthen package-level control, but allowing the WhatsApp package still allows its internal features.

## Observed test-device baseline

The root agent reported the following observations from the test phone; this research agent did not independently repeat the device commands:

- Poco F1, Android 10 / API 29, MIUI V12.
- Home intent resolution currently returns `ResolverActivity`; Maa has not been selected as the default Home app.
- Neither `com.whatsapp` nor `com.whatsapp.w4b` is currently installed.

Consequences: Home behavior must be accepted after the user selects Maa. Live WhatsApp adapter acceptance is blocked until user-approved installation and account setup have occurred. No claim in this document establishes current WhatsApp node coverage, recipient identification, or overlay behavior on this phone. Do not create an account, migrate an account, provision device ownership, or reset this phone as an incidental research step.

## Platform capability boundaries

| Mechanism | Capability | Boundary |
| --- | --- | --- |
| Default Home role | Normal Home navigation resolves to the selected launcher | Does not control the lock screen, every system surface, or later changes to the selected launcher |
| Visual mask | Covers pixels | Does not disable the underlying control |
| Touchable overlay | Consumes touches within its window | Does not change the underlying application's permissions, data, or internal navigation |
| Accessibility service | Reads exposed UI semantics and invokes available actions | UI semantics can be missing, stale, ambiguous, or changed by an update |
| Android application sandbox | Separates applications and private data | Does not grant Maa access to WhatsApp's private database or let Maa rewrite its UI |
| Device-owner policy | Restricts packages and selected system behavior | Does not automatically provide per-contact or per-feature WhatsApp policies |

For Home, declare an activity with `ACTION_MAIN`, `CATEGORY_HOME`, and `CATEGORY_DEFAULT`. On API 29, check `RoleManager.isRoleAvailable(ROLE_HOME)`, request the role through the platform flow, and verify it is held. Handle a subsequent Home intent by showing Maa's primary screen rather than retaining an arbitrary subpage. The user must select the role; a launcher cannot assume it was granted. [S1, S2]

`TYPE_APPLICATION_OVERLAY` appears above application activities but below critical system windows. Android can change its position, size, or visibility. `FLAG_NOT_TOUCHABLE` passes touch through and therefore cannot implement a touch blocker. A touchable `TYPE_ACCESSIBILITY_OVERLAY` can intercept interaction while leaving underlying windows introspectable to an accessibility service. These are window/input capabilities, not confinement. [S3, S4]

Accessibility nodes describe what the target app exposes. A custom-drawn control may lack adequate semantics. Events report notable UI changes; a handler reacting to a forbidden click is not a universal pre-action veto. Node actions are preferable to coordinates, but successful dispatch still needs outcome verification. [S5, S6, S7, S8]

Android isolates application data through per-app processes and UIDs. Maa cannot use normal launcher or overlay privileges to load consumer WhatsApp's private chat database or embed it as a controllable Maa-owned client. [S9]

## Architecture options

### A. Recommended private pilot: Home plus bounded assistance

Maa owns the Home screen, the chosen family directory, accessible labels/photos, confirmation screens, cancellation, and recovery guidance. A user-enabled accessibility service adds the floating assistant and a small set of explicit app adapters. Prefer official intents and deep links; use semantic accessibility actions only for supported tasks after observing the installed application.

Keep the model outside action enforcement:

1. The user requests a task through an accessible push-to-talk button or a clear on-screen control.
2. The model proposes a typed intent such as `draft_message(contactId, text)`.
3. Deterministic code checks the permitted recipient, operation, current application/window, adapter compatibility, and confirmation state.
4. The executor performs one validated step and observes its result before continuing.
5. Unexpected state stops the task and offers cancellation, Home, or assistance.

Store canonical contact identifiers and phone numbers locally; do not identify a recipient solely by a display name. Duplicate names, changed numbers, groups, and ambiguous screens require explicit resolution. Treat message text, webpages, and accessibility descriptions as untrusted content, not authority to change the user request or recipient. These are proposed product rules, not Android guarantees.

Keep basic Home and family selection usable without the AI service or network. Show listening/processing/action states clearly. A movable assistant button must not obscure native call controls, the keyboard, or emergency controls. Test it together with any accessibility service the intended user already needs.

### B. Experimental controlled interaction surface over a third-party app

A full app-content accessibility overlay could present Maa's approved controls and invoke selected semantic actions underneath. This is more predictable than scattered fixed-coordinate masks or touch-through holes, but it remains experimental guidance.

The underlying application can change state asynchronously. The overlay may disappear when its service/process dies. System UI, notifications, keyboards, external intents, Recents, multiwindow, other services, and updates introduce routes or states outside the controlled surface. Maa cannot guarantee it can return Home or restore an overlay while its process is dead.

Define “fail closed” narrowly: **Maa refuses to initiate an unsupported or unverified action.** Do not promise that the underlying application becomes inaccessible on Maa failure. A watchdog in the same failing process is not an independent enforcement boundary. Never advertise a mask as preventing non-family communication.

### C. Optional managed-device edition

A device-owner DPC can allowlist Maa and selected packages for lock task mode. Android 9+ supports launching another app into that mode. The Home feature can be enabled when the default launcher is also allowlisted. Package suspension can prevent activities and suppress notifications/ringing, so suspension has communication-availability consequences. Normal device-administrator activation is not equivalent to device-owner provisioning. [S10, S11, S12]

Provisioning is a separate deployment decision, typically involving clean-device setup. A work profile restricts its own scope rather than granting universal control of the personal profile. Do not infer authorization to reset or enroll a personal phone. [S13, S14]

Lock task does not select WhatsApp contacts or remove its internal features. Managed configurations only work where the target app implements the settings. No documented consumer WhatsApp configuration for Maa's requested family-only policy was established in this research. [S15]

### D. Strict family-only communication

If contact-level enforcement is mandatory, build or integrate a communication surface with supported membership, recipient, and action authorization under product control. This changes family onboarding and adoption requirements. Root/custom firmware increases operational scope and still does not establish a supported WhatsApp consumer data or contact-policy API; it is not the default pilot plan.

## WhatsApp integration boundary

Official click-to-chat links, `https://wa.me/<international-number>`, can open a specified account's chat. Adding `?text=<urlencoded-text>` prefills an unsent draft. Maa can expose family-only entry buttons, but WhatsApp navigation remains available after entry. A prefilled draft is not a sent message. A chat link is not a contact allowlist, inbox API, or full custom client. [S16]

The documented public WhatsApp API product is business/customer messaging. This research found no supported general API to replace an existing consumer account's inbox with Maa's custom family-only UI. Notification content or visible accessibility nodes are incomplete observations, not an authoritative chat database. Do not base the architecture on undocumented clients, private database access, or assumed account migration. WhatsApp terms restrict unauthorized access and impermissible automation. [S17, S18]

WhatsApp now documents parent-managed accounts with contact/group controls for pre-teens, rolling out by region. This is not an adult accessibility mode or an integration API Maa can assume is available. [S19]

WhatsApp currently lists Android 6.0 and newer as supported, so Android 10 satisfies today's published OS minimum. That does not validate any Maa adapter. [S20]

## Android 10, later Android, and MIUI

- API 29 supports the relevant Home role and established accessibility node/action APIs.
- `AccessibilityService.takeScreenshot` starts at API 30. A screen-image AI design on this Poco must use a separately consented MediaProjection flow or function without screenshots; do not call the newer API on API 29. Secure content remains a boundary. [S8, S21]
- `TouchInteractionController` starts at API 33 and cannot be a dependency of the Android 10 implementation. [S22]
- Android 12 adds restrictions on untrusted overlay touch-through and permits apps to hide application overlay windows. This must not be generalized to all accessibility overlays, which have distinct trust treatment. [S3, S23]
- Background activity starts have been restricted since Android 10. A generic background service or watchdog must not be assumed able to bring Maa forward at every moment. [S24]
- Xiaomi documents additional autostart and permission controls. No primary source reviewed guarantees exact Poco F1/MIUI V12 behavior. Record the full firmware build and measure service lifecycle, default Home, navigation, battery saver, and permission behavior on the device. [S25]

## Public Google Play release gate

Current Play guidance prohibits ordinary Accessibility API apps from autonomously initiating, planning, and executing actions or decisions. Static, human-defined deterministic scripts are permitted. There is an explicit exception for **verified accessibility tools** when the autonomous feature serves their core disability-assistance purpose. `isAccessibilityTool=true` is an eligibility declaration, not automatic approval. The policy distinguishes disability-focused tools from general voice assistants and ordinary launchers; usefulness to disabled people alone is insufficient. Cognitive impairment support can qualify when it is the primary purpose. [S26]

Therefore, a broad “AI operates any app” public roadmap is blocked pending an accurate product-positioning and eligibility review. Build pilot architecture so supported intents and deterministic execution remain usable if the general autonomous mode cannot ship. A private pilot is not proof of Play acceptance or an exemption from platform permissions, privacy, or WhatsApp requirements.

Public release also requires accurate Accessibility API declarations, review evidence, and applicable data disclosures. Do not represent preventing an adult owner from disabling/uninstalling Maa as an ordinary accessibility privilege; Play identifies specific parental-control and enterprise exceptions. Use narrower APIs where possible. Recheck policy immediately before submission. [S26, S27]

## Consent and action contracts

These are proposed product requirements:

| Operation | Required boundary | Failure handling |
| --- | --- | --- |
| Open family contact/chat | Explicit selected family identity and channel | Stop on ambiguous identity or unsupported app state |
| Place a call | Confirm recipient/channel, or use a clearly labeled, previously understood one-tap shortcut | Never redial automatically after an uncertain result |
| Send a message | Present/read the exact final recipient and text; confirm Send | Any material edit invalidates confirmation; uncertain delivery does not trigger blind resend |
| Change settings or permissions | User-visible explanation and appropriate explicit choice/platform flow | Never use assistance to evade platform consent |
| Payment | Excluded from initial automated executor | Future preparation requires amount/payee review and native user authentication; no credential/PIN/OTP entry by the model |
| Read/share screen or transcript | User understands the data involved and where it is processed | Minimize capture, avoid unnecessary retention, stop capture on cancellation |

An accessible cancel/stop action must remain available. Recovery must preserve emergency communication and a usable way for the owner to leave guided mode. Do not assume an adult's relative has guardian or enterprise-administrator authority.

## Technical spike contracts

Proposed thresholds below are release gates to measure, not observed results. An untested case is not a pass.

### P0 — Home role and Home destination

**Precondition:** Maa installed and the user selects it through the system Home-role flow.

**Test:** 100 normal Home transitions across supported installed apps, including repeat Home intents and Maa subpages. Exercise cold launch, process recreation, reboot/unlock, and each navigation mode offered by the installed firmware. Record the resolved Home component before and after.

**Pass:** Every normal Home action reaches Maa's primary screen; role state and recovery instructions are correct. **Fail:** A chooser unexpectedly recurs, another launcher opens, or Maa remains on an unintended subpage. Lock-screen and explicit owner role-change behavior are documented separately rather than counted as promises Maa can override.

### P0 — WhatsApp adapter feasibility

**Precondition:** User-approved WhatsApp installation and account setup. The current phone does not meet this precondition.

**Test:** Record exact package/version, firmware, language, font/display scale, and navigation mode. Observe family chat entry, draft creation, recipient verification, keyboard, menus, group screens, incoming calls, and interruptions. Check which semantic nodes/actions actually exist.

**Pass:** Each supported workflow has reproducible semantic targets, unambiguous recipient handling, explicit outcome checks, and a stop path. **Fail:** Correctness depends on a display name alone, blind coordinates, missing identity, or an unsupported UI state. Do not quietly broaden support when one happy path succeeds.

### P0 — Failure, exposure, and entry-path audit

**Test:** Kill/force-stop Maa; disable accessibility; revoke overlay access; change orientation/scale; open third-party apps through notifications, external links, share sheets, Recents, and multiwindow; interrupt with calls and keyboard changes. Measure transitions and record any exposed native controls.

**Pass for assistance:** Maa issues no unsupported action and reports loss of required capabilities when it can run. Every exposure route is documented. **Fail for strict confinement:** Any ability to reach a forbidden contact/control. Ordinary overlay architecture is expected to fail the strict-confinement claim and must not be marketed as passing it.

### P1 — Service lifecycle and offline recovery

**Test:** 24-hour idle exercise with screen off/on, available battery-saver modes, reboot, network loss, and low-memory pressure. Record service disconnects, overlay disappearance, recovery time, and battery use.

**Pass:** Baseline Home/family functionality remains usable, failures are detectable, and recovery is understandable. **Fail:** Essential communication depends on AI availability or the UI silently claims assistance is active after required service loss.

### P1 — Action executor and confirmation

**Test:** At least 50 task trials across supported operations, including duplicate names, changed recipient, interrupted confirmation, stale nodes, app switching, cancelled speech, repeated taps, and uncertain send/call results. Use consenting test contacts and explicitly approved send/call tests.

**Pass:** Zero wrong-recipient actions, zero duplicate sends/calls, no action after cancellation, and deterministic stopping on ambiguous state. **Fail:** A model output alone can authorize an external effect, confirmation survives material edits, or a retry can duplicate an uncertain action.

### P1 — Intended-user usability

**Test:** Ask the intended user to select family, open a chat, understand a call/message confirmation, cancel, return Home, and recover from a disabled service. Include the language, vision, motor, and cognitive needs actually present.

**Pass:** The user understands who receives an action, can stop it, and can recover using the agreed accessibility supports. Record task completion, help needed, errors, and time. **Fail:** A technically correct flow repeatedly confuses the user or hides a consequential action.

### P2 — Optional managed-device variant

**Precondition:** Separate, explicitly authorized provisioning on an appropriate test device, with recovery/unenrollment understood.

**Test:** Package allowlist, Home behavior, system navigation settings, policy persistence, crash/reboot handling, suspension consequences, emergency communication, and owner/admin exit. Verify unsupported packages are actually blocked.

**Pass:** Measured package policy matches the stated deployment contract. **Fail:** Contact-level WhatsApp confinement is inferred from package-level lock task success, or emergency/recovery operation becomes unavailable.

## Open blockers and claim register

- **Live WhatsApp validation:** blocked by absent installation/account on the test phone.
- **Strict contact-level restriction:** no supported enforcement mechanism established for consumer WhatsApp; overlay masking does not resolve this blocker.
- **MIUI reliability:** exact firmware behavior and service survival remain unmeasured.
- **Screenshot AI on API 29:** requires a consented MediaProjection design and validation, or a semantic-only scope.
- **Public autonomous assistant:** eligibility and policy review required; setting an accessibility-tool flag does not clear this gate.
- **User preference:** private-pilot-first sequencing is an assumption, not a user commitment to sideloading, device enrollment, or resetting the phone.

Safe product claims after successful relevant tests: “Maa is your selected Home screen”; “Maa offers family shortcuts”; “Maa guides these tested actions”; “Maa stops its automation when it cannot verify the screen.”

Unsupported claims: “Home always wins over every system state”; “WhatsApp is sandboxed inside Maa”; “hidden controls are disabled”; “only family can be contacted through WhatsApp”; “the overlay remains enforced after service failure”; “the AI can operate every application”; “the accessibility-tool flag guarantees Play approval.”

## Primary source register

- **S1 — RoleManager:** https://developer.android.com/reference/android/app/role/RoleManager
- **S2 — Dedicated devices cookbook / custom Home apps:** https://developer.android.com/work/dpc/dedicated-devices/cookbook
- **S3 — WindowManager.LayoutParams:** https://developer.android.com/reference/android/view/WindowManager.LayoutParams
- **S4 — AccessibilityWindowInfo:** https://developer.android.com/reference/android/view/accessibility/AccessibilityWindowInfo
- **S5 — Create an accessibility service:** https://developer.android.com/guide/topics/ui/accessibility/views/service
- **S6 — Custom view accessibility limitations:** https://developer.android.com/guide/topics/ui/accessibility/views/custom-views
- **S7 — AccessibilityEvent:** https://developer.android.com/reference/android/view/accessibility/AccessibilityEvent
- **S8 — AccessibilityService API, actions/gestures/screenshots:** https://developer.android.com/reference/android/accessibilityservice/AccessibilityService
- **S9 — Android application sandbox:** https://source.android.com/docs/security/app-sandbox
- **S10 — Lock task mode:** https://developer.android.com/work/dpc/dedicated-devices/lock-task-mode
- **S11 — DevicePolicyManager:** https://developer.android.com/reference/android/app/admin/DevicePolicyManager
- **S12 — Enterprise security / package suspension:** https://developer.android.com/work/dpc/security
- **S13 — Managed provisioning:** https://source.android.com/docs/devices/admin/provision
- **S14 — Build a device policy controller:** https://developer.android.com/work/dpc/build-dpc
- **S15 — Managed configurations:** https://developer.android.com/work/managed-configurations
- **S16 — WhatsApp click-to-chat:** https://faq.whatsapp.com/5913398998672934
- **S17 — WhatsApp Business Platform:** https://whatsappbusiness.com/products/business-platform/
- **S18 — WhatsApp Terms of Service:** https://www.whatsapp.com/legal/terms-of-service
- **S19 — WhatsApp parent-managed accounts:** https://www.whatsapp.com/security/parent-managed-accounts/
- **S20 — WhatsApp supported operating systems:** https://faq.whatsapp.com/1150261202542208
- **S21 — MediaProjection:** https://developer.android.com/media/grow/media-projection
- **S22 — TouchInteractionController:** https://developer.android.com/reference/android/accessibilityservice/TouchInteractionController
- **S23 — Android 12 hide application overlay windows:** https://developer.android.com/about/versions/12/features#hide-application-overlay-windows
- **S24 — Android 10 privacy changes / background activity starts:** https://developer.android.google.cn/about/versions/10/privacy/changes?hl=en
- **S25 — Xiaomi MIUI system settings:** https://trust.mi.com/docs/miui-privacy-white-paper-global/3/1
- **S26 — Google Play AccessibilityService policy and verified-tool exception:** https://support.google.com/googleplay/android-developer/answer/10964491?hl=en
- **S27 — Google Play sensitive permissions/APIs policy:** https://support.google.com/googleplay/android-developer/answer/16558241?hl=en

Source scope: Android API references distinguish API availability; Xiaomi evidence establishes the existence of vendor settings rather than exact Poco F1 behavior. WhatsApp and Play requirements are time-sensitive and must be rechecked before distribution. The architecture and test thresholds are recommendations derived from these boundaries, not promises made by the source documents.
