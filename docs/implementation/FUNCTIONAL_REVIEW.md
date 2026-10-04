# Independent functional review — 2026-10-04

## Asha Home follow-up — 0.3.0-preview

An independent source-only follow-up reviewed the simplified Home, onboarding, clock settings entry, settings draft preservation and trusted voice gesture. Two expired-PIN paths were repaired: changing language now retains the requested language and draft behind fresh authorization, and a returned photo selection merges into the pending/live draft without committing family preferences before authorization and Save. The critic reread both repairs and found no remaining critical regression in the reviewed paths.

The clock exposes a labeled accessibility long-click action and requires confirmation before existing PIN checks. Trusted Home voice entry uses a short-lived, one-use token; forged/replayed/restored entry cannot restart recognition. Automatic routing is limited to exact local commands and owned screens. This review did not execute a microphone/provider, build or phone interaction. Separately, root ran build/lint and all 42 instrumentation tests on the Poco; see `../BUILD_STATUS.md` for the artifact and evidence. These results do not prove unrestricted assistant control or production readiness.

## Earlier rebuild review

Scope: current launcher rebuild working tree, with particular attention to MainActivity/Home, caregiver configuration, family communication, alarm state and notification actions. Reviewed PRODUCT_PLAN.md, REBUILD_PROGRESS.md, component contracts and production source independently. No production edits, Gradle invocation, phone actions or live communication were performed by this reviewer. Reproductions below are code-derived scenarios to cover with instrumentation/fakes; they are not claimed device executions. Concurrent writer repairs were reread before recording their disposition.

## Open defects in implemented behavior

### FR-02 · P2 · Home does not invalidate an open modal operation

- **Location:** `app/src/main/java/in/family/maa/MainActivity.kt:68`, `:111–128`, `:155–157`, `:319`; `CaregiverLock.kt:31–33`, `:39–62`.
- **Reproduce:** With Maa selected as Home, open its unconfigured-settings notice (or a PIN/payment confirmation), then send a normal MAIN/HOME intent. `onNewIntent` calls `home()`, which replaces the activity content but does not dismiss the independently owned Dialog window. The modal remains over Home and its original callback remains executable. For the settings notice, pressing Open enters settings after the Home reset.
- **Impact:** Normal Home is not a dependable way to cancel a subflow or return to the primary usable screen. Confirmation authority can survive the navigation that appeared to abandon it.
- **Minimal repair:** Track/dismiss owned dialogs when processing Home and invalidate their callbacks with a navigation/session generation. Include CaregiverLock dialogs in that mechanism. A cancelled or backgrounded PIN derivation must not run `onUnlocked` for an abandoned navigation.
- **Focused verification:** Open each modal kind, issue a real MAIN/HOME intent, assert Home is immediately interactive and old callbacks cannot open settings or launch a handoff. The existing navigation test covers an ordinary settings screen, not an open modal.

### FR-03 · P2 · An old alarm action can re-enable an edited or disabled alarm

- **Location:** `app/src/main/java/in/family/maa/AlarmActivity.kt:50–54`, `:90–93`; `MaaAlarmScheduler.kt:96–106`.
- **Reproduce:** Allow an approximate alarm to post its notification. Edit its time, or enable then disable it, while leaving that notification present. Both UI mutations preserve `firedAt`. Press Snooze on the old notification. The receiver accepts the unchanged old `firedAt` and saves `enabled=true` with a new ten-minute occurrence, overriding the user's subsequent schedule/disable decision. Disabling an actively ringing repeating alarm likewise leaves its service ringing.
- **Impact:** A obsolete notification acts on the current alarm definition, and an explicitly disabled alarm can ring again.
- **Minimal repair:** Invalidate the fired occurrence (`firedAt=0`) and cancel its notification/ringing state on edit/disable, or version the definition and bind actions to both the definition and fired instance. Ensure successful persistence precedes presenting the changed state.
- **Focused verification:** Use fake alarm/notification/service adapters: fire, edit or disable, deliver the original snooze/dismiss Intent, and assert no schedule or audible state changes. Also assert disable stops a current ring.

### FR-05 · P2 · PIN removal and replacement can commit after unlock expiry

- **Location:** `app/src/main/java/in/family/maa/CaregiverLock.kt:64–80`, `:86–88`.
- **Reproduce:** Unlock a configured PIN, open Remove PIN, wait beyond the two-minute unlock window, then confirm. `remove` checks authorization only before opening the dialog and clears the PIN without a fresh check. Similarly, leave Change PIN open through expiry and save a replacement; the mutation callback checks only whether the activity/dialog survives.
- **Impact:** A configured lock can be removed or replaced while `CaregiverLock.unlocked()` is false, defeating its implemented expiry protection.
- **Minimal repair:** Check an unchanged, current unlock/session at the actual commit point; if expired, dismiss or obtain fresh authorization. Apply the same check after asynchronous derivation, and honor a failed preference commit instead of reporting success.
- **Focused verification:** Inject a clock or expire the session under an open confirmation; assert both remove and replace leave stored credentials unchanged until fresh unlock. No timing-dependent two-minute test is required.

### FR-06 · P2 · Opening a system setup page discards an unsaved settings draft

- **Location:** `app/src/main/java/in/family/maa/MainActivity.kt:69–71`, `:325–329`, `:335`, `:366–375`, `:434`.
- **Reproduce:** Configure/unlock a PIN, edit a family name/number or switches, then choose the Home-role, Accessibility, notification-access or overlay-permission settings link. `onStop` relocks because this is not a photo picker. Returning runs `home()` in `onResume`; `surface()` clears the field references and `home()` clears the photo draft. Reopening settings loads the previously stored values. These setup links are inside the editing workflow, so using them loses otherwise valid unsaved work.
- **Impact:** Data-loss regression for the configuration draft, including photos already chosen but not saved. Relocking is appropriate; discarding the draft is avoidable.
- **Minimal repair:** Capture the draft before an external setup handoff and retain it separately from live views. On return, keep it pending behind a fresh unlock. Clear it only for an explicit discard, a successful save, or a clearly defined Home cancellation.
- **Focused verification:** Edit text/switch/photo draft, simulate each permission-settings round trip with a configured PIN, unlock again, and assert the draft is retained and persistent family preferences remain unchanged before Save.

### FR-07 · P2 · Enabling notification history omits already-active notifications

- **Location:** `app/src/main/java/in/family/maa/NotificationArchive.kt:21–24`, `:47–55`; `NotificationCenterActivity.kt:27–35`; `FamilyNotifications.kt:9`.
- **Reproduce:** Grant notification access while history is off and have an ordinary third-party notification active. The listener snapshot is discarded because `record()` returns while disabled. Enable history in Maa. `setEnabled()` changes only preferences; Refresh only calls `render()`. The already-active notification remains absent until the other app updates it or the listener reconnects. Disabling and re-enabling while connected repeats the problem.
- **Impact:** The active-notifications feature shows a false empty state immediately after opting in, and provides no working refresh path for existing notifications.
- **Minimal repair:** On successful enable and explicit Refresh, request a fresh `activeNotifications` snapshot from the connected listener, respecting opt-in and connection state. Keep historical retention and executable live handles distinct.
- **Focused verification:** Connected listener + active fixture while disabled → enable → refresh should show the fixture and its current actions without requiring a new post. Then remove it and verify the actions become unavailable.

## Findings repaired during this review

- **FR-01 · P1, closed by source recheck:** AlarmActivity's resume reconciliation recalculated every occurrence from now, replacing a delayed due alarm with tomorrow/the next repeating day and cancelling the due PendingIntent. Current `MaaAlarmModel.kt:28–32` now preserves existing scheduled occurrences; `MaaAlarmScheduler.kt:28–30`, `:51`, `:115` distinguish ordinary reconciliation from clock/timezone reset. This closes the identified source path; actual delayed-broadcast/device coverage remains a verification requirement.
- **FR-04 · P2, closed by source recheck:** All alarm notification content PendingIntents previously shared request code, action, component and data, so the most recent alarm overwrote earlier alarm IDs. `MaaAlarmScheduler.kt:64` now supplies per-alarm data `maa-alarm://open/{id}`. Verify two independently fired notifications open their own controls.
- **FR-08 · P2, closed by source recheck:** MainActivity's older Help/voice dialing route bypassed canonical/duplicate validation and read a fresh raw phone number after name-only confirmation. Current `MainActivity.kt:231–234` uses FamilyDirectory and routes into CommunicationActivity, whose final handoff revalidates the name and canonical number. It does not directly place a call.

## Unimplemented parity and missing proof — separate from the defects above

The implemented phone/SMS/WhatsApp routes are explicitly unsent external-app handoffs. Default dialer/incoming/in-call handling, an owned SMS/MMS inbox/provider lifecycle, call history integration, dual-SIM behavior, full BIG-suite preferences, family slots beyond three and a supported WhatsApp media/guidance adapter remain full-scope delivery gaps described in the component contracts. Their absence must not be presented as completed parity, but is not evidence that the implemented handoff intentionally places an unconfirmed call or sends a message.

No source review proves default-Home endurance, API 26–28/current-Android behavior, physical wake/ring and reboot delivery, MIUI background behavior, notification permission/revocation behavior, real contact/voice/photo-provider behavior, TalkBack/switch input or intended-user success. Build/test status and exact-APK device evidence must be reconciled separately. The authored alarm tests cover calculations and storage, not the service/broadcast/notification interaction sequences identified here.

## Repair recheck — 2026-10-04

The following dispositions supersede the initial open/closed headings above. This is a source recheck, not acceptance of unexecuted tests.

| Finding | Recheck disposition | Current implementation evidence |
| --- | --- | --- |
| FR-01 | Closed by source recheck | `MaaAlarmModel.kt:28–32` retains an existing scheduled occurrence; `MaaAlarmScheduler.kt:28–30`, `:51`, `:115` use that rule on lifecycle refresh and recompute wall-clock times only for explicit clock/timezone changes. |
| FR-02 | Closed by source recheck | `MainActivity.kt:53–55`, `:127–128`, `:438` now dismiss tracked activity/CaregiverLock dialogs. `CaregiverLock.kt:19–24` owns/dismisses its dialogs; PIN callbacks require the dialog still to be showing. Home therefore invalidates the reviewed modal paths. |
| FR-03 | Closed by source recheck | `AlarmActivity.kt:52–55`, `:94–97` clear `firedAt`, stop the current service and cancel the occurrence notification on edit/toggle. Receiver validation still rejects old `firedAt` actions. |
| FR-04 | Closed by source recheck | `MaaAlarmScheduler.kt:64` binds each content PendingIntent to per-alarm data. |
| FR-05 | Closed for the reported expiry defect | `CaregiverLock.kt:79`, `:88`, `:98` recheck unlock both before PIN-save work, after derivation, and before removal. The original stale-authorization reproduction no longer commits. Storage failure reporting remains untested. |
| FR-06 | Closed after second source repair | The first fix preserved only a callback-local Bundle and still lost it during recreation; this was returned to the writer. Current `MainActivity.kt:51`, `:59–65`, `:69–85`, `:173–177` keep `pendingSettingsDraft`, save it as `lockedSettingsDraft`, restore it while locked, and reopen it only after unlock. Dismissal during recreation does not run the explicit cancel callback; normal Home intentionally clears the pending draft. |
| FR-07 | Closed by source recheck | `NotificationArchive.kt:25` snapshots on enable, `FamilyNotifications.kt:9–16` exposes a connected-listener refresh, and `NotificationCenterActivity.kt:35` invokes it for Refresh. |
| FR-08 | Closed by source recheck | MainActivity `dial()` reads an actionable FamilyDirectory person and enters CommunicationActivity; recipient identity is revalidated in the final owned confirmation path. |

Targeted regression evidence should still cover actual MAIN/HOME under a modal, recreation both during external settings and during the re-unlock prompt, expired PIN replacement/removal, delayed alarm delivery, obsolete snooze intents and notification enable/re-enable snapshots.

## Separate assistant/overlay review

Reviewed `AssistantActivity.kt`, `HelpOverlayService.kt`, the assistant contract/resources, manifest entry and authored AssistantTest. This review concerns implemented privacy, cancellation and recipient handling only.

### AS-01 · P2 · A stale Turn off button can turn the helper back on

- **Location:** `app/src/main/java/in/family/maa/AssistantActivity.kt:100–105`, `:276–284`; `HelpOverlayService.kt:197–216`.
- **Reproduce:** Enable the floating helper, then tap the assistant's “Floating helper is visible · Turn off” button. `toggleOverlay` calls `stopService` and immediately renders from `isActive`. Service destruction/removal runs later on the main thread, so `attached` is still true during that render. There is no subsequent refresh in the disable branch. Once the helper disappears, the same button can still say Turn off. Tapping it again now enters the inactive/start branch and enables the helper.
- **Impact:** The labeled stop action can restart a helper the user intended to disable. This does not start a microphone or upload data, but makes cancellation state unreliable.
- **Minimal repair:** Publish service attachment/stop state changes to the activity and render controls from the new state; alternatively mark the explicit stop as inactive synchronously before returning from `HelpOverlayService.stop`, ensuring later attachment callbacks cannot undo it. Bind the control's action to the displayed state rather than silently flipping the meaning after an asynchronous transition.
- **Focused verification:** Start helper, tap Turn off, deliver service destruction, and assert the button shows Turn on. A second invocation of an already rendered stop action must not restart it. Include stopping through the notification/overlay while AssistantActivity remains visible.

No additional concrete P1/P2 privacy or wrong-recipient issue was found in these two new classes. Source evidence: microphone entry requires explicit foreground Speak and provider disclosure (`AssistantActivity.kt:163–211`); speech populates an editable draft without submitting it; optional inference requires endpoint-bound consent and sends only the submitted text (`:219–269`); pause/Stop invalidate generations, cancel the worker and disconnect (`:95–118`); results recheck foreground/generation/endpoint before routing (`:268–269`). Routes enter family selection and never choose a recipient from model output (`:147–160`). The helper contains no microphone, screenshot or accessibility-content observation path.

This is not proof of network-byte recall or cancellation timing under a real recognition provider/HTTP stack. AssistantTest explicitly excludes microphone/network execution and does not currently verify those boundary races. The root verifier should reconcile those limits without treating pure parser/UI tests as privacy-lifecycle acceptance.

## Final rebuild checkpoint — root verification, 2026-10-04

AS-01 was repaired with generation-bound start requests, synchronous stop publication, service-state listeners and callbacks bound to the displayed command. The independent critic rechecked and closed the source defect. A focused on-device regression verifies immediate off presentation and rejects stale start/attachment/stop callbacks without starting a real overlay. Both Main and Assistant use the same service API. The later stale Home-role button label was also repaired by retaining and refreshing the settings button on resume.

All 38 final instrumentation tests passed on Poco. The first integration run exposed clipped Home actions and currency-display decimal formatting; repaired. The second run exposed a test harness Intent mismatch during ActivityScenario cleanup; matching its explicit launch Intent repaired cleanup. Final bounds checks include all core buttons and Settings/More in both languages. Build/lint passed (0 errors, 71 warnings). Actual settled screenshots and APK hash are in `../BUILD_STATUS.md`. Real service/recognizer/network/alarm timing and full parity remain outside this evidence.
