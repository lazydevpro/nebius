# Daily tools implementation contract

Date: 2026-10-04 UTC. Scope: local alarms, explicit family SOS preparation and opt-in notification history. These features do not depend on network or AI.

## Platform decisions before implementation

- API 26–30: user-created alarms use `setAlarmClock`. API 31+: check `canScheduleExactAlarms`, offer system special-access settings, otherwise label the schedule as an approximate reminder. An approximate broadcast posts a notification only; it does not assume exemption from foreground-service restrictions.
- Exact alarms may start a foreground `mediaPlayback` ringing service. The service has an alarm notification, snooze and dismiss, stops after ten minutes, and never starts an activity from the background. A notification tap opens the accessible ringing screen. No full-screen intent permission or artificial full-screen exception is requested.
- Alarm definitions and the currently scheduled instance are persisted. Reconcile after ordinary boot (after credential unlock), package replacement, clock/timezone changes and exact-access grant. No direct-boot delivery claim; alarms before first unlock after reboot can be missed. Recheck capabilities when the alarm screen resumes. Force-stop, OEM restrictions, muted channels and denied notifications remain visible limitations.
- SOS is a named family phone number and editable message. A cancellable countdown leads to a review screen. The owner explicitly chooses a dialer or SMS draft, where they perform the final call/send. Maa never calls this delivery or contacts emergency services. No automatic location collection, background location or emergency-service guarantee.
- Notification access and history consent are separate. History is off by default, private, bounded to 100 items and seven days, excludes Maa's own notifications, and can be cleared. Turning history off deletes retained text. PendingIntents remain memory-only and usable only for currently active notifications; vanished notifications cannot be replayed from history. External content and actions remain untrusted.

Retention is pruned when history is read or a new notification is archived; no background deletion deadline is claimed while the app is force-stopped. SOS configuration and archive opt-in use the shared caregiver lock, including a save-time unlock check. An unconfigured lock uses the existing explicit setup warning. Normal alarm creation and clearing saved history remain available to the phone's owner.

## Root-owned integration

Add permissions `RECEIVE_BOOT_COMPLETED`, `SCHEDULE_EXACT_ALARM`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` (existing app already declares base foreground-service and notification permissions). Activities `AlarmActivity` (singleTop), `SosActivity`, `NotificationCenterActivity` are non-exported. `MaaAlarmRingService` is non-exported with `foregroundServiceType="mediaPlayback"`; `MaaAlarmReceiver` is non-exported. `MaaAlarmRescheduleReceiver` is non-exported with BOOT_COMPLETED, MY_PACKAGE_REPLACED, TIME_SET, TIMEZONE_CHANGED and `android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` filters. No full-screen, direct-boot, call, SMS or location permissions are required by this implementation.

Call `NotificationArchive.onConnected(this, activeNotifications ?: emptyArray())` from the listener's `onListenerConnected`, `onDisconnected()` when disconnected/destroyed, `record(this,sbn)` before existing notification filtering, and `removed(sbn.key)` from `onNotificationRemoved`. Snapshot actions are consumed before external dispatch, preventing repeated confirmation taps from replaying the same in-memory entry. A launch still proves neither delivery nor the target app's outcome.

## Acceptance and current limits

Dedicated tests cover next-fire calculations (once/weekdays/DST), persisted alarm corruption, notification retention and canonical SOS validation. No real alarm, phone call or SMS is used as a test on the user's phone. Physical wake/ring, notification authorization, MIUI endurance, actual communications, screen reader and large-font checks require separate acceptance evidence. Archive listener integration and manifest entries are root-owned integration changes.

Sources: [Android alarm scheduling](https://developer.android.com/develop/background-work/services/alarms), [exact-alarm special access](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms), [foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types#media), [notification listener lifecycle](https://developer.android.com/reference/android/service/notification/NotificationListenerService), [common dial/SMS intents](https://developer.android.com/guide/components/intents-common).
