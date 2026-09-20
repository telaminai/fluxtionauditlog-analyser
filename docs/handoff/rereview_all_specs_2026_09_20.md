# Re-review — corrected starter-journey specifications

Date: 2026-09-20 · Reviewer: the session that wrote the
[first review](review_project_starter_journey_2026_09_20.md), its two re-reviews and the
[consolidated review](review_all_specs_2026_09_20.md) · Scope: the **current working tree**, tracked and
untracked.

Judged against the **current** text of both specifications. Where the consolidated review quoted wording that
has since been corrected, the correction is what I assessed; the historical quotations stand as the record of
what was found, not as a claim about today's requirements.

Nothing was committed or pushed, no branch switched, no spec, earlier review or response edited, and the
staged application untouched. No key, no paid compilation, no publication.

## Inspected heads

| Repository | Head | Tree |
|---|---|---|
| `fluxtionauditlog-analyser` (reviewed) | `703136e` | the two specs, `tracker.md` and the handoff documents are the subject; `tools/`, `CHANGELOG.md` and the Mermaid fix are unrelated and unjudged |
| `fluxtion-web` | `e8e70f0` | 2 unrelated (vitest cache, `.svelte-kit/`) |
| `fluxtion-compiler` main checkout | `aec606ec` | read only |
| `mongoose/mongoose` | `17a03b4`, POM version **1.0.30-SNAPSHOT** | clean — this matters for J2 |
| `mongoose/mongoose-plugins` | `3f5fd03` | 1 unrelated |

---

## Verdicts

| Subject | Verdict |
|---|---|
| `docs/specs/spec-template-from-analyser.md` | **READY FOR IMPLEMENTATION**, unchanged since the consolidated review and unaffected by the H corrections. No F or G finding regressed. |
| `docs/specs/spec-project-starter-journey.md` | **CONDITIONAL** on J1 and J2. H1–H6 are all closed. The two conditions are narrower than anything before: one cross-repo mechanism decision and one version stamp. |
| Combined implementation handoff | **CONDITIONAL**, and materially unblocked: slices 1, 3 and 4 can start today, slice 2 after J2's one-line correction, slice 5 after J1 is decided. |

All six owner decisions survive intact: support defaults on including old links with explicit false staying
off, reopen and relaunch share one explicit restore offer, the full catalogue shows with recommendations
rather than a filter, guided means a runbook walkthrough, and the analyser renders evidence and stores
pointers while execution stays with external tools. No guide verb is approved — the refusal at `:183-187` is
unchanged. Neither spec references saved spotlight captions or a two-run comparison, so no tracker proposal
has been promoted to a contract.

**No scope inflation that I can see.** H6 is the largest addition and it replaces vague words ("feed, audit")
with specific, testable statements bounded by "only available operations are promised" and "If no
reset/rewind operation is supported, say so; do not invent one". Acceptance grew in proportion.

---

## H1–H6 dispositions

**H1 — choice-neutral generated comments: CLOSED.** `:145-159` now requires comments to explain the construct
and link the contract, forbids stating which alternative is in force, gives a worked example, and states that
reconciliation owns annotations rather than prose so a preserved comment must stay accurate after a
declaration changes. Acceptance 11 `:383-385` runs DATA→TRIGGER→DATA on a commented reference and requires
comments accurate in **both** states. The spec also records the point my reproduction actually established:
`:158-159`, "Hash equivalence checks content ownership, not whether a comment tells the truth." That is the
defect stated as a rule.

**H2 — version-neutral fallback exception: CLOSED.** `:176-181` grants the analyser's reference-guide writer
an explicit, narrow exemption: discovery pointers only, a stated disclosure that compatibility is unverified,
no claim of a matched tool version, no new `ReferenceSet.Resource` field, no-overwrite guard retained, and no
spillover to generated runbooks, comments or bundled snapshots. Acceptance 10 `:379-380` checks it separately.
Verified implementable without the field: `ReferenceSet.markdown(String)` already emits framing prose around
the pointer list (`ReferenceSet.java:137-145`), so the disclosure sentence fits the existing shape.

**H3 — recurrence and evidence before removing guidance: CLOSED.** Acceptance 12 `:404-413` sets the
threshold at two independent sessions on different applicable tasks, then a trialled correction with
before/after evidence, then an unassisted held-out pass; a failed or assisted held-out run leaves adoption
open, and a single observation stays a hypothesis. Removal of guidance additionally requires non-use across
both sessions and the held-out run, a recorded check that no unique prerequisite or hazard is lost, and an
archive. It matches `docs/ONBOARDING.md:238-240` and adds the carve-out I would have asked for: an
independently reproduced broken link or wrong instruction can be fixed as a factual defect without claiming
usability recurrence.

**H4 — one canonical comment owner with cross-emitter comparison: CLOSED**, with J1 attached to the mechanism
rather than to the requirement. `:150-152` names the compiler/starter contract as canonical owner and the
playground as a revision-pinned consumer with parity fixtures. Acceptance 11 `:386-388` is exactly the check
asked for: compare emitted guidance for the same construct and contract revision directly, allowing only
indentation and line-ending differences, then mutate one emitter's wording and require the parity assertion
to fail **even though ownership hashes remain equal**. That last clause is what makes it a real test, because
both hashers deliberately skip comment tokens.

**H5 — report correctness separated from non-regression: CLOSED.** Acceptance 12 `:394-402` splits the
held-out task into (a) report correctness against independently derived per-book and per-symbol expectations
from the feed, with named assertions for arithmetic, grouping, accepted-order count and threshold
below/equal/above, across structured summaries and rendered output, both trigger paths and empty state; and
(b) existing observations compared separately as non-regression. "A generated report or unchanged old outputs
cannot satisfy (a)" closes the reading I objected to. Five isolated injected errors must each fail a named
assertion. This matches the eighth addendum's own prescription at
`review_staged_spring_feedback_2026_09_19.md:570-577`.

**H6 — ordering, reset, replay and capture: CLOSED**, with J2 and J3 attached. `:103-127` now requires
ordering domains and the order the scenario relies on, refuses to promise deterministic interleaving of
independent feeds, requires a reset table naming command, affected paths, what is retained and the resulting
feed position and processor initial state, distinguishes pre-start cached delivery from recorded-session
re-execution, and requires both service enablement and per-processor recording state with directory, roll,
retention and export settings plus a warning that retention can remove evidence.

Every factual claim it makes about mongoose checks out against `17a03b4`:

- `InMemoryEventSource.cacheEventLog` defaults false (`:33`) and cached dispatch happens at `startComplete`
  (`:44-57`).
- The publisher's cache read pointer exists and is the right mechanism to name
  (`EventToQueuePublisher.dispatchCachedEventLog:170-183`).
- `AuditCaptureConfig` defaults are exactly as recited — `enabled=false`, `backend=chronicle`,
  `rollSize=64m`, `retainHours=24`, `directory=./audit`, empty `autoStart` (`:49-87`).
- "Installing the service alone does not start recording every processor" is correct: `autoStart` is consulted
  per processor at registration (`MongooseServer.java:771-772`).

---

## New findings

### J1 — Moderate. The canonical comment owner sits in a repository whose revisions the consuming public repo cannot verify

`docs/specs/spec-project-starter-journey.md:150-152` makes the compiler/starter contract the canonical owner
of comment wording and has the playground consume "a revision-pinned copy".

The precedent this points at works only because its source is public. The playground's vendored skills record
`"provenance": "canonical@01b6a4fa…"`, `"revision"`, and `"verified": "sha256"`
(`web/src/lib/starter/skills/manifest.json`), and `VENDORED.md` names the source as
`telaminai/fluxtionauditlog-analyser` `docs/skills/` — a public repository, so the pin is checkable by the
playground's CI and by anyone else. The compiler repository is closed.

Failure scenario: the playground pins comment wording to a compiler commit SHA. Its CI cannot fetch that
revision to verify the copy is current, so the parity fixture degrades into an unverifiable assertion that the
vendored text was correct whenever a human last copied it — the exact drift the canonical-owner rule exists to
prevent. Secondarily, a public artifact then carries internal commit identifiers from a closed repository, and
neither the four-term sweep nor `mkdocs --strict` can see that.

Required correction: pin to something public. Publish the canonical comment text as part of the public
contract document or as a resource in the released starter artifact, and have the playground pin the released
**version** — the `starterCore` coordinate already exists for exactly this — rather than a closed commit SHA.
State in `:150-152` which artifact carries it.

### J2 — Moderate. The mongoose recital is unversioned, and the version inspected is not the version projects pin

`:114-116` and `:123-124` recite mongoose defaults as fact, introduced only as "the inspected Mongoose
`InMemoryEventSource`" and "The inspected `AuditCaptureConfig`", with no version attached.

The values are correct for what was read. What was read is `mongoose/mongoose` at `17a03b4`, whose POM
version is **1.0.30-SNAPSHOT**. Generated projects pin **mongoose 1.0.29**
(`fluxtion-web/web/scripts/example-versions.json`). So the spec states unreleased-snapshot behaviour as the
basis for runbook content that will ship against a released version.

Failure scenario: acceptance 8's configuration-to-runbook check is written against the spec's recited
defaults, a generated project resolves 1.0.29 with a different default, and the check either fails with
nothing to fix or passes while the runbook misstates the shipped behaviour. This is the spec's own rule at
`:168-172` — identify the version a statement describes; a live source is not proof it describes the pinned
tool — applied to the spec itself.

Required correction: stamp the recital with the inspected coordinate and note it is a snapshot, then require
the runbook check to read effective defaults from the project's pinned mongoose version rather than from this
prose. The runbook requirement at `:125` already says "Render effective settings for the pinned project
version", so only the recital needs the fix.

### J3 — Minor. Cached pre-start delivery is once per publisher, and the spec names the pointer without its consequence

`:114-116` correctly names the publisher's cache read pointer and correctly denies that this is durable
session replay. It does not state what the pointer does: `dispatchCachedEventLog` sets
`cacheReadPointer = eventLog.size()` after dispatching (`EventToQueuePublisher.java:170-183`), and dispatch
is triggered once at `startComplete`.

Failure scenario: a runbook says the feed's cached input is delivered to subscribers, a processor registers
after `startComplete`, receives nothing, and the reader concludes the cache is broken. Same shape for a second
dispatch, which delivers nothing because the pointer has advanced.

Required correction: one sentence — cached delivery happens once, at `startComplete`, to the subscribers
present at that moment; the pointer then advances, so it is not a re-readable buffer.

---

## Which slices can start, and what is still to be designed

**Can start now**

1. **Playground — `analyserSupport`, default and query override**, with captured legacy-link fixtures.
   Unaffected by any J finding.
3. **Playground — headless extension and the empty-directory recipe** in `web/static/CLAUDE.md`.
4. **Analyser — `context.savedGraphs`, project landing, project-scoped session state and the unified restore
   offer**, replacing the implicit opens in `Main.main` and `reopenLastGraphml`. No finding touches it, and
   G3's boundary is settled with a named gate.

**After a small correction**

2. **Playground — factor the emitters out of the bundle**, with one owner per path and the uniqueness
   assertion. Needs J2's version stamp so the Mongoose runbook content is written against the pinned version.
   J2 is a text fix, not a design decision.

**After a decision**

5. **Compiler/starter and playground — comments and diagnostics.** Needs J1: name the public artifact that
   carries the canonical wording and the version the playground pins to.
6. **Full-catalogue picker** after slice 2, then the optional authoring section and the witnessed runs.

**Prerequisites still to be designed, unchanged from the consolidated review and correctly still open**

The additive metadata schema for processor states (`:55-59`); registry-backed recipe plugin coordinates and
version floor (`:95-98`); diagnostic messages selected from reproduced failures (`:165-166`); authoring-page
layout (`:228-229`); the vendor template id and coordinate with M67 coordination and the feedback-29
shadowing fix (`:328-331`). These are design work, not defects, and none blocks slices 1, 3 or 4.

---

## What I executed versus only read

**Executed**

- `mvn -q -o -Dtest=SpecLinksResolveTest,ProjectPanelIsRevealOnlyTest test` → **5 tests, 0 failures, 0 errors,
  0 skipped**. `SpecLinksResolveTest` walks `docs/specs`, so the untracked spec's links are covered.
- `git diff --check` → clean, exit 0.
- CLAUDE.md rule-1 sweep, both halves: tracked files print nothing beyond the two that state the rule, and the
  same sweep over untracked files — the new spec and every handoff document including this one — prints
  nothing.

**Read in source for this pass**

mongoose `InMemoryEventSource.java:22,33,44-57`, `EventToQueuePublisher.java:61,101,138,170-190`,
`AuditCaptureConfig.java:49-87`, `MongooseServer.java:125,771-772`, `MongooseAuditCaptureService.java:27`;
`mongoose/pom.xml:7`; playground `skills/manifest.json`, `skills/VENDORED.md`, `example-versions.json`;
analyser `ReferenceSet.java:34-47,132-145`. The template spec was re-read in full and diffed: unchanged since
the consolidated review, so F and G remain closed by inspection rather than by assumption.

**Not verified**

- No UI path, no analyser started, no template generated, no Mongoose server run, no fresh-client session.
  J1 and J2 rest on source and configuration reading.
- The H1 reproduction is not repeated here; it is recorded in the consolidated review and the response
  correctly marks it as the reviewer's evidence rather than something re-run by the author.
- The participant denominators in the eighth addendum remain the participant's; that document says so itself
  at `:550-553`, and nothing in today's corrections upgrades them.

## Housekeeping

This file is the only addition. Both specs, the tracker, the response and the earlier reviews are exactly as I
found them, as is the unrelated working-tree work.
