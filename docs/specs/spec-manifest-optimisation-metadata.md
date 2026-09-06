# SPEC — manifest optimisation metadata: integrating a jar without introspecting it

**Status** PROPOSED · **Item** M50/W14 · **Date** 2026-09-06
**Target** `fluxtion-builder` (the analysis) exposed by a new goal in `fluxtion-maven-plugin` (the adapter)
**Evidence** [`round-58`](../experience/runs/round-58/NOTES.md) · **Plan**
[`spec-generated-dispatch-performance.md`](spec-generated-dispatch-performance.md) Part IV

**Paired document:** [`spec-component-catalogue.md`](spec-component-catalogue.md) owns *capability*
metadata — what a component provides, requires and consumes, so a consumer can **select** it. This
document owns *assurance* metadata — what a component **guarantees**, so a compiler can **remove
machinery** safely. They are one build-time feature emitting one artifact pair from one goal, not
competing proposals. §2 states exactly how they share it.

---

## 1. The problem this removes

Four items in the M50 plan need facts about components the integrator did not write:

| item | needs to know | would otherwise |
|---|---|---|
| **W4** `noReentrancy` | can any node raise a re-entrant event or register a callback? | scan vendor bytecode |
| **W5** ambient-read scan | does any trigger-reachable method read wall clock, randomness, IO, mutable static? | scan vendor bytecode |
| **W11** service dispatch generation | which methods handle which service registrations, in what order? | reflect at runtime |
| **W13c** recordable signatures | can this service's arguments and returns be captured for replay? | inspect signatures |

Every one of those facts is **already known to the component's own build**, which has the source, the
annotations, and the ability to run the checks. Rediscovering them at every integration is the same
waste the catalogue removes for capabilities — and, under the doctrine, the same defect: **a
determination re-derived below its binding point.**

> **The producing build computes the fact once. The consuming build reads it, and can check it.**

## 2. Relationship to the component catalogue

Both specs want the same new machinery: a goal that walks a module's classes, computes facts, and
writes them where a consumer can read them without a full classpath scan. **Surveying the plugin
found that machinery does not exist** (§10.1) — so the two specs must agree on it rather than each
inventing one.

| | catalogue spec | this spec |
|---|---|---|
| question | *may I select this component?* | *what may I assume about it?* |
| audience | a human or agent choosing a component | the generator, deciding what to emit |
| on absence | the component is not offered | **the strict mode fails** (R2) |
| keys | `Fluxtion-Provides/Requires/Constructor/Consumes/Contracts/…` | `Fluxtion-Reentrant/Ambient/Trigger-Kind/…` |

**One goal, `fluxtion:catalogue`, emits both key families and one shared sidecar.** This spec adds
attributes and a sidecar section; it does not add a second goal, a second file, or a second vocabulary.

## 3. Design rules

**R1 — Derive, do not ask.** Facts are computed from existing annotations plus bytecode by the
producing build. A new annotation is justified **only** where the fact is a *promise about code the
producing build cannot see*. Exactly one such case survives review (§6).

**R2 — Absence is not a claim.** A jar with no attribute means **unknown**, never "safe". Every
attribute is three-valued — *true / false / unknown* — and unknown **poisons the conjunction** (§8).
Strict modes must fail on unknown, naming the component. This is the line between a metadata system
and a trust system.

**R3 — Generated, never hand-written.** A hand-maintained attribute is a second authority for a
derivable fact, and it rots silently. Reinforced by a live defect: a manifest without a trailing
newline **loses its last attribute** with no error
([walkthrough](spec-authoring-session-walkthrough.md)). The emitter must be the only writer.

**R4 — The consumer holds the bytecode and must be able to re-check.** Every attribute in §5 carries
a normative derivation rule precise enough for an independent implementation to recompute it and get
the same answer (§7). A manifest that lies is then a *detectable* condition, not a trusted one.

**R5 — Provenance and binding.** `Fluxtion-Analysis` records the tool, the spec revision, and a digest
of the classes analysed, so a manifest describing *different bytecode* than the jar ships is
detectable without redoing the analysis (§5.8).

---

## 4. Carrier: what goes in the manifest, and what does not

`java.util.jar.Manifest` wraps values at **72 bytes**, continuing with a leading space, and the wrap
is **byte-oriented** — it can split a multibyte character. It is a good carrier for a short scalar
verdict and a poor one for a list of signatures.

**Therefore:**

- **The manifest carries verdicts** — one short, ASCII, unwrapped-length token per attribute. Cheap to
  read: a consumer opens `META-INF/MANIFEST.MF` alone.
- **The sidecar carries evidence** — `META-INF/fluxtion/component.json`, holding the per-method detail,
  the offending sites, and everything the verdict summarises. Read only when a build needs to *explain*
  a verdict or a user asks why.

A verdict without its evidence is still usable; evidence without a verdict is not authoritative. If
the two disagree, **the sidecar wins and the build fails** — they are written together by one emitter,
so disagreement means the jar was rewritten after the fact.

---

## 5. The attributes — normative

Emitted per **entry point** (a class the catalogue spec marks `Fluxtion-Entry-Point: true`), in that
class's section of the manifest.

```
Name: com/acme/pricing/PricingComponent.class
Fluxtion-Entry-Point: true
Fluxtion-Analysis: fluxtion-maven-plugin/1.4.0 spec=1 classes=37 sha256=9f2c…
Fluxtion-Reentrant: false
Fluxtion-Ambient: none
Fluxtion-Trigger-Kind: void
Fluxtion-Service-Consumes: 2
Fluxtion-Service-Exports: 1
Fluxtion-Replay-Capture: recordable
Fluxtion-Allocation: free:syntactic
```

**The trigger-reachable set**, referenced by every rule below, is defined once: the transitive callees
of every method on the entry point's node graph annotated `@OnEventHandler`, `@OnTrigger`,
`@OnParentUpdate`, `@AfterTrigger`, `@AfterEvent`, `@OnBatchEnd`, `@OnBatchPause`, plus the lifecycle
methods `@Initialise`, `@Start`, `@TearDown`. Transitivity stops at the JDK boundary except for the
classified calls named in §5.2. Virtual calls resolve to **every** implementation visible in the
module and its declared dependencies; an unresolvable call site makes the attribute **unknown**, not
false — R2 applied at the derivation level, and the single most important rule here.

### 5.1 `Fluxtion-Reentrant` — W4

**Values** `false` · `true` · absent (unknown).

**Derive:** `true` iff any method in the trigger-reachable set (a) invokes `Callback.fireCallback`,
`Callback.fireCallback(Object)`, or any `DirtyStateMonitor` mutator; (b) invokes
`EventProcessor.onEvent` / `onEventInternal`; (c) invokes `CallbackDispatcher.queueReentrantEvent`,
`processReentrantEvent` or `processReentrantEvents`; or (d) the entry point `@Inject`s a field of type
`Callback`, `CallbackDispatcher` or `DirtyStateMonitor`. Otherwise `false`.

**Consumer:** `noReentrancy` becomes decidable over a graph of vendor components with no scanning.
**Absent ⇒ the flag fails the build, naming the component.**

### 5.2 `Fluxtion-Ambient` — W5

**Values** `none` · a `+`-separated subset of `clock`, `random`, `io`, `static-mutable`, `native`,
`service-call` (e.g. `clock+io`) · absent (unknown).

**Derive** by classifying calls in the trigger-reachable set:

| class | triggered by |
|---|---|
| `clock` | `System.currentTimeMillis`/`nanoTime`, `Instant.now`, `*.now()` on `java.time`, `java.time.Clock` |
| `random` | `Math.random`, `java.util.Random`, `ThreadLocalRandom`, `UUID.randomUUID`, `SecureRandom` |
| `io` | any `java.io`, `java.nio.file`, `java.net` entry point |
| `static-mutable` | `getstatic`/`putstatic` on a field that is not `static final` of an immutable type |
| `native` | any method with `ACC_NATIVE` |
| `service-call` | an invocation on a field injected by `@ServiceRegistered` (§5.4) |

**The exemption that makes this usable:** a read of the **framework** clock —
`com.telamin.fluxtion.runtime.time.Clock`, whose strategy is set from the event stream — is
**deterministic under replay** and is NOT `clock`. It is identifiable by receiver type, which is why
the "approved ambient read" annotation this spec first proposed is unnecessary (§6.2).

**Consumer:** the determinism precondition — and therefore every replay and verification claim —
resolves without opening a vendor class. **This is the attribute the assurance story rests on.**

### 5.3 `Fluxtion-Trigger-Kind` — guard elision

**Values** `void` · `boolean` · `mixed` · absent (unknown).

**Derive:** over every `@OnTrigger` and `@OnEventHandler` on the entry point's nodes — `void` iff all
declare `failBuildIfMissingBooleanReturn = false` **and** have `void` return; `boolean` iff all return
`boolean`; `mixed` otherwise. A node with `@OnTrigger(dirty = false)` does not affect the value; it
affects guard emission separately and is recorded in the sidecar.

**Consumer:** the generator knows without reading bodies whether a component's nodes need dirty guards
at all — the largest single configuration lever round 58 measured.

### 5.4 `Fluxtion-Service-Consumes` — W11

**Manifest value** the **count** of distinct `(type, name)` service bindings — a scalar, because the
list does not fit §4's carrier. **Sidecar** carries the list, and it is the normative form:

```json
"serviceConsumes": [
  {"type": "com.acme.FxRates", "name": "", "onRegister": "onFxRates",
   "onDeregister": "clearFxRates", "declarationOrder": 0}
]
```

**Derive** from `@ServiceRegistered` / `@ServiceDeregistered`: the single parameter's type is the
service type; the annotation's `value()` is the service **name**, `""` meaning unnamed. `declarationOrder`
is assigned by **source order within a class, then by node topological order across classes** — never
`getDeclaredMethods()` order, which is unspecified by the JVM and is precisely the
framework-introduced non-determinism W11 exists to remove.

**Consumer:** the generator emits registration dispatch directly. **This removes runtime reflection
and, with it, the native-image reflection configuration a user currently has to write by hand and can
get wrong.**

### 5.5 `Fluxtion-Service-Exports` — W13a

**Manifest value** count. **Sidecar** the list of exported interfaces, derived from `@ExportService`
**type-use** annotations on the entry point's `implements` clause (§6.1), each with its `propagate()`
value.

### 5.6 `Fluxtion-Replay-Capture` — W13c

**Values** `recordable` · `partial` · `unrecordable` · absent (unknown).

**Derive** by classifying every parameter and return type across all methods of every consumed and
exported service:

- **recordable** — primitives, boxes, `String`, enums, `java.time` values, arrays and collections of
  recordable, and records/classes all of whose components are recordable.
- **unrecordable** — functional interfaces and any type with a single abstract method (a callback the
  replayer cannot re-enter), `Stream`, `Iterator`, `Optional` of unrecordable, types exposing a mutable
  array or collection the callee may retain, any type that is itself a registered service, and
  anything with a cycle through an unrecordable.

`recordable` iff every method is; `unrecordable` iff none is; `partial` otherwise. The sidecar names
each offending method **and the reason**, which is what the W13c build failure prints.

**Consumer:** the build states precisely what it cannot reproduce, at build time, instead of
discovering it as a replay divergence.

### 5.7 `Fluxtion-Allocation`

**Values** `free:syntactic` · `allocates` · absent (unknown).

Deliberately the weakest attribute here, and labelled so. True allocation-freedom is a property of the
*compiler's* escape analysis, not of a scanner — round 58's zero-allocation result held only because
the JIT scalar-replaced a processor that a different shape would have heap-allocated. The syntactic
check — no `new`, `anewarray`, boxing valueOf, varargs synthesis, string concatenation or lambda
capture on the trigger-reachable set — is sound only as a **necessary** condition.

**The value carries its own weakness**: `free:syntactic` never reads as a guarantee of what it does
not guarantee. Do not add a `free` value meaning something stronger without a mechanism that earns it.

### 5.8 `Fluxtion-Analysis` — R5

`<tool>/<version> spec=<n> classes=<count> sha256=<hex>`, where the digest is over the **sorted,
concatenated class files analysed**. A verifier that recomputes the digest and finds it different
knows the manifest describes different bytecode than the jar ships — the staleness case — **without
redoing the analysis.** `spec=` is this document's revision, so a consumer can refuse metadata written
against a rule set it does not implement.

---

## 6. Do we need new annotations? — answered against the source

The first draft of this spec answered "two attributes on existing annotations, no new types."
**Reading the annotation declarations falsified both halves.**

### 6.1 `@ExportService` cannot carry it — it is `TYPE_USE`

```java
@Retention(RUNTIME) @Target({ElementType.TYPE_USE})
public @interface ExportService { boolean propagate() default true; }
```

It annotates a type **use** — `class Pricer implements @ExportService PricingControl` — not the
interface declaration. A `deterministic` attribute placed there would be asserted by **each
implementing component**, which is the wrong binding point twice over: the claim is about the
*interface's contract*, and a consumer of the interface cannot see the implementor's annotation at all.

**So one genuinely new annotation type is required**, targeting the interface:

```java
@Retention(RUNTIME) @Target(ElementType.TYPE)
public @interface ServiceContract {
    boolean deterministic() default false;   // same input sequence => same returns
    boolean recordable()    default true;    // author asserts capture is faithful
}
```

**Why it cannot be derived:** a component consuming `FxRates` cannot know whether the *implementation*
it is handed at integration is deterministic — that code is not in its build, and may not exist yet.
This is the one fact in the whole spec that is a promise rather than an observation, and R1 admits
exactly it.

**It is a declaration, not a proof.** Its value is that an **unmarked** service is *flagged* rather
than silently assumed safe: the default is `false`, so silence is pessimism. W13b still captures
returns unless the interface is marked and the mark is trusted.

### 6.2 The `ambient` attribute on `@OnTrigger` is NOT needed — withdrawn

Its purpose was to distinguish an *approved* ambient read from an unapproved one. §5.2 shows the
distinction is **derivable by receiver type**: the framework `Clock` is deterministic under replay and
`System.currentTimeMillis()` is not, and a scanner can tell them apart without being told. Adding an
attribute for a derivable fact violates R1 and creates a second authority that can disagree with the
scan.

**Withdrawn.** If a case appears that the receiver type genuinely cannot classify, reopen it with that
case as evidence — not before.

### 6.3 Net

**One new annotation type — `@ServiceContract` — and no new attributes on existing annotations.** The
opposite of the first draft's answer, on both counts, and the difference was found by reading the
declarations rather than assuming their shape. It is also a smaller surface than what was first
proposed.

---

## 7. Verification — R4 made executable

Verification is **optional** in a normal build and **mandatory** whenever a strict mode consumes an
attribute. It is the same analysis, run by the consumer:

1. Recompute `Fluxtion-Analysis`'s digest over the jar's classes. **Mismatch ⇒ fail: stale metadata**,
   naming the jar. No further checks — the rest describes different bytecode.
2. Refuse a `spec=` newer than the consumer implements. Accept older only if every attribute it reads
   is unchanged between revisions; otherwise treat as unknown.
3. Re-derive each attribute the build actually uses — never all of them; verification is proportional
   to reliance.
4. On mismatch, **fail naming the attribute, the declared value, the computed value, and the site that
   produced the difference.** A verification failure that does not name a site is a bug in the verifier:
   it leaves the user unable to tell a lying vendor from a broken analysis.

A verifier that reaches an unresolvable call site reports **unknown**, and unknown ≠ mismatch: it
means *this consumer cannot check*, which is a weaker statement than *the manifest is wrong*, and the
two must never print the same message.

---

## 8. Composition — from components to a graph

A graph assembles components; the graph-level fact is a fold over theirs, in three-valued logic where
**unknown poisons**:

| fact | fold | unknown |
|---|---|---|
| reentrant | OR | any unknown ⇒ unknown |
| ambient | set union | any unknown ⇒ unknown |
| trigger-kind | `void` iff all `void`; else `mixed` | any unknown ⇒ unknown |
| replay-capture | worst of recordable > partial > unrecordable | any unknown ⇒ unknown |
| allocation | `free:syntactic` iff all are | any unknown ⇒ unknown |

Facts that depend on the **assembly** rather than any component stay with the integrator and are never
published by a component: whether two components' exported services collide, whether registration
order is total, whether the graph's own generated code is allocation-free.

**The rule that makes the whole thing safe:** a strict mode consumes the *graph-level* value, and
unknown fails. A build cannot become permissive by adding an unanalysed dependency — the usual way a
metadata system silently degrades into a trust system.

---

## 9. Conformance suite

The spec is only real if a jar can be checked against it. Fixtures under
`src/test/resources/w14/`, each a compiled component plus its expected manifest and sidecar:

| fixture | asserts |
|---|---|
| `clean` | all attributes at their strongest; the happy path emits every key |
| `reentrant-callback` | `Reentrant: true`; sidecar names the `fireCallback` site |
| `ambient-wallclock` | `Ambient: clock`; and the **framework-clock** sibling stays `none` (§5.2) |
| `ambient-static` | `static-mutable` on a `putstatic`, not on a `static final` immutable |
| `trigger-mixed` | one void + one boolean handler ⇒ `mixed`, not `void` |
| `services-named` | two same-type services under different `value()` names ⇒ 2 bindings, stable order |
| `unrecordable-callback` | a functional-interface parameter ⇒ `partial`, reason named |
| `unresolvable-call` | an unresolved virtual call ⇒ **unknown**, never `false` |
| `manifest-lies` | hand-edited manifest; the verifier fails and names the site (§7.4) |
| `manifest-stale` | classes edited after emission ⇒ digest mismatch, no attribute checks run |
| `no-metadata` | a plain jar; strict mode fails naming it, normal build proceeds |
| `last-attribute` | a manifest with no trailing newline **still emits its last key** (R3's defect) |

`unresolvable-call`, `manifest-lies` and `no-metadata` are the three that matter: they are R2 and R4,
and a suite without them tests only the happy path.

---

## 10. Work items

| id | item | acceptance |
|---|---|---|
| **W14a0** | build the `fluxtion:catalogue` goal (§10.1); analysis in `fluxtion-builder`, mojo as adapter | goal runs on a module and writes manifest + sidecar; `clean` and `no-metadata` fixtures pass |
| **W14a** | emit `Trigger-Kind`, `Service-Consumes`, `Service-Exports` — what the existing scan already knows | those fixtures pass; `services-named` order stable across 10 runs |
| **W14a2** | emit `Reentrant`, `Ambient`, `Replay-Capture`, `Allocation` — the new analyses | remaining fixtures pass incl. `unresolvable-call` |
| **W14b** | consume in the generator; scan only when absent | a graph of pre-analysed jars generates with no bytecode scan; behaviour identical either way |
| **W14c** | verification mode (§7) | `manifest-lies` and `manifest-stale` fail with the required message content |
| **W14d** | `@ServiceContract` (§6.1) | default `deterministic=false`; an unmarked service is flagged, not assumed |
| **W14e** | strict modes fail on unknown (§8) | adding an unanalysed dependency cannot make a strict build pass |

**Ordering** W14a0 → W14a → W14a2 → W14b → W14c. W14d is independent and small. **W14e last**, because
it changes build outcomes and needs the ecosystem populated first — shipping it early makes every
existing build fail on jars nobody has re-published yet.

**Dependency, stated plainly:** W14 makes W4, W5, W11 and W13c *practical across vendor jars*. Those
items can ship without it by scanning bytecode directly. **W14 is the optimisation of the optimisation,
not a prerequisite** — sequence it after they work, when there is something to optimise.

### 10.1 What surveying the plugin found

`~/IdeaProjects/dataflow-mavenplugin` is confirmed as the source of the published plugin
(`github.com/telaminai/dataflow-mavenplugin`); a local ref 18 months stale showed the pre-rebrand
coordinates, and after `git fetch` `origin/main` is
`com.telamin.fluxtion:fluxtion-maven-plugin:1.3.1-SNAPSHOT` with `v1.3.0` tagged.

Its three mojos — `FluxtionScanToGenMojo`, `FluxtionSpringToGenMojo`, `FluxtionYamlToGenMojo` — all
*generate a processor*. **The plugin writes no manifest entries at all today and there is no catalogue
goal**, so W14a0 builds machinery both this spec and the catalogue spec assumed existed.

Two decisions that follow:

- **The analysis lives in `fluxtion-builder`, not the mojo.** The same code must answer the question
  for the producing build and for the consumer's verification pass (R4); a mojo cannot be called from
  the generator. The plugin invokes; it does not reimplement.
- **`FluxtionScanToGenMojo` already walks the classpath** to find nodes, and what it learns overlaps
  heavily with §5.3–§5.5. Emitting those first (W14a) is therefore much cheaper than the §5.1–§5.2
  analyses (W14a2), which is why they are split.

Worktree `~/IdeaProjects/telamin/worktrees/mavenplugin-w14`, branch
`spec/w14-manifest-optimisation-metadata`, reset onto `origin/main` @ `d635950`.

---

## 11. Open questions

- **Signing.** A strict mode makes a build decision on these attributes. R4's verification may be a
  cheaper answer than a signing chain, since the consumer holds the bytecode anyway — but it does not
  cover a jar whose *classes and manifest* were rewritten together. Unresolved; §5.8's digest bounds
  the damage without closing it.
- **Multi-release and shaded jars.** Which class tree the attributes describe is ambiguous. Proposal:
  refuse to emit for a multi-release jar in v1 rather than emit something ambiguous.
- **Sidecar ↔ manifest disagreement** resolves to "sidecar wins, build fails" (§4). That is the safe
  default; whether a *repair* path is ever appropriate is not settled.
- **Where `@ServiceContract` lives** — `fluxtion-runtime`, alongside the other annotations, means every
  service interface takes a runtime dependency to be marked. A separate tiny artifact avoids that.
  Owner call.

---

## 12. Why this belongs to the doctrine

Every fact here is one a consuming build would otherwise re-derive on every integration — by
reflection at runtime, or by scanning bytecode at build time — despite the fact having been decidable
once, in the build that had the source.

> **Bind it where the information is. Publish the result. Check it downstream.**

The catalogue did this for *what a component provides*. This does it for *what a component
guarantees* — which is what a compiler needs before it is entitled to remove anything.

And the spec's own history is the doctrine's argument. Two of its design answers were wrong until the
annotation declarations were read: `@ExportService` cannot carry a contract because it is `TYPE_USE`,
and the `ambient` attribute was unnecessary because a receiver type already carries the fact. **Both
errors were inferences standing in for a determination that the source had already made.**
