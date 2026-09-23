# Independent review: Java source spotlight — 2026-09-23 (reviewer: claude)

**Verdict: MERGE.** No blocker. Three low findings, none of which changes a verdict, a refusal or a
published contract. Everything the brief asked to be attacked was attacked, and every gate was re-run
rather than taken from the report.

Reviewed `feat/java-source-spotlight-current` at **`0fbbdace`** against main **`9b88e6ac`** (1.18.0), in
my own worktrees (`/private/tmp/rv9`, `/private/tmp/rv9-mut`). The primary checkout and the author's
implementation worktree were not touched.

---

## Gates I ran

| Gate | Result |
|---|---|
| `mvn -o clean test` | **1,875 run, 0 failures, 0 errors, 61 headless skips** |
| `mvn -o package` | passes |
| `python3 tools/test_tools.py` | passes |
| `python3 tools/verify-m64-spotlight.py` | **94/94** existing API checks pass |
| `mkdocs build --strict` | clean |
| `git diff --check origin/main..HEAD` | clean |
| Rule-1 sweep, tracked | prints nothing |
| Rule-1 sweep, working tree (`grep -ri … --exclude-dir=target .`) | only `CLAUDE.md` and `docs/ONBOARDING.md`, the two files that state the rule |
| **Real-display run, exact CI class list**, `-Djava.awt.headless=false` both places | **62 tests across all 12 suites, 0 failures, 0 errors, 0 skips** |
| `python3 tools/verify-java-source-spotlight.py` (own disposable worktree, real display) | **12/12 SEEN RED, all restored**, exit 0 |

Per-suite display counts, all non-zero and skip-free: AsyncOpenInterleaving 12, DesignSpotlight 2,
**JavaSourceSpotlight 11**, LoadedFileObservation 1, MenuScreenshot 1, NamedGraphAndMenuSpotlight 6,
PairingDuringLoad 9, PersonAtTheScreen 3, SessionRecovery 9, Spotlight 4, TemplateCatalogue 1,
WestColumnStartsCollapsed 3.

**Both CI lists agree.** `ci.yml:80` (the `-Dtest=` list) and `:86` (the no-skip loop) each name the same
twelve suites, including `JavaSourceSpotlightFrameTest` and `DesignSpotlightFrameTest`. Neither list has a
suite the other lacks.

**Mutation witnesses, re-run by me rather than read.** All twelve reproduced, each failing the *same named
test* as the committed evidence, each source file restored byte-for-byte: `negative-cache`, `ticket`,
`read-on-edt`, `selected-model`, `raw-caret`, `no-clipping`, `first-wrapped-row`, `design-containment`,
`viewport-listeners`, `revision-binding`, `reveal-before-read`, `deadline-late-publication`.

---

## The six boundaries

**1 · Mixed record-row/Java prepares before any view change — holds.** `ActionExecutor.doJavaSpotlight`
routes any request containing a Java target through the asynchronous path, and `revealSpotlightRows` is
passed *into* `MainFrame.prepareJavaSpotlightHere` as a `Runnable` that runs only inside the EDT apply,
after the plan has been read and re-prechecked. A lookup failure throws on the worker and reaches the
error callback, so `revealRows` never runs. Witness `reveal-before-read` is red; frame test
`invalidJavaBeforeRecordRevealKeepsSelectionAndFilter` covers it. Source I/O cannot run on the EDT by
construction: `SourceService.Lookup.freshDocumentForSpotlight` throws `IllegalStateException` if called on
the EDT, and model parsing happens in the same worker call (`JavaSpotlightPlan.read`). Witness
`read-on-edt` is red.

**2 · Late publication — holds, including the case the brief singles out.** The apply callback checks, in
order: `result.isDone()`, the monotonic deadline, the request ticket, `sourceService.isCurrent(lookup)`,
the captured store, displayability, the captured tab, and both captured viewer view states; then it
re-runs `SpotlightTarget.precheck`. Expiry increments the ticket **only if it is still its own**
(`if (javaSpotlightTicket == ticket) javaSpotlightTicket++`, `MainFrame:2120`), and the cancellation path
does the same, so an expired or cancelled caller cannot invalidate a newer request's target. Clear,
Escape and any non-Java spotlight increment the ticket (`:2065`, `:2073`, `:2607`), which supersedes a
pending preparation. The deadline is ten seconds (`:2059`) against `McpBridge.CALL_TIMEOUT` of sixty, so
the caller always receives a verdict before the bridge gives up — the N-1 disposition, implemented.
Witnesses `ticket` and `deadline-late-publication` are red.

**3 · One snapshot for text and model — holds.** The worker builds the `SourceDocument` (text, origin,
SHA-256 of the rendered text) and parses `EventProcessorModel` from *that same text*;
`SourceService.acceptSpotlightModel` refuses unless the lookup generation is still current and installs
the model only when the FQN is the selected processor. `MavenSourceResolver` now caches
`Optional<SourceDocument>`, so origin travels with cached text, and `freshDocument` drops positive **and**
negative entries. Discovery stays once per resolver, and the refusal text says so. `source {fqn}` is
untouched: authorised roots, duplicate refusal, no jars — deliberately different, disclosed in the echo as
`lookup: source-viewer`, `selectionPolicy: first-match`. Witnesses `selected-model` and `negative-cache`
are red.

**4 · Bindings measure their own revision — holds.** `SourcePanel.javaBounds` refuses unless the pane is
showing **and** `pane.snapshot.equals(anchor.document())`, so a replaced document extinguishes the target
rather than re-anchoring it; measurement never re-resolves an FQN. Staged bindings are published only
after the whole set resolves (`MainFrame:2651-2653`), a refused batch publishes none, and
`relightSpotlight` reconciles the registry with the surviving lit names. `add` folds retained documents
into the one-document check inside `JavaSpotlightPlan.read`, and two FQNs in one file are allowed because
identity is the file, not the name. Witnesses `revision-binding` and `viewport-listeners` are red.

**5 · Geometry — holds.** `JavaLineGeometry.band` spans the first row's top to the last row's bottom
across the viewport width, so a wrapped logical line includes every visual row; `visible` intersects with
the viewport and sets `partial` from `!viewport.contains(band)`. Design keeps the released containment
rule — the only change to `DesignSourcePanel` is a viewport `ChangeListener`, so both viewers now remeasure
on scroll, and `DesignSpotlightFrameTest` gained the partially-visible-band refusal. Witnesses `raw-caret`,
`no-clipping`, `first-wrapped-row` and `design-containment` are red.

**6 · The three surfaces agree, and none over-claims — holds.** I inspected the generated screenshot
(`docs/site/assets/java-source-spotlight.png`) rather than trusting the description: two numbered cutouts
(the Java declaration and the topology node), both captions legible, the graph still visible beside the
source, and the label reading `Source/run: unverified · com.acme.Node · source-viewer · first-match ·
<path>`. Only neutral fixture names appear. The echo and `context` carry `revision`, `revisionBasis:
rendered-text-utf8`, `lookup`, `selectionPolicy`, the origin map and `relationship: unverified`, with
`partial` on line anchors. Nothing in any surface claims those bytes built the loaded run or that the
lit statement executed.

---

## Findings

### F1 · Low (CONFIRMED) · the mutation verifier never establishes a baseline for the test it mutates
`tools/verify-java-source-spotlight.py:84-90` deletes the surefire report, runs the named test **once with
the mutation applied**, and records `seenRed` when the exit code is non-zero, the named method has a
failure, and the suite reports no errors and no skips. It never runs that test *unmutated*. A display
test that failed for a timing reason would therefore be recorded as a witness, and the JSON would look
identical to a real one.

Not a false positive today: my own clean display run (62 tests, zero failures, zero skips) is the missing
baseline for this exact checkout, and all twelve witnesses then failed with the mutation applied and
passed without it. But the evidence stands on that separate run rather than on the checker.

**Suggested:** run the named test once before mutating and record `baselineGreen` beside `seenRed`. Three
lines, and the artefact then proves both halves by itself.

### F2 · Low (CONFIRMED by reading) · `partial` reports `true` when nothing is measurable
`MainFrame:2694` — `…javaBounds(…).map(JavaBand::partial).orElse(true)`. When a lit Java target's band
cannot be measured at all, the echo says `partial: true`, which reads as "part of it is visible" when the
honest answer is "none of it is". The window is small, because `relightSpotlight` drops such a target and
reports it under `wentOut`, but an echo assembled in that window overstates.

**Suggested:** omit `partial` when bounds are absent, or emit the existing not-visible reason instead.

### F3 · Low (CONFIRMED) · a line one past the last text line is accepted
`JavaSpotlightPlan.read` bounds-checks with `document.text().split("\n", -1).length`. For a file ending in
a newline that counts a phantom final empty line, so `line = lines + 1` is accepted and measured (Swing's
document has a matching empty final element, so the band is real). It is harmless and self-consistent, but
an adapter author reading the echo sees a line number one past the file's last line of text.

**Suggested:** either subtract the trailing empty element, or say in the guide that the anchor may name the
empty line after the last one.

---

## Verified as accurate — no action needed

- The report's own numbers reproduce: 1,875 headless (61 skips) and 62 display tests, zero failures.
- `mvn package`, `test_tools.py`, `verify-m64-spotlight.py` (94/94), strict docs, `diff --check` and both
  rule-1 sweeps all pass here as claimed.
- Twelve mutation witnesses reproduce with the same named assertions, and every source file was restored.
- The CHANGELOG carries two user-visible lines (rule R2), the tracker adds only this feature's section,
  and the spec and user-guide changes describe the shipped behaviour.
- `ActionExecutor.doJavaSpotlight` refuses if invoked on the EDT. No in-app caller reaches it that way:
  saved analyses dispatch through `render` on the request thread, and the only EDT `render` call is `goto`.

## What I did not do

No fix, no commit to the author's branch, no merge, no release, no force-push, no amend. I did not start a
server, drive a live MCP client, or exercise a third-party client timeout — the report does not claim
those either. The screenshot was inspected, not regenerated.

**Review branch:** `review/java-source-spotlight-2026-09-23-claude`; this report is
`docs/handoff/review_java_source_spotlight_2026_09_23_claude.md`.
