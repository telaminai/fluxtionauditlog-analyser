# Generalization beyond N=1 — turning the coherence thesis into evidence a stranger can emit

Sibling to [`upstream-asks.md`](./upstream-asks.md), same discipline: every item is written from something that
**actually happened**, tagged *measured* (checked against generated code, a real audit log, or a live run) or
*proposed* (an experiment/artifact-format ask, not a measured defect — labelled as such so it is never mistaken
for a fact). The distinction is load-bearing here precisely because the subject is credibility.

**Where these came from.** All five were raised on **2026-08-24** from a real session against `maker-fxoc`: an
O(N)→O(1) ingest fix, a no-alloc flush, a per-customer DEBUG audit-logging feature, a live UAT deploy, and an
end-to-end analyser study. That session was, in effect, another run *inside* the N=1 orbit — the framework author's
tools, on the author's domain, with an author-adjacent operator. It produced sharp evidence *for* the coherence
thesis, and, by doing so, made precise what is still missing to step **outside** that orbit.

## The thesis, and why N=1 is the whole risk

The suite's claim is a **coherence chain**: the compiler generates dispatch, the audit log is a faithful
projection of what those `.class` files actually executed, and the analyser is a faithful projection of the audit.
When the chain holds, the output is *trustable*, not merely fast — that is the product. This session is direct
evidence the chain holds:

- The ingest bottleneck was fixed with a **pure hand-written data-structure change** and **zero diff to the
  generated processor or dispatch** — and the layer breakdown could then attribute **87.6 ns/event, <0.6% of a
  flush**, to Fluxtion dispatch. The framework was provably not the cost. *(measured — `dispatch-result.txt`,
  `layer-breakdown.csv`, git diff scope)*
- Per-customer prices set at DEBUG on **live UAT** matched the config-implied bucket spreads (2 bp / 10 bp /
  1000 bp) once plotted from the audit. Coherence survived a deploy and a wire. *(measured by inspection)*

But every one of those checks was done **by the author's toolchain, verified by an author-adjacent human**. The
open question is not "does the chain hold" — I watched it hold — it is "does it hold, *and can a stranger prove it
held*, when the stranger built the graph and neither of us picked the domain." The asks below are the
instrumentation that makes the answer a number instead of a testimonial.

| Field | Meaning |
|---|---|
| **ID** | stable handle (`UP-GEN-NN`); quote it upstream |
| **Target** | which repo must expose/emit the artifact |
| **Evidence** | the this-session fact the ask is built on |
| **Payoff** | the specific N=1→N>1 signal it produces |

Status: ☐ not filed · ◐ filed · ☑ landed.

---

## UP-GEN-01 ☐ A coherence-fidelity metric the toolchain emits *(proposed)*

**Target** `fluxtion` (compiler + audit) → consumed by `analyser` · **Priority** high — this is the keystone

The thesis *is* the coherence, so make coherence a printed number. Emit, at build/run time, a machine-readable
fidelity report that asserts the audit is a faithful projection of the compiled graph:

- every dispatch path in the generated processor appears in the audit's callback vocabulary (no silent path);
- every audited node maps 1:1 to a graph vertex (no orphan log line, no unlogged vertex);
- score = covered paths / total paths, plus the explicit list of any leak.

**Evidence — measured.** In this session the breakdown could put dispatch at <0.6% *only because* the audit
callbacks (`onMultilevelMarketData`, `onPublishTick`, `onSnapshot`, …) mapped cleanly onto the generated dispatch
— but that mapping was confirmed **by eye**, by someone who knew the graph. Nothing in the artifacts asserts it.

**Payoff.** An independent build prints `coherence = 1.00` (or shows exactly where it leaks) with no human in the
loop. This is the single strongest generalization signal: it converts "trust the chain" into a value a stranger
reproduces on their own graph. When a stranger's coherence report matches the shape of ours, N=1 is broken on the
axis that actually carries the claim.

---

## UP-GEN-02 ◐-worthy · ☐ Label-coverage measure for the audit *(measured basis; the measure itself is proposed)*

**Target** `fluxtion` (audit) → surfaced by `analyser` · **Priority** high

The analyser's value collapses if audit labels are lazy — a dependency I named as the upstream moat, and one the
framework does **not** enforce. Emit a coverage measure: of the node's state transitions / published outputs, what
fraction emit a **labelled, typed** audit key (not a bare `toString`, not an unlabelled scalar). Expose it per node
and rolled up per processor.

**Evidence — measured.** To plot per-customer prices this session I had to **add** audit keys
(`<symbol>.<account>.bid` / `.ask`) to `IndicativePricerNode`. The computation already ran; the *projection did not
exist until I labelled it*. That is the label-quality dependency, caught in the act: analysability was gated on a
human choosing to emit typed keys, with nothing measuring the gap beforehand.

**Payoff.** An independent build can show its audit is analysable **before** anyone tries to analyse it — turning
"log-message quality is a moat/risk we assert" into "coverage = 0.82, here are the unlabelled transitions." It
makes the one irreducibly-human input to the chain a measured, improvable quantity rather than folklore, which is
exactly what a skeptic needs to believe the chain will hold for *their* graph, not just a disciplined author's.

---

## UP-GEN-03 ☐ A domain-neutral reference corpus with replay-scored acceptance *(proposed)*

**Target** `analyser` (owns the corpus + scoring) · **Priority** high — the closest thing to an N>1 test the repo can own

Formalise a corpus of audit logs from processors in domains **other than market-making**, each with committed
expected analyser outputs (series it must resolve, aggregates it must compute, topology it must render), and run
the analyser against them in CI. Seed it with the **supermarket POC** (the one existing non-financial graph) and
require ≥2 further unrelated domains before the corpus is considered load-bearing.

**Evidence — measured (partial).** The supermarket POC already exists and is this project's single non-financial,
falsifiable data point (blogged, with committed predictions). It is the proof-of-concept that a corpus is
buildable — and that N currently equals ~1 non-financial domain.

**Payoff.** Regression-tests the analyser's generalization against non-author, non-financial graphs on every
change, instead of trusting that "it worked on the market maker." Each unrelated domain added is a literal
increment of N, owned and re-runnable rather than anecdotal. The corpus is where "believe it generalizes" becomes
a green check that a stranger can read.

---

## UP-GEN-04 ☐ A blind-author transcript format *(proposed)*

**Target** `fluxtion` design service (authoring loop + FLX rejection codes) · **Priority** medium-high

The design service already couples an author-model loop with compiler-feedback codes and a retrospective. Have it
emit a **self-contained, comparable transcript** of an authoring run: the ordered sequence of author attempts,
compiler rejections (by FLX code), and recoveries, ending in a graph that compiled — with a flag asserting the
framework author was **not** in the loop.

**Evidence — measured.** My two changes this session (reverse index, audit keys) were **author-adjacent** — I
carried prior mental context about the design. That is honestly *why* they are N=1 evidence and not more. The gap
is not "can a model author a graph" — it is "can we *audit* that it was authored without the framework author, and
score it the same way we score ours."

**Payoff.** A stranger's authoring run becomes an artifact directly comparable to the author's: same rejection
codes, same recovery-loop shape, same end-state coherence report (UP-GEN-01). It is the mechanism that lets a
build "neither of us touched" be measured, not just asserted — the step that actually removes the author from the
denominator.

---

## UP-GEN-05 ☐ Determinism attestation across the deploy boundary *(measured basis; attestation is proposed)*

**Target** `mongoose` (runtime) + `fluxtion` (replay) · **Priority** medium-high

Emit a signed attestation that a **replay** through the same `.class` files reproduces the live audit
byte-for-byte — a record an independent deployer can regenerate, not a claim they must take on faith. Tie it to the
data-driven Clock so the replayed run is provably the same execution, not a re-simulation.

**Evidence — measured (by inspection).** I turned DEBUG on **live UAT** and the per-customer prices matched the
config-implied spreads exactly — coherence demonstrably survived the deploy boundary. But it "matched by
inspection," by me, once. Nothing in the run *attests* that a replay would reproduce it bit-for-bit.

**Payoff.** Converts the strongest single anecdote of this session — the chain holding across a real deploy — into
a check anyone can rerun on their own deployment. Determinism is the property the whole coherence chain rests on;
an attestation a stranger can produce is what makes "faithful projection" a verifiable claim off the author's
machine.

---

## The falsification, stated plainly

None of these is a demo the author runs. Each is an **artifact a stranger emits**: a coherence number (01), a
label-coverage number (02), a green corpus check per new domain (03), a comparable blind-author transcript (04), a
replay attestation (05). N=1 does not fall when the author shows the chain works one more time — it falls the first
time an **independent author, in a domain neither of us chose, emits these same numbers and they match**. This file
records the upstream instrumentation that makes that match measurable; the experiment that produces it is the next
thing worth funding.
