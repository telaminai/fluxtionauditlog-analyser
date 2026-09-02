# Round 48 — optimising the instruction set, and what it cost to author correctly

Fifteen cells, one model (Haiku 4.5), one problem: assemble five bought-in components into a risk
engine, scored against a held-out scenario. **The only variable was what the integrator was given.**

## Result

| cell | consumer writes | manual | turns | javap | weighted | result |
|---|---|---|---|---|---|---|
| 48 open brief | beans + runner + tests | 2,608 w | 206 | 12 | **14.80M** | **wrong fee** |
| 48b closed scope | beans + runner | 2,608 | 110 | 18 | 5.03M | pass |
| B −README | beans + runner + tests | 879 | 85 | 6 | 4.17M | pass |
| C −FQN.md | beans + runner + tests | 2,342 | 85 | 5 | 4.13M | pass |
| D −GUIDE | beans + runner + tests | 2,431 | 152 | 24 | 8.35M | pass, **27 internals declared** |
| E −SCOPE | beans + runner + tests | 2,442 | 175 | 26 | 10.78M | **wrong fee** |
| G fixed tests | beans + runner | 742 | 111 | 12 | 4.85M | pass |
| H +worked example | beans + runner | 952 | 123 | 11 | 5.00M | pass, **2 builds** |
| I +procedure | beans + runner | 894 | 108 | 21 | 4.21M | pass |
| **J indexed jar** | beans + runner | 848 | 72 | 6 | **2.91M** | pass |
| K index + example | beans + runner | 1,055 | 100 | 10 | 4.33M | pass |
| L four changes at once | beans + runner | 691 | 130 | 10 | 5.38M | pass |
| M + runner template | beans + runner | 758 | 102 | 9 | 3.85M | pass |
| N + contracts indexed | beans + runner | 788 | — | 2 | — | pass |
| **O bean file only** | **beans** | **659** | **51** | **0** | **1.98M** | pass |
| plain Java, Opus | engine + runner | — | 35 | 2 | 3.72M | pass |

**14.80M and wrong → 1.98M and correct. 7.5×, same model, same jars, same problem.**

## What earns its place

| item | words | evidence |
|---|---|---|
| the "must NOT write" scope section | 166 | removing it: wrong answer, 3 failed builds, +5.8M |
| the entry-point-is-not-a-node rule | 170 | removing it: consumer declared **27 vendor internals** instead of 5 components |
| **`field=Interface` in the manifest** | — | adding it: 111 → 72 turns, javap 12 → 6 |
| **supplying the runner** | — | 72 → 51 turns, **javap 6 → 0** |

`FQN.md` (251 w) was free to delete. The template README (1,725 w) was removable at six extra builds.

## Three findings that generalise

**1. Discovery aids expire.** The worked example produced the fewest builds in the study (2) — and
then became *harmful* once the catalogue was indexed (+28 turns), because it teaches you to `javap`
for facts the manifest now answers. The procedure was worse: its "one javap per entry point" step
produced 21 javap calls, the most of any cell. **Documentation describing a procedure for discovery
has a shelf life, and it ends when the discovery is precomputed.**

**2. Guardrails are invisible to the runs that need them least.** All three introspected cells named
the "must NOT write" section as unused — *"I never considered writing one"* — while cell E's
measurement shows removing it costs correctness. **A successful run is definitionally the one that
did not need the guardrails**, so they can never be ablated by asking a successful run. Only
measurement can retire preventive text.

**3. Cost tracks comprehension, not authoring.** Introspection put reading and component selection at
32–50% of effort and the bean file at **5–10%**. That is why manual size dominated every measurement
and why indexing the artefact beat every documentation change.

## What this does NOT show

- **n = 1 per cell.** Every number is one sample.
- **Correctness was never the differentiator.** Plain Java on a stronger model matched or beat
  Fluxtion on correctness in rounds 46, 47 and 48. The stated falsifier fired three times.
- **Most of the 7.5× is not the framework.** It is instructions, a manifest convention any Java
  library could adopt, and the removal of scaffolding this fixture invented.
- **The catalogue's selection has zero degrees of freedom.** The `Fluxtion-Requires` chain forces
  every choice, so this measured wiring, not judgement.
- **The plain-Java arm was never idiomatic.** Its libraries were Fluxtion's design with the
  annotations stripped — separate node classes, `boolean calc()`, constructor-injected parents. The
  honest comparison, coarse `onEvent` components with internal ordering, remains unrun.

## Process defects of my own, recorded

- **L bundled four changes** after a whole study insisting on one variable; its regression is
  unattributable and the cell is wasted.
- **The transcript matcher matched by content**, so `ABL-I` matched three files and two cells reported
  byte-identical figures. Matching by agent id is exact; one published table was wrong.
- **The scorer did not rebuild**, so a cell whose last command was `mvn clean test` scored FAIL with
  a correct engine on disk.
- **I only ever added documentation** for nine rounds. The first deletion pass found a third of it
  was free.
