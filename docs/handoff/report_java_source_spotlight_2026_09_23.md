# Java source spotlight — implementation handoff

Branch: `feat/java-source-spotlight-current`. Base: main `9b88e6ac` (1.18.0).
The original implementation remains at `5bf2e442` on `feat/java-source-spotlight`; this replacement
branch rebases it without force-pushing published history. Only this feature's section is added to the tracker.
The primary checkout and its IDE edits were not used for implementation. No merge or release is requested.

Contract: [source spotlight proposal](../proposals/source-spotlight.md). Predictions were committed
before source changes (`aa10f326`, rebased as `b199f1cc`, then `54658193`). The accepted review and S-1/S-2 clarifications
are preserved in the proposal packet. N-1's deadline was specified in the tracker before that code was added.

## What changed

- `source:java:<fqn>` and `source:java:<fqn>:line:<n>` identify an explicit document. No instance/type
  inference, new verb, source execution, watcher or archive download was added.
- EDT capture and pure precheck precede all reveals, including the executor's record-row `goto`.
  A worker captures origin, rendered-text SHA-256 and parsed model. EDT apply checks request ticket,
  source generation, log and view state before rendering. The selected processor model comes from that
  exact accepted snapshot. Non-Java requests retain their existing resolution path.
- A ten-second monotonic preparation deadline is below `McpBridge.CALL_TIMEOUT` (60 seconds). Expiry
  refuses while a blocked read is still running; terminal-state guards prevent later publication.
  Caller interruption cancels its own preparation without clearing a newer spotlight. The EDT never waits.
- Source roots and local archives return origin-bearing documents; archive refresh discards positive and
  negative cache entries. Discovery still happens once per resolver. A new entry in a known jar is found
  by repeating the spotlight; a new jar needs reconfiguration/restart. `source {fqn}` is unchanged:
  authorised roots only, duplicate refusal, no sources jars.
- A batch selects its destination once. Topology plus Java opens the embedded viewer without seeding
  another document. Prepared bindings publish only after the whole set succeeds. Later measurements use
  the binding, never a fresh FQN lookup. Removed bindings release their snapshot references.
- Java bands include all wrapped rows and clip to the text viewport, with `partial` in line echoes.
  Design bands keep the released wholly-contained rule. Both source viewers and the design viewport now
  notify remeasurement. Label wrapping is height-bounded so a first reveal cannot allocate a negative
  source viewport. The human label visibly states the unverified source/run relationship and lookup policy;
  its tooltip carries the complete origin and revision.

## Predictions and witnesses

All fixtures below are **constructed regression cases**, not preserved participant runs.
Every mutation was restored to its exact original bytes. The executable recipe is
[`tools/verify-java-source-spotlight.py`](../../tools/verify-java-source-spotlight.py).
The [machine-readable record](evidence/java-source-spotlight-2026-09-23/mutations.json) includes the actual
failure text, named test, command, changed source sites and SHA-256 of each restored source file. A compile
error, missing report, skipped test or an error rather than the named assertion does not count as seen red.

| Frozen prediction | Result and regression | Negative control actually observed |
|---|---|---|
| Origin/revision remain paired; repeat sees cached misses and changed hits | Held. `SourceDocumentTest`; frame `realEntranceRereadsJarMissAndHitButNewJarNeedsReconfiguration`; `duplicateRootPolicyIsDisclosedAndGlanceStillRefuses` | `negative-cache`: repeat still refuses the now-present archive entry |
| Preparation precedes reveal and runs off EDT; superseded work cannot return | Held. Frame `invalidJavaBeforeRecordRevealKeepsSelectionAndFilter`; `blockedArchiveReadLeavesEdtFreeAndClearConfigurationAndNewRequestSupersedeIt` | `reveal-before-read`: filtered table changes 0 → 1; `read-on-edt`: thread assertion fails; `ticket`: cleared request succeeds instead of refusing |
| Accepted processor model matches accepted text | Held. `SourceDocumentTest`; frame `changedSelectedProcessorUpdatesModelAndBadBatchDoesNotReveal` (also checks a non-selected reread) | `selected-model`: `OldType` remains where `NewType` is required |
| Graph and Java remain visible in either order; Java clips while design refuses | Held. Frame `graphAndJavaUseOneVisibleDestinationInEitherOrder`; `wrappedLogicalLineMeasuresAllRowsClipsAndReportsPartial`; `JavaLineGeometryTest`; `DesignSpotlightFrameTest` | `raw-caret`: no valid line band; `no-clipping`: band leaves viewport; `first-wrapped-row`: 17px rather than 136px; `design-containment`: partially visible design band incorrectly resolves |
| Viewport changes remeasure; a binding never changes document silently | Held. Frame `viewportHooksRemeasureWithoutAnotherRequestAndBindingsNeverRetarget`; `clickEscapeSelectiveClearAndReplaceReleaseBindings` | `viewport-listeners`: scrolled-away line remains lit; `revision-binding`: replacement document retains the old anchor |
| N-1: expiry cannot publish late | Held. Frame `preparationDeadlineRefusesBeforeReadReturnsAndLateCompletionCannotLight`; `interruptedCallerCancelsOnlyItsOwnPreparation` | `deadline-late-publication`: releasing the expired read lights a target |

The controlled blocked-read tests hold the real resolver's discovery monitor. They do not replace the
spotlight entrance with a helper. An EDT sentinel executes while the reader is blocked. Timeout tests
shorten the frame's internal duration to 200ms; the production default stays ten seconds.

Two implementation checks found problems before handoff: a wrapping label measured before receiving its
first width could consume the viewport, and queued layout notifications could unnecessarily supersede a
new request. The label now has bounded preferred height; preparation checks captured navigation/scroll
state rather than treating every extent notification as user input. The final display cases exercise them.

## Validation

Original local gates at `5bf2e442`, before integration with 1.18.0:

- `mvn -q clean test`: **1,786 tests, 0 failures/errors, 61 headless skips**.
- Full twelve-suite display list from CI: **62 tests, 0 failures/errors/skips**. The Java frame suite was
  also rerun after strengthening the superseded-model assertion: **11/0/0/0**.
- `mvn -q package`: passed, including the full headless test gate again.
- `python3 tools/test_tools.py`: all passed.
- `python3 tools/verify-m64-spotlight.py`: **94/94** existing API checks passed against the new jar.
- Strict MkDocs, `git diff --check`, tracked/untracked rule-1 sweeps: passed/clean.
- All **12/12** mutations failed their named assertions; source files restored.

Integration with 1.18.0 at `914ae908` was checked separately: **1,875 headless tests (61 skips),
62 display tests (no skips), zero failures/errors**. Packaging after these gates, tool checks and strict
docs and all 94 existing spotlight API checks also passed. MainFrame's added/removed lines from 1.18.0 were compared with the integrated result:
every line is preserved. The tracker diff adds only this feature's section. No source conflict required
manual resolution; tracker and changelog conflicts were resolved by retaining both bodies of work.

[PR #6](https://github.com/telaminai/fluxtionauditlog-analyser/pull/6) replaces the conflicting draft #5.
[GitHub CI on `914ae908`](https://github.com/telaminai/fluxtionauditlog-analyser/actions/runs/35839227529)
passed build, loop-bench and ui-frame; static checks also passed. The Linux/Xvfb log explicitly records
all twelve suites: **62 tests, no errors, failures or skips**. See the
[machine-readable gate record](evidence/java-source-spotlight-2026-09-23/gates.json).
The original twelve mutation witnesses remain evidence at `5bf2e442`; they were not repeated after the
rebase. Their code sites are unchanged apart from MainFrame line displacement by 1.18.0 additions.

The generated [site screenshot](../site/assets/java-source-spotlight.png) was inspected: both cutouts
align, both captions are visible, and the source/run qualification is visible. Only neutral fixture names
appear. Reproduce with a real display:

```sh
mvn -q test -Dtest=JavaSourceSpotlightFrameTest#graphAndJavaUseOneVisibleDestinationInEitherOrder \
  -Djava.awt.headless=false -DargLine=-Djava.awt.headless=false \
  -DsourceSpotlight.capture=/private/tmp/java-source-spotlight.png
```

The screenshot path is a generated output and is replaced by that command. Frame construction uses an
isolated `user.home`. The example is a constructed one-node graph, not a claim about a running processor.

## Boundaries and remaining review

- No new LLM sessions, paid generation, application execution, publication or release.
- A third-party client's timeout shorter than ten seconds cannot be inferred from a disconnected HTTP
  request. The server bound addresses the built-in bridge; thread interruption/cancel is handled explicitly.
- Filesystem I/O may ignore interruption. Such a worker may finish its cache read after refusal, but the
  terminal/ticket checks prohibit view/model/binding publication. Newly installed source jars still need
  resolver recreation, deliberately preserving the existing discovery contract.
- Node shorthand, method/overload targets, saved tours and general callout-overlap work remain out of scope.
- Independent review still owes its own gate runs and attack of the prepared-plan, first-match disclosure,
  lifecycle and geometry boundaries. Do not interpret this author report as that review.
