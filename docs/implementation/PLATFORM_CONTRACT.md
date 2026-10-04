# Platform component contract

Contracted 2026-10-04. Scope: default Home selection, live device status, searchable launchable apps, reversible favorites/hidden choices and read-only device details. These components do not claim complete BIG-suite parity.

## Invariants and prerequisites

- API 29+ requests `RoleManager.ROLE_HOME` through the system UI; API 26–28 opens `Settings.ACTION_HOME_SETTINGS`, with default-app/settings fallbacks. Neither route silently sets a default. Recheck actual Home resolution on resume; a chooser or another package is not success. Root integration must reset the primary screen on every normal Home intent.
- Battery comes from the protected system battery broadcast, time from the device clock, sound from `AudioManager`, airplane mode from the read-only global setting and the next alarm from `AlarmManager`. Missing values say unavailable; they never display made-up percentages or signal strength.
- Network transport and Android's validated-internet state use `ConnectivityManager` and the normal `ACCESS_NETWORK_STATE` permission. No runtime phone, location, Wi-Fi-name or Bluetooth permission is requested. Mobile means the active data transport, not proof that voice calling is available. This slice intentionally does not display cellular bars, carrier identity or radio generation because those require a separate permission/version contract.
- No active network means offline. A connected transport without `NET_CAPABILITY_VALIDATED` says internet is unverified; a captive portal is identified separately. Missing permission/service reports unavailable, not offline. VPN is reported without pretending it identifies the physical transport.
- Receivers and network callbacks belong to an attached status view; they unregister on detach. The view refreshes after app language changes. The details screen has its own visible-lifecycle observer.
- App visibility uses a narrow manifest query for `MAIN` + `LAUNCHER`, not `QUERY_ALL_PACKAGES`. Only enabled exported launchable activities appear; icons and labels come from their packages. Hidden/favorite preferences use component identifiers and never disable or uninstall apps. Hidden items have an explicit recovery view. Package changes trigger a reload while visible.
- Every interactive action is at least 64 dp high. Content can wrap and scroll at increased font scales; no static invented stats, no silent device-setting mutation.

## Integration required

Add `android.permission.ACCESS_NETWORK_STATE`, declare non-exported `.AppsActivity` and `.StatusDetailsActivity`, and add this query:

```xml
<intent><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent>
```

Use `DeviceStatusView(context).apply { onOpenDetails = { startActivity(Intent(context, StatusDetailsActivity::class.java)) } }`. It starts/stops on attach/detach; `refresh()` reloads values and app-language text. Use `HomeRoleController.isDefaultHome(activity)` for current state and `HomeRoleController.request(activity)` only after the user's setup action. Both activities use `AppLanguage.localizedContext` and provide visible back controls.

```kotlin
home.addView(DeviceStatusView(this).apply {
    onOpenDetails = { startActivity(Intent(this@MainActivity, StatusDetailsActivity::class.java)) }
})
// From a deliberate app-list button:
startActivity(Intent(this, AppsActivity::class.java))
```

The app catalog stores `favorites` and `hidden` string sets in private preferences `maa_apps`, keyed by flattened activity component. Favorites sort first; the explicit Show hidden apps control restores access without changing other apps. Organize, hidden-list access and long-press configuration enter through `CaregiverLock.request`. Every preference mutation rechecks an expired configured unlock; hiding also has its own explanation/confirmation. The shared helper presents a caregiver notice when no PIN exists; no-PIN mode is not authenticated protection. These preferences are separate from the current `family` configuration and contain no credentials.

The selected design uses a warm white card, 52 sp forest-green clock, date, original 48 dp battery/network graphics and a prominent measured battery percentage. Network graphics represent a transport, never made-up signal bars. Measured clock width and font scale choose side-by-side or stacked layout; large text is not shrunk to force the reference image's proportions.

## Acceptance and limits

Mechanical: build/lint, status rendering without network permission, percentage edge values, real locale changes, filtering/favorite/hide restore, installed-package change, role denial and unavailable settings. Physical: clock tick, charge/unplug, Wi-Fi/mobile/offline/captive portal/VPN, sound/airplane/next alarm, 100/150/200% text, TalkBack, repeated Home and role loss. API 26–28 and current Android need separate device/emulator acceptance; a passing API 29 run is not those versions' evidence. No real calls, messages, package removal or system settings changes are needed for automated tests.

`PlatformTest` covers impossible/overflow battery input, distinct unknown/offline/unverified/captive/validated network labels, narrow 200% text rendering and Hindi refresh, and app-search recreation without preference mutation. It does not simulate real radio transitions or prove OEM role behavior. Compilation and execution are coordinated by the root implementer; tests have not been run by this component author.

Sources: [Home role](https://developer.android.com/reference/android/app/role/RoleManager), [network state](https://developer.android.com/develop/connectivity/network-ops/reading-network-state), [package visibility](https://developer.android.com/training/package-visibility/declaring), [Home settings](https://developer.android.com/reference/android/provider/Settings#ACTION_HOME_SETTINGS).
