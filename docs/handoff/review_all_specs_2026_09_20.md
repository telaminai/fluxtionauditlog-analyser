# Independent design review — both starter-journey specifications, consolidated

Date: 2026-09-20 · Reviewer: the session that wrote the
[first review](review_project_starter_journey_2026_09_20.md) and its two re-reviews · Scope: the **current
working tree**, tracked and untracked.

This is the consolidated full review requested, superseding nothing: the
[first re-review](rereview_project_starter_journey_2026_09_20.md) raised G1–G3, the
[second](rereview2_project_starter_journey_2026_09_20.md) first covered “Learning at the point of use”, and
this file is the single document with verdicts per spec, G1–G3 dispositions, the complete H series and the
cross-repo prerequisites. H1–H3 keep the numbers they were given; H4–H6 are new here.

Nothing was committed or pushed, no branch was switched, no reviewed spec or earlier review was edited, and
the owner's staged application was not touched. No key was spent and no paid compilation was run.

## Inspected heads and working-tree state

| Repository | Head | Working tree |
|---|---|---|
| `fluxtionauditlog-analyser` (reviewed) | `703136e` | 27 changes, of which the two specs, `tracker.md` and the handoff documents are this review's subject; the rehearsal launcher under `tools/`, `CHANGELOG.md` and the Mermaid fix on the Spring conversations page are unrelated and were not judged |
| `fluxtion-web` | `e8e70f0` | 2 (a vitest results cache and `.svelte-kit/`; no source change) |
| `fluxtion-compiler` main checkout | `aec606ec` | read only |
| `fluxtion-compiler` review worktree | `fdd24d61` | clean; used for the H1 reproduction, probe removed afterwards |
| `mongoose/mongoose` | `17a03b4` | clean |
| `mongoose/mongoose-plugins` | `3f5fd03` | 1 unrelated change |

---

## Verdicts

| Subject | Verdict |
|---|---|
| `docs/specs/spec-template-from-analyser.md` (revised) | **READY FOR IMPLEMENTATION**, provided its own sequencing is honoured — the picker expansion must not ship before journey slice 2 supplies profiles and bootstrap. No finding stands against it. |
| `docs/specs/spec-project-starter-journey.md` (new) | **CONDITIONAL** on H1–H6. The architecture, ownership split and owner decisions are sound; six corrections are needed, one from a defect reproduced here. |
| Combined implementation handoff | **CONDITIONAL.** Slice 1 can start today. Slice 2 is blocked by H4 and H6, the analyser slices by nothing in this review, and the learning-route slices by H1–H3 and H5. |

All six settled owner decisions are preserved by both specs as written: old links gain support when the
setting is absent, explicit false stays off, one restore offer covers reopen and relaunch, the full catalogue
shows with onboarding entries recommended, guided means a runbook walkthrough, and application execution stays
outside the analyser. I found no text that erodes any of them.

**No dependency on unapproved proposals.** Neither spec mentions saved spotlight captions, a two-run
comparison or a guide MCP verb, except to refuse the verb explicitly (journey `:136-140`). Verified by
searching both specs for each term. Scope has not quietly expanded into the tracker's open proposals.

---

## G1–G3 dispositions

**G1 — absent query parameter means no override: CLOSED.** Journey `:160-167` now confines default-true to
decoding and preserves a template's explicit false when the override is omitted. This matches the parse path:
builtin templates run through the same zod schema as shared links (`builtin-templates.ts:18,39-43`), so the
field is always populated before the route sees it. Acceptance 1 `:288-294` adds the four-case matrix and
requires the resulting ZIPs to be checked, not just the parsed flag.

**G2 — one owner per path, duplicates fail before ZIP assembly: CLOSED.** Delivery step 2 `:261-265` requires
ownership to be chosen before emission, never by list order, and a uniqueness assertion at the end of
`generate()` that names the duplicate and fails even when contents match. That is the right target: `zip.ts`
appends bundle files to the base list (`:14-30`) and keys entries by path (`:33-43`), so today a repeat is
silently last-wins. New acceptance 9 `:318-320` injects duplicate profile and agent-guide paths.

**G3 — panel reveal-only, landing offers actions: CLOSED.** Journey `:200-206` splits the surfaces
explicitly: the Project panel states the saved-chart facts and gains no control or mutation callback,
`StartPanel` offers open and restore through existing machinery, `ProjectPanel.Navigator` is unchanged, and
`ProjectPanelIsRevealOnlyTest` is named as the gate for both D-L3 and D-L1. Acceptance 7 `:311-314` exercises
the landing actions and keeps the panel test unchanged. I ran that test: 2 tests, passing.

---

## Findings

Severity: **Major** blocks a slice; **Moderate** causes rework or a wrong surface if unresolved.

### H1 — Major. A generated comment outlives the decision it explains and then contradicts the code

`docs/specs/spec-project-starter-journey.md:114-121` asks for comments at generated decision points while
requiring reconciliation's ownership, hash and no-op guarantees to be retained.

**Reproduced** on a disposable fixture in the compiler review worktree with the current reconciler. A comment
placed between the `@Generated` marker and an owned annotation:

```
@javax.annotation.processing.Generated("fluxtion-starter")
// Choose DATA when this reference must not trigger; see the contract.
@com.telamin.fluxtion.runtime.annotations.NoTriggerReference
private final Parent parent;
```

Changing the XML binding from DATA to TRIGGER and reconciling gave: no conflict, annotation correctly
removed, developer body intact, and the comment still present word for word — now telling the reader this
reference must not trigger, on a field that triggers. I also confirmed the field's JavaParser token range
**includes** the interleaved line comment, so the comment sits inside the exact-text region the
annotation-replacement path matches on and requires to be unique across the file.

Failure scenario: a developer changes a binding in the XML, reconciles successfully, and ships source whose
tool-written comment states the opposite of the tool-written annotation beside it. Nothing warns, because
hashes ignore comments and the run is a clean success. This is worse than no comment, because the section's
whole premise is that comments are the channel people actually read.

Required correction: generated comments must be **choice-neutral** — explain the construct and link the
owning contract, never state which alternative is currently in force. "This reference's propagation mode is
declared in the XML; see <contract>" survives every mode change; naming DATA does not. Add the
changed-declaration case to acceptance 11, which today exercises implementation and a no-op run but never a
changed choice.

### H2 — Moderate. The reference-version rule has a case nobody owns

Journey `:130-134` requires generated comments and runbooks to identify the tool or template version they
describe, and says a live URL is not proof it describes the downloaded tool.

The analyser's fallback writer cannot satisfy it. `ReferenceSet.Resource`
(`src/main/java/telamin/fluxtion/audit/analyser/analyser/config/ReferenceSet.java:47`) carries
`id, url, why, status, appliesTo, note` — no version, revision or compatibility field — and the class is
deliberately constrained to "writes references, never content" (`:34-36`). It stays in scope by the spec's
own text at `:69-72`, as the fallback for legacy and profile-less projects, which the picker expansion makes
a common path.

Failure scenario: acceptance 10's documentation check ("unavailable or incompatible references fail") is
written, run against a project that took the fallback path, and cannot evaluate the rule because no version
is recorded — so either the check is quietly skipped for that path or it fails with nothing to fix.

Required correction: either exempt the fallback writer in the spec, stating its block is version-neutral
pointers only, or add a version field to `reference-set.json` and render it. The playground half already has
the machinery to copy — `x-analyser-min-version` enforced at bundle time (`bundle.ts:477-486`, applied at
`:574`) plus `skills.provenance` in the profile — and the spec should say "extend this" rather than read as
though the mechanism is new.

### H3 — Moderate. Acceptance 12 can reach a conclusion from a single session

Acceptance 12 `:329-334` is a strong protocol: frozen predictions, links followed, hazards found before the
first affected edit, unused guidance, every owner intervention recorded, assisted runs recorded as assisted,
a held-out task, and "one successful session is not cross-model validation".

What it never states is how much evidence adopts or drops a routing change. This repo already has the rule:
`docs/ONBOARDING.md:238` — "**A finding counts when it RECURS.** … one agent hitting something once is noise,
the same friction across two different tasks is a defect" — with the companion obligations to rotate the
task, hold one out, and record what went unused. The section's own evidence is explicitly one session
(`:100-103`).

Failure scenario: one fresh-client run leaves a guidance file unvisited, it is deleted as unused, and the
next task needed it — the loop that "only adds" inverted into one that only removes.

Required correction: name the threshold. Two independent sessions on different tasks, or the held-out task
specifically, before a routing change is adopted or guidance is deleted.

### H4 — Moderate. Generated-comment wording has two emitters and no named owner

Journey `:114-116` requires that "both browser-generated and locally reconciled members need consistent
guidance for the same construct" but never names who owns the wording.

Both emitters exist and produce the same constructs. The browser writes stubs through the playground's
authoring generator; the Java reconciler writes fresh members itself — visible in my H1 probe output, where
the reconciler emitted `@OnTrigger public boolean onChild() { return true; }` into the class. This is the
same shape as F10, where two repositories wrote `CLAUDE.md` and the collision was avoided only by an
existing guard.

Failure scenario: the contract changes, one repository updates its comment text, the other does not, and the
same construct carries two different explanations depending on whether it came from the browser or from
reconciliation — with no test comparing them, because the hashes deliberately ignore comments.

Required correction: name one owner for the comment text per construct, rendered from a single source in the
repository that owns the contract, and add an equivalence assertion to acceptance 11 so browser-emitted and
reconciler-emitted members carry identical guidance for the same construct.

### H5 — Moderate. Acceptance 12 lets non-regression stand in for report correctness

Acceptance 12 `:331-333` asks the held-out task to "independently check its new outputs as well as existing
behaviour". Read literally, a run passes when existing observations are unchanged and the new outputs exist.

The staged-feedback review's eighth addendum rules that out by name
(`docs/handoff/review_staged_spring_feedback_2026_09_19.md:563-568`): unchanged desk comparisons support
"non-regression for those observations on that feed, not a general proof of inertness or correct reporting",
"report arithmetic, grouping and boundaries largely untested", and the participant's discovered zero
accepted-order count was caught by output plausibility rather than by the automated checks. The same addendum
`:570-577` already specifies the shape of the right experiment — per-book and per-symbol expectations,
boundary cases, isolated injected errors each failing a named assertion, and the existing comparison re-run
separately as non-regression.

Failure scenario: the held-out run reports "existing behaviour unchanged, new report produced", the report's
grouping is wrong, and acceptance 12 records a pass.

Required correction: split the check. The new report's own arithmetic, grouping and boundaries are checked
against an independent expectation with named assertions; the unchanged-behaviour comparison is recorded
separately as non-regression and explicitly does not establish report correctness.

### H6 — Moderate. The Mongoose runbook requirement drops input ordering and reset/replay, which the earlier review accepted

The Mongoose section `:81-96` covers the foreground launcher, feed setup, graceful shutdown, deployment
targets, registry-backed recipes and capture backends. It contains **no** mention of input ordering, reset or
replay — verified by searching the whole spec for each term, which returns nothing.

The staged-feedback review accepted exactly that work
(`review_staged_spring_feedback_2026_09_19.md:587-589`): "Hosted replay/audit/sink defaults need explicit
capability, storage and ordering contracts rather than blanket activation. Document input ordering and
reset/replay scope in the Mongoose runbook without promising cross-feed determinism."

Checked against mongoose `17a03b4`, where both are real and both are narrower than the words suggest:
`InMemoryEventSource.cacheEventLog` defaults to **false** and its replay is pre-start dispatch of a cached
log to a late subscriber (`InMemoryEventSource.java:22,33,44-57`, `EventToQueuePublisher.dispatchCachedEventLog`
at `:170`), not re-running a recorded session. `AuditCaptureConfig` defaults `enabled=false` with
`rollSize`, `retainHours`, `directory` and an `autoStart` list (`:49-87`) — the storage parameters a runbook
has to state.

Failure scenario: a runbook says "replay the feed" and a reader expects session re-execution, or says
"audit is captured" without the retention and roll settings that decide whether yesterday's evidence still
exists. Both are exactly the "blanket activation" the addendum refused.

Required correction: add ordering and reset/replay scope to the Mongoose section, with the opt-in default,
the pre-start meaning of replay, the capture storage parameters, and an explicit refusal to promise
cross-feed determinism. Extend acceptance 8 to assert the runbook states them.

---

## Implementation prerequisites and cross-repo delivery order

Decisions the specs themselves leave open, each blocking a named slice:

1. **The additive metadata schema** for declared-but-not-generated, runtime-without-a-fixed-type and
   unspecified processors (`:50-54`) — must be pinned before either producer or consumer work. Blocks slices 2
   and the analyser's profile-reading work.
2. **Registry-backed recipe prerequisites**: the published plugin coordinates and version floor (`:90-92`).
   Blocks the Mongoose runbook slice, alongside H6.
3. **Diagnostic message selection** from reproduced failures before implementation (`:127-128`). Blocks the
   diagnostics slice.
4. **Authoring-page layout and navigation** (`:179-180`). Blocks slice 6 only.
5. **Vendor template id and coordinate**, with M67 coordination and the feedback-29 shadowing fix first
   (`:279-284`). Blocks acceptance 2's fourth journey.

Order, with what each depends on:

1. **Playground — `analyserSupport`, its default and the query override**, with captured legacy-link
   fixtures. Unblocked; start here.
2. **Playground — factor the emitters out of the bundle**, one owner per path with the uniqueness assertion.
   Needs prerequisite 1 and findings **H4** and **H6** resolved first.
3. **Playground — headless extension and the empty-directory recipe** in `web/static/CLAUDE.md`, linked from
   the golden path. Depends on step 1.
4. **Analyser — `context.savedGraphs`, then the project landing, then project-scoped session state and the
   unified restore offer** including replacing the implicit opens in `Main.main` and `reopenLastGraphml`.
   Independent of the playground once prerequisite 1 is pinned; no finding blocks it.
5. **Compiler/starter and playground — comments and diagnostics.** Needs **H1**, **H2** and **H4**.
6. **Analyser — the full-catalogue picker** with `agentBootstrap` and `keyNeed` disclosure. After step 2, so
   the discovery fallback is the exception rather than thirteen cases in fourteen.
7. **Optional authoring section**, then the witnessed acceptance runs. Needs **H3** and **H5** before the
   fresh-client result is allowed to license anything.

---

## What I executed, what I read, what remains unverified

**Executed**

- `mvn -q -o -Dtest=SpecLinksResolveTest,ProjectPanelIsRevealOnlyTest test` → **5 tests, 0 failures, 0 errors,
  0 skipped**. `SpecLinksResolveTest` walks `docs/specs` (`:62`), so the untracked new spec is covered and its
  links resolve.
- `git diff --check` → clean, exit 0.
- CLAUDE.md rule-1 sweep, both halves: the tracked form prints nothing beyond the two files that state the
  rule, and the same sweep over **untracked** files — the new spec and every handoff document, this one
  included — prints nothing.
- The H1 reproduction in the disposable compiler worktree; probe deleted, worktree clean at `fdd24d61`.

**Read in source, not merely in documentation**

`zip.ts:14-43`, `builtin-templates.ts:18,39-43`, `bundle.ts:110,374-393,477-486,574,578-603,770-773`,
`validate.ts:63-76`, `mongoose.ts:199-213,317-325,646`, `scaffold/+server.ts:24-79`, all fourteen
`*.starter.json` files and `index.json` on playground `origin/main`; `Reconciler.java:964-980,1145-1163,
1680-1687`, `authoring.test.ts:72-84`; `MainFrame.java:913-916,4336,4346,4387-4402,5660,5751,5820,5872,5912`,
`Main.java:76-89`, `AppConfig.java:13-15,82,112`, `ProjectProfile.java:188-236`, `ReferenceSet.java:25-47`,
`TemplateCatalogue.java:18-39,96-105`, `TemplateProjectDialog.java:268-273`, `StartPanel.java`,
`ProjectPanelIsRevealOnlyTest`; mongoose `InMemoryEventSource.java`, `EventToQueuePublisher.java`,
`AuditCaptureConfig.java`. The staged-feedback review was read through its eighth addendum, and its
participant claims are treated as claims — the denominators it records (19 records, 784 values, 15/15
mutants, 15/17 vendor predictions) are the participant's, not independently reproduced here, which that
document states itself at `:550-553`.

**Not verified**

- Any UI path. No analyser was started; H-findings about the landing, restore and picker rest on source
  reading, and acceptance 6's witnessed run has not happened.
- Template generation for the fourteen catalogue entries. The per-template facts come from the starter specs
  and the generator's gates rather than from generated ZIPs.
- Anything requiring network, publication, a compiler key or a fresh-client session.
- The mongoose replay and capture behaviour at runtime. I read the configuration defaults and dispatch path;
  I did not run a server.

## Housekeeping

This file is the only addition. Both specs, the tracker, the earlier reviews and the response are exactly as
I found them, as is the unrelated working-tree work. Scratch material is under this session's scratchpad and
in a disposable worktree.
