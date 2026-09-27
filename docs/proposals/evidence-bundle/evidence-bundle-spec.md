# Evidence Bundles v0.1 — delivery spec

**Status:** DRAFT for review, 2026-09-27. One proposal among several: it is expected to be argued over and revised
before anything is built, and nothing here is settled until the review closes. Aimed at a two-working-day delivery. **Sources:** [evidence-bundles.md](evidence-bundles.md)
(the prototype and what the platform lacks) and [evidence-bundle-positioning.md](evidence-bundle-positioning.md) (the
product and the signing model). This spec picks the smallest slice of both that makes the exchange **real**, and names
everything else as a later phase.

> **Trust the evidence, not the author.** A bundle establishes that *these exact inputs, artefacts and configuration
> are associated with these recorded results*. It never asserts that the results are correct.

---

## 1. What v0.1 delivers

One sentence: **an investigation can be captured into a single file, verified by anyone, opened into the analyser on
another machine, stepped through as the author's walk, and answered with a linked response bundle that can be
compared with it.**

| Capability | v0.1 | Source |
|---|---|---|
| Single-file bundle (`.fexp`, a zip) with a versioned `manifest.json` | ✅ | 5.2, Part 8 |
| SHA-256 of every member; `verify` reports intact or exactly what is not | ✅ | 5.3, "hard to counterfeit" |
| Optional Ed25519 signature over the manifest, signer identity declared | ✅ | signing model |
| Evidence and testimony separated in the manifest and in every report | ✅ | Part 7, "facts vs interpretation" |
| A profile inside the bundle that travels | ✅ by construction, not a new anchor (§4.3) | 5.1 |
| Excerpt with a recorded filter and counts; omissions declared | ✅ | 5.7 |
| Captured view: applied focus, charts, the author's walk | ✅ | 5.9, 5.11 |
| Walk playback on the receiver's analyser, step by step | ✅ via existing verbs | 5.11 |
| Opening never modifies the bundle | ✅ opened from a temp copy (§5.3) | 5.10 |
| Recorded **input** members plus a re-run recipe, labelled re-run | ✅ | 5.5 (partial) |
| Response bundle linked by the original's id; `compare` | ✅ basic | chains, 5.6 |
| Static `index.html` page | ❌ phase 2, after PR #53's SVG surface lands | 5.12 |
| Exact replay through a data-driven clock | ❌ phase 2 | 5.5 |
| Runtime-emitted, signed run receipt | ❌ phase 2 (Mongoose ask); v0.1 has a *declared* receipt | run receipt |
| Redaction, source coordinates, registry, organisation identities | ❌ later | 5.4, 5.8, registry |

**No analyser code changes are required for v0.1.** Everything drives released analyser 1.25.0 through its existing
REST action socket (`open`, `context`, `aggregate`, `goto`, `topology`, `graph`, `spotlight`, `report`, `screenshot`,
`source_root`), plus the filesystem. That is deliberate. It is what makes two days realistic, it keeps rule 9 (one
dispatch model) untouched, and it proves the primitives are sufficient before any of this moves into the product.

---

## 2. The tool

`tools/bundle/fexp.py` — one Python 3 file, standard library only, plus `cryptography` **only** for the optional
Ed25519 path (absent → signing is refused with a clear message; verification of unsigned bundles still works).

```
fexp capture   --analyser <rest-endpoint-file> --out incident-127.fexp [--excerpt …] [--inputs …] [--walk …]
               [--testimony MANIFEST.md] [--responds-to <bundle-id>] [--sign <key.pem> --signer "<name>"]
fexp verify    incident-127.fexp [--pubkey <pub.pem>]
fexp open      incident-127.fexp --analyser <rest-endpoint-file> [--walk]
fexp walk      incident-127.fexp --analyser <rest-endpoint-file>       # step through: Enter = next, b = back, q = quit
fexp compare   incident-127.fexp response-128.fexp
fexp keygen    --out <dir>                                               # Ed25519 pair for a signer
```

The analyser's REST endpoint file (`~/.fluxtion-analyser/rest-endpoint`) supplies the URL and token, the same way the
existing trial runner reads it. The tool never prints the token.

---

## 3. The format

### 3.1 Layout

```
incident-127.fexp                 (zip; members stored with forward-slash paths)
  manifest.json                   REQUIRED — §3.2
  manifest.sig                    optional — Ed25519 over canonical manifest.json bytes
  testimony/MANIFEST.md           optional — hypothesis, method, outcome, caveats (the author's account)
  log/<name>.yaml                 REQUIRED — the audit log or its excerpt
  topology/<name>.graphml         REQUIRED when the log pairs with a graph
  analyser/.analyser/project.fluxtion-settings   REQUIRED — a bundle-local profile (§4.3)
  walk/<name>.walk.json           optional — the author's route (§3.4)
  reports/<name>.pdf              optional — rendered reports
  inputs/<name>                   optional — recorded inputs for a re-run (feed files, config)
  rerun/README.md                 optional — the re-run recipe, stated as re-run, not reproduce
  images/*.png                    optional — ONLY those the manifest or walk references
```

### 3.2 `manifest.json` — the only formally specified part (Part 8)

```json
{
  "format": "fluxtion-evidence-bundle",
  "formatVersion": "0.1",
  "id": "sha256:…",
  "created": "2026-09-27T15:17:30Z",
  "creator": {"name": "declared by the capturer", "declared": true},
  "respondsTo": null,
  "question": "Does the admin reset clear and re-arm the rejected-row alarm?",

  "evidence": {
    "members": [
      {"path": "log/audit-analyser-bundle.yaml", "role": "log", "sha256": "…", "bytes": 42817},
      {"path": "topology/MarketProcessor.graphml", "role": "topology", "sha256": "…", "bytes": 21379}
    ],
    "capturedVerdicts": {
      "pairing": "the graph declares all 4 node(s) this log writes",
      "producerFindings": [],
      "logFingerprint": {"records": 67, "firstTime": 1790522219624, "lastTime": 1790522238411}
    },
    "excerpt": {
      "sourceLog": {"name": "audit-audit-analyser-bundle.yaml", "sha256": "…", "records": 67},
      "filter": null, "kept": 67, "dropped": 0,
      "statement": "whole log — nothing omitted"
    },
    "artefacts": [
      {"kind": "processor-jar", "coordinate": "target/audit-analyser-bundle-1.0.0-SNAPSHOT.jar", "sha256": "…"},
      {"kind": "design", "coordinate": "src/main/fluxtion/designer/application-context.xml", "sha256": "…"}
    ],
    "runReceipt": {"source": "declared-by-capturer", "note": "not emitted by the runtime; see phase 2"},
    "tools": {"analyser": "1.25.0", "fexp": "0.1"}
  },

  "testimony": {
    "members": [
      {"path": "testimony/MANIFEST.md", "role": "narrative", "sha256": "…"},
      {"path": "walk/admin-reset.walk.json", "role": "walk", "sha256": "…"},
      {"path": "reports/rejected-row-alarm.pdf", "role": "report", "sha256": "…"}
    ],
    "statement": "the author's account; each step points at evidence above, which is what a reader checks"
  },

  "reproduction": {
    "mode": "re-run",
    "members": [{"path": "inputs/input.txt", "role": "feed", "sha256": "…"}],
    "statement": "re-running these inputs gives the same records and values; wall-clock times differ. Exact replay is not carried in v0.1."
  },

  "semanticCorrectness": "NOT ASSERTED"
}
```

**Rules:**
1. **Identity.** `id` = `"sha256:" + sha256(canonical manifest with "id" set to "")`. Canonical form: UTF-8 JSON, keys
   sorted, no insignificant whitespace. Changing any member changes its hash, which changes the manifest, which changes
   the id. A response names its original by that id.
2. **Every file in the zip except `manifest.json` and `manifest.sig` is listed exactly once**, in `evidence`,
   `testimony` or `reproduction`. An unlisted file or a listed-but-missing file is a verify failure, never a warning.
3. **Evidence and testimony are separate lists.** Nothing the author wrote in prose may appear under `evidence`.
   Reports go under testimony, because a report is the author's argument even when it cites evidence.
4. **Unknown keys are preserved** on any rewrite. A reader that meets an unknown `formatVersion` major refuses, naming
   the version. For a higher minor, it lists what it cannot use and continues.
5. **`semanticCorrectness` is always `"NOT ASSERTED"`** in v0.1. It exists so the absence is explicit.

### 3.3 Signature

`manifest.sig` = Ed25519 signature over the canonical manifest bytes (with `id` filled). The signer's name is in
`creator` and is `declared: true` unless a later registry vouches for it. The signature means *this signer associates
these members and results*, never *this is correct*.

### 3.4 Walk format (`*.walk.json`), deliberately small

```json
{"walkVersion": "0.1", "title": "The admin reset re-arms the alarm", "steps": [
  {"say": "The alarm is raised on the third rejected row.",
   "view": [{"verb": "graph", "params": {"name": "Alarm lifecycle"}}],
   "spotlight": {"targets": [{"target": "graph:Alarm lifecycle:note:1", "caption": "RAISED: 3 rejected"}]}},
  {"say": "The operator reset arrives as its own audited record.",
   "view": [{"verb": "goto", "params": {"recordIndex": 36}}],
   "spotlight": {"targets": [{"target": "records:row:36", "caption": "Signal resetAlarm, via admin"},
                             {"target": "detail:node:feedAlarm", "caption": "reset true · countAtReset 3"}]}}
]}
```

- A step is a list of **existing verb calls** that set the view, then one spotlight set.
- `say` is the author's sentence. Like a caption, it is testimony, and it is shown attributed.
- A step whose view call or spotlight is refused on the receiver's analyser is **reported and skipped, never faked**. A
  refused spotlight is itself information: "the chart is not drawn at this size", or "this record is not in your log".

---

## 4. Capture

### 4.1 Preconditions, checked and refused with the reason
- The analyser has a log open, and (when a graph is open) `context.graphPairing` is read and recorded.
- `context.log.producer` findings are **recorded, not hidden**. Capturing a log with a malformed record is allowed and
  the finding travels in `capturedVerdicts`. (Today's corrupted-record run would carry its finding up front.)
- The exchange directory is not required. Capture reads the files the analyser has open, from the paths `context`
  reports.

### 4.2 Excerpt
- Default: **the whole log**, stated as such.
- `--excerpt "text:<s>" | "dimension:<Event>" | "time:<from>..<to>" | "records:<a>-<b>"` cuts by that rule.
  `excerpt.filter`, `kept` and `dropped` are recorded, and `statement` is generated ("34 of 9,210 records, cut on text
  'positionKeeper'; every price record removed").
- The excerpt is re-opened in the analyser before sealing. The pairing verdict **on the excerpt** is what is recorded,
  and the source log's verdict is recorded beside it. If they differ, capture says so.

### 4.3 The profile travels by construction (5.1 without a new anchor)
Capture writes a **new** `analyser/.analyser/project.fluxtion-settings` rather than copying the author's profile:
- source roots and workspace anchors are **omitted** (source does not travel; see 5.4);
- log and graph paths are written project-relative to `analyser/`, pointing at `../log/…` and `../topology/…`. They
  resolve because `ProjectProfile.baseDirFor` anchors a `.analyser/` profile on its parent, which is the bundle's
  `analyser/` directory inside the unpacked copy. This anchoring applies only to a file named `project.*.fluxtion-settings`
  (`isProjectProfileFileName`: `project.` prefix and `.fluxtion-settings` suffix); any other name anchors on
  `.analyser/` itself;
- charts, focuses and reports are copied from the author's live project through `context`/`report`, and chart
  definitions via their `graph` echo;
- the applied focus and selected record are recorded in the walk's first step, not left as lost session state (5.9).

The profile is **testimony** only where it carries narrative (reports). Chart definitions are evidence-adjacent
configuration, and the member is listed under `evidence` with role `profile`.

> **Pre-build check, the one real unknown:** confirm that relative `../log/...` paths in a profile resolve and load
> through `open {project}` → `open {log, graphml}` on 1.25.0. If `..` is refused by `PathForm` for these keys,
> capture places the log and topology **under** `analyser/`, and the layout in §3.1 moves accordingly. This is decided
> in the first hour, before anything else is built.

### 4.4 Sealing
Hash every member, build the manifest, compute `id`, optionally sign, zip. Then **run `verify` on the result** and
refuse to report success unless it passes. Capture is a transaction, not a checklist (Part 4).

---

## 5. Verify, open, walk, compare

### 5.1 `verify`
```
Bundle          incident-127.fexp   id sha256:9f1c…   format 0.1
Signature       VERIFIED   signer "Example Bank CI" (declared)            | or: NOT SIGNED
Members         VERIFIED   9 of 9 match their hashes; no unlisted files
Log excerpt     VERIFIED   67 of 67 records (whole log)
Topology        VERIFIED
Pairing at capture         "the graph declares all 4 node(s) this log writes"
Producer findings at capture   none
Testimony       PRESENT, NOT VERIFIED — the author's account (MANIFEST.md, 1 walk, 1 report)
Reproduction    re-run inputs present (1); exact replay NOT CARRIED
Semantic correctness       NOT ASSERTED
```
The exit code is non-zero on any mismatch, and the failing member is named.

### 5.2 `open`
1. `verify`; on failure, open only with `--force` and say so on the status line via declared provenance.
2. Unzip to a fresh temp directory. **The `.fexp` file is never written.** Anything the receiver edits lands in the temp
   copy, and a later `verify` of the original still passes (5.10 satisfied without an analyser read-only mode).
3. `open {project: <tmp>/analyser}` → `open {graphml}` → `open {log, provenance: "evidence bundle <id> (verified|UNVERIFIED)"}`,
   in that order. The project switch closes the previous session, which is the prototype's measured-safe order.
4. Re-read `context.graphPairing` and compare it with `capturedVerdicts.pairing`. A difference is reported, not
   hidden. This is the receiver's first integrity check.

### 5.3 `walk`
Steps through the walk against the opened bundle: run the step's view calls, then its spotlight, print `say` attributed
to the author, and wait. Enter goes to the next step, `b` goes back, `q` quits. Each step's refusals are printed. Because the walk is played by the
receiver's analyser from the bundle's data, **playback needs no LLM** (positioning, "guided walkthroughs").

### 5.4 `compare`
For two bundles (usually original and response):
- **Chain:** does B's `respondsTo` equal A's `id`?
- **Artefacts:** which hashes changed (processor jar, design, a vendor jar), which are unchanged.
- **Inputs:** are the reproduction inputs byte-identical? If not, the comparison is labelled *different inputs* and
  the rest is shown with that caveat.
- **Topology:** node and edge counts, and nodes added or removed (from the graphml).
- **Behaviour:** records per event type, and for each node key named in either walk, first/last value and count,
  computed from each log. A table marks each row *changed*, *unchanged* or *only in A/B*.
- **Findings:** producer findings and flagged-record notes, side by side.

It states what it compared, never "fixed" or "correct".

---

## 6. Two days

| When | Deliverable | Done when |
|---|---|---|
| Day 1 AM | §4.3 pre-build check; `manifest.json` builder, canonical id, `verify` | a hand-made bundle verifies, and any single-byte change fails naming the member |
| Day 1 PM | `capture` (whole log + text/dimension excerpt), bundle-local profile, seal-then-verify, `keygen`/signing | the rejection-alarm run 5 captured into `run5.fexp`, verified signed and unsigned |
| Day 2 AM | `open` (temp copy, ordered opens, verdict re-check), `walk` record-from-file and playback | run5.fexp opened in a fresh 1.25.0 instance, pairing matches capture, the 4-step walk plays with spotlights |
| Day 2 PM | `respondsTo` + `compare`; the demo pair; docs | run 2 (no reset) vs run 5 (admin reset) compared, and the vendor risk-A vs risk-B pair captured and compared |

### Acceptance (rule 8: each has an executable check and a wrong-result witness)
1. **Integrity:** flipping one byte in any member, adding an unlisted file or deleting a listed one makes `verify` fail
   naming the member. *Witness:* skip hashing one role, and the test must fail.
2. **Identity:** re-capturing the same run twice gives the same member hashes. The ids differ only if `created`
   differs, and the spec says `created` is in the id. Changing the log changes the id.
3. **Signature:** a signed bundle verifies with the right public key and fails with another key or after any change.
4. **Travels:** `open` on a different home and temp directory, with the author's project absent, loads the log, the
   graph, the charts and the reports. Pairing equals `capturedVerdicts.pairing`.
5. **Never modified:** after `open`, a chart edit and a report rebuild in the analyser, `verify` on the original still
   passes.
6. **Excerpt honesty:** an excerpt's `kept`/`dropped` match an independent count of the source. The statement names
   the filter.
7. **Walk:** each step's spotlights are lit (the replies say `ok`), and a deliberately broken step reports its refusal
   rather than passing silently.
8. **Compare:** original vs response shows the chain, the changed jar and design hashes, identical inputs, and the
   behaviour rows the walks name. Swapping A and B flips *only in A/B* correctly.
9. **Honesty strings:** `verify` always prints `Semantic correctness NOT ASSERTED`, and a report or walk is never
   listed under evidence. *Witness:* move `reports/` into the evidence list, and a test fails.

Tests live beside the tool (`tools/bundle/test_fexp.py`, run by the existing Python tool-test step). The two
end-to-end checks (4, 7) need a running analyser. They are marked as such and run against the released jar in CI's
display job or locally.

---

## 7. The demo this enables (≈6 minutes)

1. **A ticket arrives with `incident.fexp`.** `fexp verify` prints the VERIFIED table, with semantic correctness NOT
   ASSERTED.
2. **`fexp open` + `fexp walk`:** the author's steps light the vendor node, the chart and the record that shows the
   fault.
3. **The fix:** swap vendor risk-A for risk-B in the Spring XML, generate, and show the new wiring beside the old.
4. **Re-run the bundle's recorded inputs** on the new build and capture `response.fexp --responds-to <incident id>`.
5. **`fexp compare incident.fexp response.fexp`:** chain OK, one jar changed, inputs identical, and the fault's rows
   changed while the controls are unchanged.
6. **Attach it to the PR.** The approver runs `verify`, `open` and `walk` on the response.

*Trust the evidence, not the author.*

---

## 8. Phase 2 and later (named, not built)

| Item | Why it waits |
|---|---|
| Static `index.html` (5.12) | needs PR #53's `SvgSurface` merged; the page design in evidence-bundles.md §5.12 stands |
| Exact replay (5.5) | `ReplayRecord` exists in the runtime, but Mongoose supplies no replay-execution command today; needs a small Mongoose or template addition |
| Runtime-emitted run receipt | Mongoose should emit processor/jar/config hashes at start; v0.1's receipt is declared by the capturer and says so |
| Analyser-native `bundle` verb and read-only project mode | once v0.1 shows which parts are general (Part 8's N=1 caution) |
| Redaction (5.8) and source coordinates (5.4) | cross-organisation completeness |
| Registry and organisation identities | positioning "assurance registry"; the product decision comes first |

## 9. Decisions needed before Day 1
1. **File extension and name:** `.fexp` (positioning) or `.exhibit`/*evidence bundle* (proposal Part 7). v0.1 uses
   `.fexp` unless told otherwise.
2. **Tool location:** `tools/bundle/` in this repository (proposed) or its own repository.
3. **Timestamps in the id:** yes (proposed; two captures are two experiments), or exclude `created` so re-captures of
   identical members share an id.
4. **Signing keys:** local files for v0.1 (proposed); key storage and rotation are a later decision.
