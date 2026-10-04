# Independent technical review of the product plan

Review date: 2026-10-03 (UTC). Reviewed the researched draft of `docs/PRODUCT_PLAN.md` (171 lines), the Android feasibility research, 251-row BIG parity inventory, independent source audit, and physical-device baseline. Also checked the current Gradle minimum SDK and manifest. This is a document review, not implementation or device acceptance. No phone actions, installations, account changes, or production edits were performed.

The draft correctly distinguishes default Home, ordinary guidance, device-level management, and contact-level enforcement. It does not claim that masking WhatsApp establishes confinement or that six narrow tests certify the product. The remaining findings below concern requirements that need to be contracted before the dependent work begins.

## Findings

### P1 — Freeze the Android/API capability contract before implementing Home

**Plan locations:** lines 31, 59, 107, 116, 123 and 159.

The first executable Home ticket specifies the Home-role flow while the support matrix is deferred until implementation begins. The current app still declares `minSdk = 26` in `app/build.gradle.kts:14`; the researched `RoleManager` path begins at API 29. A Poco-only implementation can therefore meet T01 while leaving advertised API 26–28 installations without a correct onboarding path. The same unspecified matrix affects a real app catalog, default phone/SMS, alarm presentation, microphone/capture services, widgets and notification access. The generic instruction to inspect permissions is not yet a deliverable that resolves these dependencies.

**Acceptance repair:** make a Phase 0 capability table a prerequisite to T01 and each platform feature contract. Record supported minimum/target APIs, narrower API or role, prerequisite permissions/settings, declined/revoked behavior, background/locked/reboot behavior and the exact test environment. Either retain API 26–28 with a tested supported Home-selection fallback, or explicitly decide and document a raised minimum before publishing an artifact. Do not imply that all APIs use the same role flow. Public permission/policy checks belong in this table, including the eventual app-catalog mechanism.

### P1 — Specify capture and cloud data boundaries before guidance/AI work

**Plan locations:** lines 47, 58, 62, 91, 113–116 and 156.

The plan mentions separately consented capture, secure-content exclusion, optional consented inference and minimal retention, but leaves the concrete privacy controls in production qualification. That is too late to determine what the guided-screen and inference APIs are allowed to collect or transmit. Excluding Android-secure windows and refusing OTP entry does not define how ordinary messages, notifications, screenshots, speech or contact identifiers are minimized. A screen observation can contain data irrelevant to the requested task even when the active app is supported.

**Acceptance repair:** add a Phase 0 data-flow contract before any contextual capture implementation. For each input, state approved package/window/task scope, local versus cloud processing, fields sent, retention/deletion, consent and cancellation behavior, diagnostic redaction and provider configuration. Default contextual capture to off outside a current user-approved task; never treat granting accessibility as blanket cloud-sharing consent. Verify with a fake network sink that cancellation, lock/app changes, unsupported or sensitive screens, and disabled cloud processing produce no unauthorized payload. Read-only tests should use synthetic content. Keep the local Home/family functions usable when consent is declined.

### P1 — Turn duplicate prevention into an explicit operation state contract

**Plan locations:** lines 61, 67–77, 81–89, 127 and 154.

The draft requires stale-state checks, confirmation invalidation and no blind retries, which are the right requirements. However, “persist durable operation state where necessary” does not settle the critical crash interval between dispatching a send/call and observing or persisting its result. A restarted executor can repeat a still-confirmed operation, or silently lose an uncertain one, unless the operation lifecycle and replay rule are specified. Fifty task trials are not a substitute for covering each commit/crash boundary.

**Acceptance repair:** contract an operation state machine before external-action implementation, including proposed, awaiting confirmation, confirmed, dispatching, observed, cancelled and uncertain states. Bind confirmation to an immutable recipient/channel/account-or-SIM/payload/attachment identity and a freshness rule; revalidate the actual target immediately before dispatch. Persist the transition necessary to prevent restart/reconnect from replaying a dispatched operation. Explicitly acknowledge that a third-party UI cannot provide transactional exactly-once delivery: uncertain operations remain uncertain and require a new informed decision, never automatic resend. Fault-inject process death and lost responses before/after confirmation, dispatch and outcome persistence, plus rapid taps and cancellation, and assert the real side-effect count using a test double before any authorized live communication test.

### P2 — Define data ownership and the signed upgrade/recovery path earlier

**Plan locations:** lines 58–59, 108–111, 122 and 156.

The local-storage row names layouts, family identities and drafts but does not establish whether contacts, SMS, call history and notification history are authoritative Android-provider data, Maa-owned data, or derived caches. This affects migration, backup, deletion protection, role changes and uninstall behavior. Separately, signing and migration are grouped into the final qualification phase, although the current phone has a debug-signed prototype. A future production-signature transition and broken default-Home update need a concrete preservation/recovery plan before the intended person relies on it.

**Acceptance repair:** add an early storage/upgrade decision record. Identify the authority, stable key, lifecycle and backup/deletion rule for each dataset; keep caches distinguishable from native communication records. Choose the sustained pilot package/signing identity before installing a build intended for daily reliance. Test upgrade from the actual chosen predecessor, declined/revoked roles, interrupted migrations, corrupt configuration and a bad Home startup. Demonstrate caregiver recovery through an independent Android route even when Maa cannot render, preserving native messages/contacts and committed family configuration where the declared migration contract permits. Explain the debug-to-pilot transition explicitly; do not imply arbitrary signing keys permit in-place upgrade or that rollback is always possible after a destructive schema migration.

## Claims still unverified

- All 251 source-derived parity criteria and the full reference-app menu audit remain pending; documentary coverage is not feature acceptance.
- Default Home, role onboarding, locked-screen phone/SMS behavior, alarms and all guidance on the Poco remain unverified by this review.
- No supported WhatsApp semantic recipient identity, adapter profile or confinement mechanism has been demonstrated; WhatsApp is absent from the recorded phone baseline.
- A full content overlay is a proposed experiment, not a proven accessible interaction design or permission/policy approval.
- The exact MIUI lifecycle behavior, microphone/camera quality, screen-capture consent flow, backend production controls, migration/recovery design and user/caregiver usability remain unverified.
- No public-store eligibility decision is established by the research documents or this review. Time-sensitive policies must be checked again at the relevant release gate.

**Disposition:** revise the plan to add these prerequisite contracts. The draft is a sound research direction, but those dependent implementation tickets should not begin under the present underspecified boundaries. Closing a document finding does not close its implementation or device gate.

## Re-review disposition — 2026-10-03

Re-read the revised product plan, including the actual prerequisite text, executable tickets, extension ledger and release gates. All four findings are **closed at the document-contract level**:

| Original finding | Repair verified in the revised plan |
| --- | --- |
| API/capability support | Line 31 distinguishes API 29+ role selection from guarded API 26–28 Home settings/default resolution. The capability matrix at line 107 and coverage gate at line 220 now precede dependent implementation. |
| Capture/cloud boundaries | Observation sessions and input-specific processing/retention contracts at lines 109–119 distinguish local guidance from cloud consent and require a synthetic network sink to test scope and stopping. Provider retention is explicitly unresolved until verified. |
| Duplicate external effects | Lines 77–79 specify immutable confirmation, fresh target validation, a durable pre-dispatch transition, no automatic replay, uncertain outcomes, cancel/dispatch serialization and per-boundary crash tests. MAA-009 makes this a prerequisite to an external executor. |
| Data ownership and upgrade/recovery | Lines 121–123 identify native-provider authority, Maa-owned state, non-runnable restored operations, sustained pilot signing, predecessor migration and independent broken-Home recovery. MAA-012 gates daily reliance. |

T00A and the dependency ordering at lines 159–166 prevent these requirements from becoming unspecified end-of-project chores. The extension ledger and release gates carry the requirements through implementation and verification. No remaining P1 contradiction was found in this focused re-review.

**Revised disposition:** the plan is ready to start gated contracts. This approval concerns the plan's requirements and ordering only. Reference-app coverage, platform/API validation, account setup, WhatsApp semantics, public eligibility, actual implementations, migration/recovery and physical/user acceptance remain explicit prerequisites or unverified gates. No implementation or device certification is implied.
