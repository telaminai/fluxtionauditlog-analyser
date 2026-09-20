# Re-review — project starter journey, after the author response

Date: 2026-09-20 · Reviewer: the session that wrote
[the first review](review_project_starter_journey_2026_09_20.md) · Reviewed:
[the author response](response_project_starter_journey_2026_09_20.md) and the revised working-tree specs
(`docs/specs/spec-project-starter-journey.md`, the 2026-09-20 revision of
`docs/specs/spec-template-from-analyser.md`, and the new `docs/specs/tracker.md` entries).

Nothing was committed, pushed or implemented. The response file and both specs were read, not edited. New
findings are numbered **G1–G3** so the F series is not reused.

---

## Verdict: **CONDITIONAL**, and a much narrower one

Fourteen of sixteen findings are answered in spec text rather than in promises, and the two remaining are
places where **I overstated the evidence and the response is right** — see the concessions below. All eight
owner decisions I asked for are now settled and internally consistent. The delivery order in the revised spec
is the right one and is stricter than mine in the place that matters: the picker expansion is explicitly
released after default-on downloads carry their profiles.

The verdict stays CONDITIONAL for three new defects introduced by the revision itself (G1–G3). None needs an
owner decision, none changes the design, and each is a sentence. With those three corrected, this is ready
for an implementation handoff.

One thing improved on my own work: the response located `reopenLastGraphml`
(`MainFrame.java:5912`, called unconditionally from `Main.java:89`), where F8 had only cited the config field
and its comment. I confirmed it runs even when a log path is passed on the command line, so launching with an
explicit file silently restores an unrelated remembered topology. The revised spec's sentence covering exactly
that case is the better finding.

---

## Concessions — two places where my review overstated

**F4 was wrong on one word.** I wrote that the token route is one "the endpoint documents as unusable" for
non-browser clients. The header at `scaffold/+server.ts:10-12` says no client *should have to* reimplement
lz-string, not that it cannot. The route is technically callable headlessly. The gap I was pointing at
survives — an ordinary template download had no way to express the choice — and the response's narrowing is
the accurate version.

**F6 overstated the capability gap.** I said a graceful stop exists only in the bundle path. Verified against
the response's claim: `mongoose.ts:317-325` installs an unconditional `mongoose-shutdown` hook in
`MongooseMain`, emitted for every Mongoose project, and its comment records why (M19 P3 live run). So
foreground Ctrl-C **is** a supported, tested stop for ordinary hosts. What remains true, and is what the
revised spec now says, is narrower: `stop-server.sh` and `export-audit.sh` come only from
`bundle.ts:374-393` and depend on the registry helper, and audit capture requires the `perfMonitor` service
with `auditCapture` set (`mongoose.ts:199-213`), which bundle mode forces on. "Document the tested route, emit
registry-backed recipes only with their prerequisites" is the correct disposition, and it is better than the
generalisation I proposed.

Neither concession removes work; both change what the runbook may claim.

---

## Finding-by-finding

Closed in spec text, verified against source where the disposition made a factual claim:

| # | Closed by | Checked |
|---|---|---|
| F1 | Template spec's "Review correction F1" block and delivery order step 5 | Both branches now named; expansion sequenced after profiles exist |
| F2 | D-1 now requires rendering `agentBootstrap` present/empty/absent; tracker M19.23 corrected to producer LANDED / consumer OPEN | The field is on playground `origin/main`, declared only by `analyser-bundle` |
| F3 | D-1 and D-2 now require **build key: not declared** for absence, and unrecognised values reported as such | No inference from `mode` retained, which was the part worth keeping |
| F5 | Owner decision recorded; explicit false preserved end to end; the `version` field explicitly ruled out as a schema version | `version` is indeed the Maven project version in the schema |
| F7 | `context.savedGraphs` above the no-filter return, distinct from `context.graphs`, with three named docs pages | `AppConfig.savedGraphs:82` is cleared by `ProjectProfile.clearProjectScoped`, so it is genuinely project-scoped and the source is sound; all three docs pages exist |
| F8 | Owner decision; `Main.main` / `reopenLastGraphml` replaced in the same slice; legacy globals presented as unassigned | Both call sites confirmed; the CLI-argument case is correctly carved out |
| F9 | User-local, versioned, keyed by canonical profile location, excluded from portable outputs | Matches the profile's stated contract |
| F10 | Generator owns new-project bootstrap; analyser writer stays a fallback with its `ALREADY_EXISTS` guard | The guard is the tested one |
| F11 | Three states distinguished, never fabricate an FQCN, metadata schema pinned before implementation | Correct, and the schema-first ordering is the right call |
| F12 | `syncRecordsCard` named as the single decision site with three states | Matches the code |
| F13 | Acceptance 6 now names quit/relaunch, A→B, failed activation and legacy globals | |
| F14 | StartPanel section, shared selection/download controller, no new window | |
| F15 | New vendor catalogue entry, coordinated with M67, publish only after the artifact resolves | M67 is tracked |
| F16 | Playground `web/static/CLAUDE.md` owns the empty-directory recipe, linked from the golden path | Both files exist and are silent on this today |

Acceptance 1, 6, 7 and 8 are now falsifiable, which they were not before. Acceptance 8's separation of
graceful foreground stop from registry-backed stop is the concession above turned into a test.

---

## New findings

### G1 — Moderate. The headless override states the wrong precedence and would overwrite a template's own value

`docs/specs/spec-project-starter-journey.md:103-105`: "`analyserSupport=true|false`, applied before normal
validation/generation; absent means true."

Verified: builtin templates are parsed with the **same** zod schema as shared links —
`builtin-templates.ts:39-43` calls `tryImportSpecJson`, and the file's own comment at `:18` says "same parse,
same zod migration, same result". Once the field defaults to true at decode, `spec.analyserSupport` is always
a boolean by the time the route sees it. A rule that says an absent query parameter *means true* therefore
overwrites a template whose stored spec says `false`, which contradicts "explicit false stays false through
validation, share/export, import and generation" at `:31-32`.

No catalogue entry declares the field today, so this is latent rather than live — but the first template that
wants support off would be silently flipped on, and the two sentences in the same spec would each look
correct in isolation.

Correction: absent query parameter means **no override**; the default lives only in the decoder. One clause.

### G2 — Moderate, and silent. Factoring the emitters out of the bundle can produce duplicate ZIP paths that no test catches

`docs/specs/spec-project-starter-journey.md:75` requires factoring the profile, bootstrap and runbook emitters
out of the special bundle path; `:36` requires that an old `analyserBundle` still generates its existing
example. The spec does not say which emitter owns the shared filenames when both apply.

Verified in the generator: `zip.ts:14-30` builds the base files and then **appends** `bundleFiles(spec)` for
an analyser-bundle spec, and `buildStarterZip` (`:33-43`) collects them into an object keyed by
`${root}/${f.path}`. A repeated path is therefore **last-wins, silently** — there is no dedupe anywhere in
`zip.ts` or `bundle.ts`, and the existing guards (`assertNoBundleMarkers`, `assertGeneratedArtifacts`,
`assertNoRestrictedNotices`) inspect content, not duplication.

Consequence: if the general emitter and the bundle both write `.analyser/project.fluxtion-settings` or
`CLAUDE.md`, the ZIP keeps one of them according to list order, with no error and no failing test. The bundle's
profile is pinned by contract `m19-bundle/3`; losing it to a generic one, or the reverse, is exactly the kind
of regression this factoring is supposed to avoid.

Correction: state that a path has one owner — for a bundle spec the bundle's emitter wins and the general one
is skipped — and add a duplicate-path assertion in `generate()` so the rule is enforced rather than
remembered. The assertion is three lines and belongs in delivery-order step 2.

### G3 — Moderate. "Project-panel/landing row" conflates two surfaces, and one of them cannot carry the action

`docs/specs/spec-project-starter-journey.md:141` asks for the saved-definition facts in a "**Saved charts**
Project-panel/landing row"; `:184` requires the landing to "Offer explicit open/restore actions with freshness
and failure information". Read together, an implementer may put an open-or-restore control on the Project
panel.

Verified: `ProjectPanelIsRevealOnlyTest` enforces D-L3 structurally — the panel's bytecode must never name
`MainFrame`, and its only exit is `ProjectPanel.Navigator`, whose method set is asserted with the message
"adding a method here is a spec change (D-L3)". A restore action on the panel fails that test, and it also
cuts against D-AI1, *the panel STATES; the menu ACTS*. I ran the test on the current tree: 2 tests, passing.

The same test's second half is good news for F7 and worth naming in the spec: D-L1 requires every dotted key
`ProjectModel` reads to appear as a `put("leaf"` in `context()`, checked as source text. So adding
`context.savedGraphs` plus a panel row is exactly the shape the test already polices, and the spec's
"Swing reads these facts rather than constructing a second model" is the same rule restated.

Correction: split the sentence — the Project panel **states** the saved-chart facts; the landing (StartPanel)
**offers** open and restore; mutation stays where it already lives. Cite `ProjectPanelIsRevealOnlyTest` as the
gate so the constraint is met by design rather than discovered by a red test.

---

## Verified versus read

**Ran or read the source behind the claim**

- `mvn -o -Dtest=SpecLinksResolveTest,ProjectPanelIsRevealOnlyTest,TemplateCatalogueTest,ProjectProfileTest
  test` → **31 tests, 0 failures, 0 errors, 0 skipped**. This confirms the response's 3-test link result and
  adds the structural panel test that G3 depends on.
- Rule-1 sweep: the tracked-file form prints nothing beyond the two files that state the rule, and the two
  new documents plus the revised template spec are clean.
- `mongoose.ts:317-325` shutdown hook, `mongoose.ts:199-213` audit-capture gating, `bundle.ts:374-393`
  lifecycle scripts — the basis for the F6 concession.
- `builtin-templates.ts:18,39-43` shared parse path — the basis for G1.
- `zip.ts:14-43` append-then-key-by-path — the basis for G2.
- `ProjectProfile.restore` / `clearProjectScoped:212-236` — confirms `savedGraphs` is project-scoped, so the
  F7 disposition rests on a sound source.
- `Main.java:76-89` and `MainFrame.reopenLastGraphml:5912` — confirms the F8 slice is correctly scoped and
  that the GraphML restore runs even with a command-line log.
- The three docs pages named in the F7 disposition all exist.

**Read, not re-run**

- The earlier close/reopen evidence packet, unchanged since my first review.
- The tracker's wider Spring-authoring entries, for context only.

**Not run**

- Any UI path, any network fetch, any generation of the fourteen templates, anything requiring publication or
  a key. G1–G3 rest on source reading, not on a witnessed run.

---

## What is still owed before this is finished work

Unchanged by this revision, and the specs say so themselves: the restore offer, the project landing, the
saved-definition context key, the headless parameter, the factored emitters and the vendor catalogue entry are
all unimplemented. Acceptance 6's witnessed run — including quit and relaunch with an active project — has not
happened. No owner decision is outstanding.

## Housekeeping

This file is the only thing added. The response, both specs, the tracker and the rest of the working tree are
exactly as I found them. Scratch work is under this session's scratchpad and is disposable.
