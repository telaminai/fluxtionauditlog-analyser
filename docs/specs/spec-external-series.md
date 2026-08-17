# External Series — plotting what the outside world did (Design Spec)

Status: ACCEPTED v3 (v2 adopted docs/handoff/review_m29_external_series.txt — D-F4 allowlist narrowed,
three contract gaps closed, D-F3's rationale made durable. v3 finishes the D-F4 fix: the project
directory is a repository root by the same argument that removed source roots, so the read root is the
**export directory** `ExportGuard` already confines writes to; plus original-line-number diagnostics
across the G1 sort) · Owner: greg.higgins · Last updated: 2026-08-17 · Milestone **M29**

Companion to **[tracker.md](tracker.md)** (M29), the graph engine (`graph/Series`, `graph/SeriesExtractor`)
and **[spec-expr-conditionals-windows.md](completed/spec-expr-conditionals-windows.md)** (M28), whose window
semantics this spec deliberately does **not** reopen. Prompted by the owner: *"Claude could filter, parse
and generate a series from a FIX log; pass the file location to the analyser to plot. Accept CSV to begin
with."*

## The principle

**The analyser never learns a foreign format.** The agent does the parsing — FIX, GC logs, venue latency,
a Grafana export — and hands over a CSV of `(timestamp, value)`. The analyser stays hermetic, exactly as
`Expr` stays a bounded vocabulary rather than a scripting engine. Teaching this tool FIX would invite
protocol code into a codebase whose whole argument is that it has none.

This is why the feature is small. It is spec'd as **external timeseries**, never as "FIX support".

### Why the plotting is already built

`Series` is `long[] xs`, `double[] ys` and a label — and `key == null` for a **derived (formula) series**
is an existing, supported case. A foreign series is structurally identical to a formula series: no
`GraphKey`, a display label, points in time order. The renderer, legend, axes, styling, CSV export and
PNG/PDF paths therefore need **no changes at all**. The work in this spec is not drawing the line; it is
making sure the line does not lie.

## A — the CSV contract

One row per sample. Columns are **named explicitly by the caller**; nothing is sniffed (D-F1).

| field | meaning |
|---|---|
| `path` | the CSV file |
| `label` | legend name — must not collide with an existing series on the graph |
| `time` | the timestamp column name |
| `timeFormat` | `epochMillis` · `epochSeconds` · `iso8601` · an explicit `DateTimeFormatter` pattern |
| `zone` | IANA zone (`UTC`, `Europe/London`) — **required** unless the format carries an offset |
| `value` | the value column name |
| `offsetMillis` | optional, default 0 — a deliberate clock correction, always displayed |

A blank or non-numeric value cell yields `NaN` → a gap, reusing the existing **NaN is no data point**
invariant. Rows whose timestamp fails to parse are counted and reported, never silently dropped.

Three cases the contract answers explicitly (review G1–G3), because each is a silent choice otherwise:

- **Row order** — rows may arrive out of time order (merged captures routinely are) and are **sorted on
  load**; the echo reports `"N rows reordered"` when it happened. Refusing would bounce real files for
  no safety gain; sorting silently would hide that the source was odd.
  Diagnostics cite the **original file line number**, carried through the sort. A message naming the
  post-sort position would point at the wrong row, and under D-F4 those messages are the one place file
  content reaches an agent at all — a misleading line number there is worse than none.
- **Duplicate timestamps** — both points are kept (the audit-log side already permits multiple records
  per millisecond); nothing is deduplicated.
- **Size bound** — the loader refuses past **5,000,000 rows**, loudly, naming the bound (the M26
  cap-honesty rule: bounded input, never a silent subset). The check runs during the streaming pass —
  a 10 GB file is never buffered to find out it is too big.

## B — the five decisions (answers proposed, reviewer should challenge)

- **D-F1 — the clock domain is declared, never inferred.** `timeFormat` and `zone` are required inputs; the
  loader does not sniff ISO-8601-vs-epoch and does not guess a timezone. *Rationale:* the audit log's
  `logTime` is the processor's clock and a foreign log is another host's. A 200 ms skew silently converts
  "the venue messaged us, then our book moved" into its reverse — a **false causal claim**, which is the
  single failure this tool exists to prevent. A wrong declared zone is visible in the legend; a wrong
  guessed one is invisible.
  *Alternative rejected:* sniffing common formats for convenience. The convenience is small and the
  failure mode is undetectable.

- **D-F2 — foreign series are permanently second-class, visibly.** They carry no `recordIndex` and no
  `byteOffset`, so they cannot be `goto`'d, cannot be `flag`ged (a `Finding` is record-anchored), and
  cannot return crossing anchors from `series`. They are marked in the legend and **stamped as external in
  every export** (PNG caption, PDF report, CSV export).
  *Rationale:* in an exported report a foreign line otherwise looks exactly as authoritative as an
  audit-derived one while being unreproducible from the log. The reports are evidence; that property is
  worth more than the convenience.
  *Alternative rejected:* synthesising a nearest-record anchor so foreign points become clickable. That
  manufactures a link the data does not contain — the same **never fabricate** rule that decides M28's
  D-W2.

- **D-F3 — no foreign references in formulas, in M29.** `Expr` refs stay audit-log-only; a foreign series
  is plotted, not computed against.
  *Rationale:* `spread − venueMid` puts two sampling regimes and two clocks inside one LOCF/STRICT
  resolution. M28's landing supplies the **vocabulary** for that question, not the **answer** — carry
  semantics across a clock boundary is a decision nobody has made, and it deserves its own proposal
  rather than arriving as an accident of namespace admission. (First drafted as "not answerable before
  M28 lands"; M28 then landed and the deferral still stands — the durable reason is the one above.)
  *Alternative rejected:* admitting foreign keys into the `GraphKey` namespace now. Cheap to type, and it
  would silently define cross-clock carry semantics by accident.

- **D-F4 — reading a path is a new capability, and is confined to the directory the user already
  nominated.** Foreign files may be read only from the **configured export directory** — the one
  `ExportGuard` already confines verb *writes* to, behind the existing *Settings ▸ Assistant ▸ "Allow
  file exports"* opt-in — or from a **file the user picked in a chooser this session; the chooser IS the
  grant**.

  Source roots are NOT on the allowlist (review challenge, accepted): a user who adds a source root
  consented to "read `.java` under here for navigation", and reusing that grant as "an agent may read
  any file under here as data" is scope creep on directories that routinely hold `.env` files and keys.
  **Neither is the active project directory** — that argument condemns it equally. `ProjectProfile`
  resolves its file as `<projectDir>/.analyser/project.fluxtion-settings` and the profile is *designed to
  be committed*, so the project directory is normally a repository root: the same `.env` files, the same
  keys. Allowing it would have removed one repo-shaped grant and kept another on reasoning that rules out
  both.

  The export directory is the right root because it is the only place the user has explicitly nominated
  as *"where the analyser may keep files"*, and it makes the symmetry this decision claims actually true —
  `ExportGuard` confines writes to a **designated single-purpose directory**, not to a repo, so the read
  counterpart must be a designated directory too. It also answers the workflow question the spec
  otherwise leaves open — *where does the agent's derived CSV land?* — and yields the invariant:

  > the analyser can only read back what it, or the user, was already permitted to put there.

  No new consent surface, and a user may still drop a file into that directory by hand. Anything else
  goes through the chooser. The FAQ's security answer gains a sentence describing the read rule when
  M29.3 lands, pinned the same way the write rules are (`FaqSecurityContractTest`).
  **Parse diagnostics are bounded and sanitised** — they name the line number and the column, never the
  offending cell contents verbatim beyond a short, escaped excerpt.
  *Rationale:* `ExportGuard` already makes verb *writes* opt-in and directory-confined; reads deserve the
  symmetric treatment now that an MCP-connected agent can drive them. The real leak vector is not the plot
  but the error message: `line 4: expected number, got "ssh-rsa AAAAB3…"` echoes arbitrary file content
  straight into a model's context.
  *Alternatives rejected:* unrestricted paths with a UI confirmation (confirmation fatigue makes that a
  yes-button, and it does nothing about the diagnostic echo); source roots on the allowlist, as above.

- **D-F5 — a saved graph stores a path, and degrades out loud.** Paths persist project-relative (`~`-relative
  outside a project, matching profile conventions). Opening a graph whose foreign file is missing reports
  **"2 of 3 series resolved — 'venue mid' not found at …"** and draws the rest. Export dialogs and the
  sharing docs **disclose that a shared graph may depend on local files**.
  *Rationale:* the F1 lesson from M27 — a category that silently carries something the export side does not
  mention. A shared graph that depends on a file the recipient does not have must say so before it is sent,
  not after it is opened.
  Share-merge edge (review N1): graphs merge **replace-by-name**, so an arriving graph replaces the
  same-named local one wholesale — external series and all; a label collision between an incoming
  external series and a local series can therefore only occur *within* the arriving graph, where the
  add-time rule above already forbids it. M29.4 pins this with a test rather than assuming it.
  *Alternative worth weighing:* **embedding the sample data in the saved graph** rather than a path. That
  makes a shared graph fully reproducible and immune to a moved file — genuinely attractive — at the cost
  of unbounded profile growth and data that silently goes stale against its source. Proposed as a possible
  M29.5 (`embed: true` for small series), not as the default.

## C — surface

**UI** — *File ▸ Add series from CSV…* opens a small dialog: file, label, the time/zone/value columns
(populated from the header row), and an offset field. The legend marks the series external; the status line
names the applied offset.

**Verb** — extends `graph` rather than adding a verb, because this is a series source and `graph` is where
a chart's contents are declared:

```
graph { external: [{ path: "runs/venue-mid.csv", label: "venue mid",
                     time: "ts", timeFormat: "iso8601", zone: "UTC",
                     value: "mid", offsetMillis: 0 }] }
```

The echo follows M26.4's hardening: it names rows loaded, rows skipped with reasons by count, rows
reordered, the resolved time range, and the applied offset. A path outside the allowlist is refused
**loudly**, naming the rule. The **resolved time range in the echo is a designed defence**, not
decoration (review N2): a `timeFormat` pattern that parses but is wrong (US vs EU day/month) is D-F1's
silent-skew risk arriving through the declared path — an agent that sees "resolved range:
2019-03-04..2019-12-01" against last week's log catches its own pattern error in the same reply.

## D — convergence with M28 P3

M28's unscheduled **P3 (event markers / rug strip)** is the same loader with a different consumer: a CSV of
*events* rather than values becomes tick marks under the time axis. The loader should therefore return
points, not a chart — one reader, two consumers. Worth knowing before the loader's output type is fixed.

## Non-goals / guardrails

- **No protocol knowledge, ever.** No FIX, no SBE, no vendor formats. The agent adapts; the analyser plots.
- **No format sniffing** (D-F1).
- **No foreign refs in expressions** in this milestone (D-F3).
- **Read-only.** Nothing in M29 writes to a foreign file.
- **No network sources.** File paths only; a URL fetch is a different trust conversation and a separate spec.
- **Single forward pass**, bounded memory — the same contract the extractor already keeps.
- `FaqSecurityContractTest` and the `ExportGuard` write rules are untouched; D-F4 adds the read counterpart.

## Acceptance

1. A `(timestamp, value)` CSV plots alongside an audit-derived series on the demo fixture, with the declared
   zone and any applied offset visible on the chart — not only in the dialog that created it.
2. An exported **PDF and PNG both mark the foreign series as external**; a reader cannot mistake it for
   audit-derived evidence.
3. A saved graph whose foreign file has moved opens, draws the remaining series, and states what did not
   resolve (D-F5 demonstrated, not merely documented).
4. A path outside the allowlist is refused with a message naming the rule; a malformed cell produces a
   diagnostic that does **not** contain the cell's full contents (D-F4 pinned by test).
5. Every D-decision above is pinned by a named test.
6. Foreign series remain unreferencable from `Expr` — attempting it is a parse error naming the label
   (D-F3 pinned).

## Delivery slices

1. **M29.1** Loader + the explicit CSV contract + time/zone handling + bounded sanitised diagnostics.
   Headless and pure; ships with the full D-F1/D-F4 test set before any UI exists.
2. **M29.2** UI: *Add series from CSV…*, legend marking, offset display, D-F2 export stamping.
3. **M29.3** `graph {external}` verb + M26.4-style echo + read confinement wired to the allowlist.
4. **M29.4** Persistence and sharing: project-relative paths, honest degradation, export-side disclosure,
   docs (`sharing-setups.md`, `graphing.md`) + changelog.
5. **M29.5** *(optional, decide after 29.4)* `embed: true` — carry small series inside the saved graph for
   fully-portable sharing (D-F5's alternative).

**Effort:** M29.1–29.3 are comparable to M26.1 (the `series` verb). M29.4 is small but touches the share
surface, which is where this milestone's real review attention belongs.
