# SPEC (PROPOSED) — authoring token lowering: state facts locally, send the model the residual

**Status** PROPOSED, 2026-10-13 · **Milestone** M72 · **Owner direction** 2026-10-12/13 (quoted in §2)
**Evidence** PR #129 (branch `feature/g14-model-grid`, open):
`docs/handoff/evidence/authoring-ladder-2026-10-12/RESULTS.md`,
`docs/handoff/evidence/g14-model-grid-2026-10-11/RESULTS.md`, and their `results/*/score.json`
**Baseline** a re-run of the authoring ladder (rungs 2–5) on the released stack (analyser 1.33.0, fluxtion-web #8
and #10, runtime 1.1.1, compiler 1.0.78) is **in progress**. Every acceptance test below is stated against
that re-run, not against the 2026-10-12 ladder, which predates the shipped levers in §1.4.
**Companions** [`spec-authoring-modes.md`](spec-authoring-modes.md) (tiers; canonical architecture) ·
[`spec-authoring-mode-selector.md`](spec-authoring-mode-selector.md) (its *fourth partial evaluation* table) ·
[`spec-spring-authoring-edit-loop.md`](spec-spring-authoring-edit-loop.md) (§F local preflight, §H `context {sections}`) ·
[`spec-baselines.md`](spec-baselines.md) (M39) ·
[`spec-minimal-authoring-instructions.md`](../proposals/upstream-specs/spec-minimal-authoring-instructions.md) (N1–N4, P1, P3)

**Source tags.** Every number carries one: **[L]** the ladder RESULTS.md or its score.json files; **[G]** the G14
grid / A/B RESULTS.md; **[D]** derived here by arithmetic over the score.json files (method stated);
**[S]** a session measurement made 2026-10-12/13 that is **not** in committed evidence; **INFERRED** an estimate
whose assumptions are stated beside it. A number with no tag is a defect in this document.

---

## 1. The problem, measured

### 1.1 Every scored ladder run crossed the 100k tier

- Haiku 5.5 sent **3–25 prompts over 100k per run**, with maximum prompts of **105k–173k** [L]. Sonnet 5's maxima
  were 151k–210k [L].
- Across the twelve scored Haiku 5.5 runs, **177 of 542 requests (33%)** were over 100k; per run the share ranged
  7%–52% [D: sum of `usage.over100k` / sum of `usage.requests`]. Sonnet 5: 219 of 426 (51%) [D].
- Haiku 5.5 bills **5× per token above 100k** [L]. Supplied pricing [S]: $0.10 / $0.50 per M input / output at or
  below 100k, $0.50 / $2.50 above; cache read $0.01 / $0.05; cache write $0.125. Re-read the provider's pricing page
  before relying on these (CLAUDE.md rule 6); the CLI's billed totals in score.json already include the tier [L].
- Haiku 5.5: 11/12 PASS, **$2.34 total, median $0.190/run** [L]. Sonnet 5: 3/4, $13.67, median $3.425 [L].

### 1.2 What predicts cost on this evidence

Pearson r against `usage.costUsd`, twelve Haiku 5.5 ladder runs [D]:

| driver | r |
|---|---|
| `usage.maxPrompt` | **+0.96** |
| `usage.over100k` | **+0.94** |
| `reads.toolResultBytes` | +0.69 |
| `usage.turns` | +0.60 |
| `reads.generatedReads` | +0.29 |

P3 of the minimal-instructions spec ("cost tracks TURNS", r = +0.955) was measured on Haiku 4.5 rounds under the
earlier price structure. On this evidence, with a 5× tier, **how far the prompt grows** predicts cost better than how
many turns there are. Caveats: n = 12, correlation not cause, and `over100k` is partly priced into the cost by
construction — which is exactly why it is the lever.

### 1.3 Where the prompt goes

- Base prompt ≈ **22k tokens**; growth ≈ **55% tool results**, ≈ **32% model output**, of which ≈ **20% thinking** [S].
  With maxima of 105k–173k [L], each Haiku run therefore accumulated ≈ 83k–151k tokens above the base [D: max − 22k].
- Generated-code reads: Haiku 5.5 ladder median **7 per run (3–9)**, and those reads were **37% of all tool-result
  bytes** (21%–47% per run) [D: `reads.generatedBytes` / `reads.toolResultBytes`]. The ladder predates
  fluxtion-web #8 (INFERRED from dates: ladder 2026-10-12, #8 merged 2026-10-13).
- **Unmeasured:** how the other ≈ 63% of tool-result bytes splits between audit-log text, build output, server output
  and documentation. score.json does not categorise them. Proposal L0 fixes this; until then, any saving claimed for
  L1 or L4 is an estimate.

### 1.4 Levers already shipped (the baseline, not proposals)

| lever | where | measured effect |
|---|---|---|
| read Spring XML + compiler messages, not generated code | fluxtion-web #8, analyser 1.33.0 (`add-a-node`) | A/B v2: median generated-code reads **4 → 1**, tool-result bytes −20%, cost −21%, 3/3 correct both arms [G] |
| `run-and-export.sh`: one command to run, settle, export, stop | analyser bundle | authors used 5–7 turns for that step [S] |
| `audit-compare.py`: business/framework split, MATCH/DIFFER/INCONCLUSIVE | fluxtion-web #10; `docs/skills/spring/add-a-node/` | not measured for tokens |
| trace `annotation:` (why a callback ran, from the log) | runtime 1.1.1 + compiler 1.0.78 | not measured for tokens |

**How strong the A/B is** [G]: two runs of the same pristine control arm in different sessions differed by 26% in
cost and 18% in bytes, so the −20% / −21% deltas are inside run-to-run noise at n = 3. **The robust result is reads
4 → 1.** This spec quotes the byte and cost deltas as indicative only.

### 1.5 Per-run cost split (INFERRED)

The transcripts are not committed [L], so per-request token buckets cannot be checked. Assume an over-100k request
averages ≈ 120k tokens of mostly cache-read input ($0.05/M → ≈ $0.006) and an under-100k request ≈ 60k ($0.01/M →
≈ $0.0006). Over the twelve Haiku runs: 177 × $0.006 ≈ $1.06 and 365 × $0.0006 ≈ $0.22, so **≈ 83% of estimated
input spend and ≈ 45% of the $2.34 total came from over-100k requests**; the remainder is output and cache writes.
Moving every such request under the tier would save at most ≈ 0.8 × $1.06 ≈ **$0.85, ≈ 36%** of the total. The
averages are assumptions; error bars are wide and L0 replaces this paragraph with a measurement.

---

## 2. The principle — Higgins lowering

The owner's direction, verbatim:

> "In reality the skill should be using the analyser to filter etc access to the audit log. Or pass a text log
> through the binary parser so smaller logs are sent to the cloud"

> "This is a huge saving. Effectively partial evaluation and greedy algorithms across multiple processes and
> activities. Design and diagnostic are two separate activities that can be combined"

> "This is the same idea as Higgins lowering: if a set of facts can be stated at a higher level then move it there
> and then reuse cheaply"

Stated so it can be built against:

1. **Lower each fact to the highest level at which it can be stated**, and compute it locally — in the compiler,
   the analyser or a bundle script — not in the model's context. "The graph compiled as declared" is a compiler fact;
   "these two runs log the same business entries" is an analyser fact; "the server stopped cleanly" is a script fact.
2. **Send the model only the residual**: the part of the question no local stage can answer — selection, judgement,
   the explanation to the person. This is the mode selector's *fourth partial evaluation* (evidence → the toolbench)
   carried into the authoring loop, and P1 of the minimal-instructions spec (discovery aids expire once the answer is
   precomputed) applied to tool output instead of prose.
3. **Design and diagnosis are separate activities that share lowered facts.** Design produces a receipt (what was
   declared, what was generated); diagnosis consumes the receipt and the audit log. Neither needs the other's raw
   material, so they can run in separate contexts — or one context that never holds both raw inputs.
4. **Reuse the lowered fact cheaply**: across turns (a stable, small answer instead of a re-read), across sessions
   (a receipt or a baseline summary on disk), across runs (the M39 derived summary).
5. **Lowering never replaces the evidence.** The audit log stays the evidence (trust structure); every projection
   names what it omitted and how to get it back. A lowered fact that silently drops a qualification is a defect,
   as §H of the edit-loop spec already requires for `context {sections}`.

---

## 3. Proposals

Each item: **change** · **driver** (§1) · **expected saving** with basis · **acceptance** on ladder rungs 2–5 ·
**regression check** (rule 8). Common acceptance protocol (all items): Haiku 5.5, n = 3 per rung, **baseline and
treatment interleaved in the same session window** (the A/B showed between-session noise of 26% [G]); PASS count must
not fall below the baseline's; the stated metric must move by more than the baseline's own min–max spread on that
rung; otherwise the item is reported as *no effect shown*, not as a saving.

### L0 — Measure the split before claiming it (prerequisite)

- **Change.** The G14 runner's scorer records per request: `input_tokens`, `cache_read_input_tokens`,
  `cache_creation_input_tokens`, `output_tokens`, and the tier; and classifies tool-result bytes as
  `auditLog` / `generated` / `build` / `server` / `docs` / `analyser` / `other`. P3a already requires the cache buckets.
- **Driver.** §1.3's unmeasured 63% and §1.5's inferred split.
- **Saving.** None directly; it decides whether L1 or L4 is worth building.
- **Acceptance.** Re-scoring the in-progress baseline yields a category table whose bytes sum to `toolResultBytes`
  per run, and a cost recomputed from the buckets within 2% of `usage.costUsd`.
- **Regression check.** A scorer unit test on a frozen synthetic transcript: categories sum to the total; deleting a
  category rule makes the sum assertion fail.

### L1 — A readable tabular projection is the author's default log read

- **Change.** `read` gains a profile (proposed `read {profile: "table"}`; `read {fields}` stays the precise form):
  one row per record, columns = the business keys present, framework trace keys collapsed into a dispatch column
  (`node.method@annotation`), identity hashes masked as `audit-compare.py` masks them. The reply ends with an
  **omission statement**: records not shown, keys omitted, values truncated, the `recordIndex` range, and the exact
  call that returns the omitted data. The `load-audit-log` / `add-a-node` skills say to use it (or `fields`, `series`,
  `aggregate`) and never to `cat`/`grep` the export.
- **Driver.** Tool results (≈ 55% of growth [S]); the owner's first quote.
- **Saving.** ≈ **16× fewer tokens** than the raw log for one audit log [S, one sample]. The share of tool bytes that is
  raw log text is unmeasured (L0), so the per-run saving is unknown; if it were a quarter of the non-generated bytes,
  a 16× projection would remove ≈ 15% of tool-result bytes (INFERRED, illustrative only).
- **Acceptance.** L0 shows `auditLog` bytes per run fall ≥ 75% against the baseline, every rung's oracle still passes, and no run reads the raw export with a shell tool.
- **Regression check.** A frozen fixture's table projection: byte count recorded (not a token claim, as §H requires);
  every value in the table equals the same field from `read` raw; removing the omission statement, or dropping a
  record without counting it, fails separate assertions. `CanonicalSkillsTest` asserts the skills name the profile.

### L2 — Lower `audit-compare.py` into the analyser as `compare`

- **Change.** A read-only `compare {against: <export path>}` that reads the second file with the analyser's own
  reader (RecordBreak, C32/C33 positional trace rule) and returns MATCH / DIFFER / INCONCLUSIVE / NOT-COMPARABLE, the
  business and framework difference counts, and the first N differences as `recordIndex` anchors. It changes no
  session state (rule 9 unaffected); the path is confined like other agent-supplied paths.
- **Driver.** Turns and re-reads when the script is missing or misread; two readers of one format.
- **Saving.** **Small for tokens** — the script already prints a compact verdict (INFERRED). The real gain is one
  reader instead of two, and an answer available to clients without Python.
- **Acceptance.** On rung 4/5 before/after captures, `compare` and `audit-compare.py` agree on verdict and counts in
  every run; tokens per comparison step are no higher than the script's.
- **Regression check.** Run `tools/test_audit_compare.py`'s fixtures through both; any disagreement fails. A mutation
  that treats a trailing `annotation` as framework must fail C33.
- **Owner decision.** [`spec-baselines.md`](spec-baselines.md) D-N3 says a comparison "never renders a verdict". This
  verdict is scoped to *same-input equality of logged business entries*, not to "normal"; the owner must confirm the
  distinction holds, or `compare` returns the two facts without the word MATCH.

### L3 — Lowered design facts: the compiler's receipt answers "did it compile as declared"

- **Change.** The compiler's validation report / authoring receipt carries, as structured data: declared vs
  generated nodes (with §F's aliases and canonical ids), edges, per-event-type dispatch order, guards, and
  unresolved references. The analyser exposes the same facts from GraphML through `topology` / `context {sections}`
  for a session that has no receipt. The skill names the receipt as the single answer to the design question.
  Producer side is upstream work (record in `upstream-asks.md`).
- **Driver.** Generated-code reads: 37% of Haiku ladder tool bytes [D], median 7 reads [D]; after #8, a median 1 read
  remains [G].
- **Saving.** Against the new baseline, the remaining ≈ 1 read per run — its bytes are measured by L0. Against the
  2026-10-12 ladder it would have been up to 37% of tool bytes [D], most of which #8's guidance already took
  (INFERRED from the A/B).
- **Acceptance.** Median generated-code reads **0** on every rung, PASS not lower, and no answer cites generated
  source as evidence.
- **Regression check.** A receipt-schema test on the rung-4 fixture: dropping an edge or a guard from the receipt
  fails; the skill-text test asserts the receipt is named and generated source is not.

### L4 — Keep every request under 100k

- **Change.** (i) Bundle scripts cap their own output: full output to a file under `logs/`, a bounded summary to
  stdout (exit status, the first error with its location, the file path); `mvn -q -ntp` by default with a `--verbose`
  escape. (ii) Server output goes to a file; the script prints lifecycle facts only. (iii) Skill guidance: start the
  diagnosis activity in a fresh context seeded with the design receipt (L3) and the run's export path, rather than
  carrying the design transcript forward (principle 3). (iv) Never re-read a file already summarised in the context.
- **Driver.** `over100k` (r = 0.94 [D]); `maxPrompt` (r = 0.96 [D]); 33% of Haiku requests over the tier [D].
- **Saving.** Upper bound ≈ **36%** of Haiku run cost if no request crossed the tier (INFERRED, §1.5). Partial
  achievement is likely; (iii) carries the most uncertainty because a fresh context re-pays the ≈ 22k base [S].
- **Acceptance.** Median `over100k` per run ≤ 1 on every rung, median cost below the baseline's minimum on at least
  three of four rungs, PASS not lower.
- **Regression check.** A script test runs each bundle script against a failing and a passing fixture and asserts
  stdout ≤ a fixed line budget while the full log file holds the error; removing the cap fails it.

### L5 — One-call diagnosis summaries

- **Change.** A query, not a workflow verb (the `add-a-node` skill rules out a compound build/run verb):
  `context {sections: ["evidence"]}` or `coverage` extended to return, per node, logged yes/no with level,
  record count, first/last `recordIndex`, and for each business key its first/last/min/max — the answer to "did the
  new node run, when, and with what values" in one reply.
- **Driver.** Turns (r = 0.60 [D]) and the read-around-and-look pattern.
- **Saving.** A few turns per run (INFERRED; unmeasured). Each turn re-reads the whole context, so a turn saved late
  in a run is worth more than one saved early.
- **Acceptance.** Analyser calls in the diagnosis phase fall by ≥ 50% (L0 counts them), PASS not lower.
- **Regression check.** Summary values on a frozen fixture equal `aggregate` / `series` over the same keys; a mutation
  that drops a node that never logged must fail (the uncovered node is the finding, M40).

### L6 — Text-log → binary pre-parse, and why compression is not the route

- **Finding.** gzip + base64 of an audit log was **unreadable to the model** [S]: it removes bytes from storage, not
  meaning from the context, and base64 does not tokenise compactly (INFERRED). Rejected.
- **What the owner's second clause buys.** "Pass a text log through the binary parser" is valuable as **local
  parsing**: the analyser's index already parses once and answers `aggregate` / `series` / `read {fields}` from it.
  The saving is the projection sent (L1, L5), not the encoding. A binary export (M52) helps the *producer* and the
  local parse; the model must still receive text.
- **Proposal.** No new work beyond L1/L5; a skill line saying never to paste or encode the raw log.
- **Regression check.** The skill-text test asserts the line; L1's fixture covers the projection.

---

## 4. Ranking and the first slice

Score = expected saving × confidence ÷ cost to build (each High / Medium / Low).

| rank | item | saving | confidence | cost | why |
|---|---|---|---|---|---|
| 1 | **L0** measure the split | — | High | Low | every other estimate depends on it |
| 2 | **L4 (i)(ii)** capped script output, quiet build | High | Medium | Low | strongest measured driver; bundle-only change |
| 3 | **L1** tabular projection + skill default | High? | Low–Medium | Medium | one 16× sample [S]; share unmeasured |
| 4 | **L3** design receipt | Medium | Medium | Medium–High | measured driver, much already taken by #8; upstream |
| 5 | **L4 (iii)** design/diagnosis split | High? | Low | Low | biggest prompt cut, but base re-paid; model-dependent |
| 6 | **L5** diagnosis summary | Low–Medium | Low | Medium | turns are a weaker predictor here |
| 7 | **L2** `compare` verb | Low | High | Medium | correctness and one reader, not tokens |
| — | **L6** compression / base64 | none | High | — | rejected [S] |

**Recommended first slice:** L0 + L4 (i)(ii) + the skill text of L1 using the existing `read {fields}`, `series` and
`aggregate` (no new analyser code). Measure it on rungs 2–5 against the in-progress baseline under §3's protocol.
Build L1's `table` profile only if L0 shows raw-log text is a material share of tool bytes.

---

## 5. Risks, and what would show the idea is wrong

- **Correctness — lowering must not hide evidence.** A projection that omits the record that disproves the
  conclusion is worse than the raw log. Mitigation: every lowered reply states what it omitted and the call that
  returns it (§2.5); the audit log remains the evidence and is never deleted or rewritten; acceptance always includes
  the oracle, not cost alone.
- **Model dependence.** Haiku 4.5 failed the chart step in all three grid runs [G]; Sonnet 5 claimed success on a
  rung-3 run whose counter ran on every trade [L]. A lever that helps Haiku 5.5 may do nothing for another model, and
  a compact answer can make an overclaim easier to write. Results are per model and are not transferred.
- **Measurement noise.** n = 3 per rung; same-arm sessions differ by 26% in cost [G]. Only interleaved arms and
  effects outside the baseline's spread count (§3).
- **Fresh-context cost.** L4 (iii) re-pays the ≈ 22k base [S] and loses implicit context; it may cost more turns than
  it saves.
- **Cache floor.** Shrinking early outputs can push a prefix under a model's minimum cacheable size (P3a); L0's cache
  buckets detect it.
- **The idea is wrong if:** with L0 in place, (a) runs whose `over100k` is ≤ 1 do not cost materially less than
  matched runs above it; or (b) L1/L4 cut tool-result bytes ≥ 50% and cost does not move outside the noise band; or
  (c) PASS falls on any rung under a lowering lever — evidence that the residual sent was not sufficient.

---

## 6. What this spec could not establish from committed evidence

- The prompt composition (22k base, 55% / 32% / 20%), the 16× projection ratio, the gzip+base64 finding, the
  pricing and the 5–7 turns are **session measurements [S]**, not committed. Each should be committed as a fixture
  measurement with L0 before it is quoted externally.
- The per-run cost split (§1.5) is **INFERRED**.
- The category split of non-generated tool bytes is **unmeasured**.
- Whether the ladder bundle carried `run-and-export.sh` and `audit-compare.py` is not recorded in score.json; the
  in-progress baseline should record the bundle revision in `config.json`.
