# Independent re-review: M66 design render — revision 2 and the implementation checkpoint

Reviewed `232c846a` on `feat/m66-design-render` (spec revision 2 as revised in `fc9a307e`, the M49→M66
correction, the tracker's M66 section with its implementation decisions and plan), against the first review
[`review_spec_design_render_71e50ee5.md`](review_spec_design_render_71e50ee5.md) and its follow-up guidance,
and against the analyser source on `main` and the compiler's diagnostics registry. New items are G-numbered
(owner's instruction); they are independent of the Spring-authoring branch's G numbers.

**Verdict: READY FOR IMPLEMENTATION, with G2 and G3 folded into the spec before the verb lands** — they change
what "done" means for acceptance 1 — **and G4 decided by the owner.** R1–R4 are addressed as the first review's
follow-up guidance asked.

## R1–R4, checked

- **R1 (location resolution) — addressed.** D-4 is a per-kind table, offending location before referenced bean,
  every row with an *unavailable* outcome, XML and Java locations distinguished, report `sourceRoot` mapped to an
  authorised local root, all three producers named. The upstream ask is narrowed to "accurate `sourceRef` where
  the producer knows the position, explicit unavailable otherwise", and `xpathHint` is kept separate. Acceptance 5
  carries the six added cases and says it is gated. The `NODE` row (`nodeName` is the bean id under Spring
  authoring) and the `SOURCE_MEMBER` row (a Java location; `field:` / `implements:` members) match the registry.
- **R2 (relationship state) — addressed.** D-5's four states, `unverified` as the only state a loaded log can
  reach, `input-current` meaning XML-input identity only, the combined case (unchanged XML, edited Java, old log,
  failed build) spelled out per artefact, anchors stamped with the document revision and never rebound (D-3).
- **R3 (intake) — addressed.** `open.diagnostics` with shape-detected wrappers for all three files, refuse-and-
  clear on unknown schema or unreadable file, replace-not-accumulate, `discover: "diagnostics"` that lists without
  loading, works with no log open; project switch clears. Acceptance 7 covers each.
- **R4 (private reference) — addressed.** "Builds on" cites the public authoring documents, the project
  `RUNBOOK.md` and the published diagnostics contract.
- The follow-up's other asks are in: the session/glance pin (`open.design = A; source {file: B}` lights A and echoes
  it), design Follow with its own eligibility (`MainFrame.setFollowing` does require a followable store —
  confirmed at `MainFrame.java:3659`), mixed selectors refused rather than ranked, the refusal-whole rule for
  spotlights that cannot be visible together.

## New findings

### G1 — P2, process: "re-reviewed and implementation authorised" has no independent pass behind it

- Spec status line and the tracker's first plan item (`[x] Re-review revision 2`) say revision 2 was re-reviewed.
  The only review on the branch is the first reviewer's `71e50ee5` pass; the "re-review" was the author's own
  check of R1–R4. This file is the independent re-review. FIX: cite it, and keep "author-checked" and
  "independently reviewed" as different words in both places — the method note in the Spring-authoring tracker
  exists because those two claims were once made with one word.

### G2 — P2: `source` is a NEW verb, the sixteenth, and the spec does not name the inventory it joins

- There is no `source` verb today (`VerbSchemas.java` has a `source` *boolean parameter* on `topology`, which the
  skill text must not confuse with the new verb; `source_root` is the only source-related verb). M64 recorded
  `spotlight` as "the fifteenth verb — the only one added"; this is the next.
- A verb in this codebase lands in more places than `tools/list` (acceptance 1's only claim): `VerbSchemas` and
  `McpTools` (with `readOnlyHint`); the built-in assistant's manifest, which since M48.7 is held to an inventory
  of every published verb **and parameter** (`InProcessManifestNamesEveryVerbTest`, brace-group rule); the
  user-guide verb table and the spotlight-targets table in `docs/site/user-guide/assistant.md`; the vocabulary
  pointer (`SpotlightVocabulary.TEXT` must equal `SpotlightTarget.vocabulary()` — three new `source:design…`
  entries); `tools/verify-m64-spotlight.py`, which claims to cover "every family"; the canonical skills that name
  verbs (`guided-start`, `point-at-the-fault`), re-pinned and re-vendored into the playground as M64.8 was.
- FIX: add a "where the verb lands" list to D-1 and make acceptance 1 assert the manifest inventory test and the
  vocabulary equality test pass with the new family; decide whether any skill teaches `source` in this
  milestone (if none, say so, so the playground re-vendor is not owed).

### G3 — P2: the root policy widens from `.java` to XML and JSON without saying so

- `source_root` today: *"a root grants source reading of every .java file beneath it"* (the schema text, and
  what `context.sourceRoots` and the Projects page teach). D-1 reads the design (`.xml`) and result files
  (`target/*.json`) "under an authorised root" — the same word, a wider grant.
- FIX: state which roots authorise which file types (source roots for `.java` and the design? the project root
  for `target/` results?), update the `source_root` description and the docs that quote it, and say whether a
  design outside every source root but inside the project root is allowed. Acceptance 1's "refused with the root
  list" then names the right list. The M38 path-anchor rules (project-relative, never rewritten) apply to
  `open.design` as they do to `log`/`graphml` — say that too.

### G4 — P3, owner's call: the checkpoint bumps the analyser's Fluxtion dependencies as an "implementation decision"

- Tracker M66: *"Builder 1.0.71 and public runtime 1.0.16 are the selected released versions."* `main` pins
  runtime **1.0.15** and builder **1.0.68** (`pom.xml:31,35`). Nothing in the spec needs a newer runtime or
  builder — D-2 says "no Spring context, no bean instantiation, no classpath; text and an index" — and 1.0.16
  is the release after the one whose millisecond audit timestamps the record-format spec (M34.3) and its
  conformance suite were written against.
- FIX: either name the API M66 needs from 1.0.71/1.0.16, or take the bump out of M66 and give it its own line
  (with the conformance suite and the demo fixtures re-run under it). A render milestone should not carry a
  runtime bump silently.

### G5 — P3, housekeeping: the branch is five docs commits behind `main` and its tracker will not merge

- Merge-base is `6f8568e8`. Since then `main` gained the availability correction (`a3a2aa73`), the two tidies,
  the M47 reconciliation and the M49→M66 pointer (`4706dbc5`); the branch's diff against `main` therefore
  *reverts* those (it carries the old "1.0.70" CHANGELOG wording and drops the *Tidy 2026-09-19* section). A
  dry-run merge conflicts in `docs/specs/tracker.md`. FIX: rebase onto `main` before the next checkpoint; the M66
  section then sits under a head note that already points at this branch.

### G6 — P4: two names in D-2/D-4 that the registry and the XML do not use

- The bean index (D-2) and the `SPRING_SERVICE_BINDING` row (D-4) say **`serviceBindings`**; the property on
  `FluxtionSpringConfig` is **`serviceRegistrations`** (its entries are `ServiceBinding` inline beans) — the index
  would look for a property that no design contains. `…_TYPE_COLLISION` is `SPRING_EVENT_SERVICE_TYPE_COLLISION`
  in the registry; the rest of the row's codes exist as written.

### G7 — P4: D-5's hash comparison should follow the receipt as it now is

- The authoring receipt changed on 2026-09-19 (Spec 2 §5.2, response to its own review): stages carry `outputs`
  after their write and `options` for per-run overrides; the reading rule is *"compare `outputs` when present,
  otherwise `inputs`"*; hashes are `sha256:<hex>` of the file's bytes. D-5's `input-current` compares "the
  stage's `inputs.xmlHash`" to "the document revision (sha256 of the bytes)". Say `outputs`-then-`inputs`, and
  the exact string form, so the two hashers agree by construction.

## Read, not exercised

- The tracker's decisions (the session graph owns completed reads, refresh failures and clears; parsing and
  Swing are adapters; a generation token rejects late Follow results after a session switch) are consistent with
  M44.3's thread-confinement rule and M35's project boundary; nothing here contradicts a standing decision.
- No implementation exists on the branch yet; nothing was run beyond the reads. The checkpoint's own gate (1,656
  tests, 31 display skips) is the docs-only baseline.

## Housekeeping

- This file only; no spec or tracker text was edited by the reviewer. Scratch worktree under this session's
  scratchpad.
