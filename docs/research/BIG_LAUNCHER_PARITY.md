# BIG Launcher suite parity inventory for Maa

Research snapshot: **2026-10-03**. Device target: **Poco F1, Android 10/API 29, MIUI V12.0.3.0.QEJMIXM**, as reported by the root agent's physical-device query.

**Status: provisional documentary inventory, not proven implementation parity.** This artifact covers the full Launcher, Phone, SMS, Notifications and Alarm suite, including paid, optional, legacy, disputed and platform-dependent behavior. No reference app was installed or exercised for this research, and no Maa implementation is certified by these rows.

Coverage: **251 stable requirement IDs; 132/132 rows in manual Part 13 mapped; 5 suite products; 54 requirements explicitly carrying an audit/platform gate.** All 251 acceptance criteria are pending execution. Counts include workflow, integration, maintenance and commercial-boundary records; they are not a marketing claim that BIG has exactly 251 features. The online manual and its PDF count as one source.

Capability keys and acceptance criteria below are original Maa engineering proposals. Source citations establish the existence or boundary of the reference capability; test fixtures, failure cases and desired recovery behavior are our proposed quality bar. Some Maa behavior intentionally improves recovery and accessibility. Obtain original or permissively licensed visual assets and implement independently; do not copy BIG code, screenshots, icons, sounds or theme assets.

## Scope and disposition rules

- **P1**: dependable home, accessible interaction and caregiver configuration.
- **P2**: calling, contacts, SMS and assistance functions, including real default-role integration.
- **P3**: remaining suite breadth, integrations, alternate interaction devices and extended locales.
- **G**: installed-reference or platform decision gate. A slash joins a delivery stage to its gate; it does not remove the requirement.
- **Base**: documented ordinary capability. **Optional**: configurable or dependent on another app/device, still within target scope. **Paid-full reference**: test the useful unlocked behavior. Free trial restrictions are recorded rather than reproduced.
- **Compatibility**: reproduce the user-visible reliability outcome. A legacy implementation workaround need not become a Maa setting if an independent implementation solves the underlying issue; record that equivalence explicitly.
- Each row eventually needs one disposition: implemented-and-tested, equivalent-and-tested, platform-blocked-with-tested-fallback, deliberately-deferred-with-release, or reference-commerce-only. An unavailable platform operation cannot silently become done.
- Hindi and English are mandatory from P1. The broader locale inventory and RTL remain staged coverage for a literal full-suite claim.
- AI assistance and guided use of other apps are additional Maa scope. Launching or deep-linking to another app does not establish guidance or control inside it.

## Primary sources

| Key | Official URL | Evidence used |
|---|---|---|
| L | https://play.google.com/store/apps/details?hl=en&id=name.kunes.android.launcher.demo | Launcher capabilities and free/full boundary |
| P | https://play.google.com/store/apps/details?id=name.kunes.android.launcher.bigphone | Phone capabilities, paid keypad/history, release notes |
| S | https://play.google.com/store/apps/details?hl=en&id=name.kunes.android.launcher.bigmessages | SMS capabilities, limitations, release notes |
| N | https://play.google.com/store/apps/details?id=cz.twobig.notifications | Notifications capabilities and paid settings |
| A | https://play.google.com/store/apps/details?id=cz.twobig.alarm | Alarm capabilities and free status |
| W | https://biglauncher.com/home/en/ | Suite overview, layouts, contact images, notification log |
| M | https://biglauncher.com/manual/en/ | Version 5.9.0; numbered parts and named preference sections |
| MPDF | https://biglauncher.com/manual/en/big_launcher_manual_en.pdf | Same manual, 77-page PDF; not an independent source |
| B | https://blog.biglauncher.com/show-only-needed-apps-android-home-screen/ | Buttons, integrations, app-list filtering |
| V | https://blog.biglauncher.com/big-launcher-low-vision-simpler-android-setup/ | Accessibility and notification behavior |
| H | https://blog.biglauncher.com/android-phone-handover-checklist-uk/ | Defaults, SOS, caregiver setup and handover |
| C | https://blog.biglauncher.com/simple-android-phone-calls-texts-en-gb/ | Phone/SMS roles, keyboard and MMS boundary |
| E | https://blog.biglauncher.com/make-android-phone-easier-older-parent-senior-launcher/ | Stable configuration and accidental-action protection |

Evidence locators such as `M:13.Phone` identify the manual part and preference group. `M:6 images` is deliberately provisional because a control appears in illustrated documentation and requires reference-app inspection. Public source pages can change; keep the snapshot date and record the installed builds at the audit gate.

## Reference-app audit gate: required before “complete parity”

Gate ID: **AUDIT-REFERENCE-01**. Status: **not run**.

1. Record the actual F1 model, Android release/API, MIUI build, navigation mode, SIM configuration, locale, reference package IDs, installed versions and source of installation. Use legitimate full-version entitlements for paid capabilities; record each app's entitlement separately.
2. Capture a private reference configuration and starting default apps. Use synthetic contacts, test messages and consenting family endpoints. Do not place real emergency-service calls. Keep secrets and personal message content out of the audit report.
3. Walk every first-run step and default-role prompt. Then enumerate every top-level menu, submenu, overflow, long-press action, editor option, selector value, switch, dependent item and permission screen in **all five apps**. Repeat with supported permission/role states and relevant companion apps present or absent. Inspect additional controls that appear only during a call, an alarm or receipt of a notification.
4. For each observed control record its exact breadcrumb, screenshot/evidence identifier, default, values, dependencies, paid/free state, visible effect and matching BL requirement ID. A newly discovered control creates a new stable ID; never renumber existing IDs.
5. For Launcher, compare every layout, every tile type, all screen operations, all app-list options, themes, labels, indicators, gestures, administration and recovery. For Phone, inspect contacts, details/editing, dialing, history, ringing and active-call states. For SMS, inspect composition, message detail, templates, filters, alerts and failures. For Notifications, inspect settings, active/history views and real notification actions. For Alarm, inspect all schedule, ringing, snooze and dismissal controls.
6. Resolve every conflict below using the specific installed build and F1 behavior. Preserve disagreement as evidence rather than overwriting it with a marketing claim. Missing source documentation remains an unknown until inspection.
7. Execute the mapped Maa criterion with recorded results. Repeat critical Home, locked-screen incoming call, received SMS, help route and alarm checks after reboot and prolonged idle. Add dual-SIM, denied permissions and disconnected-network checks where relevant.
8. Close the gate only when every observed reference control has a disposition and evidence, every Part 13 row remains mapped, no daily-task failure remains, and mother can perform the selected real tasks without prompting. Explicitly list any deferred or platform-substituted capability beside any parity statement.

**Complete documentary mapping is not complete menu coverage.** Notifications and Alarm do not have an equally detailed preference manual in the inspected sources; their menu audit is especially important. The audit also checks documented behavior absent from Part 13 and options absent from all public material.

## Source conflicts and open decisions

| ID | Conflict / unknown | Evidence | Required decision |
|---|---|---|---|
| C01 | Minimum Android claims disagree with current Phone/SMS release notes specifying Android 6. | P; S | Record actual package metadata. Android 10 clears these advertised minima, but installation and permissions must still be tested. |
| C02 | SMS listing excludes fullscreen SMS popups on Android 10+; manual suggests related controls. | S; M | Test exact F1/build states and retain a readable supported fallback. Do not promise a bypass. |
| C03 | SMS listing says MMS unsupported; official calling/texting guide describes receiving attachments but no MMS creation. | S; C | Test reception separately from composition; preserve media-app handoff as an explicit behavior. |
| C04 | Six layout choices are advertised, but the manual's layout table lists five alongside a six-button default. | W; M | Count actual layout choices and inspect their controls before freezing the equivalent set. |
| C05 | Short/prolonged-touch descriptions are inconsistent. | M; H | Record actual gesture behavior and select a predictable Maa policy with tested accidental-touch protection. |
| C06 | Marketing says no swipe/long-press menus, while optional gesture behavior is documented. | W; H; V | Treat the simple default and optional configuration separately; keep gesture-free daily routes. |
| C07 | Home page generalizes paid suite upgrades, while Alarm's listing says entirely free. | W; A | Use product-specific verified entitlement behavior; do not invent an Alarm payment requirement. |
| C08 | BIG language counts and older compatibility wording vary across sources. | L; P; S; M | Audit locale list in installed builds; independently verify Hindi/English rendering and speech. |
| C09 | Hiding apps and protecting Maa settings do not establish system-wide restrictions. | B; E; L developer response | Document scope accurately; a system-wide managed-device mode needs a separate platform design. |
| C10 | Network toggles and external widgets/shortcuts vary with Android and installed providers. | B | Test F1 handoffs and each supported provider; no guessed deep-link contract. |
| C11 | Backup is mentioned without a complete documented workflow. | M | Audit actual export/restore controls; keep any Maa profile backup clearly identified as independently specified. |
| C12 | SOS automation has role, network and location dependencies; it is a family-contact route. | H | Test supported personal-number flows and cancellation; maintain a separate Android emergency route. |
| C13 | Companion settings and capabilities vary by installation and permissions. | M | Audit all five apps individually with useful entitlements and permission states. |
| C14 | Alarm and Notifications settings beyond published descriptions are unknown. | A; N | Enumerate their installed menus; add IDs for every discovered option. |
| C15 | Historical WhatsApp shortcut examples do not prove current direct call/video behavior. | B | Verify on the installed WhatsApp build; stage guidance/calling extensions separately. |
| C16 | In-call volume protection does not imply global hardware-volume control. | L developer response | Verify foreground-call scope; document Android-controlled behavior outside it. |

## Requirement coverage by area

| Prefix | Area | Requirements |
|---|---|---:|
| BL-SET | Setup and suite administration | 15 |
| BL-VIS | Appearance and status | 12 |
| BL-ACC | Accessible interaction | 11 |
| BL-BTN | Screens, buttons and integrations | 38 |
| BL-APP | App list | 8 |
| BL-SEC | Caregiver protection | 9 |
| BL-CON | Contacts | 23 |
| BL-TEL | Calling and call history | 41 |
| BL-SMS | SMS and message notifications | 33 |
| BL-SOS | Assistance / SOS | 13 |
| BL-NTF | Enlarged notifications | 18 |
| BL-ALM | Alarm | 9 |
| BL-OPS | Compatibility, maintenance and remaining manual controls | 16 |
| BL-COM | Commercial and deployment boundaries | 5 |
| **Total** | **All rows pending execution** | **251** |

## Part 13 crosswalk

All **132** preference/reference rows in manual Part 13 are accounted for. The positions below are **one-based within that group's table in the 2026-10-03 snapshot**, excluding the header. An asterisk maps a container entry to its expanded requirement section. This compact crosswalk avoids copying the manual's descriptions. It must be reconciled after a documentation or reference-app version change.

| Manual group | Row count | Position → Maa requirement |
|---|---:|---|
| Main | 18 | 1: BL-COM-005; 2: BL-SET-006; 3: BL-SET-005; 4: BL-VIS-*; 5: BL-ACC-*; 6: BL-SEC-*; 7: BL-BTN-*; 8: BL-BTN-*; 9: BL-CON-*; 10: BL-TEL-*; 11: BL-SMS-*; 12: BL-SOS-*; 13: BL-APP-*; 14: BL-OPS-*; 15: BL-SET-014; 16: BL-SET-015; 17: BL-SET-011; 18: BL-SET-013 |
| Appearance | 10 | 1: BL-VIS-001; 2: BL-VIS-002; 3: BL-VIS-003; 4: BL-VIS-004; 5: BL-VIS-005; 6: BL-VIS-006; 7: BL-VIS-007; 8: BL-VIS-008; 9: BL-VIS-009; 10: BL-VIS-010 |
| Accessibility | 7 | 1: BL-ACC-005; 2: BL-ACC-006; 3: BL-ACC-004; 4: BL-ACC-002; 5: BL-ACC-003; 6: BL-ACC-007; 7: BL-ACC-008 |
| Preferences | 5 | 1: BL-SEC-001; 2: BL-SEC-002; 3: BL-SEC-003; 4: BL-SEC-004; 5: BL-SEC-005 |
| Screens | 11 | 1: BL-BTN-034; 2: BL-BTN-037; 3: BL-BTN-038; 4: BL-BTN-030; 5: BL-BTN-031; 6: BL-BTN-033; 7: BL-ACC-011; 8: BL-BTN-026; 9: BL-BTN-027; 10: BL-BTN-028; 11: BL-BTN-029 |
| Contacts | 14 | 1: BL-CON-003; 2: BL-CON-004; 3: BL-CON-005; 4: BL-CON-006; 5: BL-CON-007; 6: BL-CON-008; 7: BL-CON-009; 8: BL-CON-010; 9: BL-CON-011; 10: BL-CON-012; 11: BL-CON-013; 12: BL-CON-014; 13: BL-CON-016; 14: BL-TEL-034 |
| Phone | 30 | 1: BL-TEL-005; 2: BL-OPS-001; 3: BL-TEL-006; 4: BL-OPS-002; 5: BL-TEL-007; 6: BL-TEL-008; 7: BL-TEL-009; 8: BL-TEL-010; 9: BL-TEL-011; 10: BL-TEL-012; 11: BL-TEL-013; 12: BL-TEL-014; 13: BL-TEL-015; 14: BL-TEL-016; 15: BL-TEL-017; 16: BL-TEL-018; 17: BL-TEL-019; 18: BL-TEL-020; 19: BL-TEL-021; 20: BL-TEL-022; 21: BL-TEL-023; 22: BL-TEL-024; 23: BL-TEL-025; 24: BL-TEL-026; 25: BL-TEL-027; 26: BL-TEL-028; 27: BL-TEL-029; 28: BL-TEL-030; 29: BL-TEL-031; 30: BL-TEL-033 |
| Messages | 17 | 1: BL-SMS-010; 2: BL-SMS-011; 3: BL-SMS-012; 4: BL-SMS-013; 5: BL-SMS-014; 6: BL-SMS-015; 7: BL-SMS-016; 8: BL-SMS-017; 9: BL-SMS-018; 10: BL-SMS-019; 11: BL-SMS-020; 12: BL-SMS-021; 13: BL-SMS-022; 14: BL-SMS-023; 15: BL-SMS-024; 16: BL-OPS-003; 17: BL-SMS-025 |
| SOS | 9 | 1: BL-SOS-001; 2: BL-SOS-002; 3: BL-SOS-003; 4: BL-SOS-004; 5: BL-SOS-005; 6: BL-SOS-006; 7: BL-SOS-007; 8: BL-SOS-008; 9: BL-SOS-009 |
| Applications | 6 | 1: BL-APP-002; 2: BL-APP-003; 3: BL-APP-004; 4: BL-APP-006; 5: BL-APP-007; 6: BL-APP-008 |
| Compatibility | 5 | 1: BL-OPS-005; 2: BL-OPS-006; 3: BL-OPS-007; 4: BL-OPS-008; 5: BL-OPS-009 |

## Setup and suite administration

| Requirement ID | Capability key | Official evidence | Reference scope | Stage | Original Maa acceptance criterion |
|---|---|---|---|---|---|
| BL-SET-001 | `homeRole` | L; C | Launcher / base | P1 | After first-run setup and reboot, Home always opens Maa; revoking the role leaves an intelligible recovery path. |
| BL-SET-002 | `phoneRole` | P; C | Phone / base | P2 | Incoming and outgoing test calls use the selected phone handler; denied role leaves emergency dialing available through Android. |
| BL-SET-003 | `smsRole` | S; C | SMS / base | P2 | Received SMS and a composed reply use the selected handler; role revocation never produces a false sent state. |
| BL-SET-004 | `guidedSetup` | M:1 | Launcher / base | P1 | A caregiver completes setup from a fresh install without terminal commands; interruption resumes without corrupting saved choices. |
| BL-SET-005 | `language` | M:1,13,Appendix B | Suite / base | P1 | With Android in English, select Hindi in Maa and traverse all owned screens; switching back loses no settings. |
| BL-SET-006 | `setupReplay` | M:13.Main | Launcher / optional | P1 | Reopen onboarding after configuration; explicit cancel preserves the prior working profile. |
| BL-SET-007 | `permissions` | H | Suite / base | P1/P2 | Deny each permission individually, retry later, and confirm unrelated daily tasks still work. |
| BL-SET-008 | `suiteControls` | M:9 | Suite / optional | P2 | Hide administrative entry points inside calling and messaging while retaining a documented caregiver route. |
| BL-SET-009 | `inheritance` | M:9 | Suite / optional | P2 | Change a shared preference centrally and verify all owned modules agree after restart. |
| BL-SET-010 | `overrides` | M:9 | Suite / optional | P2 | Set a module override, change the shared default, and verify the intended override survives; reset restores inheritance. |
| BL-SET-011 | `manual` | M:13.Main | Suite / base | P1 | Open contextual help from every failure state; missing connectivity must not strand the user. |
| BL-SET-012 | `version` | M:12,13.Main | Suite / base | P1 | Caregiver can identify app build and device OS when reporting a fault. |
| BL-SET-013 | `updates` | M:13.Main | Suite / base | P3 | An update check succeeds or reports offline status without interrupting a live call. |
| BL-SET-014 | `privacy` | M:13.Main | Suite / base | P1 | Explain permissions and any Maa cloud processing in readable Hindi/English before activating dependent functions. |
| BL-SET-015 | `exit` | M:12 | Launcher / optional | P1 | Restore the previous launcher through a documented caregiver flow without uninstalling personal data. |

## Appearance and status

| Requirement ID | Capability key | Official evidence | Reference scope | Stage | Original Maa acceptance criterion |
|---|---|---|---|---|---|
| BL-VIS-001 | `themes` | W | Launcher / base | P1 | Original light, dark and alternate palettes pass contrast checks for all text, focus, warnings and disabled states. |
| BL-VIS-002 | `fontScale` | L; P; S | Suite / base | P1 | Test at least three readable presets with Hindi conjuncts, long names and system scaling; no critical action clips. |
| BL-VIS-003 | `rotation` | M:7,13.Appearance | Launcher / optional | P1 | Rotate repeatedly during navigation and editing; each chosen orientation policy preserves state. |
| BL-VIS-004 | `borders` | M:7,13.Appearance | Launcher / optional | P1 | At the smallest and largest supported margins, deliberate edge grips trigger no tile action while central taps work. |
| BL-VIS-005 | `signal` | M:13.Appearance | Launcher / optional | P1 | Toggle each indicator mode and compare the displayed connection state with Android under no-service and Wi-Fi changes. |
| BL-VIS-006 | `battery` | C | Launcher / base | P1 | Compare charging, low-battery and unplugged states against the device; the indicator remains readable. |
| BL-VIS-007 | `alarmIndicator` | M:2,13.Appearance | Launcher / optional | P3 | Schedule, cancel and reschedule a real alarm; the home indicator follows actual next-alarm state. |
| BL-VIS-008 | `immersive` | M:7,13.Appearance | Launcher / optional | P1 | Change status-bar visibility and return from another app; system navigation and caregiver recovery remain reachable. |
| BL-VIS-009 | `navigationTint` | M:13.Appearance | Launcher / optional | P3 | Check navigation contrast on Android 10 with dark and light themes; unsupported behavior has an explicit disposition. |
| BL-VIS-010 | `settingsScale` | M:13.Appearance | Suite / optional | P1 | Caregiver can read and change the longest preference at maximum chosen size without truncation. |
| BL-VIS-011 | `clockDate` | C | Launcher / base | P1 | Verify midnight, timezone changes and a Hindi locale; displayed time matches Android. |
| BL-VIS-012 | `alarmEntry` | M:2 | Launcher / base | P3 | Activate the time area and reach the configured alarm experience; absent handler has a recoverable explanation. |

## Accessible interaction

| Requirement ID | Capability key | Official evidence | Reference scope | Stage | Original Maa acceptance criterion |
|---|---|---|---|---|---|
| BL-ACC-001 | `talkBack` | V | Suite / base | P1 | Complete Home, contact selection, a mock reply and error recovery using screen-reader navigation alone. |
| BL-ACC-002 | `pressSpeech` | V | Suite / optional | P1 | Holding a Hindi or English tile speaks its meaning once without launching its destination. |
| BL-ACC-003 | `pressPopup` | V | Suite / optional | P1 | Request an item explanation and dismiss it with touch or keyboard without accidental activation. |
| BL-ACC-004 | `haptics` | V | Suite / optional | P1 | Toggling tactile feedback affects intentional activations only and respects the device configuration. |
| BL-ACC-005 | `pressPolicy` | M:10,13.Accessibility | Suite / optional | P1 | Exercise short, prolonged and interrupted touches with a tremor simulation; chosen behavior remains predictable. |
| BL-ACC-006 | `outsideDismiss` | M:13.Accessibility | Suite / optional | P1 | Touch outside a pending dialog in each policy; no destructive action is confirmed. |
| BL-ACC-007 | `inactiveFocus` | M:13.Accessibility | Suite / optional | P3 | Traverse an intentionally empty position; spoken help is available when configured without executing an action. |
| BL-ACC-008 | `announcementVerbosity` | V | Suite / optional | P1 | Record announcements through five successive screens; essential context remains understandable without repeated boilerplate. |
| BL-ACC-009 | `keyboard` | L; V | Suite / base | P3 | Traverse every daily flow using arrows, Enter and Back; focus never becomes trapped. |
| BL-ACC-010 | `switches` | L; P; V | Suite / optional | P3/G | Validate with real switch hardware and its Android input configuration before advertising compatibility. |
| BL-ACC-011 | `widgetFocus` | M:10,13.Screens | Launcher / optional | P3 | Enter and exit an embedded widget using keyboard input without losing the surrounding home focus. |

## Screens, buttons and integrations

| Requirement ID | Capability key | Official evidence | Reference scope | Stage | Original Maa acceptance criterion |
|---|---|---|---|---|---|
| BL-BTN-001 | `layouts` | W | Launcher / base | P1 | Audit all six reference densities; Maa provides an equivalent readable configuration with a route to settings at every density. |
| BL-BTN-002 | `allPositions` | L | Launcher / paid-full reference | P1 | Edit every tile position, including the left column, and verify persistence after process death. |
| BL-BTN-003 | `extraScreens` | L | Launcher / paid-full reference | P1 | Create more than five additional pages and verify navigation, persistence and bounded memory use. |
| BL-BTN-004 | `linkCreation` | H | Launcher / optional | P1 | Add a page while its preceding navigation slot is occupied; preserve the displaced destination and create no unreachable page. |
| BL-BTN-005 | `appLaunch` | B | Launcher / base | P1 | Open each chosen installed app; uninstall one and confirm a useful missing-app state. |
| BL-BTN-006 | `contactDetails` | B | Launcher / base | P1 | Open a person with two numbers and distinguish the intended number before calling. |
| BL-BTN-007 | `directCall` | B | Launcher / optional | P2 | Choose one number from a multi-number contact; later contact edits cannot silently change the recipient. |
| BL-BTN-008 | `directSms` | B | Launcher / optional | P2 | Open a new draft for the configured number and verify that no message sends merely by opening it. |
| BL-BTN-009 | `pageLink` | B | Launcher / base | P1 | Navigate a page graph with a removed destination; every surviving page retains an escape route. |
| BL-BTN-010 | `website` | B | Launcher / optional | P1 | Open a saved HTTPS address; invalid address and offline states are recoverable. |
| BL-BTN-011 | `sosEntry` | B | Launcher / base | P2 | The help tile consistently enters the configured assistance flow without starting twice after repeated taps. |
| BL-BTN-012 | `wifiEntry` | B | Launcher / optional | P3/G | On the F1, use a supported Android control or settings handoff; never show a successful state before the OS changes. |
| BL-BTN-013 | `bluetoothEntry` | B | Launcher / optional | P3/G | Test permission refusal, paired and unpaired states, and return from Android controls. |
| BL-BTN-014 | `airplaneEntry` | M:6 | Launcher / optional | P3/G | Validate the platform-supported handoff; an ordinary tile must not claim exclusive network control. |
| BL-BTN-015 | `appListEntry` | B | Launcher / base | P1 | Reach the filtered app list from multiple pages and return to the correct home state. |
| BL-BTN-016 | `preferencesEntry` | B | Launcher / optional | P1 | Administrative shortcuts consistently require the configured caregiver protection. |
| BL-BTN-017 | `externalWidget` | B | Launcher / optional | P3 | Bind an actual provider, restart, resize and remove it; allocated widget IDs are not leaked. |
| BL-BTN-018 | `externalShortcut` | B | Launcher / optional | P3/G | Test an app-provided shortcut and its stale/uninstalled state without guessing unsupported deep links. |
| BL-BTN-019 | `whatsappConversation` | B | Launcher / optional | P3/G | Verify a real conversation destination on the installed WhatsApp build; distinguish this from calling/video actions. |
| BL-BTN-020 | `clearTile` | B | Launcher / optional | P1 | Clear a tile and verify the underlying contact/app is untouched. |
| BL-BTN-021 | `revertTile` | M:6 | Launcher / optional | P1 | Restore a changed tile to its documented default without resetting neighboring tiles. |
| BL-BTN-022 | `customIcon` | B | Launcher / optional | P1 | Apply original artwork and verify visibility in every palette without reusing BIG assets. |
| BL-BTN-023 | `iconSources` | M:6 | Launcher / optional | P3/G | Exercise built-in, app-derived and licensed external artwork; unavailable providers produce a selectable fallback. |
| BL-BTN-024 | `themeExpansion` | M:7 | Launcher / optional | P3/G | Install an original or permissively licensed style extension and recover if it is removed. |
| BL-BTN-025 | `customLabel` | B | Launcher / optional | P1 | Save Hindi and English labels with long words; screen reader and visual label agree. |
| BL-BTN-026 | `automaticLabel` | M:13.Screens | Launcher / optional | P1 | Assign a new action and verify the generated name; user edits survive later app-name changes. |
| BL-BTN-027 | `labelContrast` | M:13.Screens | Launcher / optional | P1 | Inspect labels over bright and dark photographs and focus backgrounds. |
| BL-BTN-028 | `labelScale` | M:6,13.Screens | Launcher / optional | P1 | Exercise every supported label size using a long Hindi fixture without hiding the action. |
| BL-BTN-029 | `labelPosition` | M:6,13.Screens | Launcher / optional | P1 | Each supported placement remains readable with a photo, icon and empty image. |
| BL-BTN-030 | `swipeEnabled` | H | Launcher / optional | P3 | Disable gestures and complete all page transitions through visible controls. |
| BL-BTN-031 | `swipePolicy` | H | Launcher / optional | P3 | Verify both bounded and circular page traversal, including one-page and deleted-page cases. |
| BL-BTN-032 | `swipeMembership` | M:6 images | Launcher / optional; provisional | P3/G | Audit page inclusion controls in the reference, then test included/excluded pages without inaccessible destinations. |
| BL-BTN-033 | `pageMenu` | M:13.Screens | Launcher / optional | P1 | Configure administrative access per page and verify the caregiver can always recover. |
| BL-BTN-034 | `appAttention` | M:2,6,13.Screens | Launcher / optional | P3 | Generate and clear an app notification; the matching tile changes state without affecting unrelated tiles. |
| BL-BTN-035 | `missedAttention` | M:2 | Launcher / base | P2 | A missed test call produces an understandable cue that clears after review. |
| BL-BTN-036 | `smsAttention` | M:2 | Launcher / base | P2 | A new unread SMS produces an understandable cue; reading one thread does not clear another. |
| BL-BTN-037 | `attentionReset` | M:13.Screens | Launcher / optional | P3 | Reset stale attention state and verify a subsequent notification repopulates it correctly. |
| BL-BTN-038 | `suiteAttention` | M:13.Screens | Suite / optional | P3 | Compare internal-module and ordinary-app shortcuts under identical events; neither leaves permanent stale cues. |

## App list

| Requirement ID | Capability key | Official evidence | Reference scope | Stage | Original Maa acceptance criterion |
|---|---|---|---|---|---|
| BL-APP-001 | `installedApps` | L | Launcher / base | P1 | Install, update and remove a sample app; the visible catalog stays accurate without a restart. |
| BL-APP-002 | `recentApps` | L | Launcher / optional | P1 | Use several apps in a known order and verify enabled/disabled recent history. |
| BL-APP-003 | `recentCount` | M:13.Applications | Launcher / optional | P1 | Exercise zero, one and a larger limit; filtering prevents hidden entries reappearing. |
| BL-APP-004 | `allowedApps` | L; B | Launcher / optional | P1 | Hidden entries remain absent from lists, results and recent shortcuts inside Maa. |
| BL-APP-005 | `bulkVisibility` | M:9 | Launcher / optional | P1 | Disable then re-enable a batch; preserve caregiver recovery and existing app data. |
| BL-APP-006 | `searchPlacement` | M:13.Applications | Launcher / optional | P1 | Toggle search visibility with keyboard open; the list stays navigable. |
| BL-APP-007 | `preferencesPlacement` | M:11,13.Applications | Launcher / optional | P1 | Test every supported administrative-list placement with protection enabled. |
| BL-APP-008 | `substringSearch` | M:11,13.Applications | Launcher / optional | P1 | Match a middle-of-name query and verify empty and non-Latin queries without crashes. |

## Caregiver protection

| Requirement ID | Capability key | Official evidence | Reference scope | Stage | Original Maa acceptance criterion |
|---|---|---|---|---|---|
| BL-SEC-001 | `editMenu` | M:13.Preferences | Launcher / optional | P1 | With editing hidden, ordinary presses cannot mutate layout; caregiver entry remains available. |
| BL-SEC-002 | `systemMenu` | M:13.Preferences | Launcher / optional | P1 | Toggle the system-settings entry and verify no stale duplicate appears. |
| BL-SEC-003 | `protectPreferences` | L; E | Launcher / paid-full reference | P1 | Wrong credentials, Back and process recreation cannot expose mutable settings. |
| BL-SEC-004 | `protectSystemEntry` | M:9,13.Preferences | Launcher / paid-full reference | P1/G | Require caregiver verification on Maa's own system-settings route; document other OS routes accurately. |
| BL-SEC-005 | `credential` | M:9,13.Preferences | Launcher / paid-full reference | P1 | Exercise set, change, rejected entry and explicitly chosen confirmation-only mode; never log the secret. |
| BL-SEC-006 | `recovery` | M:9 | Launcher / optional | P1 | From the least dense layout, a caregiver can use a documented recovery path without invoking a daily action. |
| BL-SEC-007 | `passwordLoss` | M:14 | Suite / recovery boundary | P1 | Define and test a secure recovery policy; do not copy an unrecoverable failure merely for similarity. |
| BL-SEC-008 | `stableLayout` | E | Launcher / base | P1 | Repeated long presses and incidental touches cannot move daily actions. |
| BL-SEC-009 | `deletionPolicy` | E | Suite / optional | P2 | Confirm contacts, history and SMS have separate caregiver-controlled deletion policies. |

## Contacts

| Requirement ID | Capability key | Official evidence | Reference scope | Stage | Original Maa acceptance criterion |
|---|---|---|---|---|---|
| BL-CON-001 | `list` | P | Phone / base | P2 | Import a mixed Hindi/English contact fixture and locate the intended person without reading tiny controls. |
| BL-CON-002 | `favorites` | P | Phone / base | P2 | Add and remove a favorite; the home shortcut and calling view remain consistent. |
| BL-CON-003 | `searchFields` | M:13.Contacts | Phone / optional | P2 | Test records with differing given name, family name and notes; supported search choices return expected fixtures. |
| BL-CON-004 | `phoneFilter` | M:13.Contacts | Phone / optional | P2 | Mix callable and email-only contacts; each filter policy gives the intended list. |
| BL-CON-005 | `surnameSort` | M:13.Contacts | Phone / optional | P2/G | Compare a known multilingual fixture with the chosen ordering; explain unavailable provider behavior. |
| BL-CON-006 | `favoritesSwitch` | M:13.Contacts | Phone / optional | P2 | Move between all contacts and favorites without losing the chosen search or focus. |
| BL-CON-007 | `duplicates` | M:13.Contacts | Phone / optional | P2 | A contact with repeated equivalent numbers never causes the wrong recipient to be selected. |
| BL-CON-008 | `starVisibility` | M:13.Contacts | Phone / optional | P2 | Hide favorite editing while preserving access to existing favorites. |
| BL-CON-009 | `deleteVisibility` | E | Phone / optional | P2 | Disable contact deletion and attempt it from every Maa entry point. |
| BL-CON-010 | `systemDetailEntry` | M:13.Contacts | Phone / optional | P2 | Open Android's view for the exact selected record; cancellation returns to Maa safely. |
| BL-CON-011 | `systemDetailDefault` | M:13.Contacts | Phone / optional | P2 | Change the preferred detail handler without changing the underlying contact identity. |
| BL-CON-012 | `internalEdit` | M:4,13.Contacts | Phone / optional | P2 | Change a phone label and name, cancel once, save once, and verify provider data. |
| BL-CON-013 | `systemEdit` | M:13.Contacts | Phone / optional | P2 | Edit an unsupported field through Android and confirm refreshed Maa content on return. |
| BL-CON-014 | `smsChoice` | C | Phone / optional | P2 | The calling choice can present messaging when configured; both paths use the displayed person. |
| BL-CON-015 | `callConfirmation` | E; H | Phone / optional | P2 | A cancelled call prompt makes no call; rapid repeated confirmations produce only one call. |
| BL-CON-016 | `directPolicy` | H | Phone / optional | P2 | In explicitly selected direct mode, one accepted gesture starts one call; default remains recoverable. |
| BL-CON-017 | `create` | H | Phone / base | P2 | Create a new test contact and find it again after restarting. |
| BL-CON-018 | `multipleNumbers` | B; C | Phone / base | P2 | A contact with home and mobile numbers exposes an unambiguous choice. |
| BL-CON-019 | `email` | M:4 | Phone / optional | P2 | Open an email draft for the selected address; no email is sent by viewing details. |
| BL-CON-020 | `otherFields` | M:4 | Phone / optional | P2 | Display a website, note and date fixture legibly; treat their contents as untrusted data. |
| BL-CON-021 | `photoCapture` | M:4 | Phone / optional | P2 | Take a permitted contact photo, cancel camera once, and preserve the previous photo. |
| BL-CON-022 | `photoSelection` | M:4 | Phone / optional | P2 | Select and crop a local picture without retaining access to unrelated images. |
| BL-CON-023 | `numberLabels` | M:4 | Phone / base | P2 | Save multiple labeled numbers including an international number without normalizing them incorrectly. |

## Calling and call history

| Requirement ID | Capability key | Official evidence | Reference scope | Stage | Original Maa acceptance criterion |
|---|---|---|---|---|---|
| BL-TEL-001 | `dialer` | P | Phone / base | P2 | Place a test call using a long international number and a visible correction control. |
| BL-TEL-002 | `answer` | P | Phone / base | P2 | Answer an incoming call while locked, unlocked and inside another app. |
| BL-TEL-003 | `reject` | P | Phone / base | P2 | Reject once and verify the remote endpoint observes rejection without a subsequent accidental callback. |
| BL-TEL-004 | `callerImage` | W | Phone / base | P2 | Check saved, unknown, withheld and photo-less callers with intelligible fallbacks. |
| BL-TEL-005 | `ringtone` | M:13.Phone | Phone / optional | P2/G | Choose a tone and test audibility on the F1; Android denial produces a useful settings route. |
| BL-TEL-006 | `ringingView` | M:13.Phone | Phone / optional | P2/G | After idle and reboot, measure whether the call view appears promptly; record actual platform behavior. |
| BL-TEL-007 | `incomingPresentation` | M:13.Phone | Phone / optional | P2/G | Exercise supported foreground and notification presentations; every mode provides a reachable answer action. |
| BL-TEL-008 | `swipeReject` | M:13.Phone | Phone / optional | P2 | Dismiss a notification under both policies and verify whether the underlying call is affected as intended. |
| BL-TEL-009 | `incomingChannel` | M:13.Phone | Phone / optional | P2/G | Open the correct Android notification channel; return with the caller state still correct. |
| BL-TEL-010 | `audioPreference` | M:3,13.Phone | Phone / optional | P2/G | Start calls with wired/Bluetooth devices connected and absent; an unavailable preference falls back safely. |
| BL-TEL-011 | `audioSelection` | M:3,13.Phone | Phone / optional | P2/G | Switch available routes using each chosen interaction style; confirm sound on the actual endpoint. |
| BL-TEL-012 | `volumeGuard` | M:13.Phone | Phone / optional | P2/G | Press hardware volume keys during the guarded call view and after leaving it; report scope accurately. |
| BL-TEL-013 | `endConfirmation` | E | Phone / optional | P2 | Touch End accidentally then cancel; both participants remain connected. |
| BL-TEL-014 | `actionConfirmation` | E | Phone / optional | P2 | Attempt each protected in-call action; Cancel leaves the connection and audio state unchanged. |
| BL-TEL-015 | `cheekSpace` | M:13.Phone | Phone / optional | P2 | Simulate cheek contact around the top area at each configured spacing; no unintended mute or hangup occurs. |
| BL-TEL-016 | `photoScale` | M:10,13.Phone | Phone / optional | P2 | Test extreme photo allocations with long caller names; essential actions remain reachable. |
| BL-TEL-017 | `keypad` | P | Phone / paid-full reference | P2 | Complete a real IVR interaction; key presses produce the expected DTMF sequence. |
| BL-TEL-018 | `audioVisibility` | M:13.Phone | Phone / optional | P2 | Hide and restore the audio control; hidden controls do not retain ghost touch targets. |
| BL-TEL-019 | `durationVisibility` | M:13.Phone | Phone / optional | P2 | Displayed elapsed time is monotonic; hiding it does not shift a held finger onto End. |
| BL-TEL-020 | `holdVisibility` | M:13.Phone | Phone / optional | P2/G | Toggle availability and place/resume a supported call; unsupported carrier behavior is explicit. |
| BL-TEL-021 | `muteVisibility` | M:13.Phone | Phone / optional | P2 | Verify remote silence when muted and restored speech after unmuting; hidden state stays accessible to caregiver. |
| BL-TEL-022 | `activeEntry` | M:13.Phone | Phone / optional | P2 | Leave a live call screen and return to the same connection without redialing. |
| BL-TEL-023 | `contactsEntry` | M:13.Phone | Phone / optional | P2 | Remove the full-list menu entry while keeping configured contact shortcuts functional. |
| BL-TEL-024 | `favoritesEntry` | M:13.Phone | Phone / optional | P2 | Create a favorites-only calling menu and complete a call without dead ends. |
| BL-TEL-025 | `dialerEntry` | M:13.Phone | Phone / optional | P2 | Hide manual dialing while preserving a documented Android emergency route. |
| BL-TEL-026 | `createEntry` | M:13.Phone | Phone / optional | P2 | Hide contact creation for daily use; caregiver can re-enable it without losing existing data. |
| BL-TEL-027 | `historyEntry` | M:13.Phone | Phone / optional | P2 | Hide recent history in the simple view while continued calls remain recorded correctly. |
| BL-TEL-028 | `historyGrouping` | M:13.Phone | Phone / optional | P2 | Repeated incoming/outgoing calls to one number produce the configured grouping without losing records. |
| BL-TEL-029 | `historyDeletion` | E | Phone / optional | P2 | With deletion enabled, remove a selected fixture only; disabled policy blocks all Maa deletion routes. |
| BL-TEL-030 | `missedChannel` | M:13.Phone | Phone / optional | P2/G | Change missed-call channel preferences and verify a real subsequent missed call. |
| BL-TEL-031 | `dialerFit` | M:13.Phone | Phone / optional | P2 | A number exceeding the usual visible width remains inspectable before dialing. |
| BL-TEL-032 | `digitCorrection` | M:3 | Phone / base | P2 | Enter a mistaken final digit, correct it and compare the dialed number exactly. |
| BL-TEL-033 | `wholeCorrection` | M:13.Phone | Phone / optional | P2 | A configured prolonged correction clears the draft without starting a call. |
| BL-TEL-034 | `dialConfirmation` | M:13.Contacts | Phone / optional | P2 | Verify confirmation and direct modes independently of contact-button policy. |
| BL-TEL-035 | `callLog` | P | Phone / paid-full reference | P2 | More than five mixed call records remain browsable with identifiable direction and time. |
| BL-TEL-036 | `callback` | P | Phone / base | P2 | Select a known history record; resulting call reaches its number exactly once. |
| BL-TEL-037 | `historyReply` | P | Phone / base | P2 | Start a message from history and verify recipient before composing. |
| BL-TEL-038 | `dualSim` | H | Phone / optional | P2/G | Place real calls on both SIM slots and verify displayed selection against carrier records. |
| BL-TEL-039 | `endCall` | P | Phone / base | P2 | End a connected call once from Maa; confirm both sides disconnect and UI returns coherently. |
| BL-TEL-040 | `collapse` | M:3 | Phone / optional | P2 | Collapse and expand controls during a call without terminating it or concealing the recovery action. |
| BL-TEL-041 | `bulkHistory` | M:9 | Phone / optional | P2 | Compare before/after fixtures for per-contact and all-history deletion; each requires the configured protection. |

## SMS and message notifications

| Requirement ID | Capability key | Official evidence | Reference scope | Stage | Original Maa acceptance criterion |
|---|---|---|---|---|---|
| BL-SMS-001 | `threads` | S | SMS / paid-full reference | P2 | Browse more than five conversations including identical display names and unknown senders. |
| BL-SMS-002 | `conversation` | S | SMS / base | P2 | Receive messages out of order and inspect dates, direction and readable Hindi text. |
| BL-SMS-003 | `compose` | S | SMS / base | P2 | Send to a saved person and an explicitly entered number; compare received content exactly. |
| BL-SMS-004 | `reply` | S | SMS / base | P2 | Reply from a thread after a new message arrives; recipient cannot change silently. |
| BL-SMS-005 | `direction` | S | SMS / base | P2 | Mother distinguishes sender direction without relying only on color. |
| BL-SMS-006 | `keyboard` | C | SMS / external integration | P2 | Test Hindi, English and chosen voice input; opening the keyboard never covers the only Send control. |
| BL-SMS-007 | `multipart` | M:5 | SMS / base | P2 | Send long Latin and Devanagari fixtures; verify reassembly and no missing characters at the recipient. |
| BL-SMS-008 | `copy` | M:5 | SMS / optional | P2 | Copy a selected fixture and paste locally; unrelated messages are not exposed. |
| BL-SMS-009 | `templates` | M:5 | SMS / optional | P2 | Create, reuse and amend a canned Hindi reply; insertion remains editable before sending. |
| BL-SMS-010 | `systemAlert` | M:13.Messages | SMS / optional | P2/G | Test notification permission/channel states; receipt still appears in the inbox even when visual alerts are disabled. |
| BL-SMS-011 | `channelSettings` | M:13.Messages | SMS / optional | P2/G | Open the correct Android settings page and verify the next real alert follows the chosen configuration. |
| BL-SMS-012 | `lockscreenPopup` | S; M:13.Messages | SMS / optional; conflict | P2/G | Resolve C02 on the F1; when unavailable, provide a tested readable notification route and record the parity substitution. |
| BL-SMS-013 | `dismissRead` | M:13.Messages | SMS / optional | P2 | Dismiss one alert under each policy; unrelated unread records retain their state. |
| BL-SMS-014 | `deliveryReports` | M:13.Messages | SMS / optional | P2/G | Distinguish queued, sent, delivered, failed and unknown carrier outcomes without asserting receipt prematurely. |
| BL-SMS-015 | `sendFeedback` | M:13.Messages | SMS / optional | P2 | Introduce no-service failure; outcome feedback remains readable and does not misreport success. |
| BL-SMS-016 | `callAction` | M:13.Messages | SMS / optional | P2 | Calling from a message uses its sender, with the configured confirmation and SIM behavior. |
| BL-SMS-017 | `replyVisibility` | M:13.Messages | SMS / optional | P2 | A caregiver can make the reading view simpler while preserving the received content. |
| BL-SMS-018 | `resend` | M:13.Messages | SMS / optional | P2 | Retry a failed test once after service returns; avoid duplicate submissions after rapid taps. |
| BL-SMS-019 | `forward` | M:13.Messages | SMS / optional | P2 | Forward to a different selected recipient only after that identity is visible. |
| BL-SMS-020 | `deletion` | E; M:5 | SMS / optional | P2 | Exercise individual, conversation and full-inbox fixture deletion with cancellation and disabled policy. |
| BL-SMS-021 | `sendPlacement` | M:13.Messages | SMS / optional | P2 | Test each supported Send placement with tall keyboards and largest text. |
| BL-SMS-022 | `senderPolicy` | M:13.Messages | SMS / optional | P2 | Evaluate contacts, favorites, unknown and blocked fixtures; caregiver understands which messages are suppressed. |
| BL-SMS-023 | `blocklist` | M:13.Messages | SMS / optional | P2 | Add and remove one sender; other conversations remain unaffected. |
| BL-SMS-024 | `dualSim` | H | SMS / optional | P2/G | Send through each available SIM and verify the recipient observes the intended number. |
| BL-SMS-025 | `roleHealth` | C | SMS / base | P2 | Change default messaging externally; Maa identifies the problem and avoids reporting an unsent message as sent. |
| BL-SMS-026 | `mmsReceive` | C; S | SMS / optional; conflict | P2/G | Resolve C03 with actual image/video MMS, carrier data and an offline retry on the reference build. |
| BL-SMS-027 | `mmsComposeBoundary` | C | SMS / external handoff | P3 | Offer the selected photo-messaging app for media; do not represent an SMS-only composition path as multimedia delivery. |
| BL-SMS-028 | `sound` | M:5 | SMS / optional | P2/G | Confirm one audible alert per message under the chosen Android channel configuration. |
| BL-SMS-029 | `vibration` | M:5 | SMS / optional | P2/G | Exercise available duration choices and disabled mode on real hardware. |
| BL-SMS-030 | `led` | M:5 | SMS / optional | P3/G | Inspect whether the actual F1/build exposes notification-light behavior; unsupported status is recorded explicitly. |
| BL-SMS-031 | `repeatAlert` | M:5 | SMS / optional | P2/G | Use a controlled clock to verify repeated reminders cease after reading and survive only as configured. |
| BL-SMS-032 | `popupActions` | S | SMS / optional; platform-limited | P2/G | Where supported, a displayed message offers the correct return-call/reply actions without leaking another sender. |
| BL-SMS-033 | `unmodifiedBody` | S | SMS / paid-full reference | P2 | More than twenty sends never add promotional or unintended text. |

## Assistance / SOS

| Requirement ID | Capability key | Official evidence | Reference scope | Stage | Original Maa acceptance criterion |
|---|---|---|---|---|---|
| BL-SOS-001 | `enableSms` | H | Launcher + SMS / optional | P2 | With transmission disabled, entering and cancelling help never sends a message. |
| BL-SOS-002 | `recipients` | H | Launcher + SMS / optional | P2 | Configure two family test numbers and compare actual recipients; invalid entries cannot be silently accepted. |
| BL-SOS-003 | `message` | H | Launcher + SMS / optional | P2 | Configure a Hindi help message; inspect received Unicode text exactly. |
| BL-SOS-004 | `smsDelay` | H | Launcher + SMS / optional | P2 | Cancel immediately, midway and just before the deadline; the state machine sends at most once. |
| BL-SOS-005 | `location` | H | Launcher + SMS / optional | P2/G | Test precise, approximate, stale, unavailable and denied location; show only truthful location context. |
| BL-SOS-006 | `locationUpdate` | H | Launcher + SMS / optional | P2/G | A later improved fix produces the configured follow-up without an unbounded stream of messages. |
| BL-SOS-007 | `enableCall` | H | Launcher + Phone / optional | P2 | Exercise SMS-only, call-only and combined assistance configurations with consenting test endpoints. |
| BL-SOS-008 | `callRecipient` | H | Launcher + Phone / optional | P2 | An edited help recipient takes effect only after explicit save; obsolete numbers receive no call. |
| BL-SOS-009 | `callDelay` | H | Launcher + Phone / optional | P2 | Verify SMS and call sequencing with a deterministic clock and an unavailable network. |
| BL-SOS-010 | `speaker` | H | Launcher + Phone / optional | P2/G | Confirm assistance-call audio route on the real F1, including attached headset conditions. |
| BL-SOS-011 | `cancel` | H | Launcher + Phone + SMS / base | P2 | Repeated touches and process recreation cannot restart a cancelled sequence. |
| BL-SOS-012 | `defaultDependency` | H | Launcher + SMS / base | P2 | Revoke the required role and invoke help in a controlled test; Maa clearly explains a necessary manual handoff. |
| BL-SOS-013 | `serviceBoundary` | H | Suite / platform limitation | P2/G | Provide a separate Android emergency-dialer route; never test assistance automation against real emergency numbers. |

## Enlarged notifications

| Requirement ID | Capability key | Official evidence | Reference scope | Stage | Original Maa acceptance criterion |
|---|---|---|---|---|---|
| BL-NTF-001 | `reader` | N | Notifications / companion base | P3 | Read real alerts from three daily apps in one consistent accessible view. |
| BL-NTF-002 | `detail` | V | Notifications / base | P3 | Open a long alert with multiline Hindi text and inspect its entire content without clipping. |
| BL-NTF-003 | `actions` | N; V | Notifications / base | P3/G | Invoke a real notification action once; expired actions fail visibly and safely. |
| BL-NTF-004 | `reply` | V | Notifications / action-dependent | P3/G | Reply through an exposed messaging notification; preview recipient and compare delivered text. |
| BL-NTF-005 | `media` | V | Notifications / action-dependent | P3/G | Use exposed playback controls; the actual media session changes state and updates the view. |
| BL-NTF-006 | `automatic` | N | Notifications / optional | P3/G | Generate alerts while Maa is foreground, background and locked; record which presentations Android permits. |
| BL-NTF-007 | `shortcut` | N | Notifications / optional | P3 | Open the enlarged view from the configured Android-area entry and return predictably. |
| BL-NTF-008 | `history` | W | Notifications / base | P3 | Inspect older alerts after a burst and clearly distinguish historical records from live actionable notifications. |
| BL-NTF-009 | `talkBack` | N | Notifications / base | P3 | Traverse source, title, content and actions in a meaningful order without accidental invocation. |
| BL-NTF-010 | `fontScale` | N | Notifications / paid reference | P3 | Test every chosen size with long titles and multiple action buttons. |
| BL-NTF-011 | `protectedSettings` | N | Notifications / paid reference | P3 | Prevent ordinary use from changing alert behavior while retaining caregiver access. |
| BL-NTF-012 | `vibration` | N | Notifications / paid reference | P3/G | Confirm configured vibration is not duplicated by Maa and Android handling the same event. |
| BL-NTF-013 | `pause` | N | Notifications / paid reference | P3 | Pause presentation, generate alerts, resume, and verify the documented retention policy. |
| BL-NTF-014 | `theme` | V | Notifications / optional | P3 | Verify original light/dark appearance and screen-reader contrast requirements. |
| BL-NTF-015 | `wake` | V | Notifications / optional | P3/G | Test locked-screen arrival on actual hardware without promising a platform-forbidden wake or activity launch. |
| BL-NTF-016 | `displayDuration` | V | Notifications / optional | P3 | A timed presentation closes as configured while leaving an accessible way to revisit the information. |
| BL-NTF-017 | `appExclusion` | V | Notifications / optional | P3 | Exclude one noisy app without hiding a selected family-messaging source. |
| BL-NTF-018 | `standalone` | N | Notifications / deployment optional | P3/G | Audit whether a separately usable module is required; equivalent behavior must work even with another Home app selected. |

## Alarm

| Requirement ID | Capability key | Official evidence | Reference scope | Stage | Original Maa acceptance criterion |
|---|---|---|---|---|---|
| BL-ALM-001 | `overview` | A | Alarm / companion; free reference | P3 | Mother can identify the current time and next alarm without entering settings. |
| BL-ALM-002 | `creation` | A | Alarm / base | P3 | Set an alarm a few minutes ahead with readable controls and verify exact scheduled time. |
| BL-ALM-003 | `change` | A | Alarm / base | P3 | Amend a pending alarm; the obsolete time must not also fire. |
| BL-ALM-004 | `once` | A | Alarm / base | P3 | A once-only alarm fires once and does not unexpectedly recur after reboot. |
| BL-ALM-005 | `daily` | A | Alarm / base | P3 | Verify recurrence across midnight and a timezone change with a controlled clock. |
| BL-ALM-006 | `snooze` | W | Alarm / base | P3 | Snooze an active alarm and distinguish the next ringing time from dismissal. |
| BL-ALM-007 | `dismiss` | H | Alarm / base | P3 | Dismiss once; the sound stops and the next state is unambiguous. |
| BL-ALM-008 | `contrast` | A | Alarm / base | P3 | Validate large controls at bedside lighting and maximum text scaling. |
| BL-ALM-009 | `palette` | W | Alarm / optional | P3/G | Audit available visual options and provide equivalent original readable themes. |

## Compatibility, maintenance and remaining manual controls

| Requirement ID | Capability key | Official evidence | Reference scope | Stage | Original Maa acceptance criterion |
|---|---|---|---|---|---|
| BL-OPS-001 | `ringtoneRepair` | M:13.Phone | Phone / compatibility | P2/G | Diagnose muted channels, volume and Do Not Disturb before proposing a fix; rerun a locked-screen test. |
| BL-OPS-002 | `callViewRepair` | M:13.Phone | Phone / compatibility | P2/G | Detect failed incoming presentation and offer a device-specific tested recovery sequence. |
| BL-OPS-003 | `missingMessages` | M:13.Messages | SMS / compatibility | P2/G | Compare imported/provider fixtures by stable IDs and prove no thread or message disappears. |
| BL-OPS-004 | `duplicateMessages` | M:14 | SMS / compatibility | P2/G | Deliver the same provider event twice; the UI and storage show one message. |
| BL-OPS-005 | `iconRepair` | M:13.Compatibility | Launcher / compatibility | P1 | Corrupt or missing app artwork gets a legible fallback without breaking app launch. |
| BL-OPS-006 | `imageSemantics` | M:13.Compatibility | Suite / compatibility | P1 | Accessibility inspection finds a useful label and correct role for every interactive image. |
| BL-OPS-007 | `homeCache` | M:13.Compatibility | Launcher / optional compatibility | P3 | Under memory pressure, returning Home remains correct and never clears personal configuration. |
| BL-OPS-008 | `iconQuality` | M:13.Compatibility | Launcher / optional | P3 | Compare memory use and readability across quality presets on the F1. |
| BL-OPS-009 | `restart` | M:13.Compatibility | Suite / optional | P1 | Restart Maa through caregiver controls; saved screens, drafts and policies survive. |
| BL-OPS-010 | `processLifecycle` | M:14 | Suite / compatibility | P1/P2 | Recreate activities during each daily flow; no duplicate call/send or lost committed configuration occurs. |
| BL-OPS-011 | `backgroundHealth` | H | Suite / device-dependent | P2/G | After prolonged idle and reboot, repeat calls, SMS, alarms and selected notifications on the installed MIUI build. |
| BL-OPS-012 | `supportRecord` | M:15 | Suite / base | P1 | Generate a redacted diagnostic summary with device/build and permission state; exclude contact/message content. |
| BL-OPS-013 | `multilingual` | L; P; S | Suite / extended locales | P3/G | Beyond required Hindi/English, establish an audited locale list before claiming full multilingual parity. |
| BL-OPS-014 | `rtl` | M:Appendix B | Suite / extended locales | P3/G | Test an RTL locale with mixed Latin phone numbers; visual order and spoken numbers remain correct. |
| BL-OPS-015 | `backup` | M:14,Appendix C | Suite / undocumented workflow | G | Audit whether reference export/restore exists; separately test Maa recovery from a deliberately saved profile if included. |
| BL-OPS-016 | `helpRecovery` | H | Suite / base | P1/P2 | Mother performs a deliberate wrong tap and independently returns to the intended daily task. |

## Commercial and deployment boundaries

| Requirement ID | Capability key | Official evidence | Reference scope | Stage | Original Maa acceptance criterion |
|---|---|---|---|---|---|
| BL-COM-001 | `launcherLimits` | L | Launcher / reference licensing | P1 | Maa supports all tile positions, more than five extra pages and protected configuration without reproducing trial restrictions. |
| BL-COM-002 | `phoneLimits` | P | Phone / reference licensing | P2 | Maa's promised calling scope includes complete available history and working IVR input. |
| BL-COM-003 | `smsLimits` | S | SMS / reference licensing | P2 | Maa's promised messaging scope includes older conversations and unchanged outgoing content after twenty sends. |
| BL-COM-004 | `independentLicenses` | C | Suite / reference commercial model | G | Record each reference entitlement separately during audit; Maa packaging must not accidentally omit an entire companion. |
| BL-COM-005 | `purchaseRestore` | M:1,12 | Suite / reference commercial model | G | Audit purchase/restore/transfer controls for completeness; if Maa is free, explicitly record these as reference commerce rather than unfinished user functionality. |

## Completion evidence to attach later

For each BL row retain: observed reference build; reference breadcrumb/evidence; Maa build; test device/OS; actual result; disposition; test date; defect link if any. A source citation alone never marks a row passed. For platform substitutions retain the failed or unavailable reference operation, the supported Maa fallback, and the successful user task it preserves.

Commercial controls are included so the reference audit has no unexplained omissions. Free-edition caps, license refresh and purchase transfer are not requirements to monetize Maa. If Maa remains free, explicitly close those records as reference-commerce-only while testing the full useful functionality.

A release can be useful before this inventory closes. Label its implemented scope accurately and keep unresolved rows in the release plan. Claim full parity only after AUDIT-REFERENCE-01 closes with an explicit list of any accepted platform differences.

