# Review — M69 review-response fixes (PR #57), 2026-09-28

**Range reviewed:** `ad61a37c..1d342499` (eleven commits). R4 (`1a6ddf63`) is baseline and was not reviewed.
**Pushed by this review:** nothing. No fixes were pushed; every finding below is `suggested`.
**Verdict: mergeable after F1, and after correcting the two claims in F2 and F6.** F3–F5 are follow-ups.

Checked, not taken on trust: every claim below was reproduced from the code at `1d342499`, and the gates were
re-run here rather than read from `RESPONSE.md`.

## Gates re-run independently

| gate | author's claim | mine |
|---|---|---|
| `mvn -o clean test`, fresh Surefire XML | 2679 / 0 / 0 / 144, 358 reports | **2679 / 0 / 0 / 144, 358 reports** — matches |
| `--mode preflight` | 29 frame suites, 339 anchors | **29 / 339, no orphan or ambiguous anchor** — matches |
| mutation controls | 90 of 90 caught | **6 of 6 spot-checked caught** (see skips) |

**Skips, reported separately from passes:** I did **not** re-run all 90 M69 controls (I ran six: the three
re-anchored ones plus `m69-r1-node-decides-trust`, `-presenter-reads-no-untrusted-text` and
`m69-r6-replace-ends-the-showing`). I did **not** run the display gate or `mkdocs --strict`. Per the brief I did not
re-examine the `onLoaded` NPE, the focus-bound skips, the probes, W-A4, W-A11, the skills publication, or the
`no-log-design-open` flake.

---

## F1 — Settings import replaces the showing walk's definition and the node is never told *(major; incomplete fix of R6)*

**Where.** `SettingsShare.java:560-564` replaces walks by name inside `Category.REPORTS`. `SettingsShare` has **no
session handle at all** (zero occurrences of `post` or `SessionEvents` in the file), and its only UI caller,
`MainFrame.java:5386` (`share.apply(plan, selected, config)` followed by `applyImportedConfig()`), posts nothing.

**Failure scenario.** Play walk `W` (3 steps). Import a share file whose REPORTS category carries a walk also called
`W`, with 5 different steps, and accept it. Afterwards `config.walks` holds the new `W`; the node still holds the
frozen 3-step `W` and keeps `showing()` true; `context.walks.showing` describes a definition that now exists
nowhere. R6's own policy — *"a save that changes the steps ends the showing and says why"* — is never applied,
because no `WalkDefinitionChanged` is posted on this path.

**Why it matters.** This is the scattered-decision defect R6 exists to close, surviving in a path the fix did not
cover. `RESPONSE.md` enumerates the covered paths as "save, replace, delete, rename, restore" and does not mention
import; the brief asked for it specifically.

**Suggested fix.** Keep `SettingsShare` pure. In `MainFrame`, snapshot `config.walks` by name before `share.apply`
and post one `WalkDefinitionChanged(name, now, null)` per name whose definition changed or vanished. That keeps the
adapter reporting and the node deciding (rule 9). The regression wants to be at the frame seam, since that is where
the boundary is; a pure helper computing the facts from before/after lists is the testable core.

## F2 — the claimed equivalent mutant is **not** equivalent *(major; a false claim, not a code defect)*

`RESPONSE.md` states the presenter's use of the frozen definition has no control because *"under the node's policy
a showing walk's config never differs from its frozen copy, so reading config instead would be an equivalent
mutant."*

**Refuted.** The premise is completeness of `WalkDefinitionChanged` coverage, and F1 shows it does not hold.
Two scenarios distinguish the mutant:

1. **F1's import.** Frozen: the presenter plays the old 3-step definition. Mutant (`WalkBin.find(config.walks, …)`):
   it plays the *new* definition at that index, or refuses when the new one has fewer steps. Different observable
   behaviour.
2. **A project transition.** `config.walks` is cleared while the node still holds its frozen copy. Frozen:
   playback continues to the moment the view change dismisses it. Mutant: `find` returns null and the presenter
   refuses with *"walk '…' is no longer saved"* — a different message, at a different moment. Weaker than (1),
   which is on its own sufficient.

So the mutant is distinguishable **today**. The honest statement is the other way round: once F1 is closed
the equivalence argument becomes sound — which is a reason to close them, not a reason the control is unnecessary.

## F3 — a project transition clears `config.walks` without telling the node *(NOT a defect; I tried to demonstrate it and disproved it)*

**Where.** `ProjectProfile.java:320` (`c.walks.clear()` in `clearProjectScoped`) and `:295`
(`into.walks.addAll(s.walks())`). Neither posts, which is true and is what I first reported.

**What I predicted.** Play a walk while **no log is open** (nothing in `onWalkPlayRequested` requires one, so
`logOpenAtStart` is false and the log-close route cannot fire), leave the project, and the walk should keep
showing over a project whose config never contained it.

**What actually happened.** I wrote that frame test. The walk **ended** — with
`"ended: the spotlight went out (a click, Escape, or a view change from outside the walk)"`. Applying project
settings is itself a view change, so the existing dismissal path covers it. **My F3 finding was wrong**, and the
test that was meant to prove it is the reason I know.

**What remains, and what I did.** The coverage is *incidental*: the walk ends for a reason that does not name the
cause, and it rests on the view-change dismissal rather than on a decision about the walks. I kept a two-line fix
(the two project arms report through the same helper as the import), so the coverage becomes intentional and the
reason accurate. **It carries no regression of its own and therefore no mutation control** — the two project call
sites are unguarded, and deleting them would break no test. That is a deliberate, disclosed gap rather than a
control I could not make non-vacuous; the honest alternative is to revert those two lines, which the owner may
prefer.

## F4 — `topology` is selection-dependent and R5 does not cover it *(moderate; incomplete fix of R5)*

**Where.** `WalkPresenter.dependsOnSelection` covers `DETAIL` and `DETAIL_NODE` only. `TOPOLOGY` is in
`WalkSteps.ALLOWED` (`WalkSteps.java:38`) and its `basisKind` is `"none"` (`:165`, the `default` arm), so it needs
no basis — while the topology canvas carries a **record cursor** kept in step with the table
(`TopologyPanel.java:114-119`, "Told when the cursor rolls into a different record, so the table can follow",
"Guards the table ⇄ cursor loop"; `moveToRecord`).

**Failure scenario.** A step `{view: {tab: "topology", record: 42, filter: <hides 42>}, targets: [{target:
"topology", caption: "the nodes that logged for record 42"}]}`. `selectRecord(42)` fails and adds a note; the
`topology` target needs no basis, is not caught by `dependsOnSelection`, resolves (the canvas is visible) and is
lit; the phase is **SHOWN**. The person is shown a lit canvas whose cursor is on a different record, with a caption
naming record 42 — precisely R5's "a visible pane counted as proof", one surface over.

**Checked and NOT affected:** `records:row:N` names its own index, and `MainFrame.java:2766-2779` yields empty
bounds when `viewRowOf` returns −1, so a filtered-out row is correctly not lit. `TOPOLOGY_VERDICT` states the
pairing verdict, which is not record-scoped.

## F5 — the trust rule fails open on an unassessed log, and the run basis cannot see a same-length rewrite *(moderate)*

Two halves that compound, and the second is the brief's explicit question.

**The brief's question, answered: no.** `WalkIdentity.runBasisOf` cannot tell an in-place rewrite of the same record
count from an unchanged run. The file digests are taken when the log opens and never re-taken; the record count is
unchanged by definition in that scenario; so `compareRuns` returns `CURRENT`. The count closes the Follow-append
hole it was added for, and nothing more.

**Why the intended mitigation does not always catch it.** `WalkIdentity.recordsTrusted(null)` returns **true** —
null is neither `UNVERIFIED` nor `REPLACEMENT` — and `openLog.identity()` is null before Follow has polled and on a
store that does not report read-through. So in exactly the case the count cannot see, the identity gate may be
open.

**The inconsistency this produces.** In that state a walk certifies records and charts `CURRENT`, while `context`
tells an agent, in its own words, *"this log's reader does not report whether its file has changed since it was
read, so no change being shown is not evidence that there was none"* (the third identity branch in
`MainFrame.context`). Two surfaces of one session give different verdicts about the same question.

**Suggested.** Not fail-closed — that would refuse every walk on a non-assessing store. Either resolve record and
chart bases as `UNRESOLVED` (not `CURRENT`) while the verdict is unassessed, which is what "unknown is never equal"
already says elsewhere in `WalkIdentity`, or carry the caveat onto the strip. A decision either way should be
written down, because the current behaviour is the one place the walk feature is more confident than the rest of
the app.

## F6 — `restore` posts nothing, and `RESPONSE.md` says it does *(minor; a false claim, no defect found)*

`WalkVerb.restore` (`:179`) calls `WalkBin.restore` and never posts, though `RESPONSE.md` lists restore among the
paths that report. **No behavioural defect follows:** `WalkBin.restore` refuses when a live walk of that name
exists (`WalkBin.java:42-44`), and deleting the showing walk always ends it, so a restore cannot reach a showing
walk. Correct the claim, or add the post for symmetry; it becomes live the moment delete stops ending.

## F7 — a test name that no longer describes its assertion *(minor)*

`WalkVerbTest#deletingTheShowingWalkEndsIt` no longer asserts that anything ends — correctly, since that moved to
the node. Its `@DisplayName` is accurate; the method name is not, and the method name is what a future reader greps.

---

## Judgement challenged, and the answers

- **The two changed tests are honest, and both are *stronger*.** `deletingTheShowingWalkEndsIt` replaced an
  `anyMatch` with an exact-list assertion and added a rename case. `readIdentityPolicyPerOperation` flips play to
  `true` with the reason stated and **adds** three assertions (delete, end, restore). Nothing was weakened.
- **The three re-anchored controls are faithful.** `m69-s4-delete-ends-showing` now guards the report rather than
  the end, and the end is guarded node-side by `m69-r6-delete-ends-the-showing` — together they cover the original
  property. `m69-s4-play-reads-the-session` is stronger than before: its mutant fabricates an accepted answer and
  the named test's refusal cases fail. `m69-s4-read-identity-per-operation` still fails its first assertion. All
  three were run and caught.
- **The equivalent-mutant claim is refuted** — see F2.
- **No merge or integration analysis was done**, per the corrected brief: `origin/main` (`80decad7`) is already an
  ancestor of `1d342499`, which I verified with `git merge-base --is-ancestor`.

## Checked and found correct (no action)

- **R8 is not an overreach.** §3.3 gives every filter field a concrete default and says a missing field takes it
  *"never 'whatever is selected now'"* — unlike `tab`, `record`, `graph` and `focus`, whose rows say "left as it
  is". W-A4 requires that a dirty filter not leak into step 1, which is only achievable by applying `Filter.ALL`.
  Applying it to a status-only step follows from the same rule.
- **R9 is sound.** `Float` widens to a non-integral double and is refused; a non-`Double`/`Float` `Number` goes
  through `new BigDecimal(n.toString())`, which is exact and covers `BigInteger` beyond `long`; `-0.0` has
  `signum() == 0`, skips the scale test and yields 0, which is right; a huge scale-negative `BigDecimal` passes the
  scale test and is then refused by the range check.
- **R7 cannot mistake a stale or id-0 answer.** `requests` is a field of the single `walkVerb` instance
  (`MainFrame.java:2540`) and starts at 1; UI plays post id 0 and `WalkPlayback.answer` ignores 0, so no UI play can
  ever produce an answer a verb accepts. A UI play interleaving between post and read yields a mismatch and a safe
  refusal — a false negative, never a false accept.
- **R3's renumbering survives a later `{add: true}`:** `renumber` sets `nextNumber = highest + 1`, so additions
  continue above the session's numbers. (Not verified: behaviour when one step names the same target string twice,
  since the map is keyed by target.)
- **R1's always-request-a-light change does not upset "lit fewer".** An empty effect takes the walk's light down and
  answers `lit=0`; `wanted` is then also 0, so `onWalkTargetsLit`'s `e.lit() < wanted` is false and the phase set by
  `onWalkStepPrepared` stands.
- **Trust is re-evaluated per effect,** not frozen at play time: `recordsTrusted()` is called inside `prepare()` and
  in the re-resolution arm of `onLogChanged`, so a mid-walk degradation is picked up at the next step.
- **The verb has no navigation operation** (`OPERATIONS` is steps/delete/rename/restore/play/end), so extending
  `readsRecords` to `play` closes the whole assistant-side surface; stepping is a UI act and outside the read policy
  by design.

## Vacuous regressions and equivalent mutants, kept separate

- **Equivalent mutants:** one claimed by the author, and **refuted** (F2). None found by me.
- **Vacuous regressions:** none found — but I did not go looking exhaustively, and six of ninety controls is not a
  survey. The six I ran were each red at a named assertion and restored byte-identically.

## W-A4 and W-A11

- **W-A4 (byte comparison of both settings files after a dirty start): follow-up, but schedule it before release,
  not after.** R8 *increased* what playback touches — every step now writes the filter, including steps that
  previously left it alone — so the "playback persists nothing" property is more load-bearing at `1d342499` than it
  was when W-A4 was deferred. Not a merge blocker; the risk is a persisted filter, not a wrong verdict.
- **W-A11 (native screenshot capture): follow-up.** It is evidence-capture convenience and blocks nothing here.

## Merge verdict

**Mergeable after F1**, plus the two claim corrections (F2, F6), which are documentation edits.

F1 is the one I would hold the merge for: the PR's R6 claim is that definition changes are facts the node decides,
and a mutation path that changes the showing walk without reporting it means that property does not hold as stated.
It is a small fix of the same shape as the ones already made. F3, F4 and F5 are real and worth issues, but each is
narrower than the ground this PR already gained, and none of them makes the branch worse than `ad61a37c`.
