# Review: M68 v3 and the tracker sweep

**Subject:** `abb4a697b6c2c4de2b1ded2530bdc37d01acd75e`, specifically
`docs/specs/spec-evidence-integrity.md`, `docs/specs/tracker.md` and
`docs/specs/completed/tracker.md`. Source references below refer to that commit.
**Review date:** 2026-09-24.

**Verdict: M68.1 MAY START. The complete specification and the tracker reconciliation remain
CONDITIONAL.** R1–R3 are answered at the specification level. R4 has an implementable predicate,
but its original malformed-input acceptance remains unverified. R5 is acknowledged and scheduled,
not discharged. The new freshness policy and the SG-2 closure need correction. The systemic
tracker check finds unfinished clauses outside the live work list, not just M45.4.

No implementation, tracker, specification or evidence was edited. This review does not close
an implementation acceptance merely because its requirement is now clear.

## Independence and method

I was the reviewer in rounds 1 and 3, and participated in preparing the recovery evidence.
This is a further review by that same reviewer, not a third independent vote. In particular,
the physical-line framing predicate was my round-three suggestion. I have a reason to favour it;
its adoption is not independent validation.

I re-read v3, the prior review dispositions, the relevant source, the published format contract,
the graph XML, tracker history and the cited public CI log. Each question below distinguishes
fresh inspection from reused results. **No new behavioural probe or mutation was run.** The only
application tests run were the existing baseline suite. Cases described as attacks below are
source-derived counterexamples, not new measured failures.

## Required corrections

### V3-1 · Medium — freshness needs an exhaustive, executable verification rule

**Location:** `spec-evidence-integrity.md:154–166`, acceptance 7.
**Basis:** source and contract inspection; no filesystem attack executed.

The definition of append requires every old byte to be unchanged. A boundary sample and a
bounded prefix sample cannot establish that. For example: preserve the file key, change a byte
in an old middle record outside both samples, then append a complete record. Both samples
match and length grows, but old indexed fields describe different content. If “prefix
fingerprint” means hashing **all bytes through the previous end again**, it can establish that
comparison, but it is an O(previous-length) read. Avoiding a hash of the new tail does not make
that a bounded cheap check. A saved digest alone cannot detect a subsequent middle rewrite.

There is also a decision-table hole: same key, same length, changed modification time and a
matching checked prefix/boundary. That is neither append, an expressly listed replacement,
nor clearly the stated unverified case (“metadata is unchanged, or no file key is available”).
Missing/unreadable input and a change during verification need dispositions too.

The actual implementation confirms why this matters:

- `parse/HeapLogStore.java:152–189` reads the file as UTF-8 text, compares **character lengths**,
  returns immediately at equal length, publishes replacement text on growth and skips previously
  indexed rows. It currently proves no unchanged prefix.
- `ui/MainFrame.java:4291–4328` observes metadata, calls append and publishes/refetches based
  on its integer result. Verification must precede that publication, including zero-row polls.
- `core/FileObservation.java:10–33` explicitly provides metadata evidence only. A missing file
  key is currently stringified as `"null"`; that must not become an equal, established identity.
- `parse/MappedLogStore.java:105–119` reads row bytes from the channel on demand. Retaining
  its old index is **not** retaining an immutable old snapshot after an in-place rewrite.

**Required before M68.5:** define precisely the fingerprint's byte range, when it is reread,
and what consistency check brackets that read. Either verify the complete old prefix and state
the cost, or treat a bounded match as insufficient and label it unverified. Make unverified the
explicit catch-all for insufficient evidence, including I/O failure and concurrent change.
Define what reads may return in that state. Permit the “superseded snapshot” option only for
bytes actually retained; otherwise suspend reads until reopen. Add the middle-rewrite-plus-growth
and changed-mtime/same-length cases to acceptance. This does not block M68.1.

### V3-2 · Medium — SG-2's evidence closes provisioning, not its entire written acceptance

**Location:** live tracker `:447–455`, `:2139–2142`; completed tracker `:4303–4333`.
**Basis:** inspected the actual public CI log, not just the copied tracker claim.

The archived requirement at `completed/tracker.md:4320–4321` explicitly includes
“generation/run” on the hosted ZIP. The cited
[public run 35929392911](https://github.com/telaminai/fluxtion-web/actions/runs/35929392911)
reports for `fluxtion-spring-mongoose`:

```json
{
  "status": "PASS",
  "starterVersion": "1.0.74",
  "emptyMavenCache": true,
  "generationAttempted": false,
  "clientSessions": 0,
  "classpathEntries": 97,
  "validationValid": true,
  "originalFilesUnchanged": 36
}
```

The other successful build/run/export/stop job requests `template=analyser-bundle`.
It is the keyless-bundle regression check, not hosted Spring changed-graph generation/run.
The archived entry itself correctly retains the branch-artifact qualification and says hosted
generation/run is the earlier branch trial. The new sentence “That is exactly what this item
required” therefore overstates the evidence. Running a later provisioning check does not
retroactively upgrade the older trial's artefact identity.

**Required:** split the closed public provisioning/keyless-regression clause from the still
unverified public hosted generation/run clause, or explicitly transfer that clause to a live
acceptance that names the **hosted** template. Merely saying G14 remains open does not establish
that its standalone extended-design trial will exercise the hosted archive. Preserve the
successful evidence and do not rerun anything for this review.

Beta consequence: hosted Spring is now a genuine candidate with verified acquisition/setup/
validation. It is reasonable to reconsider the recommendation on merit. It is not evidence
that the hosted A1–A2 journey, or every guided step, has been verified. Keep BETA-B3's actual
template decision and dry run open.

### V3-3 · Medium — the archive still contains unfinished work without a live disposition

**Location:** completed tracker `:737–738`, `:810–814`, `:3143–3166`;
live tracker `:1055–1059` states the new sweep rule.
**Basis:** both trackers searched and surrounding entries inspected; source checked where noted.

The rule is useful but the reconciliation is incomplete. At least these clauses need an
explicit live home or an explicit withdrawn/delivered disposition:

| Archived entry | What remains inside it | Check at this review |
|---|---|---|
| M7.3, under “M7 DONE” | `◧` background load; progress-percent and cancel deferred | No live M7.3/progress/cancel task. The ordinary file-open path still uses background work without a file-load progress/cancel surface. Template-download cancellation is a different operation. This entry already existed in the initial public history; it is not newly lost by abb4a697. |
| Refinements round 11, marked DONE | Autoscale-Y's O(points) drag scan explicitly “Not built” | No live task/disposition. `ui/ChartPanel.java:399` still loops over each series point in `setViewWindow`. It was deliberately low priority; that does not make it delivered. |
| M55.2, marked COMPLETE | First-key-seen iteration-order acceptance explicitly “Still open” until a downstream construct exposes iteration | No live M55.2/order-acceptance pointer. This is a cross-repository verification debt: I did not inspect or run the current C++ implementation, so I do **not** claim it is still missing there. The tracker has no closure evidence or transfer for this clause. |

These are not reasons to reopen every historical “partial” label. Distinguish genuinely
unfinished work from obsolete wording; question 6 lists both. Correct the tracker coverage,
not the historical record. None is a dependency of M68.1.

### V3-4 · Medium — R5 is deferred, not closed

**Location:** `spec-evidence-integrity.md:106–115`, acceptance 4.
**Basis:** re-inspected the action contracts and existing tests; reusing R5's required outcome.

The new text honestly calls its exception list unwritten. That resolves the misleading claim
of completeness, but does not settle existing behaviour that the general rule would prohibit.
`report` retaining valid sections beside a rejected section is deliberately tested by
`ReportVerbTest.anUnknownKindIsSkippedAndNamed_itsSiblingsSurvive`; graph warning/partial
application is also an existing contract. Saved-analysis execution retains earlier effects
when a later step fails. An unknown parameter can still be reported after execution.

**Required before changing those behaviours in M68.4:** write the disposition table and obtain
any required policy decisions. Include report siblings, graph series/markers/bands, source-root
mixed additions/removals, open precedence/format combinations, multi-field topology calls,
unknown parameters and saved-analysis steps. “Audit during the slice” can be its first
deliverable; it is not permission to choose different semantics while implementing each case.
This is the residual R5, not an additional blocker for M68.1.

### V3-5 · Low — qualify the historical safety argument and link the contemporary caveat

**Location:** `spec-evidence-integrity.md:246–250`, M45.4 reconciliation.
**Basis:** history and preserved report inspection; no compiler run.

The empty declared-only set was a measurement of one session graph, not a theorem that the
declaration can never shrink another graph's population. Acceptance 1 itself correctly
requires a declared framework node with an ordinary class name to leave that population.

There is a further caveat in the **same** adoption report,
`docs/handoff/completed/response_compiler_1.0.65.txt:195–220`: “ATTACK 2” says registration
windows remain, and narrows the assurance to the tested shapes. A report cannot be evidence
for its optimistic first half while its explicit qualification disappears.

**Required documentation correction:** say “on that measured graph”; distinguish verification
of the declaration comparison from verification of an implemented analyser consumer. Link the
contemporaneous caveat, or its separately evidenced upstream resolution if one is found. I did
not establish whether that upstream window survives in 1.0.74, and am not reopening the compiler
based on an old report. M68.1 can still consume declared provenance under its stated trust policy;
it must not call that proof that producers cannot be wrong.

## Answers to the ten questions

### 1. R1 to R5, individually

| Prior finding | Disposition at v3 | Evidence / independence |
|---|---|---|
| R1: membership, ratio and retention conflated | **Closed as a specification finding.** D-E1 and acceptance 2 expressly separate the three and exercise the downstream public paths. | Rechecked `GraphPairing.of`, `session/CoveragePolicy` and v3. Reusing my prior reproduced no-IDs example; not rerun. |
| R2: version gate mistaken for authority | **Closed as an implementation-policy gap**, with V3-5's qualification needed in the supporting history. Authority, node scope and inferred fallbacks are now specified. | Rechecked `GraphVocabulary.trustedForNodeFacts`, `Scaffolding`, the Sep 1 commits and report. No claim that the old measurement proved all possible producer graphs. |
| R3: different observation scopes and uncovered surfaces | **Closed as a specification finding.** D-E2 includes revision and observation scope; acceptance 3 places a foreign ID after the sampled region; the surface table is binding. | Rechecked current callers and acceptance text. Same conclusion as my prior requested correction, freshly inspected. |
| R4: undefined framing predicate/pending observation | **Predicate supplied; original-input acceptance still unverified.** Question 4 gives the remaining boundary. | Reusing my suggested predicate and earlier one-record reproduction, checked against the actual format grammar. No new implementation claimed. |
| R5: whole-or-refused under-enumerated | **Open before M68.4 behaviour changes.** V3-4 above. | The list is explicitly deferred, not populated. Existing tests still protect partial successes. |

None of those is an assertion that production now implements v3: abb4a697 changes documentation.

### 2. Three-state attack

**Inspection: sound enough to implement.** I independently extracted the committed XML: 18
vertices; `checked`, `child`, `rootNode` each declare `framework=false`, `auditCapable=true`;
`serviceRegistry` declares both framework and audit capability true. Three eligible authored
nodes plus a labelled log writing all three gives 3/3. The graph alone establishes the
population, not that those nodes logged.

The service registry must match membership but leave the authored ratio. Its single-node case
has 1/1 membership and no ratio. Foreign IDs remain reportable even when that ratio is absent.
No acceptance derives retention from ratio, or positive pairing from retention. With no
logged IDs, acceptance 2 now explicitly forbids the downstream coverage/context/report claim.
That catches the real current leak: `GraphPairing.java:67–70` returns `applies=true` for an empty
log population; `session/CoveragePolicy.java:104` treats that policy bit as evidence.

Acceptance does not change the >0.5 retention threshold. A retained partial match cannot
therefore silently become complete agreement. A source-constructed log is sufficient for
these set/scope boundaries; it supplies the required node IDs, audit-level/provenance inputs
and record positions without claiming to replay the client's run.

One vocabulary clarification is advisable: “NODE-scoped” must mean runtime-node entities,
including `EVENT_HANDLER`, not just the literal XML value `NODE`. All three authored nodes in
this fixture have `fluxtion.kind=EVENT_HANDLER`; the existing Java model's event/service
exclusions and the 3/3 fixture make the intended interpretation testable.

### 3. Freshness attack

**Inspection: not yet sufficiently specified. V3-1.** Implementable with an explicit full-old-
prefix comparison or an honest unverified result; not implementable as proof from two bounded
samples. Byte offsets, consistency during the check, null identity, zero-row polls and mapped
store mutability all matter at the existing interfaces. No performance benchmark or new
filesystem experiment was run.

### 4. Framing attack

**Inspection plus explicitly reused round-three reproduction.** The old false-positive input
was a single physical line `eventToString: "literal eventLogRecord: text"`. It cannot be a
second line whose entire trimmed content is `eventLogRecord:`. Thus the new predicate rejects
that particular false positive by construction; I did not execute a new detector.

The contract does not grant general YAML semantics. Format §3 treats legacy quote marks
differently from the reader-declared `QUOTED_SCALARS` grammar in §3a. “Active encoding” must
reach this scan, including across physical lines; do not turn any quote character anywhere
into a YAML multiline-scalar delimiter. Publish the inspected range and exact header-candidate
locations. A suspicion is not a new record count. A bounded scan that cannot classify the
frame must disclose not-assessed, as v3 now requires.

The committed recovery packet still contains no original malformed audit log. `run1.txt` and
`run2.txt` are checker output. The old temporary location named in round one yielded no YAML
inputs in this review. I therefore **cannot verify that the exact malformed input is caught**.
A frame containing repeated unquoted standalone header lines is caught by the proposed rule;
that conditional statement is not verification of the missing file. V3 appropriately keeps
originals or a labelled replacement as a prerequisite for that acceptance.

Pending Follow is feasible but requires new plumbing: the store exposes indexed `rawText(row)`
and a pending count, not the pending text span. A bounded raw-tail observation can be supplied
separately without accepting the record. It must refresh on pending-byte changes even when
zero rows arrive: `MainFrame.pollFollow:4317–4328` currently refreshes diagnostics conditionally
and returns on zero additions. Acceptance 10's live/split-separator case should drive that
entrance. No quiet-time rule may commit the pending record. This does not require new sessions.

### 5. M45.4 history

**Inspected git history; dates and the loss confirmed.**

| Event | Evidence |
|---|---|
| Authority recorded on Sep 1 | `856c2936`, “Upstream 1.0.65 is released and deployed”, records upstream's classification change and unparks verification. |
| Analyser-side measurement on released 1.0.65, Sep 1 | `8a5d5ba2`, “adopt released builder 1.0.65”, adds the measured declared/heuristic set comparison and “OUR HALF VERIFIED”. Its report and generated graph changes are preserved. |
| Partial entry removed from live tracker Sep 3 | `a5298fcf`: parent contains M45.4 with `◧` and an internal tick; child moves it into completed and leaves other open slice lines behind. The commit claims all 25 open items were retained; that claim missed this partial item. |
| Consumption absent at review subject | `Scaffolding.java:46–64` still classifies by ID, package and simple name. The declared authorship key never enters this path. |

This verifies the loss and the current missing consumer. The internal tick causing the sweep
is a reasonable interpretation of the diff; git does not establish the earlier author's mental
process. “Our half verified” meant a measurement, not implemented consumption. V3-5 limits
what that measurement proves. M68.1 completes this missing consumer and adds membership/scope
corrections; it does not duplicate an existing declaration-aware classifier.

### 6. Systemic tracker sweep

**Inspection: there are other cases.** I searched both entire files for partial markers,
unchecked clauses, deferred/still-open wording, and mixed tick/partial blocks, then checked
live references and selected source paths. Mechanical matches alone are not findings.

| Pattern found | Disposition |
|---|---|
| M7.3, round-11 autoscale and M55.2 order acceptance | Require explicit live/deferred/delivered dispositions: V3-3. |
| H8.6 partial, copy-row-as-YAML still unchecked (`completed:875`) | **Stale archival status, not missing implementation.** `MainFrame:665–666` exposes “Copy selected as YAML”, with its handler below. Record a closure pointer rather than reopening it blindly. |
| M19.21 tick with “Still open for the playground” and later “stays ◧” (`completed:2522–2553`) | **Unreconciled historical clauses.** Live `:1684–1688` says this slice was completed and archived but does not dispose of those specific clauses. Find later marker/re-vendor/re-review evidence and link it, or restore the remaining obligation. Not evidence those upstream features are currently absent. |
| M44.3 old unchecked entry and later completed entry (`completed:2797`, `:3803`) | Superseded history: later implementation, M44.3b and the moved spec's as-built block supply the closure. Do not count both as separate unfinished tasks. |
| M31.4 guide versus out-of-tree reader | Reader continuation is retained in the live plugin/onboarding work; shipped guide alone is not a lost whole milestone. |
| M13.5, M20.5, M21.7–.9, M29.5, M33.5, M40.2c | Live continuations exist. Archiving their shipped neighbours did not lose them. |
| M14.6 window transforms, A10.7 node-log aggregation | Old deferred labels are not enough to reopen them: later graph/aggregation facilities and their tests now exist. These need historical cross-references if tidied, not new feature requests from this review. |
| UC-LANDED's tick with open hook table; UC5 idiom clauses | Deliberately live, explicitly explained at live `:1075`. Their open parts have live pointers. Do not sweep them merely for having ticks. |
| AF release halves, vendor site, tool-agreement halves | Completed publication/reader slices name live upstream or remaining slices. That is legitimate partial delivery where the boundary is explicit. |

The two file-level searches also found incidental “partial” describing geometry and historical
failure narratives; those are not partial work statuses. This was an obligations audit, not
a demand to remove all such words. Upstream delivery for M55.2 and the residual M19.21 clauses
remains uncertain; that uncertainty belongs in the tracker rather than an invented closure.

### 7. Audit the actual abb4a697 sweep

**Inspection of parent-to-subject diff and path checks.** The screenshot-refresh block can
move; historical/excluded capture modes are disclosed limits, not an outstanding request to
rerun them. The Spring guide's delivered prose/screenshots and SG-1 can move. SG-2 as a whole
cannot be called fully evidenced without V3-2's split or explicit transfer.

The async-driver spec's retirement is reasonable: its as-built section records M44.3/.3a and
the later .3b close policy. Other session work still has a live home. All changed Markdown
file links resolve. The four deliberately retained specs have genuine live continuations:
M64.13, M65.5, M19.19 and TA-5b/5c/9/B. No newly moved link was found broken.

A broader path check did find **four pre-existing broken links** in completed tracker lines
2549, 2556, 2557 and 2630: they use `../handoff/completed/...` where this directory needs
`../../handoff/completed/...`. These were not introduced by this sweep. Correcting them is
an optional housekeeping improvement, particularly because they make M19.21 harder to audit.
The existing suite's green link check does not establish that every archive link works.

### 8. SG-2 and the beta recommendation

**Inspected public run, not rerun: V3-2.** The 97 classpath entries and 36 unchanged originals
are real, as is the separately tested keyless bundle. Public hosted generation/run is not
established by a result that says `generationAttempted: false`. The narrow acquisition claim
is earned. The broader guided-authoring claim and beta template selection still need their
named acceptance; passing CI cannot fill a scope it explicitly did not exercise.

### 9. Narrowed conformance claim

**Fresh inspection confirms the narrowing.** The tool-agreement TA-2 clause is about
same-named graph copies and says opening a disagreeing sibling is “announced, not” refused;
it is not a global replacement for every retention rule. `GraphPairing` itself still uses
different-build language on a negative overlap result. I over-read the broader claim in an
earlier round; v3 does not repeat that mistake.

The fixture-specific defect needs no general ban: the graph **declares `checked`**. A message
premised on its absence is false. That does not prove graph/log build identity either, and v3
correctly makes factual, scoped wording a new general requirement. Not over-corrected.

### 10. May M68.1 start?

**Yes.** Its sets, fallback basis, downstream no-IDs case and observation scopes are now
sufficiently defined, and the committed graph plus labelled constructed logs can exercise them.
No new client, producer release or original malformed log is needed to start that slice.

Before claiming completion: implement and test the public paths and every listed surface,
preserve foreign-graph controls, and run the independent mutations. One mutation must disable
only declaration consumption, another only membership-set choice, another the zero-population
state separation, and another the retention-to-claim gate. A combined failure is not evidence
for all four. I did not run those proposed mutations against an unimplemented specification.

Before later slices: V3-1 for M68.5; the R5 disposition matrix for M68.4; preserved or labelled
framing inputs and a pending observation for M68.3. Before calling this documentation handoff
fully reconciled: correct SG-2's closure scope, give the stranded clauses dispositions and
qualify the historical safety argument. Those corrections can proceed alongside M68.1.

Optional improvements: repair the four old archive links; clarify NODE versus EVENT_HANDLER;
reverse the wording of the third proposed mutation at `spec:368` to explicitly attack deriving
**no membership from no ratio** (the original regression); fix Q2's stale acceptance-number
reference (naming is acceptance 6). Consider a tracker check that requires a live continuation
or explicit disposition for partial subclauses; a checkbox-only check would repeat this loss.

## Checks actually run and limits

- Isolated review worktree and branch based on **abb4a697**, not a moving main.
- Existing baseline, exact command:

  ```sh
  JAVA_HOME=/Users/greg/Library/Java/JavaVirtualMachines/corretto-21.0.8/Contents/Home mvn -q test
  ```

  **246 Surefire XML suites; 1,876 tests, 0 failures, 0 errors, 62 skips.** Counts were summed
  from the actual reports. This is the headless baseline; no real-display run is claimed.
- Read-only XML extraction of the committed recovery graph; tracker text inventory and git
  history comparisons; relative Markdown file-target checks over both trackers and the moved
  spec plus its changed session-spec caller. These are artefact inspections, not application
  reproductions. The check verified file existence, not every heading anchor.
- Retrieved the cited public CI log with `gh run view 35929392911 --repo telaminai/fluxtion-web --log`.
  Initial sandbox network failure, then successful read with network permission. No workflow
  was dispatched and no public project was generated or run.
- `git diff --check` and the exact tracked-file public-content sweep from CLAUDE.md are run on
  the staged review before commit. Only this review is staged.
- No new tests, mutations, source edits, UI session, LLM session, compilation key, merge,
  publication or deployment. The old behavioural counterexamples are explicitly reused;
  the new freshness attacks are inspection-based. Current upstream C++/compiler internals
  were not reverified. The original malformed log is not verified here.

The baseline establishes repository consistency at the reviewed commit. It does not establish
that the proposed M68 behaviours already work, or that the tracker has no remaining stale text.
