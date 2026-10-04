# Independent product and accessibility review of the rebuild plan

Review date: 2026-10-03 (UTC). Reviewed `PRODUCT_PLAN.md`, `BIG_LAUNCHER_PARITY.md`, `INDEPENDENT_CODE_AUDIT.md` and `DEVICE_BASELINE.md`. This review made no production edits or device changes. Locations below refer to the draft reviewed, before repairs.

## Verdict

The plan preserves the user's full suite ambition and correctly distinguishes default Home, simplified entry points, guided interaction and actual confinement. It does not mistake the six existing Poco regressions for production acceptance. Its source inventory, physical-device limits, reference-app closure gate and independent engineering loop are materially useful. The following acceptance gaps should be repaired before the plan is called ready for implementation contracts.

## Findings and acceptance repairs

### P1 — Passive guidance can be omitted while the written executor tests pass

Locations: `PRODUCT_PLAN.md:79-89` (guided execution), `:112` (guided phase), `:157` (guidance release gate).

The user explicitly wants fewer wrong clicks even when they are not asking the assistant to act. Most of the current contract validates Maa-generated steps and says unsupported states stop the Maa task. A build could satisfy those rules while exposing unrestricted native controls to the person's touch, keyboard or TalkBack whenever the assistant is idle. Hiding controls visually also does not demonstrate that other input paths cannot activate them. The current limitations about process death are appropriate and should remain.

**Repair:** add a separate manual-guidance contract. In each advertised supported screen, specify which controls the person can activate, which app owns them, and which input methods are supported. Guidance must operate with voice and cloud inference idle or unavailable. When the live guard loses screen identity, replace its controlled surface with an understandable recovery state instead of merely stopping the model. Test attempted taps, long presses, rapid/double taps, swipes, touches during transition, TalkBack, keyboard and switch activation of disallowed native controls, as well as legitimate reading/reply/cancel/Home actions. Record touch-through and focus exposure separately from process/service-death exposure. Switch support remains unverified until tested with actual hardware. If an input path cannot be controlled accessibly, narrow the supported profile explicitly; do not advertise strict confinement. An overlay neither disables the underlying app nor proves protection after the overlay's service dies.

### P2 — Traceability currently covers BIG parity more precisely than Maa's central extensions

Locations: `PRODUCT_PLAN.md:103-114` (scope and delivery), `:133` (contract), `:153` (traceability); `BIG_LAUNCHER_PARITY.md:12-21` (stage meanings).

The 251 BL identifiers form a concrete scope ledger, but the floating assistant, passive guidance, recipient identity, privacy boundaries, handover and recovery extensions have no equivalent stable identifiers. A parity-complete report could therefore omit essential Maa behavior. The inventory's P1/P2/P3 delivery stages are also not the plan's numbered phases; treating them as equivalent would place some launcher integrations before their phone/SMS or alarm dependencies.

**Repair:** add a compact extension ledger with stable IDs, observable behavior, acceptance evidence and dependency/gate. Explicitly map inventory stage groups to delivery phases, and assign the final phase/ticket per BL row as contracts are created. A phase gate must distinguish the launcher's owned control from a still-pending external integration; neither silently waive the integration nor force acceptance before its dependencies exist. Release reconciliation must cover both BL and Maa-extension IDs.

### P2 — Caregiver setup lacks a concrete ready-to-handover decision

Locations: `PRODUCT_PLAN.md:95-99` (design), `:108` (foundation), `:123-126` (first tickets); `INDEPENDENT_CODE_AUDIT.md:77` (missing setup-complete state).

The plan mentions caregiver unlock/recovery, role health and family identities, but it does not define when the caregiver may safely leave the phone with the intended person. Passing individual configuration tests is insufficient if no family contact is configured, the needed calling capability is absent, or the selected guidance service is inactive. Setup could be declared complete with a working launcher but an unusable daily journey.

**Repair:** define a caregiver handover summary that distinguishes ready, degraded and unavailable capabilities in both languages, based on observed permissions/roles/services and configured contacts. Require at least one verified intended family route or an explicit incomplete-setup state; do not falsely mark it ready because a name is saved. Rehearse one daily task, Home and a help/recovery route with the intended person. Test interrupted setup, no contacts, an invalid/changed number, a denied default role, service loss and a forgotten caregiver credential. Preserve a usable Home/recovery path throughout. Any real test send or call still needs its specific authorization.

### P2 — Screen-aware AI has no explicit user-visible observation-session boundary

Locations: `PRODUCT_PLAN.md:47` (capture), `:62-63` (voice/diagnostics), `:91` (AI scope), `:160` (release operations).

Optional consented inference and minimal retention are good requirements, but they do not specify when observation starts/stops, which visible context can leave the phone, or what happens after switching to an unrelated app. This matters because the proposed floating assistant may remain on top of private conversations while the user is not speaking to it. A generic permission explanation can pass without proving that the implemented data flow follows the person's immediate intent.

**Repair:** add an observation-session state with visible start/stop indication, explicit local versus cloud context boundaries, supported-app scope and a revocation path. Default to gathering only the context required for the active authorized task; invalidate it on app/window changes. Define exclusions for secure and credential-entry contexts, no background microphone recording, and bounded/redacted diagnostic retention. Verify with a controlled backend that cancellation, revocation, an unsupported app and a completed task stop further screen/transcript upload. Local Home and manual guided interaction must remain usable with cloud sharing disabled.

## Inventory verification

Independently counted the actual requirement table rows, rather than repeating the headline:

| Check | Result |
| --- | --- |
| BL requirement rows | 251 |
| Unique BL IDs | 251; no duplicate IDs found |
| Rows with a G stage gate | 54 |
| Area counts | SET 15, VIS 12, ACC 11, BTN 38, APP 8, SEC 9, CON 23, TEL 41, SMS 33, SOS 13, NTF 18, ALM 9, OPS 16, COM 5 |
| Crosswalk row-count arithmetic | 18 + 10 + 7 + 5 + 11 + 14 + 30 + 17 + 9 + 6 + 5 = 132 |

The crosswalk arithmetic is internally consistent. This review did not independently re-enumerate the live manual or installed reference menus, so it does not certify the 132 source rows or exhaustive product parity. That uncertainty is already honestly captured by `AUDIT-REFERENCE-01`, including especially the less-documented Notifications and Alarm menus. The inventory correctly includes paid functionality, optional controls, broader locale/RTL scope and commercial-boundary records rather than treating trial restrictions as Maa requirements.

## Additional design evidence worth retaining

For the promise of a better design, record the same few daily journeys on the existing prototype and, where legitimately available, the reference product. Compare completion, accidental activations, coaching and recovery, then preserve why the revised design performs better. This supplements the proposed minimum-target, contrast, text-scale and participant gates; screenshots alone cannot establish the improvement.

The intended-person pilot and a broader five-person usability sample are appropriate early evidence, not a population-wide accessibility certification. Do not weaken the no-wrong-recipient rule to achieve an aggregate success rate.

## Recheck required

The coordinator should repair these four findings, link the repaired locations and request re-review. This report's initial verdict is not a production approval or a claim that the proposed features have been implemented.

## Re-review disposition — 2026-10-03

Re-read the revised actual plan after the coordinator's repairs. All four findings are **closed at the document-contract level**:

| Finding | Revised evidence | Disposition |
| --- | --- | --- |
| Passive/manual guidance | `PRODUCT_PLAN.md:95-101`, MAA-004, phase 5 and release gate `:212-213` explicitly require local guidance with voice idle/cloud off; legitimate tasks and attempted tap/long-press/rapid-tap/swipe/transition/TalkBack/keyboard/switch activation are covered. Unsupported input profiles remain explicit, and service-death exposure is retained. | Closed as a plan defect. Implementation feasibility and actual input-path evidence remain mandatory. |
| Extension traceability and phase mapping | `:139` distinguishes inventory delivery groups from phases and leaves integrated rows pending for their dependencies; `:168-188` adds the Maa ledger; `:208` reconciles both ledgers. Independently counted 15 unique MAA IDs with no duplicates. | Closed. Per-row ticket/phase assignment remains an explicit contracting task. |
| Caregiver handover readiness | `:133`, MAA-011 and `:218` require ready/degraded/unavailable status, at least one verified family route, daily-task/Home/recovery rehearsal and the specified incomplete/failed setup cases. | Closed as a plan defect; no handover is certified. |
| Observation-session and cloud boundaries | `:109-119`, MAA-007 and `:215` specify local versus AI sessions, explicit observation/Stop state, data classes, sensitive-state exclusions, capture/upload stops and fake-network-sink checks. Already transmitted bytes and provider retention are treated honestly. | Closed as a plan defect; data-flow implementation is unverified. |

The comparative observation requested for “better design” is now present at `:133`. The revised text preserves the original full-suite target, bilingual scope and strict-mode feasibility boundary. No remaining P1 product/accessibility contradiction was found in this focused re-review. The phase-2 headline is read together with the explicit integration dependency rule: no incomplete integration row can be accepted merely because its tile renders.

This disposition approves the repaired requirements for subsequent feasibility and implementation contracts. It does **not** certify the current prototype, reference-product completeness, Play approval, guided-app feasibility, production readiness, usability, data privacy implementation or physical-device behavior. Those gates remain pending exactly as the plan states.
