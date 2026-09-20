# Task prompts — give verbatim, in order, one at a time

Preface each run once, then paste T1. Nothing else is said unless the subject blocks on the environment.

> **Preface.** Keep the journal in `JOURNAL.md` as described in that file, starting with the predictions.
> Work in this directory. I will not answer questions about the tooling — use what the project gives you.

Each task has a **cap**. At the cap, stop the task and record it incomplete. Caps are generous on purpose;
hitting one is a result, not a failure of the test.

---

## T1 · Acquire and run — *acceptance 4*

> Build me an event-driven Java application using Fluxtion. Start from nothing in this empty directory.
> I want to see it processing some data and I want to see the output.

**Cap:** 60 minutes or 12 build cycles.

Deliberately vague about *what* the application does — a real user is vague too. It tests catalogue
discovery, download, build, and above all **how they choose to feed it data**.

**Watch for:** whether data arrives through the shipped feed mechanism or through a hand-written `main`
with literal events. In v1 I wrote the `main`. Twice.

---

## T2 · Extend with a new event

> Add a new kind of event and a node that handles it. The node should keep a running total of something
> from that event. Show me it working with the new event type included in the data.

**Cap:** 30 minutes or 6 cycles.

Tests the edit → validate → generate → run loop, whether they find the stub-implementation workflow, and
whether the new node's state reaches the audit output at all.

---

## T3 · Two triggering parents

> Add a node that depends on two other nodes and needs to know **which one** changed when it fires.
> Show me it distinguishing the two.

**Cap:** 30 minutes or 6 cycles.

The narrowest routing test in the set. There is one right answer and it is not guessable from general Java
knowledge. Whether they find it, and via which `from:`, is the cleanest single signal in this whole battery.

---

## T4 · Held-out task — *acceptance 12*

Do not run until T1–T3 are closed. **Freeze the assertions below before starting.**

> Add a reporting node that reads the state of the existing nodes and publishes a summary — per-group
> totals, counts, and any exceptional conditions. It must be triggerable two ways: by an event in the data,
> and on demand by a user outside the data. Prove to me that the numbers in the report are right.

**Cap:** 90 minutes or 15 cycles.

"Prove to me that the numbers are right" is the load-bearing sentence. It does not say how. What they build
in response is the measurement.

### Two separate results are required

**(a) Report correctness.** Expectations derived independently from the feed — not from the application's
own output — with **named assertions** for: arithmetic, grouping, an accepted/processed count, and a
threshold at **below, exactly equal, and above**. Checked against both the structured audit values and the
rendered report, on **both** trigger paths, and against the empty state.

**(b) Non-regression.** Existing behaviour compared separately and shown unchanged.

A report that runs, or unchanged old outputs, satisfies neither.

### Injected errors — operator runs these after the subject declares done

Apply one at a time to the subject's reporting code, rebuild, re-run their check. **Each must fail a named
assertion of theirs.** Restore between each.

| # | Injection | Should fail |
|---|---|---|
| 1 | mark/price selection: use one side of the book instead of the midpoint | arithmetic |
| 2 | drop a multiplier or scale factor from a value calculation | arithmetic |
| 3 | threshold comparison `>=` → `>` | threshold-equal |
| 4 | group by the wrong key, or merge two groups | grouping |
| 5 | swap two counters | count |

Record survivors. In v1 two of six survived, and **both were scenario gaps, not check gaps** — nothing in
the data sat exactly on a boundary. A survivor here means the subject's scenario never created the state
that would expose it, which is the most common and least visible weakness in LLM-written verification.

---

## T5 · Vendor component — *skip if the catalogue entry has not shipped*

> Add this third-party component to the project and wire it in so its output reaches your report.
> Then confirm it is the component you think it is.

Supply a jar and nothing else. The second sentence tests whether anything in the toolchain can establish
**which build** of a dependency was used — v1 could not, and a tampered jar passed every check green.

**Cap:** 45 minutes.

---

## T6 · Diagnose from evidence

> Here is an audit log from a run that produced a wrong number. Tell me which node is wrong and why.
> Do not change any code until you can point at the record that proves it.

Supply a log from a build with one known injected defect, and nothing else. Tests whether the evidence is
self-explaining to someone who did not write the application — your proven production workflow, from the
cold-start end.

**Cap:** 30 minutes.

---

## Operator log

Per run, record alongside the journal:

```
model + version:
date, run number:
source-tree access blocked:        yes / no
staged sample reachable:           yes / no   (must be no)
interventions (verbatim, or none):
tasks capped:
transcript saved at:
```
