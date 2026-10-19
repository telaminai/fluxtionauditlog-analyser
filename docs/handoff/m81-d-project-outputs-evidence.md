# M81 D — project output offers

Implementation candidate; not yet independently reviewed or shipping. C/A are already in 1.37.0. B remains
explicitly deferred: draft #184 is closed, its branch/evidence preserved, and no B production code is a dependency.

## Ownership and actual boundaries

`ProjectOutputs` owns watch lifetime, two-sample stability, revision supersession, held-for-turn offers and one-use
person acceptance. Filesystem/HTTP/EDT adapters report observations and actual outcomes. The banner renders the
immutable state; it does not discover, choose, open or compose an assistant request. The starter consumes the
general first-build graph change; its separate watcher, modal and live handlers are retired. Historical fact DTOs
remain readable by the offline scorer, whose current operator inventory includes `ProjectOutputOfferAnswered`.

No stable-change fact opens anything. The person's **Open** is the explicit switch, including with file exchange off;
**Not now** consumes one revision. Different paths remain distinct. A same-path rewrite invalidates an old button
before the next revision settles. Existing parser, operation-supersession and pairing behavior remains in force.
Pending project transitions do not queue acceptance. A failed switch retains the old project/watch, but an old
deferred acceptance cannot resurrect or borrow that later operation's identity; retry requires a new button.

An already-allowed hosted Full open transports its original node-admitted generation/ticket and original watch/
project lifetime through HTTP, EDT and asynchronous loading. Only actual successful application reports completion,
with canonical-path digest and exact observed revision. A request/admission alone, failed application, external
client, unknown ticket, old watch or older revision cannot suppress a new offer. An exact successful receipt also
covers a short turn completed between polls without substituting the current ambient turn.

## Detection limits

Four-second bounded metadata observations, followed by a second matching sample at least four seconds later, are
not proof of semantic completeness or a lock against a subsequent rewrite. Ongoing growth postpones the offer;
paused writers, malformed contents and metadata-preserving rewrites remain possible. No `streamEnd` shortcut is
used. Opening retains the existing read/parse checks.

The candidate scans depth 12, at most 10,000 entries and 100 output candidates, excluding `.git`, `.claude` and
`node_modules`. GraphML is discovered under the real project root. Declared/current local-log directories
contribute candidates only inside that root. The owner explicitly approved this root-only scope on 2026-10-19;
outside-root configured/current log folders are deferred to a later explicit opt-in. Escaping symlinks, empty files
and incomplete/over-limit observations cannot confirm stability.
The first complete observation is the baseline, not an offer for every pre-existing file. A file created and
removed entirely between samples, or already present in that initial baseline, cannot be reconstructed from polling.
Correlation retains eight known turn ids and at most 800 completed-open receipts, pruning absent/superseded
revisions after complete scans. Missing/expired/over-limit proof leaves ordinary offers possible; it never invents
permission or successful application.

## Evidence at retained checkpoint `1b346e804e0f56b423650de6ec7a7ba7f3691197`

- Before implementation, `ProjectOutputsTest#normalProjectsUseTheGeneralWatch` failed its named assertion that an
  ordinary open project must request general output observations: one failure, no error or skip. The regenerated
  focused run restored it to green.
- Functional regeneration succeeded, with generated headers preserved. Ten focused suites passed 78 tests, no
  failures, errors or skips (before the latest additional API/scanner cases).
- `ProjectOutputsFrameTest`: three real-display journeys passed, no skips: ordinary project real **Open/Not now**,
  no automatic log switch, explicit pairing with file exchange off; actual hosted loopback HTTP admission → EDT
  graph application and exact-revision suppression; actual managed sample/bundle roots requesting no watch.
- Final native regression run: `ProjectOutputsFrameTest`, `StarterProposalFrameTest`, `StarterProposalVisibleFrameTest`,
  `AssistantChromeBudgetFrameTest`, `AsyncOpenInterleavingFrameTest`, `PairingDuringLoadFrameTest`,
  `SessionRecoveryFrameTest` and `AssistantScopeRaceFrameTest`: **49 cases, zero failures/errors/skips**, sequential
  with the shared display lock held. The log-acceptance witness deliberately puts a different graph on screen first,
  then proves the real Open button replaces it with the offered graph and a matching pairing.
- One native test initially compared platform temporary-path aliases rather than their canonical identity. Both log
  and graph had actually landed; correcting the fixture expectation restored green. This is **not** claimed as a
  production wrong-result witness.
- The initial 17 maintained controls each caught a named assertion RED and restored GREEN, with source and class
  trees byte-identical (12 controls in 125.7 s, five in 85.1 s). This includes real-button disconnect, premature
  stability/automatic opening, managed-root classification, pending/deferred-switch scope, stale revisions, actual
  hosted completion versus admission, EDT origin propagation, and the starter's general-change cue.
- A stronger native case reproduced a real pre-existing graph-open seam: a failed parse returned success when an
  older topology remained visible. Requiring a newly observed graph application restored the named case to GREEN;
  the previous view is retained. A premature completion-receipt control catches this distinction independently.
- The first full run found only the release-stamping test defect, independently reproduced at clean main
  `aa4b9aa87a5235c0681c929019248ee0312b4f67` with unchanged test bytecode. The coherence check now retains its
  introducing 1.36.0 release and every newer note, without weakening any wording assertion. The subsequent full run
  passed 4,327 tests, no failures/errors, 355 display skips; 612 source-mapped reports, no orphan reports.
- Final bounds review reproduced a named failure for 100 successfully opened output candidates against a
  64-receipt cache. The bounded retention now covers the supported candidate set and prunes stale revisions; all
  13 output-node tests restored GREEN. Its maintained control caught the old 64-receipt bound and restored GREEN
  byte-identically in 5.3 s: **18 controls total**, all named assertion RED, restored GREEN, source/classes identical.
- Final full Maven run: **4,328 tests, zero failures/errors, 355 display skips**, 612 source-mapped reports and no
  orphan reports. The subsequent native assertion strengthening changed only the frame test, not production code,
  and all 49 relevant display cases passed again.
- Final node regeneration succeeded and reproduced both generated Java copies and GraphML byte-identically, with
  headers preserved. Strict MkDocs and preflight passed (77 frame suites, 713 anchors).

No live CLI, model, paid provider, participant project, real Claude configuration or credential inspection was used.
Native tests use isolated DEMO projects and simulated CLI/session facts. CI and independent approval are not yet
claimed; they must be checked separately at the published PR head.

## Current-main integration and publication gates

The retained checkpoint was integrated by a normal merge, not a rebase, with main
`e03d2028a941e38c92c6ee59e71a465cad6a5550`. Both M80 discovery effects and D output effects are retained, along
with both frame-list registrations. The combined processor/GraphML were regenerated, never hand-merged. Main
independently contains the introducing-release changelog-test fix; the maintained release-boundary control was
adapted to its equivalent implementation without weakening the assertions.

- Full merged-source Maven: **4,345 tests, zero failures/errors, 357 headless display skips**; 615 source-mapped
  reports, zero orphans.
- Nine sequential native suites: the eight listed above plus `M80DesignDiscoveryFrameTest`, **51 cases, zero
  failures/errors/skips**, shared display lock held.
- All **18** maintained controls caught named assertion RED and restored GREEN in **213.0 s**, with source and
  class trees byte-identical after every control. This includes the actual Open listener, hosted HTTP-to-EDT
  origin, successful application versus admission, pending/deferred/old-watch guards and the starter cue.
- Strict MkDocs and preflight passed: **78 frame suites, 724 anchors**. Python onboarding contract: three tests
  passed. D's changelog entries remain under the new `[Unreleased]`, not 1.37.0.

These are local publication-tree gates, not an independent approval or a claim that the entire native/mutation
matrix was run locally. Exact-head CI is reported separately. B remains excluded and closed unmerged.
