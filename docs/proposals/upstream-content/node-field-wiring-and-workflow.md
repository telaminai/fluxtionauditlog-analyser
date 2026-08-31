# Proposed upstream content — how a node's fields get their values, and the order to do it in

**What this is.** Drafted content for the **static authoring resources**, not for this repo. Third in the
set, after [`audit-authoring.md`](audit-authoring.md) (how a node participates in the log) and
[`audit-runtime.md`](audit-runtime.md) (how the log gets out).

**Where it goes**, in priority order:

| File | Why |
|---|---|
| `telaminai/fluxtion` `docs/claude.txt` | it already has the *"Field can't be reconstructed"* triage entry; this is the half of that story it does not tell |
| `fluxtion-playground.dev/CLAUDE.md` | carries the same triage table |
| `fluxtion-playground.dev/fluxtion-golden-path.md` | the workflow half belongs beside the build loop, not in triage |

## Evidence the gap is real — retrieved 2026-09-01

| term | `claude.txt` | playground `CLAUDE.md` | golden path |
|---|---|---|---|
| `transient` / `@FluxtionIgnore` | **covered, well** | covered | covered |
| `@AssignToField` | covered | covered | mentioned |
| **`non-final`** | **0** | **0** | **0** |
| **JavaBean / setter-wiring of a field** | **0** | **0** | **0** |
| **`@ConstructorArg`** | **0** | **0** | **0** |
| **"final field"** | **0** | **0** | **0** |

**What is already right, and should not be touched.** `claude.txt` states the exclusion remedy with its
fully-qualified name and adds the sentence that matters most:

> The field initialiser still runs in the generated processor's no-arg constructor, so runtime semantics
> are preserved.

That is exact, and it is the fact a consumer of `FLX-1009` most needs — this repo measured the same
behaviour independently against a generated processor before finding it stated here.

**What is missing is the other half of the same question.** The triage entry answers *"how do I stop
Fluxtion mapping this field"*. It never answers *"what decides whether a field is mapped in the first
place"*, and the answer is one word that appears nowhere: **final**.

---

## Draft — *How a node's fields get their values*

> Measured against `fluxtion-builder` 1.0.64 and a generated processor, not inferred.

### The rule

Fluxtion generates source that **reconstructs** each node, so every field it must reproduce needs a route
in. By default it **constructor-maps every FINAL, non-transient, non-`@FluxtionIgnore` instance field**,
because a final field can only be set by a constructor — and then it needs a constructor whose parameters
match that set.

**Finality is the trigger.** That one word explains the whole failure:

```java
public class PriceStats {
    private final Map<String, Double> stats = new HashMap<>();   // FINAL → mapped → needs a constructor
    private final RootNode root;                                 // FINAL → mapped → needs a constructor
    public PriceStats(RootNode root) { this.root = root; }       // ...and this one accepts only `root`
}
```

### The four routes a field can take

1. **A constructor parameter** matching the mapped field. The default for final fields.
2. **Explicit opt-in** — `@ConstructorArg` on the field, or `@AssignToField` on a constructor parameter
   naming it. This forces constructor mapping **regardless of finality**, and takes precedence, so it
   must be removed before either route below applies.
3. **A JavaBean setter** — available for a **non-final** field with no explicit opt-in. Fluxtion
   setter-wires those; they are never constructor-mapped at all.
4. **Exclusion** — `transient` or `@FluxtionIgnore`. Fluxtion stops supplying the value; the field's own
   initialiser still runs, so a map or counter the node builds for itself is unaffected. Only a value
   the *builder instance* held before generation is lost.

**Route 3 is the one authors do not know exists.** Measured across nine LLM authoring sessions, three
independently repaired a constructor failure by removing `final` — and none could explain why it
worked. It is not a workaround: a non-final field is genuinely wired through its setter.

### Choosing between them: local state or graph reference?

For each field, ask what it *is*:

* **node-local state** the node builds for itself — a map, a counter, a buffer: **exclude it**
  (`transient` / `@FluxtionIgnore`). Nothing is lost; the initialiser still runs.
* **a reference or configuration the graph supplies**: give it a route — constructor parameter, or
  non-final with a setter. Excluding it would silently drop the value.

---

## Draft — *A workflow that avoids a build you cannot escape*

This belongs beside the build loop rather than in triage, because it is about the order to do things in.

### The trap

An AOT project **commits its generated processor**, and that generated source constructs each node.
Change a node's **constructor signature** and the committed source stops compiling — which blocks the
regeneration that would fix it. `mvn -Pregen`-style builds compile before they scan, so the build cannot
bootstrap out of it by itself.

### The workflow

**While the graph's shape is still moving, use JavaBean style** — non-final field, setter. The generator
emits:

```java
public final transient AuditInstallation auditInstallation = new AuditInstallation();
...
auditInstallation.setOpenGraph(openGraph);      // in an init block
```

Because the **constructor signature never changes**, the committed generated source stays valid while
dependencies come and go. Adding a dependency is a compile-and-regenerate, never a deadlock.

**Harden to constructor injection when the shape settles.** Final fields make a node's dependencies
explicit and immutable, and by then you are not changing them often.

**Treat the migration as one deliberate break**, not something to discover mid-change: converting a node
*from* constructor *to* bean style does break the committed source once, because it still calls a
constructor that no longer exists. Do it on purpose, regenerate, move on.

### Why this is worth saying out loud

**Measured** (2026-08-31, on a real 12-node graph): four constructor-shape breaks during the early
slices while the node set was churning, **none** after it stabilised. The friction is front-loaded, which
is exactly when an author is least able to tell a workflow mistake from a framework limitation.

Two probes settled it rather than reasoning:

| probe | result |
|---|---|
| convert a node from constructor to bean style | breaks — committed source calls a constructor that is gone |
| **add a new dependency to an already-bean-style node** | **compiles, regenerates, wires it** |

**No compiler diagnostic can carry this.** A message fires at a failure; this is advice about the order
to work in, and the only place it can live is the document an author reads before they start.
