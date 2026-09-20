# Cold-start validation of the v2 starter — operator protocol

Measures whether a fresh LLM, with no prior Fluxtion exposure and no access to your source tree, can get
from an empty directory to a verified result using only what the v2 starter ships.

Satisfies acceptance 4 and 12 of `spec-project-starter-journey.md`. **v1 baseline** is the 2026-09-19/20
session; its trap outcomes are in the scoresheet as the control.

| File | Who uses it | What it is |
|---|---|---|
| `README.md` | operator | this protocol |
| `PROMPTS.md` | operator | the exact words given to the subject, in order |
| `JOURNAL.md` | **subject** | the journal format and its rules — copy into the run directory |
| `score_journal.py` | operator | parses the journal, fingerprints the working tree, emits objective counts |

## The one thing this measures

Every journal entry carries a **`from:`** field naming *what pointed the subject here*. That field is the
experiment. Everything else is supporting detail.

| `from:` | Meaning | v2 wants |
|---|---|---|
| `runbook:` `readme:` `contract:` | a shipped pointer routed them | **up** |
| `error:` | a diagnostic taught them at the moment of need | **up** |
| `stub:` | generated code showed them the shape | **up** |
| `example:` | they copied an existing project file | **depends which file** |
| `search:` | they went hunting | down |
| `prior` | general Java/Spring knowledge or a guess | down |
| `operator` | they had to ask you | **zero is the target** |

`example:` is the channel that caused the largest v1 failure and is not yet addressed by the spec. Score
**which file** was copied: the intended harness is a success, a hand-rolled `main` is the failure.

## Setup

1. **Fresh context.** New session, no history. Never reuse a session across tasks unless testing re-entry.
2. **No source-tree access.** The subject must not be able to read the analyser, compiler, playground or
   mongoose checkouts. Run it in a directory with no path to them. In v1 I escaped two dead ends by reading
   Mongoose and starter source; a customer cannot.
3. **No contamination from this session.** `desk/ITERATIONS.md`, `desk/reference_model.py`,
   `desk/mutation_test.py`, `vendor/PREDICTIONS.md` and the two feedback documents contain the answers to
   task T4, including which mutants survive and the feed rows that kill them. Confirm the subject cannot
   reach the staged sample or any copy of it. Start from a clean playground download only.
4. **Models.** At least two capability levels, three runs each. One session is a hypothesis, per H3.
5. **Record the transcript.** The journal is a convenience for scoring; the transcript is the evidence.
   Where they disagree, the transcript wins and that disagreement is itself a finding.

## Running it

Give the subject `JOURNAL.md` and the first prompt from `PROMPTS.md`, verbatim. Then:

- **Say nothing else.** No hints, no corrections, no "have you looked at…".
- **Answer only environment questions** — is there network, which JDK, where do I put files. Anything about
  Fluxtion, the project, the tooling or what to do next gets: *"Use what the project gives you."* Log it as
  an `ASK` entry either way.
- **Never name a trap**, never mention Mongoose, feeds, runbooks or any file by name.
- **Let it fail.** A failed task is the most informative outcome you can get. Stop a task at the cap in
  `PROMPTS.md` and record it as incomplete.
- If the subject asks whether it is being tested, answer honestly but add nothing: *"Yes, of the starter,
  not of you. Carry on as you would normally."*

## Scoring

```bash
python3 coldstart/score_journal.py <run-dir>/JOURNAL.md <project-dir>
```

Produces the `from:` distribution, the `example:` targets, entries per task, elapsed times, ASK count, and
static trap fingerprints over the resulting project. It prints what it **cannot** determine — those go on
the manual half of the scoresheet it emits.

Then compare against the v1 control. **v2 is better only if the trap column gets shorter and `prior` +
`search` + `operator` go down.** A faster run with the same routing failures is not an improvement.

## What counts as a pass

- **T1 (acceptance 4):** a measured first result, unassisted, from an empty directory.
- **T4 (the held-out task):** correctness established against independently derived expectations, **and**
  each injected error caught by a named assertion. A generated report that merely runs does not pass.
  An assisted run is recorded as assisted, never as passed.
- **Overall:** no `operator` entries, and no trap fingerprint that v2 claims to have closed.

## Known limits of this instrument

- Self-reported `from:` can be rationalised after the fact. The pre-action rule reduces this; the transcript
  checks it. Spot-check 10% of entries against the transcript.
- The journal itself costs tokens and attention and may change behaviour. If a subject abandons or degrades
  the journal, **record that** — it means the instrument is too heavy and the run is still usable via the
  transcript.
- Trap fingerprints are static checks over the final tree. They cannot see a trap that was hit and then
  fixed; the journal's `ERROR` entries carry those.
- This measures onboarding, not the framework. A subject may route perfectly and still write poor logic.
