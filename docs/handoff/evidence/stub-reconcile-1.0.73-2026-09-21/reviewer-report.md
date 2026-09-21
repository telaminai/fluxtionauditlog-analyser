# Stub reconciliation test — starter 1.0.73, 2026-09-21

**Question:** the site guide (PR #1) tells readers to make new-node stubs extend `EventLogNode` by hand,
because generated stubs don't (feedback #6). That edits a generator-owned class. Does the reconciler
accept the edit, or does the next regenerate conflict?

**Answer: it accepts it.** The superclass, a body that writes to `auditLog`, and a later design change
to the same node all survive regeneration, and the result compiles against the 1.0.73 classpath.

## What was run

1. Downloaded the **public** standalone template: `https://fluxtion-playground.dev/start/scaffold?template=fluxtion-spring`
   → `public-template-fluxtion-spring.zip` (SHA-256 `4ec5ef4c…74492a9c`; pins `fluxtion-starter-core:1.0.73:jar:all`).
2. `./setup.sh` → exit 0, no key (`setup.log`).
3. Declared `alertNode` (`com.example.myapp.node.AlertNode`, `constructor-arg ref="riskEngine"`) and added it
   to `nodeBeans` → `java -jar .fluxtion/fluxtion-starter-core.jar regenerate` (`regen1.log`: "6 nodes, 7 edges").
4. Hand-added `extends com.telamin.fluxtion.runtime.audit.EventLogNode` → regenerate (`regen2.log`).
5. Changed the body to call `auditLog.info("alert", "checked")` and return `false` → regenerate (`regen3.log`).
6. Bound `NewsEvent` to `alertNode` in the XML → regenerate (`regen4.log`).
7. `javac` of `node/*.java` and `event/*.java` against `$(cat .fluxtion/classpath)` → exit 0 (`javac.log`).

Only the local `regenerate` step was run. The `generate.sh` build step calls the remote generator and
needs a key, so the value landing in a real audit record is **not** demonstrated here.

## Predictions (sealed before running) and results

| | Prediction | Conf. | Result |
|---|---|---|---|
| W1 | new-node stub does not extend `EventLogNode` | 90% | ✅ confirmed — `public class AlertNode {` (`AlertNode.as-generated.java`) |
| W2a | regenerate accepts the hand-added superclass | 55% | ✅ accepted, kept |
| W2b | …and the edited body | — | ✅ kept |
| W3 | a later design change to the same node still reconciles | 70% | ✅ `onNewsEvent` added; superclass and audit call kept |
| — | compiles against the 1.0.73 classpath | — | ✅ `javap`: `public class …AlertNode extends …EventLogNode` |

## Unpredicted finding — for upstream

The 1.0.73 stub repeats the comment-contract comment two to four times per member and interleaves it
**between modifiers** (`AlertNode.as-generated.java`):

```java
    private// This reference's propagation mode is declared in the XML; …
     // This reference's propagation mode is declared in the XML; …
    final// This reference's propagation mode is declared in the XML; …
     com.example.myapp.node.RiskEngine riskEngine;
```

Legal Java (line comments end at the newline), but it is what every new node looks like on 1.0.73.
Proposed as row D21 of `docs/specs/spec-tool-agreement.md`. Also still present: `arg0` constructor
parameter names (feedback #7) and handlers returning `true` by default (#22).

## Files

| File | What |
|---|---|
| `public-template-fluxtion-spring.zip` | the pristine public download — diff `project/` against it |
| `project/` | the template after steps 2–6 (starter jar excluded; `.fluxtion/classpath` kept) |
| `AlertNode.as-generated.java` | the stub exactly as regenerate first wrote it |
| `AlertNode.hand-finished.java` | after steps 4–6 |
| `setup.log`, `regen1–4.log`, `javac.log` | each step's output |

To re-run: unzip the template, `./setup.sh`, then repeat steps 3–7.
