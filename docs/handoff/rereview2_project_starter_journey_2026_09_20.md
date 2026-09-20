# Second re-review — G1–G3 corrections and the new “Learning at the point of use” section

Date: 2026-09-20 · Reviewer: the session that wrote the
[first review](review_project_starter_journey_2026_09_20.md) and the
[first re-review](rereview_project_starter_journey_2026_09_20.md) · Reviewed: the revised
`docs/specs/spec-project-starter-journey.md` (now 334 lines, including “Learning at the point of use” and
acceptance 9–12), the updated
[author response](response_project_starter_journey_2026_09_20.md) and the new tracker entry.

Nothing was committed, pushed or implemented, and no reviewed file was edited. New findings are numbered
**H1–H3** so the F and G series are not reused.

---

## Verdict: **CONDITIONAL**, now only on the new section

**G1, G2 and G3 are closed.** Each correction is in the spec text, each is stated more precisely than my
finding was, and each gained an acceptance check that would fail if it regressed. The F series remains
closed. If the earlier scope were all that was on the table, this would be READY.

The new section is the right shape: it puts routing where the generator already is, it refuses to expand the
analyser into a documentation store, and it labels its own evidence honestly as one session's ranking rather
than a proven model of how agents learn. Three corrections are needed before it can be handed off, one of them
from a defect I reproduced rather than inferred.

---

## G-series: closed, with what I checked

| # | Where it landed | Check |
|---|---|---|
| G1 | Spec `:160-167` — an absent query parameter now means **no override**, default-true confined to decoding | Matches the parse path: builtin templates go through the same zod schema (`builtin-templates.ts:18,39-43`), so a template's explicit false now survives. Acceptance 1 gained the four-case matrix and asks for ZIP contents, not just the parsed flag |
| G2 | Delivery step 2 `:261-265` — one emitter per path, ownership chosen before emission, `generate()` asserts unique paths and fails naming the duplicate | Correct target: `zip.ts:14-30` appends bundle files and `:33-43` keys entries by path, so last-wins is the current silent behaviour. New acceptance 9 injects duplicate profile and guide paths, which is the test that would have caught it |
| G3 | Spec `:200-206` — the Project panel **states**, `StartPanel` **offers**, `ProjectPanel.Navigator` unchanged, `ProjectPanelIsRevealOnlyTest` named as the gate for both D-L3 and D-L1 | The split is exactly the boundary the test enforces; acceptance 7 now exercises open/restore from the landing and keeps the panel test unchanged. Ran it: 2 tests, passing |

---

## The new section

### What is sound, and verified rather than assumed

- **Ownership is placed correctly.** Routing sits with the generator, which is where the project's choices
  already are; the analyser only exposes pointers. Verified that this works today with no new surface:
  `runbooks` is put into `context` at `MainFrame.java:5660`, **above** the `filter == null` early return at
  `:5820`, so an active project with no log already exposes its runbooks to an MCP client. The "no guide
  verb" decision is therefore supported by the code, not just by preference.
- **Refusing the guide verb is consistent with a standing decision.** The verb surface is pinned by
  `VerbSchemasTest`; I ran it (5 tests, passing). Declining to add a verb here is the cheap, reversible
  choice, and the section says what a future documentation-provider capability would have to contract for.
- **Cross-language hash equivalence survives added comments** — the section's riskiest compatibility
  requirement is satisfiable, and both halves already implement it. On the Java side `bodyHash`
  (`Reconciler.java:964-980`) and `constructBodyHash` (`:1680-1687`) both skip tokens where
  `getCategory().isWhitespaceOrComment()`. On the browser side there is a passing test asserting that
  swapping a block comment for a line comment leaves ownership entries identical
  (`authoring.test.ts:72-84`). An implementer should be told these exist rather than re-deriving them.
- **Version-compatible references have existing machinery too.** The playground already enforces
  `x-analyser-min-version` frontmatter at bundle time (`bundle.ts:477-486`, applied at `:574`) and records
  `skills.provenance` in the profile, alongside the pinned skills revision and sha256. The section's rule
  should say "extend this", because it reads as though the mechanism is new.
- **The 250-line figure is correctly labelled** an editorial target rather than a correctness gate, and the
  twelve-fact claim and "retrieval beats training" are both marked as observations to test. That is the right
  handling of single-session evidence.

### H1 — Major. A generated comment survives a changed decision and then contradicts the code

Spec `:114-121` asks for brief comments at generated decision points explaining required user choices,
while `:120-121` requires reconciliation's ownership, hash and no-op guarantees to be retained.

**Reproduced** on a disposable fixture in the compiler worktree, using the current reconciler. I put a
comment between the `@Generated` marker and the owned annotation of a reference field:

```
@javax.annotation.processing.Generated("fluxtion-starter")
// Choose DATA when this reference must not trigger; see the contract.
@com.telamin.fluxtion.runtime.annotations.NoTriggerReference
private final Parent parent;
```

then changed the XML binding from DATA to TRIGGER and reconciled. Result: no conflict, the annotation
correctly removed, the developer's body intact — and the comment still there, word for word, now telling the
reader that this reference must not trigger on a field that triggers. I also confirmed that a field's
JavaParser token range **includes** an interleaved line comment, so the comment is inside the exact-text
region that the annotation-replacement path matches on and requires to be unique.

Consequence: reconciliation owns the annotation, not the prose about it. The section's premise is that
comments are the channel people actually read, so a tool-generated comment that outlives its subject is worse
than none — and it is invisible, because hashes ignore comments and the run is a clean success. The probe
files were removed; the worktree is clean.

Correction, and it is cheap: require generated comments to be **choice-neutral** — explain the construct and
link the owning contract, never state which alternative is currently in force. A comment that says "this
reference's propagation mode is declared in the XML; see <contract>" stays true through every mode change; one
that names DATA does not. Add the changed-decision case to acceptance 11, which today exercises implementation
and a no-op run but never a changed declaration.

### H2 — Moderate. The reference-version rule has a case nobody owns

Spec `:130-134` requires generated comments and runbooks to identify the tool or template version they
describe, and says plainly that a live URL is not proof it describes the downloaded tool.

The analyser's fallback writer cannot satisfy that rule. `ReferenceSet.Resource`
(`ReferenceSet.java:47`) carries `id, url, why, status, appliesTo, note` — no version, revision or
compatibility field — and the class is deliberately constrained to "writes references, never content"
(`:34-36`). It remains in scope by the spec's own text at `:69-72`, as the fallback for legacy and
profile-less projects, which after the picker expansion is a path real users will take.

Correction: either exempt the fallback writer explicitly, saying its block is version-neutral pointers only,
or add a version field to `reference-set.json` and render it. Silence leaves an implementer to discover the
mismatch when writing the documentation check that acceptance 10 requires.

### H3 — Moderate. Acceptance 12 can reach a conclusion from a single session

Acceptance 12 is a strong protocol — frozen predictions, links followed, hazards found before the first
affected edit, unused guidance, every owner intervention recorded, assisted runs recorded as assisted, a
held-out task, and "one successful session is not cross-model validation". It is better than the acceptance it
replaces.

What it does not state is how much evidence adopts or drops a routing change. This repo already has a rule for
exactly that: `docs/ONBOARDING.md:238` — "**A finding counts when it RECURS.** … one agent hitting something
once is noise, the same friction across two different tasks is a defect" — together with the obligations to
rotate the task, hold one out, and record what went unused. The section's own evidence is one session
(`:100-103`), and the spec is honest about that, but acceptance 12 is where the honesty has to become a
stopping rule.

Correction: name the threshold. Two independent sessions on different tasks, or the held-out task specifically,
before a routing change is adopted or a guidance file is deleted as unused. One sentence, and it makes
acceptance 12 falsifiable in the direction that matters — dropping guidance nobody read is as consequential as
adding it.

---

## On the brief's four questions

- **Cross-repo ownership** — correct, with one gap (H2). Routing is the generator's, diagnostics belong to
  the diagnostic owner, the analyser stores pointers. The one place two repos still write for the same
  construct is generated-comment text across the browser and the local reconciler; the spec requires them to
  be consistent but does not name a single owner of the wording. Worth a clause, since F10 was the same shape.
- **Reference/version consistency** — the rule is right and the mechanism largely exists; say so, and close
  H2.
- **Reconciliation compatibility** — better than the spec claims on hashes, which I verified both sides of,
  and worse than it claims on meaning, which is H1.
- **Does acceptance 12 test discovery without coaching** — yes for the mechanics, and interventions are
  recorded rather than hidden, which matches the precedent set by the September Spring trial where a needed
  workaround falsified a prediction. H3 is about what a result licenses, not about whether the run is clean.

---

## Verified versus read

**Ran**

- `mvn -o -Dtest=SpecLinksResolveTest,ProjectPanelIsRevealOnlyTest,TemplateCatalogueTest,ProjectProfileTest,VerbSchemasTest test`
  → **36 tests, 0 failures, 0 errors, 0 skipped**.
- The H1 reproduction: a throwaway JUnit probe in the disposable compiler worktree, showing the field token
  range includes the comment and that a DATA→TRIGGER reconcile leaves the comment contradicting the result.
  Probe deleted; that worktree is clean at its pushed head.
- Rule-1 sweep over the revised spec and the updated response: clean.

**Read in source**

`zip.ts:14-43`, `builtin-templates.ts:18,39-43`, `bundle.ts:477-486,574`, `Reconciler.java:964-980,1680-1687`,
`authoring.test.ts:72-84`, `ReferenceSet.java:34-47`, `MainFrame.java:5660,5820`, and the
`ProjectPanelIsRevealOnlyTest` assertions behind G3.

**Not run**

No UI, no network, no template generation, no fresh-client session, nothing requiring publication or a key.
H2 and H3 rest on source and on this repo's own written rules.

---

## Still owed

Unchanged and correctly stated by the spec: every slice in the delivery order is unimplemented, acceptance 6's
witnessed run has not happened, the vendor entry does not exist, and the headless parameter is not deployed.
No owner decision is outstanding from my side — H1–H3 are corrections, not choices.

## Housekeeping

This file is the only addition. The specs, the response, the tracker and the rest of the working tree are as I
found them. Scratch work is under this session's scratchpad and in a disposable worktree, both removable.
