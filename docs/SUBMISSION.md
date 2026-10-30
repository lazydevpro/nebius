# Devpost submission text

Copy each section into the matching field on the
[hackathon site](https://nebiusglobalaihackathon.devpost.com/). Replace `[[YOUTUBE_LINK]]` before submitting.

## Project name

Maa: a phone my mom can talk to

## Elevator pitch

Maa turns an Android phone into something my mom can talk to. Asha, on NVIDIA Nemotron, understands Hindi and English, shows family photos, starts video calls and reads medicine packs.

## Track

Best Apps and Agents

## Links

- Code: https://github.com/lazydevpro/nebius
- Demo video: [[YOUTUBE_LINK]]
- Test build: https://github.com/lazydevpro/nebius/releases/tag/test-build

## Built with

kotlin, android, nvidia-nemotron, nebius-token-factory, camera2, whatsapp, cloudflare-workers, python

## Project story

### Inspiration

My mom recognises family faces and can use speed dial, but she cannot comfortably read smartphone menus. Ordinary things, like seeing a photo the family sent, video calling her son or checking which tablet she is holding, usually end with her asking someone for help.

Simple launchers make the buttons bigger, but the menus are still there. I wanted a phone she could just talk to, in the language she speaks.

### What it does

Maa replaces the Android home screen. Its largest button is **Ask Asha**. Asha understands Hindi, English and Hinglish, and she acts instead of only answering.

- **Family photos come to her.** When a photo lands in the family WhatsApp group, Asha turns yellow. One tap and she says a new photo arrived and shows it in Maa's own viewer. Mom never opens WhatsApp or looks through a gallery.
- **Video calls by voice.** She says "video call Beta". Maa opens the chat and presses Call, Video call and the confirmation for her.
- **"What medicine is this?"** She points the camera at a pack. Nemotron reads the name, what it treats and the expiry date, and matches it to the medicines the family saved. Asha then says when to take it, using the family's schedule.
- **Everything stays inside Maa.** The camera and photo viewer are built in, so she does not land in a system app she cannot get out of.
- **The basics.** Calls, medicine reminders, alarms, battery, time and date, a caregiver PIN and a floating help button, in Hindi and English. Common requests still work without a network.

### How I built it

Maa is a native Kotlin app with a programmatic UI. The in-app camera uses Camera2, the family photo alert uses a NotificationListenerService that filters to the family group on the phone, and the voice video call uses an AccessibilityService.

Both AI jobs use one NVIDIA open model, **`nvidia/Nemotron-3.5-Super-VL`**, through the **Nebius Token Factory** inference API:

- **Understanding requests:** a fixed prompt turns her words, plus phone status, into JSON with an action, a person, a channel and a spoken reply. This took 0.9 to 1.2 seconds across 8 test requests in Hindi, English and Hinglish, including one prompt-injection attempt, which it refused.
- **Reading medicine packs:** the same model reads the photo and returns structured JSON. This took 2.3 seconds on a clean test image.

Token Factory's OpenAI-compatible API meant I could run Maa's real prompts against several models in minutes and pick from measurements. A Cloudflare Worker gateway keeps the key off public builds and owns both prompts.

### Safety decisions

This is for an elderly parent, so what the AI cannot do matters as much as what it can.

- The AI never decides a dose. Times come only from the schedule the family saved, and an expired pack gets no time at all.
- Text inside a photo is treated as data, never as instructions.
- The model chooses from a fixed list of actions. It cannot pay anyone or run commands.
- The accessibility service stays idle until she asks for a video call, then acts for 15 seconds at most on WhatsApp's call buttons only.
- WhatsApp messages are not sent to the AI.

### Challenges

- **WhatsApp has no link that starts a video call.** The one deep link that takes a phone number is voice-only and asks for confirmation. A narrowly armed accessibility service was the only way to make "video call Beta" work.
- **A resent photo did not look new.** WhatsApp reuses the old file for a photo the phone already has, so only its modified time changes. The first version looked at the added time and missed it.
- **"On" did not mean running.** Android can show an accessibility service as enabled while it stays stuck connecting. Maa now tracks the real connection, and if the service is down, Asha opens the chat and tells Mom which buttons to press instead of silently doing nothing.
- **Reasoning models and a waiting parent.** With thinking on, some Nemotron models took 8 to 11 seconds or ran out of tokens before the JSON. Turning thinking off brought replies down to about a second.

### What I am proud of

Mom can do things that used to need someone else. The features connect in one flow: a photo arrives and Asha changes colour, she taps once, sees it, then asks for a video call with her voice to talk about it.

### What I learned

The hardest part of building for an elderly user is removing steps, not adding features. Model choice mattered less than speed and clean output, because a parent will not wait for a phone that seems frozen. Limiting the AI to identifying things, and leaving decisions to the family's own data, made the medicine feature much easier to trust.

### What's next

- Wider testing with Hindi speech and real medicine packaging.
- A signed release, and a supported way to handle video calls if WhatsApp changes its screens.
- Telling the family when a dose is missed.
- Running the gateway on Nebius Serverless Endpoints.

## Significant updates during the Submission Period

Maa was created during the Submission Period. The oldest commit is dated 4 October 2026, and the camera, family photo alert, video calling, medicine scanning and Nemotron integration were all built after that.

## Testing instructions

Maa is an Android app (Android 8 or later). It was tested on a Poco F1 running Android 10.

1. Download the test build: https://github.com/lazydevpro/nebius/releases/tag/test-build
2. Install it, allowing installs from your browser if Android asks. It is signed with a debug key.
3. Open Maa, keep English and Asha, and allow the microphone and camera.
4. Tap **Ask Asha** and say "what's the battery?" or "open the camera".
5. Say "what medicine is this?", then point the camera at any medicine pack and tap **Scan Medicine**.

To try the WhatsApp features, you need WhatsApp or WhatsApp Business on the phone:

- **Family photos:** allow Maa's notification access in Android settings, and in WhatsApp allow storage and turn on auto-download. Send a photo to a group with "family" in its name.
- **Video call:** hold the clock on Home and add a contact in settings, then turn on *Maa WhatsApp screen guard* in Android's accessibility settings. This places a real call.

The test build holds no API key. Its AI requests go through a gateway to Nebius Token Factory. To build from source with your own key, follow the README.

## Feedback on Nebius and NVIDIA

### What worked well

- **One model for text and vision.** `nvidia/Nemotron-3.5-Super-VL` replaced two models in my app, Qwen3 for text and DeepSeek for vision, with one simpler code path.
- **Speed.** With thinking off it replied in about a second, which is fast enough for a voice assistant used by an elderly person.
- **Token Factory's OpenAI-compatible API.** I changed the model name and one request field and the app kept working.
- **Language matching.** Super-VL replied in English to an English question. Qwen3 replied to the same question in Hindi.

### What could be better

- **Thinking behaviour differs across models.** On `NVIDIA-Nemotron-3-Nano-30B-A3B`, default thinking with `max_tokens` of 120 returned null `content` with `finish_reason: length`, and a request with 800 tokens took 11.4 seconds. On `Nemotron-3_5-Lightning`, the reasoning arrived inside `content` as "Here's a thinking process", which breaks JSON parsing.
- **`reasoning_effort` is unreliable.** `none` worked on Super, Lightning and Super-VL but returned null `content` on Nano. `low` returned null `content` on Super-VL and leaked reasoning into `content` on Lightning. Only `chat_template_kwargs: {"enable_thinking": false}` worked on all four models I tried. It would help to document one supported switch, and to keep reasoning out of `content`.
- **Model IDs follow no pattern.** The five NVIDIA IDs are written `NVIDIA-Nemotron-3-Nano-30B-A3B`, `Nemotron-3-Ultra-550b-a55b`, `nemotron-3-super-120b-a12b`, `Nemotron-3_5-Lightning` and `Nemotron-3.5-Super-VL`. The mix of case, separators and version format makes them easy to mistype.
- **Mobile apps need a safe way to call the API.** A key inside an APK can be extracted, so I had to build a gateway. A spend-capped key made for client apps would remove that step for small projects.
- **Hindi tone.** Super-VL sometimes replied to Hinglish in Roman script and used masculine verb forms for a female assistant. Both can be handled in the prompt, but better defaults would help Indian-language voice apps.

### Comparison with other models

| Test | Nemotron 3.5 Super VL | Qwen3 30B | DeepSeek V4.1 Flash |
|---|---|---|---|
| Hindi call request | Correct, about 1 s | Correct, about 1 s | Not tested |
| English question | Replied in English | Replied in Hindi | Not tested |
| Prompt injection | Refused, finished cleanly | Refused, cut off by token limit | Not tested |
| Medicine pack photo | All fields correct, 2.3 s | No vision | All fields correct, 2.9 s |

These are small tests on my own prompts, not a benchmark.

## Other form questions

- **Models used:** `nvidia/Nemotron-3.5-Super-VL`. I also compared Nemotron 3 Nano, Nemotron 3 Super, Nemotron 3.5 Lightning, Qwen3 30B and DeepSeek V4.1 Flash.
- **Nebius services used:** Token Factory inference API. I did not use AI Cloud, Serverless Endpoints or Serverless Jobs.
- **Tavily:** Not used.
- **AI tools used to build it:** Claude Code and OpenAI Codex.
- **What I would like next from the Nemotron team:** one documented way to turn thinking off, better Hindi defaults, and vision models tuned for reading small print on packaging.
- **Your own answers needed:** submitter type, country, the 1 to 10 ratings, eligibility checkboxes, learning level and career value.
