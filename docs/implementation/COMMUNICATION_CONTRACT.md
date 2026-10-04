# Communication implementation contract

Date: 2026-10-04. Owner: communication implementation agent. Status: implemented; root integration, build, independent review and device verification pending.

## Intended result

Maa provides an accessible photo family directory and explicit recipient screens for phone, SMS, and WhatsApp. A large keypad and searchable, read-only device contacts support other calls. Existing `family` preferences (`name1..3`, `phone1..3`, `photo1..3`) remain authoritative; no destructive migration or duplicate second store is introduced.

Every sensitive handoff displays the selected person's name, canonical phone number, and exact draft. Calling opens the system dialer with a number; SMS opens the messaging app with an unsent draft; WhatsApp opens a specific chat with an unsent draft. These are handoffs, and Maa does not report them as completed calls or sent messages. No generic share chooser represents delivery to family. No background transmission, live call, real SMS send, account setup, or permission grant is part of implementation testing.

## Authority and safety boundaries

- Activities are internal (`exported=false`). They do not handle public `ACTION_DIAL`/`SENDTO` filters and do not request default dialer or SMS roles.
- In the default `family_only=true` profile, broad keypad/contact tools are available through a clearly labelled More phone tools action guarded by `CaregiverLock.request`. The caregiver-protected setting can expose them directly. This is accidental-use protection, not Android kiosk confinement.
- Default dialer eligibility requires both incoming and ongoing call UIs backed by a complete `InCallService`. Default SMS needs delivery receivers, respond-via-message service, and correct provider persistence, including MMS. These are separate full-parity work; registering incomplete role handlers would risk missed communication.
- No `CALL_PHONE`, `SEND_SMS`, `READ_SMS`, `WRITE_SMS`, `READ_CALL_LOG`, or `WRITE_CALL_LOG` is requested. Optional `READ_CONTACTS` is requested only after the user chooses the device contacts screen and sees the explanation. Denial leaves the family directory and manual keypad usable.
- Phone normalization rejects dial sequences, service/USSD codes, extensions, control characters, arbitrary URLs, and incomplete numbers. Family local numbers use the existing product's explicit India region default; international numbers use `+country code`. The UI shows the resulting full number before handoff. Duplicate canonical numbers across family slots are surfaced and blocked pending caregiver correction.
- Family actions re-read the authoritative slot immediately before handoff. Changed or removed recipients invalidate the pending action. Name alone is never a recipient identity.
- Drafts are stored locally by canonical recipient and channel. They survive recreation; changing recipient does not carry an old recipient's draft to a new one. A handoff does not clear a draft or claim delivery. Clearing a draft is an explicit action.
- Contacts queries and bounded thumbnail decoding run off the UI thread. Contact data remains on this device. Photo decoding is bounded; a missing/invalid/revoked image produces a clear initial placeholder.
- Android navigation bars remain accessible. Controls use at least 64dp height, wrap text, and stay scrollable with large fonts. English and Hindi use resource strings.

## Acceptance evidence

`CommunicationTest` contains eight checks for canonicalization/control rejection, duplicate family numbers, identity change invalidation, recipient-specific/channel-specific drafts, explicit unsent intent destinations, editable draft restoration, confirmation cancellation, stale recipient cancellation and keypad restoration. Test fixtures restore previous preferences; no test confirms a valid external handoff. Root owns Gradle/build and physical-device execution. These tests are authored, not yet passed evidence.

Manual acceptance must cover TalkBack order, 200% font scaling, contact permission denial and revocation, missing WhatsApp/SMS handler, selected recipient accuracy, retained unsent draft after external app returns, and denied/revoked photo URI. These checks remain pending until actually run.

## Integration

Manifest additions (root owns the manifest): `READ_CONTACTS` permission; internal `.CommunicationActivity`, `.PhoneContactsActivity`, `.PhoneDialerActivity`. WhatsApp package query already exists; add `com.whatsapp.w4b` only if business app support is enabled.

Launch API: `CommunicationActivity.intent(context)` opens family; `.intent(context, slot)` selects family slot 1–3; `PhoneDialerActivity.intent(context)` opens keypad; `PhoneContactsActivity.intent(context)` opens contacts. A default `draft_channel` extra may choose `sms` or `whatsapp` for a family member, with only those values accepted.

Static verification: the English and Hindi communication resource files parse as XML, all 56 labels have matching translations, and every referenced communication string has a definition. Source compatibility guards API 28 accessibility headings for the API 26 minimum. The standalone keypad permits Android-recognized emergency numbers for a system-dialer handoff, and always offers Open Phone app; it never initiates a call. Call history opens the system Phone app without reading its provider or requesting call-log permission.

## Full-parity gaps tracked explicitly

Custom default dialer/incoming/in-call UI; native call log; dual-SIM selection and in-call audio/DTMF; default SMS inbox, SMS receiver/provider lifecycle, delivery receipts, MMS reception; contact editing and family slots beyond the existing three; safe WhatsApp media attachment and screen guidance. External system UI handoffs are useful interim behavior, not parity evidence for these features.

## Primary sources

- [Android default phone application requirements](https://developer.android.com/develop/connectivity/telecom/dialer-app)
- [Android common intents](https://developer.android.com/guide/components/intents-common)
- [Contacts provider retrieval](https://developer.android.com/identity/providers/contacts-provider/retrieve-names)
- [WhatsApp click to chat](https://faq.whatsapp.com/5913398998672934)
- [Google Play SMS and Call Log permission policy](https://support.google.com/googleplay/android-developer/answer/10208820)

## Money input repair (additional delegated work)

`MoneyAmount.parse(String): BigDecimal?` is a pure whole-expression parser shared by manual and spoken amounts. It accepts positive values through 100000 with at most two decimal places, ASCII/Devanagari digits, valid Western/Indian grouping, ₹/Rs./INR and rupee labels, English cardinal words/scales, Hindi cardinal words 0–99 with सौ/हजार/लाख, decimal digit speech, and explicit rupee/paise forms. It never extracts the first digit substring from unrelated text. Unsupported, negative, conflicting, overprecision, zero and out-of-range inputs return null for the caller to ask again.

Examples: `1,000` → `1000`, `₹१२३.५०` → `123.5`, `one hundred rupees and five paise` → `100.05`. `-50`, `125.999`, `not 100, 200`, and `one thousand two thousand` reject. Hindi fractional idioms such as `साढ़े तीन सौ` and `ढाई सौ`, verbal corrections, mixed-language number grammar, locale decimal commas and leading-dot decimals are deliberately unsupported, so callers must offer manual entry rather than infer an amount.

Verification completed on 2026-10-04: compiled the exact `MoneyAmount.kt` and `MoneyAmountTest.kt` with the project's cached Kotlin 2.2.10 compiler outside Gradle, then ran JUnit 4.13.2 on JDK 17. All four tests passed. The corpus includes numeric grouping/bounds, supported English/Hindi speech, audit regressions, ambiguous phrases, signs, overprecision and canonical positive decimal invariants. Android instrumentation/UI integration remains the root's check; this JVM result does not imply real speech recognition was tested.
