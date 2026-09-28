# M69 spotlight walks — predictions, recorded before any code (2026-09-27)

Spec: `docs/specs/spec-spotlight-walks.md` r4. Each prediction is scored in `RESULTS.md`, and a miss is recorded as a
miss.

**Build shape:**
- **P-1 (S0).** `clearGraph` and `loadFromSource` are the only graph transitions that leave a stale digest. The fix
  touches `TopologyPanel` alone, and the session-recovery tests pass unchanged.
- **P-2 (S1).** Walk storage follows the report pattern without changing any existing report test. It adds new tests
  only, and changes three `SettingsShare` paths (export, preview, apply) plus the category label.
- **P-3 (S1).** The record digest over `rawText(i)` is identical across two opens of the same file, for both the heap
  store and the mapped store.
- **P-4 (S2).** `FilterState.setAll` turns up to four change events for one step's filter into exactly one.
- **P-5 (S2).** The paint outcome needs changes to `ChartPanel` only, plus one data-stamp call where `GraphPanel`
  hands it new data.
- **P-6 (S2).** A step on the demo fixture becomes ready in under 500 ms. No test reaches the 5 s bound except the one
  that asserts it.
- **P-7 (S3).** Classifying input before dismissal changes only `SpotlightOverlay`'s mouse handling. Every existing
  M64 spotlight frame test passes unchanged.
- **P-8 (S3).** The keyboard frame check passes under CI's Xvfb. On this Mac it may be skipped, or need real focus
  (the known limit that posted keys are dropped without a focus owner), and any skip is reported as a skip.
- **P-9 (S4).** Apart from the contract tests the spec names, only `CloseVerbTest` and `ProjectVerbTest` assert the
  verb count.
- ~~**P-10.** The session processor is not regenerated: no node, fact or effect changes (O-3).~~ **Withdrawn before any
  code** (2026-09-27): the owner superseded O-3, so playback is a session node and the processor *is* regenerated.
  Replaced by **P-10b**: the `walkPlayback` node needs only `openLog` and the operation gate as parents. Its facts and
  effects fit the existing `SessionEffects` / `SessionEvents` shapes without changing any other node, and
  `SessionGraphShapeTest` needs one new expectation.

**Size and cost:**
- **P-11.** 2,500–4,000 lines, including tests.
- **P-12.** At least 25 new mutation controls.
- **P-13.** More than the "two days" the earlier plan implied: it is measured by working sessions, and recorded in
  RESULTS.

**What would show the design wrong (stated in advance):**
- A step cannot be applied without writing the profile or settings, whatever path is used.
- The paint outcome cannot tell a stale paint from a current one without a second size calculation.
- Input cannot be classified before dismissal without breaking an existing M64 behaviour.
