# Independent review — analyser tool agreement

Review `origin/feat/tool-agreement`, based on `08954d43`. Use a separate worktree and a reviewer
branch; the owner's primary checkout and other sessions must remain untouched. This is an
implementation review, not permission to merge, publish docs, release, fix findings or rebase.

Read, in order:

1. `docs/ONBOARDING.md`, `CLAUDE.md`, `docs/handoff/REVIEWER-ORIENTATION.md`.
2. All of `docs/specs/spec-tool-agreement.md`, including the revised fixture identities and counting rule.
3. `docs/handoff/report_tool_agreement_2026_09_21.md` and its linked mutation witnesses.
4. `docs/specs/tracker.md` tool-agreement entries and the existing feedback items they reference.
5. The session report and **three** GraphML files in
   `docs/handoff/evidence/unguided-session-2026-09-21/`. The packet contains no audit log.
6. The branch diff: `git diff 08954d43..origin/feat/tool-agreement`.

## Scope and claimed boundary

Implemented: TA-1–4, TA-5a, TA-6–8, and TA-9's **before** case; also D14–D19 under their existing
tracker items. Analyser baseline **13 → 1 open**, upstream **8 → 8 open**. D20 remains blocked on
producer metadata. TA-5b delivery/starter vendoring and TA-5c post-shipment client spot-check remain
open. Do not count TA-5a as delivery of live follow. No new client sessions were run or authorized here.
No upstream implementation, no automatic application execution and no new verb were added.
The owner explicitly kept evidence-correctness work at P1. No release is claimed.

Graph tests use the committed GraphML. Marker tests use preserved standalone/hosted logs in the
round-2 feedback packet. Window, pending-tail, flags, source geometry and overwrite tests use labelled
constructed inputs where the packet supplies none. Never describe those as participant replay.

## What to attack

- **TA-1:** all three pairing paths share declared ids, including framework loggers. Keep the foreign
  graph negative control failing. Hiding scaffolding must change the view, never the fact.
- **TA-2:** disagreeing copies are announced without picking a winner. No-log discovery works; late
  asynchronous results cannot qualify a different graph. Truncation/scan errors stay visible.
- **TA-3:** confirmation survives save/explicit recovery and renders neutral labels in table,
  topology callout, report and actual PDF. Old flags still mean faults; changed logs withhold flags.
- **TA-4:** rolling history precedes the output window while non-time filters still apply. Test both
  resolution modes, a flip at the first in-window record and unchanged whole-log answers.
- **TA-5a:** canonical runbook/skill parity and index pins; observations are version-scoped testimony,
  not newly verified plugin behavior. Route delivery remains open.
- **TA-6/7:** a trailing prefix stays pending even after quiet; only a full separator completes it.
  Standalone `open {follow:true|false}` agrees with actual timer/toolbar state and refuses unsupported
  or mixed requests. A closed log must remain absent from context. The complete display gate found
  and corrected that last regression; headless alone missed it.
- **TA-8:** typed text/boolean expression semantics, missing values, marker refusal, hidden Project
  reveal, precise source-root refusal, PDF flag fallback, note grouping, footer overflow and legend
  reservation/export. Do not accept rectangles alone as proof of readable screenshots.
- **TA-9:** absent hierarchy metadata yields unknown; do not infer dispatch from classes or cycle
  co-occurrence. Explicit complete invocation evidence keeps its stronger absence semantics.
- **D14:** distinguish loaded size from on-disk metadata. Overwrite log, GraphML, receipt and source;
  no silent reopen. Same-path explicit reopen works. Producer checks expire and say as of intake.
  Attack races at load/publication and Follow; inspect cost of checking source metadata on context.
  `unchanged-metadata` is deliberately NOT content verification; recovery's full hashes are unchanged.
  Ensure qualifications survive Project/Source views and PDF export. Combined opens remain pending
  until their log loads, rather than echoing the previous pair as the new one.
- **D15:** real source scrolling, narrow viewports, an oversized set, add/departure accounting,
  survivor numbering and settled echo/paint equality. Bean and line anchors share geometry. Inspect
  queued scroll cancellation and a later resize. Positive bounds alone are not enough.
- **D16:** preserved logs produce 5 buys / 6 sells / 8 bare-price markers. New MCP conditions default
  STRICT; existing persisted definitions without a mode retain LOCF. Explicit LOCF remains available
  and labelled as carried state. Inspect config/share round trips, y resolution and record anchors.
- **D17–19:** saved pin on a different time range explains emptiness without silently clearing it;
  pending/failed extraction cannot publish old counts as current. Separate y scales survive pin,
  filter, refresh and restoration. Show all exits nested focus, even with scaffolding in one call.

Repeat a recorded mutation per TA code item and the D14–D19 fixes. Witness JSON names the changed
behavior and exact failing test. A failed compilation is not a witness. Confirm the named assertion
fails, restore your disposable worktree, and run green again. For TA-5a, test parity and inspect pins.
Do not modify historical evidence to get green.

## Gates to run yourself

Use JDK 21. Do not run concurrent Maven processes in one checkout.

```sh
mvn -q clean test
mvn -q '-Dtest=PairingDuringLoadFrameTest,AsyncOpenInterleavingFrameTest,SpotlightFrameTest,WestColumnStartsCollapsedFrameTest,MenuScreenshotFrameTest,PersonAtTheScreenFrameTest,NamedGraphAndMenuSpotlightFrameTest,SessionRecoveryFrameTest,TemplateCatalogueFrameTest,DesignSpotlightFrameTest,LoadedFileObservationFrameTest' -Djava.awt.headless=false '-DargLine=-Djava.awt.headless=false' test
mvn -q package -DskipTests
python3 tools/test_tools.py
python3 tools/verify-m64-spotlight.py
mkdocs build --strict
git diff --check
```

On Linux use `xvfb-run -a` for display commands. Fail if any display test skips. The branch adds the
new frame tests to the CI display job; a branch push alone is not evidence that CI ran.
Use CLAUDE rule 1's exact tracked-file sweep and check untracked review files too; read screenshots
visually because the sweep cannot inspect PNG text. Check repo-local author email before committing.
Restore generated outputs such as `dependency-reduced-pom.xml` when they are not review findings.

The report/assets README records capture commands and scope. Historical vendor/owner-witnessed web
images are evidence, not pictures to rewrite. Do not touch `docs/handoff/evidence/sg1-release-2026-09-21/`.
Do not start an application-generation or paid client trial to fill a fixture-test gap.

## Concurrent main / integration is a separate check

At final fetch `origin/main` was `9c10de82`; it has advanced from this branch's base with docs,
tracker, positioning, beta protocol and skill-index changes. Those commits were not merged into
this feature branch. In particular, read its `docs/proposals/mongoose-audit-format/README.md`:
new owner decisions and future text delivery affect the eventual TA-5 route; later endpoint evidence
also narrows upstream diagnoses. Do not equate this branch's captured 1.0.43 testimony with a fresh
endpoint experiment. Main's point-at-the-fault skill/index changes must survive integration alongside
this branch's Mongoose skill pin. Flag conflicts and stale prospective decisions before merge.
No combined-main gate is claimed. Review on the recorded feature head; any integration rehearsal
must be on a disposable branch and must not touch main or the feature branch.

## Deliverable

Write `docs/handoff/review_tool_agreement_2026_09_21_<reviewer>.md` on your own review branch.
Include reviewed SHAs, verdict per area and for the branch, numbered findings with severity,
file:line, reproduction/expected/actual, and verified versus read versus unverified. Judge baseline
closures independently and report both counts. Separate merge readiness from completion of the
producer-blocked spec. Record gate totals and exact mutation failures, not just “green/red”.
Commit and push only your review file, then give the owner the branch/commit/report path and a short
handoff to the author. Do not fix findings, merge, publish, amend, rebase or force-push.
