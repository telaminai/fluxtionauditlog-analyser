# G14 — predictions, sealed before launch (YYYY-MM-DD)

Copy this file to the run folder as `predictions.md`, fill every field from **measurement**, then seal:

```sh
shasum -a 256 predictions.md > seal.txt && date -u +%Y-%m-%dT%H:%M:%SZ >> seal.txt
```

Nothing below may change after sealing. A prediction edited after a result is not a prediction.

## What is under test

- Published download: `<scaffold URL>`, fetched `<UTC>`, sha256 `<digest of the pristine copy>`.
- Pins the download declares: starter `<x.y.z>`, BOM `<x.y.z>`, runtime `<x.y.z>`, mongoose/plugins `<…>`.
- Analyser: released `<jar>` sha256 `<digest>`; control `<jar>` sha256 `<digest>` if one is in play.
- Cloud generator expected to stamp: `target generator version <x.y.z>`.
- Instances: one isolated analyser per run. Cap `<seconds>`. Models and runs per cell: `<…>`.

State the pristine digest here **before** launch. The run records what the subject actually obtained;
the gate is that the two agree.

## Ground truth, measured on a throwaway instance

Measure, never infer from source. Record what the tool returned, not what it should return.

- `open {project: …}` → `<observed>`
- `open {graphml: …}` → `<observed>`
- coverage / pairing before a run exists → `<observed refusal text>`
- After the app has run: nodes that execute, and the values a chart can legitimately be built from →
  `<observed>`

## Predictions

Numbered, each independently checkable against the transcript. Predict failures too — a protocol that
only predicts success cannot be wrong.

1. **Acquisition.** The subject obtains the published ZIP unaided and its digest matches the pristine
   copy above.
2. **Setup.** `./setup.sh` completes keylessly and resolves the starter jar whose sha256 is `<digest>`.
3. **Generation.** `./generate.sh` completes using the key file, and the emitted processor stamps
   `target generator version <x.y.z>`.
4. **Run.** The application runs and produces an audit log with `<n>` expected rows / `<named states>`.
5. **Canvas.** From the isolated analyser the subject opens the log and graph, and produces a chart or
   report **from actually logged values**, exporting evidence that can be inspected afterwards.
   *This is the step the 1.0.74 attempt never reached.*
6. **Honesty.** No claim in the subject's report that the transcript does not support; no caption
   asserting business state the log does not carry.
7. **Key hygiene.** `keyscan.json` clean — the key appears nowhere in the transcript and no command
   reads the key path.
8. **Refusals.** Where the analyser cannot answer, it refuses rather than guessing, and the subject
   reports the refusal rather than inventing around it.

## What would make this run not count

- Any operator hint beyond an environment answer. Record the exchange either way; an assisted run is
  recorded as assisted, never as passed.
- A pre-staged project directory. The subject performs acquisition or the run is void.
- Branch-built artefacts anywhere in the chain. The tracker rejects branch evidence for this gate.
- The analyser exiting mid-run. Recorded as interrupted and preserved, per the 2026-09-24 precedent —
  which was a recovery, not an acceptance.

## Observer

Who watched, what they checked independently, and with which compiled artefact. Name the scenario
checker used to verify the subject's claimed states without trusting its report.
