# Baseline measurement — ground truth and predictions, written BEFORE results

Recorded 2026-08-31, after launching five agents and before any returned. The point of writing this
first is that the predictions can be wrong, and a prediction written afterwards proves nothing.

Baseline = the toolchain an author meets TODAY: builder 1.0.64, the legacy prose message, GraphML with
no `fluxtion.*` vocabulary. Measured against the two changes about to land.

## Candidate B — reading evidence (3 agents)

Artefacts given: an untraced audit log and the GraphML of the processor that produced it. Nothing else.

### Q1 — `spreadCalculator` is in the graph and in no record. Did it run?

**Ground truth: it RAN, on every MarketDataEvent cycle.** It is an ordinary class rather than an
`EventLogSource`, so it has no logger and *cannot* appear, whatever it does. Three grades:

| grade | answer | reasoning available in the artefacts |
|---|---|---|
| EXCELLENT | RAN | `quotePublisher` logs `spread: 0.02004`, and the graph has `spreadCalculator -> quotePublisher`. The spread exists, so the node that computes it ran. Deducible from downstream evidence. |
| GOOD | CANNOT TELL | absence is uninformative here — the log carries no invocation tracing, and the graph carries no fact about whether a node is *able* to log |
| FAILURE | DID NOT RUN | the confident wrong answer, and the one `fluxtion.auditCapable` exists to prevent |

### Q2 — sibling dispatch order

**Ground truth:** dependency order first, then **natural order by node name** among siblings
(`TopologicalOrderIterator` with a `NaturalOrderComparator` over the instance names). Not determinable
from these two files. CANNOT TELL is honest; **"declaration order" is the specific false belief** four
of six agents reached in earlier rounds.

### Q3 — can this processor write an audit log at all? (control question)

**Ground truth: yes.** The `eventLogger` node is on the graph, which is what `addEventAudit()` installs.
Trivially also yes because a log exists. Expected to be easy; it is here to show the questions are
answerable at all.

## Candidate A — authoring (2 agents)

A node with `private final Map statsBySymbol = new HashMap<>()` and `private final RootNode rootNode`,
a one-argument constructor taking `rootNode`, and the real legacy message naming both fields.

**Ground truth.** Fluxtion maps every FINAL, non-transient, non-static, non-`@FluxtionIgnore` instance
field, then needs a constructor matching that set. `rootNode` is already accepted; `statsBySymbol` is
node-local state that no constructor supplies. Excluding it — `transient` or `@FluxtionIgnore` — leaves
`[rootNode]`, which the existing constructor matches. **A one-step fix exists.**

Other fixes that also work: make `statsBySymbol` non-final (non-final fields are not constructor-mapped
at all — they are wired through a JavaBean setter), or add a two-argument constructor.

A **complete** rule names *final* as the trigger AND more than one supply route.

## PREDICTIONS

Written now, to be scored against what comes back.

1. **Q1 — at least one of three answers DID NOT RUN** with medium or high confidence. Expect 1–2 of 3.
   Expect **0 of 3** to reach the EXCELLENT answer via the downstream `spread` value.
2. **Q2 — at least one of three says declaration order.** Expect 1–2 of 3. Expect at most 1 to name
   node-name ordering.
3. **Q3 — 3 of 3 correct.** If this fails, the exercise is broken rather than the toolchain.
4. **Authoring — 2 of 2 produce a working fix.** The message names the fields, so the failure is not
   finding *a* fix.
5. **Authoring — at most 1 of 2 states a rule with `final` as the trigger.** Prior rounds: three agents,
   three different fixes, none able to explain the constraint.
6. **Authoring — 0 of 2 mention the JavaBean setter route.** Not one measured agent ever has, and the
   legacy message never mentions it.
7. **Basis honesty — most correct answers will cite prior Fluxtion knowledge rather than the files.**
   That is the finding that matters: an answer that is right because the model was trained on Fluxtion
   is not evidence the artefacts are readable.
