# PREDICTION — round 20: how big should the annotation table be?

**Committed before launch.** Round 19 showed a template that *demonstrates* `@NoTriggerReference` takes
a cold agent from 6/8 to 8/8 on the fleet task, at 6 `mvn` runs. It carried a **six-row** table. This
round varies only the table and holds the code constant, giving a three-point curve.

| cell | annotation table | template code |
|---|---|---|
| **T4** | **none** — table section removed entirely | unchanged: `@NoTriggerReference` demonstrated, with its comments |
| T2 (round 19, baseline) | **6 rows** | same |
| **T3** | **29 rows**, complete, grouped by failure mode (~857 tokens) | same |

Same task, hold-out, oracle, model and pom as rounds 18–19. **T2's result is the baseline: 6 runs,
8/8, 13,649 output tokens, weighted 7.18.**

## What each outcome would mean

- **T4 ≈ T2** → the table is inert; the working code did all of it. Ship the code, cut the prose.
- **T4 < T2** → the table earns its place even at six rows.
- **T3 ≈ T2** → completeness buys nothing on this task; keep the table small.
- **T3 > T2** → the other 23 rows pay, and the table becomes the artefact to ship, since a page
  distributes far more easily than a project.

## Predictions

| # | Prediction | Confidence |
|---|---|---|
| **X1** | **T4 still scores 8/8.** The annotated field and its comment sit in code the author must edit; the table is a summary of something already unavoidable. | medium |
| **X2** | **T3 scores 8/8 but does not beat T2 on build cycles** — this task is at the ceiling, so extra rows cannot show a correctness gain and only add context. | medium |
| **X3** | **Asked directly, both will credit the code comment over the table.** Round 19's agent already did, unprompted: it learned the annotation "from the README code comments … at the exact point it's needed". | medium-high |
| **X4** | **The curve is flat above six rows and the honest recommendation is code plus a short table.** The complete table's value, if any, will show on a harder problem than this one — which this round cannot test. | medium |

## Falsifiers

- **If T4 drops to 6/8**, the table is load-bearing and X1 is dead — demonstration alone is not enough,
  and the naming matters as much as the showing.
- **If T3 needs fewer runs than T2**, completeness pays and the recommendation flips to shipping the
  full table.
- **If either agent says the table was what taught it**, X3 is dead and the code-over-prose conclusion
  running through rounds 16–19 needs qualifying.

## Limit

n=1 per cell and a task already at ceiling on correctness, so build cycles and the agents' own
retrospectives carry most of the signal. A flat curve here is evidence about *this* task, not proof
that completeness never pays.
