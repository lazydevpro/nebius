# Maa — a phone my mom can talk to

**Maa** (माँ, "mother") is an Android home screen for someone who recognises family faces and
can use speed dial, but cannot comfortably read smartphone menus. She talks to **Asha**, an
assistant that understands Hindi, English and Hinglish, instead of navigating apps.

Asha runs on **NVIDIA Nemotron** through **Nebius Token Factory**.

Built for the [Nebius x NVIDIA Global AI Hackathon](https://nebiusglobalaihackathon.devpost.com/),
Best Apps and Agents track.

| | |
|---|---|
| Package | `in.family.maa`, Kotlin, Android 8+ |
| Tested on | Poco F1, Android 10 (MIUI), over ADB |
| Model | `nvidia/Nemotron-3.5-Super-VL` on Nebius Token Factory |
| License | [MIT](LICENSE) |

## What she can do

- **Ask Asha.** One large button. Asha understands the request and acts on it: call family,
  open the camera or photos, read the battery, time or date, or check the next medicine. If
  the network is down, a local fallback still handles the common requests.
- **See family photos without opening WhatsApp.** When a photo arrives in the family WhatsApp
  group, Asha turns yellow. One tap and she says a new photo arrived and shows it in Maa's own
  viewer. WhatsApp's auto-download fetches the file. Messages outside the family group are ignored.
- **Video call by voice.** "Video call Beta" opens that chat in WhatsApp and presses Call, Video
  call and the confirmation for her, because WhatsApp has no link that starts a video call.
- **"What medicine is this?"** She points the camera at a pack. Nemotron reads the name, what it
  treats and the expiry date, and matches it to the medicines the family saved. Asha then says
  when to take it, using the family's schedule, and adds nothing of her own.
- **Stay inside Maa.** The camera (photo and video) and the photo viewer are built in, so she
  does not land in a system app she cannot get out of.
- **Everything else.** Medicine reminders, alarms, a caregiver PIN, app hiding, a floating help
  button and a QR photo she can send to family. Voice and screens work in Hindi and English.

## How NVIDIA Nemotron and Token Factory are used

Maa makes runtime calls to the Token Factory inference API, and one NVIDIA open model does both jobs.

| Job | Where | What Nemotron does | Measured |
|---|---|---|---|
| Understand a request | [`NebiusIntentDispatcher.kt`](app/src/main/java/in/family/maa/NebiusIntentDispatcher.kt) | Turns Hindi, English or Hinglish into JSON: action, person, channel and a spoken reply, using the phone's status | 0.9–1.2 s over 8 test requests |
| Read a medicine pack | [`MedicineScanner.kt`](app/src/main/java/in/family/maa/MedicineScanner.kt) | Vision: name, strength, use, printed timing, expiry and which saved medicine it matches | 2.3 s on a clean test image |

Using one model for text and vision kept the app small: one API, one prompt style and one place
to change the model.

Token Factory's OpenAI-compatible API let me compare models using Maa's real prompts in a few
minutes. Thinking is turned off with `chat_template_kwargs: {"enable_thinking": false}`, because an
elderly user is waiting for a spoken reply and the app needs clean JSON.

```text
Mom's voice ──> Android speech recognition ──> Asha ──> local routing for exact commands
                                                  └───> Nemotron on Nebius Token Factory
                                                          (directly, or through the gateway)
Camera ──> Maa's scanner ──> Nemotron vision ──> saved family schedule ──> spoken answer
WhatsApp notification ──> family group filter on the phone ──> Asha turns yellow
```

## Safety decisions

- **AI never decides a dose.** Times come only from the schedule the family saved, and an expired
  pack is given no time at all.
- **Text in a photo is data.** The prompt tells the model not to treat it as instructions.
- **Fixed actions.** The model chooses from an allowlist. It cannot pay anyone or run commands.
- **The accessibility service stays idle.** It presses WhatsApp's call buttons only for 15 seconds
  after Mom asks for a video call, and only those buttons.
- **What leaves the phone.** The request text, medicine photos, family contact names and phone
  status (battery, network, time, date, next medicine). WhatsApp messages are not sent to the AI.

## Run it

You need JDK 17, Android SDK 36 and a [Nebius Token Factory](https://tokenfactory.nebius.com) API key.

1. Copy `secrets.properties.example` to `secrets.properties` and fill in `NEBIUS_API_KEY`. This file
   is git-ignored. Do not share an APK built this way, because the key can be extracted from it.
2. Build and install:

   ```powershell
   ./build.ps1
   adb install -r dist/maa-v1.0.2.apk
   ```

   On another platform, set `local.properties` and run `./gradlew assembleDebug lintDebug`.
3. Open Maa, choose the language and assistant name, then allow the microphone and camera.
4. Optional, per feature:
   - **Family photos:** allow Maa's notification access. In WhatsApp or WhatsApp Business, allow
     storage and turn on auto-download. The group name must contain "family" or "परिवार", or be
     on the trusted list in settings.
   - **Video calls:** turn on *Maa WhatsApp screen guard* in Android's accessibility settings, and
     save the contact in WhatsApp.
   - **Home screen:** choose Maa as the default Home app.

Hold the clock on Home and confirm **Open settings** to add family contacts and medicines.

### Test build without a key

**[Download the test build](https://github.com/lazydevpro/nebius/releases/tag/test-build)** to try
Maa without an API key. It was tested on a Poco F1 and is signed with a debug key.

`./build-public.ps1` builds this APK, which holds no Nebius key and refuses to finish if it finds
one. AI requests go through the gateway in [`cloudflare/`](cloudflare/README.md),
which keeps the key on the server, owns both prompts and accepts a token that can be revoked by
itself. A Python gateway with the same intent contract is in [`backend/`](backend).

## Tests

```powershell
python -m unittest discover -s backend
adb shell am instrument -w -e class in.family.maa.NemotronLiveTest in.family.maa.test/androidx.test.runner.AndroidJUnitRunner
```

`NemotronLiveTest` calls Nemotron for real from the phone and needs a key. The other instrumented
tests are in `app/src/androidTest`.

## Known limits

- This is a working prototype signed with a debug key, not a Play Store release.
- Video calling depends on WhatsApp's English and Hindi button labels, so a WhatsApp update can
  break it. Asha then opens the chat and tells Mom which buttons to press.
- After an app update, Android can show the accessibility switch as on while the service stays
  disconnected. Asha notices this and gives the same instructions. Turning the switch off and on
  fixes it.
- Automated tests do not cover real speech recognition or a real WhatsApp call. Those were checked
  by hand on the Poco. The Hindi medicine flow has had less testing than English.
- QR support only sends a photo and amount to family. Maa never pays anyone.

More detail: [product plan](docs/PRODUCT_PLAN.md), [testing checklist](docs/TESTING.md) and
[submission text](docs/SUBMISSION.md).

Original code is MIT licensed. ZXing core is Apache-2.0 and included as a Maven dependency.
