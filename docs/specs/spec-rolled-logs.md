# Rolled Log Sets — one session, many files (Design Spec)

Status: PROPOSED v1 · Owner: greg.higgins · Last updated: 2026-08-17 · Milestone **M30**

Companion to **[tracker.md](tracker.md)** (M30). Prompted by the owner: *"support a set of audit log
files that have the same name root but are rolled, and the name has a date-time or an index to show the
relative ordering… some date/time validation would be needed as well to capture logs that are not
correctly sorted wrt time."*

## The problem

A production session rarely lands as one file. Rollers cut by size or by the clock, so the trading day
arrives as `maker.log`, `maker-2026-08-17_09.log`, `maker-2026-08-17_13.log` — or as `maker.log.1`,
`maker.log.2`. Today the analyser opens exactly one file, so the investigation that crosses a roll
boundary ("the book was fine at 12:58, wrong at 13:02") means opening two files in two windows and
carrying the comparison in your head — the exact failure mode this tool exists to remove.

The owner's second clause is the sharper one: rolled sets are where **time-order violations** actually
happen — a mis-ordered concatenation, an overlapping copy, a file from the wrong day with the right
name. And since M26.2/M28, record time order is **load-bearing**: `read {at}` binary-searches it, time
windows prune by it, buckets group by it. Today that assumption (A2 in the M26 report) is documented
but never checked. This spec makes it checked — for sets AND for single files.

## The principle

**Names discover; content orders; violations are reported, never repaired.** A filename suffix is a
hint good enough to find siblings, and nothing more — index suffixes are genuinely ambiguous
(logrotate's `x.log.1` is the *newest* rolled file; an incrementing writer's `x.log.1` is the
*oldest*), and a date in a name is a claim, not a fact. The authority on order is the content: each
file's first and last `logTime`. And when the content itself is disordered, the analyser **says so and
shows the evidence** — it never silently re-sorts records, because a record's position in its file is
part of the evidence (dispatch order within a file is exact; spec §8.7 relies on it).

## A — the decisions (answers proposed, reviewer should challenge)

- **D-R1 — discovery by name, ORDER by content.** Sibling discovery matches `<root>` + a recognised
  suffix (ISO-ish date-time, or a numeric index) in the same directory. The load order is then decided
  by each file's **first record `logTime`** (a cheap head-probe, not a full parse). The suffix is never
  consulted for ordering.
  *Rationale:* this dissolves the logrotate-vs-incrementing-writer ambiguity instead of adding a
  convention toggle someone will set wrongly, and it means a lying filename cannot mis-order the set.
  *Alternative rejected:* ordering by suffix with a newest-first/oldest-first option. A toggle whose
  wrong setting silently reverses causality is D-F1's sniffing mistake wearing a different hat.

- **D-R2 — the set is ONE logical log with a gap-free global `recordIndex`; byte offsets become
  (file, offset) pairs.** `recordIndex` stays the primary anchor everywhere (it already is — goto,
  flag, series crossings, read). `byteOffset` remains meaningful as an offset **within the record's own
  file**, and every surface that reports one also names the file (`read` rows, crossing events,
  `context`, the copy-prompt's anchor list). `read {byteOffset}` and `goto {byteOffset}` gain an
  optional `file` discriminator; given a bare offset the verbs refuse with the file list rather than
  guessing.
  *Rationale:* the copy-prompt promises an agent "here is the file and the byte offsets — grep it
  yourself". That promise survives only if offsets stay real file offsets. A synthetic concatenated
  offset would be a number that exists in no file on disk.
  *Alternative rejected:* encoding the file in the offset's high bits. Compact, invisible, and the
  first agent that seeks to it in a real file reads garbage.

- **D-R3 — time validation is a first-class result, for sets and single files alike.** On load the
  analyser checks (a) **within-file monotonicity** — `logTime` non-decreasing record to record — and
  (b) **cross-file continuity** — each file's first time is ≥ the previous file's last time. The
  outcome is a `TimeOrderReport`: clean, or a bounded list of violations, each with record anchors
  ("`maker.log.2` overlaps `maker.log.1` by 3.2s — 214 records interleave", "17 records out of order
  within `maker.log`, first at record 3,412"). The report is shown in the UI (status banner), echoed
  by the `open` verb, and included in `context` — an agent must not have to discover disorder by
  getting wrong answers from `at`.
  *Rationale:* the owner's ask, and A2 made checkable. Both checks also run on a plain single-file
  open — the assumption was always load-bearing there too; sets are just where it breaks in practice.
  *Alternative rejected:* silently sorting records into time order. Never — repair destroys the
  evidence (a backwards timestamp IS a finding: a clock step, a mis-merge, a bad transport), and
  dispatch order within a file is authoritative in ways wall-clock is not.

- **D-R4 — when time order is violated, time features degrade LOUDLY, record features don't degrade
  at all.** Filtering, reading, stepping, flagging, graphs-by-record all work regardless (they walk
  record order). The time-anchored features — `read/goto {at}`, time windows, `buckets`, time-range
  filtering — keep working but carry the violation note in their echoes while a violation exists
  ("time order is violated in this log — 'at' resolution may be approximate; see open's report").
  *Rationale:* refusing time features outright would punish the common mild case (a 2-record clock
  jitter) as hard as the pathological one; silence would be A2's failure. The note names the blast
  radius and points at the evidence.
  *Alternative rejected:* per-feature hard refusal above a violation threshold. A threshold is a
  second policy to justify; the note plus the report lets the human judge.

- **D-R5 — opening a set is offered, never assumed.** Opening a file that has rolled siblings shows
  an offer — "*4 rolled siblings found (09:00–17:30, 2.1 GB total) — open the set?*" — listing the
  files in content order with their time ranges. The `open` verb takes an explicit list
  (`open {logs: [path, …]}`); `open {log}` on a set member echoes that siblings exist but opens ONE
  file, exactly as today.
  *Rationale:* M20.5's offer-never-act, again: a same-rooted file in the directory is strong evidence,
  not consent — the sibling might be another environment's log with a copied name. An agent declares
  the set explicitly (D-F1's "declared, never inferred", applied to file sets).
  *Alternative rejected:* auto-loading siblings on open. The wrong-file case corrupts an investigation
  invisibly, which is worse than one extra click on every right-file case.

- **D-R6 — memory scales per file, not per set.** The composite store delegates to one backend per
  file, each chosen by the existing size threshold (`HeapLogStore` small, `MappedLogStore` big), under
  one global index. A 6 × 400 MB set must not need 2.4 GB of heap because each member individually
  sat under the heap threshold.

## B — surface

**UI** — the open-time offer (D-R5); a status banner when the `TimeOrderReport` is not clean, clicking
it shows the violations with go-to-record links; the window title shows `maker.log (+3 rolled)`.

**Verbs** — `open {logs: [..]}` (explicit list, validated, echoing the content order chosen, per-file
record counts and the `TimeOrderReport`); `context` gains the file list and the report; `read`/`goto`
accept `{byteOffset, file}` and refuse a bare ambiguous offset naming the files. Everything else is
untouched — `recordIndex` anchors are global and gap-free by construction.

**Copy-prompt / agent brief** — lists every file of the set with its record range and time range, and
states the (file, offset) anchor rule, so a grep-capable agent seeks in the right file.

## Non-goals / guardrails

- **No record re-ordering, ever** (D-R3). The analyser reports disorder; it does not repair it.
- **No cross-set merging** — one set = one name root. Interleaving two different processors' logs is
  a different feature with different semantics (and a multi-clock problem, per M29 D-F3).
- **No S3 sets in M30.** A local directory only; S3 listing/multi-fetch is a follow-up once the local
  contract is proven.
- **Follow/tail of a rolled set is out of scope** — `supportsFollow()` stays false for the composite;
  the existing single-file follow is unchanged. Rotation-aware follow is a natural M30 follow-up, not
  a v1 requirement.
- Sibling **discovery reads names only**; file CONTENT is read only for the head/tail time probe of
  files the user (or the explicit verb list) actually included. No directory-wide content scanning.
- `FaqSecurityContractTest` untouched: `open` already reaches the filesystem and is already marked
  destructive; a list of paths widens nothing.

## Acceptance

1. A 3-file rolled set (demo fixture, cut at two boundaries) opens as one log: filtering, stepping,
   graphing and `series` behave identically to the same data in one file — pinned by a test that runs
   the same assertions against both shapes.
2. An index-suffixed set named in logrotate convention (`.1` = newest) and the same set named in
   incrementing convention both load in correct time order without any configuration (D-R1
   demonstrated).
3. A deliberately mis-ordered set and a file with interleaved timestamps both produce a
   `TimeOrderReport` naming files, counts and first-violation anchors — in the UI banner, the `open`
   echo, and `context` (D-R3).
4. With a violation present, `read {at}` still answers and carries the caveat note (D-R4).
5. `read`/`goto` with a bare `byteOffset` against a set refuse with the file list; with `{file}` they
   resolve. The copy-prompt names every file and its ranges (D-R2).
6. Opening one member of a set never auto-loads siblings; the offer appears; `open {logs}` is exact
   (D-R5).
7. A set whose members are individually under but jointly over the heap threshold opens with mixed
   backends and bounded heap (D-R6).

## Delivery slices

1. **M30.1** `RollSetResolver` (pure, headless): sibling discovery (date-time + index suffix
   grammars), head/tail time probe, content ordering, `TimeOrderReport` model with both checks — the
   full D-R1/D-R3 test set, including the two logrotate-convention fixtures, before any UI or store
   work.
2. **M30.2** Composite store: `RolledLogStore implements LogStore` over per-file backends (D-R6),
   global index with a per-record file id, (file, offset) anchors through `read`/`goto`/crossings/
   `context`/copy-prompt (D-R2).
3. **M30.3** Validation surfaced: UI banner + go-to-violation, `open` echo, `context` report, the
   D-R4 caveat notes on time-anchored features — and the single-file monotonicity check wired into
   the normal open path.
4. **M30.4** The offer (D-R5), `open {logs}` verb + schema, docs (`records-and-filtering.md`, the
   assistant guide, FAQ if wording needs it) + changelog.

**Effort:** M30.1 is the thinking; M30.2 is the widest ripple (every offset consumer audited — the
M26.2 `targetRow` seam and `SeriesScan.event()` are the known sites); M30.3–.4 are small. The
per-record file id adds one small column to `LogIndex` — the same pattern as the existing dimension
and thread columns.
