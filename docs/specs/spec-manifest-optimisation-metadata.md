# Spec — manifest optimisation metadata: integrating a jar without introspecting it

**Status:** PROPOSED · **Item:** M46/W14 · **Date:** 2026-09-06
**Extends** [`spec-component-catalogue.md`](spec-component-catalogue.md), which covers *capability*
metadata (`Fluxtion-Provides`, `Requires`, `Constructor`, `Consumes`). This spec covers *optimisation
and assurance* metadata — the facts a consuming build would otherwise have to rediscover by
introspecting vendor bytecode.
**Evidence:** [`round-58`](../experience/runs/round-58/NOTES.md) ·
**Plan:** [`spec-generated-dispatch-performance.md`](spec-generated-dispatch-performance.md) Part IV

---

## 1. The problem this removes

Four items in the M46 plan need facts about components the integrator did not write:

| item | needs to know | currently would |
|---|---|---|
| **W4** `noReentrancy` | can any node raise a re-entrant event or register a callback? | scan vendor bytecode |
| **W5** ambient-read scan | does any trigger-reachable method read wall clock, randomness, IO, mutable static? | scan vendor bytecode |
| **W11** service dispatch generation | which methods handle which service registrations? | reflect over `@ServiceRegistered` |
| **W13c** recordable signatures | can this service's arguments and returns be captured for replay? | inspect signatures |

Every one of those facts is **already known to the component's own build**, which has the source, the
annotations and the ability to run the checks. Rediscovering them at every integration is the same
waste the component catalogue removes for capabilities — and, per Higgins lowering, the same defect:
**a determination re-derived below its binding point.**

> **The producing build computes the fact once. The consuming build reads it.**

## 2. Design rules

**R1 — Derive, do not ask.** Most facts are computable from existing annotations plus bytecode by the
producing build. New annotations are justified *only* where the fact is a **promise the author makes
that cannot be derived** (§5).

**R2 — Absence is not a claim.** A jar with no attribute means **unknown**, never "safe". Strict modes
(`noReentrancy`, replay assurance) must **fail** on unknown, not assume. This is the difference between
a metadata system and a trust system.

**R3 — Generated, never hand-written.** The catalogue spec's core claim, reinforced by a real defect:
a manifest without a trailing blank line silently drops its last attribute
([walkthrough](spec-authoring-session-walkthrough.md) §*What simulating it exposed*). Hand-maintained
optimisation metadata would be a second authority for a derivable fact.

**R4 — The consumer may verify, and must be able to.** The consumer has the bytecode. Every declared
fact must be re-checkable, so a wrong or stale manifest is detectable rather than merely trusted.
Verification is optional in normal builds and **mandatory** when a strict mode relies on the fact.

**R5 — Every attribute carries its provenance.** The tool and version that computed it, so a fact
produced by an older analysis can be identified and recomputed.

---

## 3. Proposed manifest attributes

Per entry point, alongside the existing catalogue attributes.

```
Fluxtion-Analysis: fluxtion-maven-plugin/1.4.0            # R5 provenance
Fluxtion-Reentrant: false                                 # W4
Fluxtion-Ambient: none                                    # W5
Fluxtion-Trigger-Kind: void                               # guard elision
Fluxtion-Service-Consumes: FxRates#onFxRates, Limits#onLimits    # W11
Fluxtion-Service-Exports: PricingControl                  # W13a
Fluxtion-Replay-Capture: recordable                       # W13c
Fluxtion-Allocation: free                                 # zero-alloc claim
```

### 3.1 `Fluxtion-Reentrant` — W4

`false` when no trigger-reachable method calls the re-entrant dispatch API, registers a callback, or
injects `EventProcessorContext`, `Callback` or `DirtyStateMonitor`. `true` otherwise.

**Consumer effect:** `noReentrancy` becomes decidable across a graph of vendor components without
scanning any of them. **Absent ⇒ the flag fails the build**, naming the component.

### 3.2 `Fluxtion-Ambient` — W5

`none`, or a comma-separated list of what was found: `clock`, `random`, `io`, `static-mutable`,
`native`, `service-call`.

**Consumer effect:** the determinism precondition, and therefore every replay and verification claim,
resolves without scanning vendor jars. **This is the attribute the whole assurance story rests on.**

### 3.3 `Fluxtion-Trigger-Kind` — guard elision

`void` when every trigger returns `void` (via `failBuildIfMissingBooleanReturn = false`), `boolean`
when they return a dirty flag, `mixed` otherwise.

**Consumer effect:** the generator knows without reading bodies whether a component's nodes need dirty
guards at all — the largest single configuration lever measured
([round-58 §Addendum 6](../experience/runs/round-58/NOTES.md)).

### 3.4 `Fluxtion-Service-Consumes` / `-Exports` — W11, W13a

`Type#method` pairs derived from `@ServiceRegistered` / `@ServiceDeregistered` and `@ExportService`.

**Consumer effect:** the generator emits the registration dispatch directly. **This is the attribute
that removes runtime reflection and, with it, the native-image reflection configuration a user
currently has to write and can get wrong.**

### 3.5 `Fluxtion-Replay-Capture` — W13c

`recordable`, or `unrecordable:` followed by the offending methods and the reason (unserialisable
argument, mutable return, callback-shaped, returns a service).

**Consumer effect:** the build can state precisely what it cannot reproduce, at build time, rather
than discovering it as a replay divergence.

### 3.6 `Fluxtion-Allocation` — the zero-allocation property

`free` when no trigger-reachable path allocates in steady state; otherwise `allocates`.

The one attribute here that is **hard to compute soundly** — escape analysis is the compiler's job,
not a scanner's. Recommended initial semantics: a **conservative syntactic** check (no `new`, no
boxing, no varargs, no string concatenation on trigger-reachable paths), reported as
`free:syntactic` so the weaker guarantee is visible in the value rather than assumed from it.

---

## 4. Where each fact binds

```
component author        writes annotations and bodies
        │
        ▼
COMPONENT'S OWN BUILD   ← binds here: it has the source, and runs the checks
  fluxtion:catalogue       computes capability + optimisation metadata
        │
        ▼
    the jar manifest    the serialised determination
        │
        ▼
INTEGRATOR'S BUILD      reads; verifies when a strict mode depends on it;
                        never re-derives
```

Facts that cannot be computed by the producing build — because they depend on the *assembled graph*
rather than one component — stay with the integrator: whether **this** graph is re-entrancy-free is a
conjunction over its selected components, computed from their declared attributes.

---

## 5. Do we need new annotations?

**Mostly no.** Derivable from what exists today: `Fluxtion-Reentrant`, `Fluxtion-Ambient`,
`Fluxtion-Trigger-Kind`, `Fluxtion-Service-Consumes`, `Fluxtion-Service-Exports`,
`Fluxtion-Replay-Capture`.

**Two cases genuinely need author input**, because they are promises rather than observations:

**5.1 Purity of an injected service.** A component consuming `FxRates` cannot know whether the
*implementation* it is handed is deterministic — that arrives at integration. The service **interface**
author can declare intent:

```java
@ExportService(deterministic = true)     // new attribute on an existing annotation
public interface FxRates { double lookup(String pair); }
```

**A declaration, not a proof.** The consuming build must still capture returns unless it can verify,
and the attribute's value is that an unmarked service can be **flagged** rather than silently assumed
safe.

**5.2 Ambient reads that are approved.** A component may legitimately read the clock through the
framework. The scan needs to distinguish approved from unapproved rather than rejecting both:

```java
@OnTrigger(ambient = Ambient.CLOCK)      // new attribute on an existing annotation
public void calc() { ... clock.getProcessTime() ... }
```

**No new annotation types are proposed.** Both are attributes on annotations that already exist,
which keeps the surface small and avoids a second vocabulary.

---

## 6. Work items

| id | item | where |
|---|---|---|
| **W14a** | compute and emit the optimisation attributes in `fluxtion:catalogue` | plugin + `fluxtion-builder` |
| **W14b** | consume them in the generator; fall back to scanning only when absent | generator |
| **W14c** | verification mode — re-derive from bytecode and fail on mismatch | plugin or generator |
| **W14d** | `deterministic` on `@ExportService`, `ambient` on `@OnTrigger`/`@OnEventHandler` | runtime annotations |
| **W14e** | strict modes fail on **absent** attributes, naming the component (R2) | generator |

**Ordering:** W14a → W14b → W14c. W14d only if §5's two cases survive review. W14e last, because it
changes build outcomes and needs the ecosystem populated first.

**Dependency:** W14 makes W4, W5, W11 and W13c *practical across vendor jars*. Those items can ship
without it by scanning bytecode directly — **W14 is the optimisation of the optimisation, not a
prerequisite.** Sequence it after they work.

---

## 7. Open questions

- **Signing.** The catalogue spec discusses signed policy profiles. Should optimisation attributes be
  signed, given that a strict mode makes a build decision on them? R4's verification may be the
  cheaper answer than a signing chain.
- **Staleness.** A manifest computed by an older plugin may predate a check. `Fluxtion-Analysis` makes
  it detectable; what a build should *do* about it is unresolved.
- **Multi-release and shaded jars.** Which classes the attributes describe is ambiguous when a jar has
  several class trees.
- **`Fluxtion-Allocation` soundness** — see §3.6; the syntactic form is proposed deliberately weak.
- **Repository.** The published plugin is `com.telamin.fluxtion:fluxtion-maven-plugin:1.3.0`
  (`goalPrefix: fluxtion`). The nearest local source, `~/IdeaProjects/dataflow-mavenplugin`, is
  `com.fluxtion.dataflow:dataflow-maven-plugin` at a ref dated **2025-03-02** and no `git fetch` has
  been run. A worktree exists at `~/IdeaProjects/telamin/worktrees/mavenplugin-w14` on branch
  `spec/w14-manifest-optimisation-metadata`, **based on a possibly stale ref**. Confirm the repository
  and fetch before implementing.

---

## 8. Why this belongs to the doctrine

Every fact here is one a consuming build would otherwise re-derive on every integration — by
reflection at runtime, or by bytecode scanning at build time — despite the fact having been decidable
once, in the build that had the source.

> **Bind it where the information is. Publish the result. Read it downstream.**

The component catalogue did this for *what a component provides*. This does it for *what a component
guarantees*, which is what the compiler needs in order to remove machinery safely.
