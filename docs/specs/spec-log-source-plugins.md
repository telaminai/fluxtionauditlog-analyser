# Log-Source Plugins — other containers, same records (Design Spec)

Status: PROPOSED v1 · Owner: greg.higgins · Last updated: 2026-08-17 · Milestone **M31**

Companion to **[tracker.md](tracker.md)** (M31) and **[spec-rolled-logs.md](spec-rolled-logs.md)**
(M30, which generalises the anchor model this spec relies on). Prompted by the owner: *"we should also
be able to support different auditlog parsers — parquet, chronicle, db etc. These would be plugins,
not a requirement."*

## The principle

**The analyser understands one thing: the Fluxtion audit record. Containers are plugins.** A parquet
file, a Chronicle queue and a database table holding audit records differ only in how bytes become
records — everything after that (index, filter, topology, graphs, verbs, MCP) is container-blind
already, because `LogStore` is an interface and every consumer works through it.

Two standing commitments make "plugin, not a requirement" the only acceptable shape:

- **The fatjar stays lean.** FlatLaf is the only runtime dependency, `jbang analyser@…` depends on
  that, and a Chronicle or Arrow dependency tree in the core would end it. Format readers live in
  separate artifacts the core never compiles against.
- **The core never learns a foreign format** (M29's rule, one level down). M29 keeps foreign *data*
  out by making the agent adapt it; M31 keeps foreign *containers* out by making a plugin adapt them.
  Both leave the core hermetic.

## A — the decisions (answers proposed, reviewer should challenge)

- **D-P1 — the SPI hands over RECORDS, not stores.** A plugin implements a small reader interface —
  identify yourself, say whether a source is yours, then stream `(header-scalars, node-log text)`
  record by record in **container order**. The CORE builds the `LogIndex`, the store, the filter
  columns and everything above them. Plugins never see Swing, the index, or each other.
  *Rationale:* `LogStore` is too big a contract to hand a third party (offsets, mapped IO, follow
  semantics, close discipline — all core invariants). The record stream is the narrow waist: a
  plugin that can produce records correctly gets every analyser feature for free, including ones
  that ship after it.
  *Alternative rejected:* `LogStore` as the SPI. Every core improvement (M30's file ids, a future
  column) would become a breaking plugin-API change.

  **Make the time base a MANDATORY per-source declaration from day one** (`UP-FLX-25`, review X2): a
  parquet reader *knows* its epoch unit, a DB reader knows its column type, and a Chronicle reader
  knows what the writer stamped. Requiring `timeBase {epoch, zone, source}` of every reader costs a
  plugin author nothing, and means plugin sources arrive **better described than the native YAML log**
  — which declares none of it today, so the analyser assumes epoch millis in six files and hardcodes
  UTC for bucketing. That inversion is itself worth putting in the upstream ask. Adding the field
  later would be a breaking change to the one contract this spec exists to keep stable.

- **D-P2 — every record carries a CANONICAL TEXT RENDERING.** The plugin supplies each record's text
  in the standard eventLogRecord YAML shape (for a text container, the original bytes; for parquet/DB,
  a rendering the plugin produces). The core treats it exactly like file text: the detail viewer, the
  free-text filter, `read`, and exports all keep working.
  *Rationale:* half the tool's surfaces are text-shaped (raw read-through, text filter, copy-prompt
  quoting, reports quoting evidence). A record with no text form would need a parallel "structured
  detail" path through every one of them — a rewrite disguised as an adapter.
  *Consequence, stated honestly:* for non-text containers the text is a RENDERING, not original bytes
  — the copy-prompt's "grep the file yourself" clause and byte-offset anchors are text-container
  features. Non-file sources anchor by `recordIndex` only (M30's D-R2 already makes recordIndex the
  primary anchor and (file, offset) an optional extra; this is that decision meeting its second
  customer).

- **D-P3 — plugins load by explicit user action, and the boundary is named.** A plugin is a jar the
  user places in `~/.fluxtion-analyser/plugins/` (or adds via Settings ▸ Plugins). Loading a jar is
  **arbitrary code execution** — the spec says so in those words, the Settings page says it, and the
  FAQ security answer gains the sentence. Discovery is `ServiceLoader` over an **isolated
  per-plugin classloader** (a plugin's Chronicle version must not fight another's, or ours — we have
  none). No network fetch, no auto-install, no plugin ever bundled: the analyser without plugins is
  byte-identical to today's.
  *Rationale:* the trust model must be legible. "A jar you chose to install can do anything" is
  honest; a curated in-app marketplace is a distribution business this tool is not in.
  *Alternative rejected:* sandboxing plugin code (SecurityManager is deprecated-for-removal; a real
  sandbox is a process boundary, which is a different architecture — see D-P5).

- **D-P4 — capability flags, not lowest-common-denominator.** The reader declares what its container
  supports: `follow` (a Chronicle queue can tail; a parquet file cannot), `byteAnchors` (text files
  yes, DB no), `randomAccess`. Core features check the flag and degrade the M28/M30 way — loudly, in
  the echo/UI, never silently ("this source cannot follow — Follow is disabled for it").
  *Rationale:* forcing every container to pretend to be a text file wastes Chronicle's tailing and
  fabricates offsets for DB rows; forcing every feature to the weakest container throws away what
  works today.

- **D-P5 — in-process SPI first; an adapter PROCESS is the recorded fallback.** If a plugin's
  dependency tree proves hostile in-process (native libs, JPMS conflicts), the same record-stream
  contract can run over stdio in a subprocess — the MCP bridge already proves the pattern in this
  codebase. That is a delivery option under the same SPI, not a second architecture; recorded here so
  the in-process choice is revisited with evidence rather than relitigated from scratch.

## B — surface

**Open path** — *File ▸ Open* gains plugin-declared filters; `open {log}` consults `canOpen` across
installed plugins when the path is not a text log (echoing which plugin claimed it — and refusing
with the installed-plugin list when none does). `open {log, format: "chronicle"}` forces a reader.

**Settings ▸ Plugins** — list (name, version, formats, jar path), add/remove, and the arbitrary-code
warning. `context` names the active source's format and capability flags.

**What ships in-tree** — the SPI module, the classloader/registry, capability wiring, docs — and the
existing text parser refactored to BE the built-in reader behind the same SPI (the proof the seam is
real, and the reference implementation plugin authors read). **No format plugin ships in this repo**;
a `parquet-reader` example lives in the playground/examples repo where its dependencies can be honest.

## Non-goals / guardrails

- **No format reader in the core, ever** — parquet/chronicle/DB dependencies never enter this pom.
- **No write path.** Readers read. An exporting plugin is a different trust conversation.
- **No plugin verbs/UI extensions** — the SPI is sources only. A plugin cannot add actions to the
  socket (the FAQ's verb enumeration must stay true without asterisks).
- **No auto-discovery beyond the plugins directory**; no network; no bundled plugins.
- Existing behaviour is bit-for-bit unchanged with an empty plugins directory; the text reader moving
  behind the SPI must be invisible (the full suite is the regression net, as in M28.2).

## Acceptance

1. The shipped text parser runs as the built-in SPI reader and the whole existing suite passes
   unchanged — the seam demonstrated on the format that matters most.
2. A toy example plugin (CSV-of-records or JSONL, built out-of-tree against the SPI artifact) opens a
   non-YAML container and every downstream feature works: filter, topology step-through, graphs,
   `series`, flags, report export — with `recordIndex` anchors and the D-P2 text rendering visible in
   the detail viewer.
3. A source without `follow`/`byteAnchors` degrades loudly per D-P4 (Follow disabled with a reason;
   `read {byteOffset}` refused naming the capability).
4. Removing the plugin jar and reopening yields the D-P3 refusal naming installed plugins; the FAQ
   names the plugin trust boundary; `FaqSecurityContractTest` pins the sentence.
5. Two plugins with conflicting transitive dependencies coexist (isolated classloaders demonstrated
   by test fixture).

## Delivery slices

1. **M31.1** The SPI (`AuditLogReader`: identity, `canOpen`, record stream, capability flags) + the
   text parser refactored behind it; suite green unchanged. The SPI published as a tiny separate
   artifact (`analyser-reader-spi`) plugin authors compile against.
2. **M31.2** Registry + isolated classloaders + Settings ▸ Plugins + the trust boundary in FAQ/docs.
3. **M31.3** Capability wiring (follow/byteAnchors/randomAccess degrade loudly) + `open` integration
   (`canOpen` sweep, `format` override, refusal echo).
4. **M31.4** The out-of-tree example reader (playground repo) + plugin-author guide on the docs site
   + changelog.

**Effort:** M31.1 is the real work (the text parser owns framing today; inverting it under an SPI
without behaviour change is M28.2-shaped — wide, shallow, compiler-enforced). M31.2–.4 are small.
**Sequencing note:** M30.2's composite store and M31.1's SPI both touch how stores are assembled;
whichever lands second rebases on the first — do not run them concurrently.
