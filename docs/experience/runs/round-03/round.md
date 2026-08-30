# Round 03 — 2026-08-30

## Environment

| | |
|---|---|
| Bundle | clean re-unzip of the verified P3 zip · **Doc set** `current/` **v2** |
| Task | **DIAGNOSIS, no code change** — "one event isn't handled properly": establish what ran, what never ran, and what the log cannot settle |
| Key | present (unused — no regeneration) · **Analyser** not reachable · **Port** free before and after |

Task shape deliberately unlike rounds 01–02, both of which were *authoring* tasks. This one exercises
coverage, proof-of-absence and the refusal to overclaim.

## Outcome: TASK SUCCEEDED, and it produced the most valuable round so far

The agent answered all three parts and — unprompted — drew the exact distinction the product is built on:
*"the log rules out a dispatch fault for all five events, and cannot address a logic fault because no
logic is instrumented — or present."*

## R3-A · A RULE I WROTE IS FALSE — and I verified it myself before recording this

`read-audit-log/SKILL.md` (v2, mine) stated:

> *a node that does not extend `EventLogNode` never appears at all*

**Verified false in this very bundle:**

```
$ grep -n "class RiskCheck" src/main/java/com/example/myapp/node/RiskCheck.java
8: public class RiskCheck {                     <-- no extends
$ grep -c riskCheck logs/audit-*.yaml
5                                               <-- appears in EVERY PriceEvent record
```

**How the error happened, which matters more than the error.** Round 01's agent *reported* that
`RiskCheck` "does not extend `EventLogNode` and never appears". That was an inference, not a measurement.
I promoted it to a normative rule in v2 without checking it. **This is the same failure the loop exists to
catch, performed by the person running the loop.**

The real mechanism, which the agent found by reading generated source: the generator emits
`auditInvocation(node, "name", "method", …)` at dispatch sites, and `eventLogger` records what it is
handed there — independent of the node's superclass. Extending `EventLogNode` is what supplies a node its
own `auditLog` handle for *its own* messages; it is **not** what makes the node appear.

Those are two different facts and v2 collapsed them into one wrong one.

**Unresolved, and the agent says so:** it further inferred that `EventLogManager` records only nodes
passed to `nodeRegistered`, and that `initialiseAuditor` registers five. Confirmed present at
`MarketProcessor:142-146` — but whether that is *the* governing rule is **not established**. Recorded as
an open question, not promoted. Twice is the lesson.

## R3-B · I broke the profile's runbook pointers by renaming a skill

`.analyser/project.fluxtion-settings` still names `load-audit-log`; I renamed it `read-audit-log` in v2
and never updated the profile. Anything resolving the pointer literally gets nothing.

**Process defect, not just a typo:** the loop seeds skills by copying a directory, so a rename silently
desynchronises the profile. Seeding must rewrite `runbook.N.*` from the doc set, and a round must check
the pointers resolve before the agent starts.

## R3-C · My worked example advertises evidence this project cannot produce

`read-audit-log` shows a record containing `priceThresholdAlert: { threshold: 500.0, … }` — a node from
**round 01's task**, absent from the shipped graph, which logs no values at all. I illustrated the skill
with the artefact of a previous experiment. A reader would look for output the project cannot emit.

## Bundle defects (not doc defects — for the bundle owner)

- **`MarketProcessorSupplier` lies at runtime.** It sets `setAuditLogProcessor(… System.out.println …)`
  with a comment saying an audited graph would otherwise look idle. Under Mongoose the processor is
  replaced by `ChronicleAuditCaptureService$ProcessorSink`, so **no `PriceEvent` record ever reaches
  stdout**. The agent lost a cycle believing nothing had been processed.
- **`data/output.txt` is 0 bytes after a full run.** The config declares the sink and the README claims
  feed → processor → sink, but no node publishes to it — and **sinks never appear in the audit log**, so
  the log cannot tell you this.
- **README cites a `ServerSmokeTest` that does not exist.**
- **The feed offset message appears on a first-ever run** in a clean directory (*"Found previous offset"*),
  making it unverifiable rather than merely surprising.
- **README's three-command recipe still hangs** — only the skill says to background it, and README is what
  a newcomer opens first.

## Files never opened

**`CLAUDE.md`, `AGENTS.md`, and `regenerate/SKILL.md`.** The agent verified the first two are
byte-identical and read neither.

Uncomfortable and worth stating plainly: **the agent that never opened my bootstrap doc reached a more
accurate conclusion about the logging mechanism than that doc contains.** For a diagnosis task the
bootstrap was not merely unused — its central rule would have misled. `regenerate` going unread is
expected and correct: no graph change.


## Changes made for round 04 (v3)

| Finding | Change | Root or symptom |
|---|---|---|
| R3-A | The false rule replaced in **both** places it appeared, stating the two facts separately: the *generator* decides appearance; `EventLogNode` supplies a node's own `auditLog`. `perfMon` named as a node that runs and never appears. | root |
| R3-B | **`seed-project.sh`** — seeding now REWRITES `runbook.N.*` from the doc set and **fails closed** if any pointer does not resolve. A rename can no longer silently desynchronise the profile. | root, and process |
| R3-C | The worked example replaced with a **real record from this bundle**, plus the point it makes: this log shows what ran, not whether the result was right. | root |
| R3 open | The `nodeRegistered` inference is recorded as **unresolved** and deliberately NOT promoted to a rule. | — |

**Not fixed here** — bundle defects for the bundle owner: the stdout `setAuditLogProcessor` comment that
Mongoose overrides, the 0-byte sink, the missing `ServerSmokeTest`, the first-run offset message, and
README's hanging recipe.

**Size:** v3 is not larger than v2 — the false rule was replaced, not appended to.


## R3-D · Owner correction, 2026-08-30 — the contract is an INTERFACE, and I knew that

The owner: *"You can implement the interface, no need to extend the event log node class."* Verified
against `fluxtion-runtime` 1.0.13:

```
public interface EventLogSource { void setLogger(EventLogger); }
public class EventLogNode implements EventLogSource { protected EventLogger auditLog; … }
```

`EventLogNode` is a **convenience class**; `EventLogSource` is the contract. My doc set said "extend
`EventLogNode`" as though inheritance were the only route.

**Why this is worse than an omission.** Java gives a class one inheritance slot, and a real application's
nodes usually want it for a domain base class. A reader following v3 would either contort their hierarchy
or conclude their node cannot be audited. The interface is the escape hatch and the doc hid it.

**And I already knew.** M40.2b in this repo turns on exactly this: `NodeLogging`'s javadoc says *"the real
contract is the interface `EventLogSource`; `EventLogNode` is only a convenience base that implements
it"*, and the analyser's `AUDIT_CAPABLE` set was built by walking the runtime for both. I established the
fact for the product and then wrote documentation contradicting it.

**Third instance in this loop of the same failure**, and the pattern is now unmistakable: R2-A (asserted
"two edits" untested), R3-A (promoted an agent's inference to a rule), R3-D (documented against knowledge
I had already verified elsewhere in this repo). None was caught by review; two were caught by running,
one by the owner. **Documentation written from memory is unreliable even when the memory is my own and
recent.**

Partial light on R3-A's open question, recorded but still not promoted: `Auditor` declares
`nodeRegistered(Object, String)`, and `EventLogManager` is an `Auditor` — so the agent's inference that
appearance depends on registration has a real mechanism behind it. Still not established as *the* rule.

**Fixed in v4** (v3 archived): the interface documented with a worked example, the reason to prefer it,
and the note that Fluxtion bases such as `SingleNamedNode` already inherit `EventLogNode`.

## R4-A · Owner correction, 2026-08-30 — the static authoring resources, which I had not read

The owner: *"I thought you were reading this https://fluxtion-playground.dev/build-with-ai — which has
instructions and prompts to use?"* I had not. Nor `claude.txt`. This repo's own
`docs/handoff/REVIEWER-ORIENTATION.md` lists `build-with-ai` as **"Start here"**, so the pointer existed
and I walked past it — a rule 6 miss, and the **fourth** instance of this loop's recurring failure.

**What five sources actually say** (checked 2026-08-30: `claude.txt`, playground `/CLAUDE.md`,
`spring-authoring/skill.md`, `spring-authoring/contract.md`, `/audit-replay`):

| Round finding | Published upstream? | Verdict |
|---|---|---|
| R2-A `transient` | **yes** — canon *and* the playground's source-gen triage table | would have been **prevented** |
| R1-G `nodeBeans` | **yes** — the Spring skill and contract | would have been **prevented** |
| R1-G `@OnTrigger` return | **yes** — canon | would have been prevented |
| R1-A / R3-A / R3-D audit contract | **no — none of the five** | **not** prevented |
| R1-E dispatch order is position in `nodeLogs` | **no** | **not** prevented |
| R2-E `auditLog` overloads · R2-F `getLatestEvent` | **no** | **not** prevented |
| bundle defects (R1-B/C, R2-C/D, R3) | n/a | unaffected |

**Answer to "would your outcomes be better?": yes on Fluxtion authoring, not at all on the audit log.**
Roughly a third of the doc findings evaporate — and the survivors are precisely the audit-log cluster,
which is this product's own subject. The upstream material is good at the framework and silent on the
record.

**Also a second wrong rule of mine, found by reading rather than running.** My `CLAUDE.md` §4.2 said *"A
bean not listed there is not in the graph."* `contract.md` is more precise: *"If present, only these beans
are added as explicit Fluxtion nodes; **referenced children are still discovered by Fluxtion**."* So a bean
reached by `constructor-arg ref` from a listed node **is** in the graph. Mine was over-broad and would
mislead anyone whose node is a child. Fixed in the doc set; UP-FLX-33 re-scoped to the genuinely silent
case — declared, in neither list, unreferenced.

**And the confound this exposes, which is larger than the missing analyser.** No round used the
playground's own authoring prompt — *"You have a compile/run loop — Fluxtion's errors are directive, so
when one fires it's telling you the fix"* — or gave the agent the canonical orientation. So rounds 01–03
measured a bundle **stripped of the authoring context the product already ships**. That is not a small
harness detail: two of the findings I treated as documentation gaps were artefacts of it.

**Settled at last — R3-A's open question, from the runtime source rather than inference.** Held unresolved
for two rounds because I had twice promoted an unchecked inference. `EventLogManager` (an `Auditor`) has
two distinct methods: `nodeInvoked(node, name, method, event)` runs at **every** dispatch site, which is
what makes a node **appear**; `nodeRegistered(node, name)` hands out a logger under exactly one test,
`if (node instanceof EventLogSource)`, which is what lets a node record **values**. Two mechanisms, two
facts — and the agent's round-03 inference that appearance depends on registration was **wrong**.
Verified in `fluxtion-runtime` 1.0.13 sources. Refusing to promote it twice was the right call.

## R3-A CORRECTED AGAIN — 2026-08-30, by an independent review (F1)

The settlement recorded above under R3-D — *"`nodeInvoked` at every dispatch site is what makes a node
APPEAR; `nodeRegistered` hands out a logger only to an `EventLogSource`"* — is **two mechanisms where the
runtime has three**, and the missing one is the one that matters for this product.

Verified in `fluxtion-runtime` 1.0.13 after the review named the lines:

1. `nodeRegistered` builds an `EventLogger` for **every** registered node and stores it in `node2Logger`
   **unconditionally**; it only *injects* it when the node is an `EventLogSource`.
2. `nodeInvoked` looks the logger up and calls `logNodeInvocation(traceLevel)`, which adds the trace
   **only when the configured level admits it**. `addEventAudit()` installs `tracingOff()`.
3. So with tracing off, an invoked node that logs no value **need not appear at all** — and an
   unregistered invocation resolves to `NullEventLogger` and appears nowhere either.

**Why this one is worse than the earlier two.** R3-A was a false rule about a mechanism. This one
overstates what an ordinary **untraced** record proves — it would let absence be read as proof a node did
not run. The analyser already refuses that conflation (it distinguishes traced from untraced precisely
because absence supports different claims in each), so I contradicted the product's own model while
writing documentation for it, and carried the error into two public upstream issues.

**Fourth correction to the same subject in this loop**, and the first found by an independent reviewer
rather than by me or the owner. Every previous one was caught by reading an artefact; this one needed
someone reading the *same* artefact more carefully than I had. That is an argument for the review step
that no amount of self-checking replaces.
