# Source spotlight: accepted handoff and final clarifications — 2026-09-21

[Independent review at 3665e237](review_source_spotlight_r4_response_2026_09_21_claude.md):
**READY FOR HANDOFF**, U-1 and L-1–L-3 closed. The review is preserved unchanged. It judged proposal
`2b17209d`; the two clarifications below are subsequent author changes, not retrospectively reviewed
implementation. [Proposal](../../proposals/source-spotlight.md).

## S-1 — off-EDT read, guarded EDT application

Confirmed at `401da35b`: ActionExecutor.render runs doSpotlight on the EDT. Merely inserting I/O
between MainFrame.precheck and resolveAll would block the UI. Java-bearing requests now require a
separate orchestration path: EDT capture/precheck and ticket, Background read/parse/hash, then EDT
supersession check and apply. The existing non-Java route remains synchronous. No future join on the
EDT; no early successful echo. A stale/error result cannot publish bindings or models.

The source read also exposed an earlier reveal entrance: ActionExecutor.doSpotlight calls doGoto
for record-row targets before MainFrame's entry. Preparation must precede that loop as well, or a
mixed invalid-Java request would still change filters before refusing. The proposal explicitly covers
both entrances and requires the mixed-batch regression. While the worker is pending the EDT continues
normal event handling; only the short reveal/apply phase defers layout callbacks.

Acceptance uses an instrumented, latch-blocked unwarmed/missing archive lookup, an EDT sentinel,
a repeated miss, a concurrent clear/configuration change and separate EDT/ticket mutations. It
establishes where the read ran and whether the UI remained responsive, not a timing benchmark.

## S-2 — selectedModel follows the accepted processor snapshot

Confirmed selectedModel is separately cached. When preparing the selected processor's FQN, parse
its model from the same prepared text off the EDT. After ticket/selection/configuration checks,
install the model with the rendered snapshot on the EDT. Superseded/failed preparations do not alter
it; rereading another class does not alter it. This avoids both stale field mappings and an EDT lazy
reread of bytes different from the displayed snapshot. A visibility refusal after actual reveal
still leaves the model consistent with that revealed snapshot.

Acceptance changes a processor field type, reissues its Java spotlight, and compares the displayed
revision and subsequent mapping; disabling model replacement must expose the stale type. This does
not make permissive mapping authoritative or reintroduce the deferred node target.

## Optional session-graph note

Recorded as an implementation option: express ticket/lifetime decisions through SessionProcessor
if appropriate, with its designated-thread rule, one authoritative state and Swing as the geometry
adapter. Do not create a parallel registry merely to use the graph. Headless transition checks are
required either way; real geometry remains in the non-skipping display job.

## Scope and checks

Documentation only. Read current ActionExecutor, Background, SourceService and SessionDriver; no
runtime behavior changed, no UI/client trial or release. The independent verdict is preserved;
implementation still owes the new acceptance checks. Repository gates are recorded in the brief.

Final clarification gates: Maven 1,767 / 0 failures / 0 errors / 49 headless skips; strict docs,
local packet links, diff whitespace and tracked/untracked rule-1 sweep pass. No display run needed
for these documentation edits, and none is claimed.
