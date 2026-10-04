# Verified device baseline — 3 October 2026 (UTC)

- Dedicated test connection: Poco F1 at 100.79.216.34:5555 over Tailscale.
- Model reported by Android: POCO F1; Android 10 / API 29; MIUI V12,
  incremental firmware build V12.0.3.0.QEJMIXM (read directly during this research).
- Display: 1080x2246, density 440 dpi.
- User enabled the ADB bridge and approved this computer's debugging key.
- Maa was not installed before testing. Current bilingual debug APK is installed.
- APK on device matches SHA-256 AE5ED95396015156CA3A65E1B6D2807292F1D8DFA61FAF369F8CBEA3F6897E21.
- Six instrumentation cases passed (five navigation/settings/language; one QR fixture).
  See ../verification/instrumentation-poco.txt and instrumentation-poco-qr.txt.
- QR fixture ran only after checking there were no existing Maa QR photos; cache
  was empty afterward. No messages, final calls or payments were sent.
- System animation changes were denied by MIUI; values were verified unchanged.
  Generic ADB input injection was also denied; app-scoped instrumentation worked.
- A default-Home resolution query returned Android ResolverActivity. Maa has a
  HOME intent filter but has not been selected as the default Home application.
- Neither com.whatsapp nor com.whatsapp.w4b returned an installed package path.
  Live WhatsApp adapter tests need an installed supported app and a user-managed
  test account/family-contact setup; no account registration was performed.
- Camera hardware, microphone/voice quality, actual SMS/calls, screen guidance,
  overlays across apps, and long-running MIUI background behavior remain unverified.
- The API35 emulator was unstable during this session. It is not the acceptance
  authority for the Poco target; preserve both failure logs and physical evidence.
