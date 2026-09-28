# Evidence bundle packaging — first delivery (package, verify, open, walk; no replay)

**Status: r4 (2026-09-28), IMPLEMENTED on `feat/evidence-bundle-v1`; the review's two REQUIRED findings fixed.** r3
records what building it and driving it end to end changed; r4, the review fixes (§11, §12). The executable reference is `tools/evidence-bundle-demo.py`; the
results are in `docs/handoff/evidence/evidence-bundle-v1-2026-09-28/RESULTS.md`.

The review is
[`review_spec_evidence_bundle_packaging_2026_09_28_claude.md`](../handoff/review_spec_evidence_bundle_packaging_2026_09_28_claude.md)
(`f784dac9`, `220f78ed`). r2 **accepts its placement verdict (C), with one extension that argues back** (§3.3),
records the audience decision (§3.4), and corrects two of its facts (§12).

**Where it comes from:**
- the combined proposal and its discussion log (L-1…L-33) on PR #52, branch `proposal/evidence-bundles`,
  `docs/proposals/evidence-bundle/`;
- M69 spotlight walks, shipped in 1.26.0 ([`spec-spotlight-walks.md`](spec-spotlight-walks.md)).

**Owner decisions this spec builds on (L-33, 2026-09-28), not open for review:**
- **D-0, amended.** The first delivery is a portable investigation **without replay**: capture, verify, open and play
  the walk, so the demo works easily. Replay and comparison are **deferred, not dropped**. No first-delivery claim may
  describe a bundle as reproducing or fixing the incident.
- **D-2.** A bundle's identity is `sha256:` of the **exact manifest bytes**, held outside the manifest.
- **D-3.** Unsigned first. Verification detects a changed member; it does not authenticate the sender, and every
  surface says so.

## 1. What the first delivery is for

A person investigates an incident in the analyser and leaves a report and a spotlight walk. They hand **one file**
to someone else, who opens it on their own machine and follows the walk. The recipient sees the same records, the
same charts and the same explanation, and can tell that nothing in the file was changed on the way.

**The demo, about 3 minutes, not yet measured:**

| step | target | what may be claimed |
|---|---|---|
| a `.fexp` arrives and is verified | 30 s | every member's bytes match the manifest; provenance and limits are shown; **no claim about who sent it** |
| open it | 30 s | it opens from a disposable working copy; the received file is never modified |
| follow the walk | 90 s | the walk's record, chart and topology steps show the incident, numbered, with the author's captions |
| the report | 30 s | it is there, as the sender left it |

**What the recipient cannot do** (stated so that a passing EP-A6 does not overpromise — review §7): they have none
of the sender's source roots, so **source navigation is dead**. Java and design views are unavailable, and the walk
must not rely on source or menu targets (M69 refuses those anyway). Walk targets on records, charts and the graph
are *current* (RAN by the review, on the same machine).

## 2. What already exists (inventory, checked at `0e268087`)

| need | exists today | gap |
|---|---|---|
| walks, reports, saved charts and focuses | `SettingsShare` `GRAPHS` and `REPORTS` (walks since 1.26.0), with export, preview and apply | none, **except that GRAPHS carries file paths** (next row) |
| **paths inside the allow-list** | `SettingsShare:182`: external-series CSV paths (`graph.i.ext.j.path`) and external marker sources are written **home-relative, "like source roots"** | **this is the silent re-anchoring risk the review names.** On another machine they point at a CSV that is not there. v1 **excludes charts with external series or markers from a bundle, and names them** (§4.2). The question is now "does any allow-listed key carry a path?", not "is any path absolute?" |
| the view | `SettingsShare.VIEW` (hidden columns); a walk step restores its own tab, filter and record | the sender's live filter and selection are not carried; the walk's first step states the view (§5) |
| **flags** | the `flag` verb and `context.flags`. **They persist nowhere:** an in-memory `HashSet` (`MainFrame:131`), cleared on every log open (`:706`, `:4282`, `:4457`, "flags are per-file (model row indices)") | **dropped from the first delivery** (§4.2, §9) |
| the log | the opened file, and its read identity with per-file `sha256` | a coherent capture (§4.1) |
| the graph | the opened graphml and its digest | none |
| a pending load | **`context.inFlight`**: present while any gated operation is outstanding (`operationGate.inFlightWhat()`, M44.3 D-A4 "a load that has not landed is reportable"), including an open | none. **The review says this is not exposed; it is** (§12) |
| **the log generation** | internal only (`SessionSnapshot.logGeneration`). The walk save already refuses across a generation change (`WalkAuthoring`: "another log was opened while this walk was being saved") | **one `context` field: `log.generation`** (§4.1) |
| open a project, log and graph | `open {project}`, `open {log}`, `open {graphml}`. The review's cold reopen restored three walks and seven reports and played a four-step walk with every target CURRENT (RAN, same machine) | none |
| a command-line entry | `Main.java` parses `--mcp` and `--rest` | `--pack`, `--verify` and `--unpack` (§3.3) |
| agent-driven steps | skills `load-audit-log`, `guided-start`, `point-at-the-fault`, `replay-a-run`; the owner's capture and load experiments | `capture-evidence-bundle` and `open-evidence-bundle` skills |

## 3. Placement — decided

### 3.1 The rule

Adopted from the review, and meant to be reused (it will be asked again for replay):

> **Put it in the analyser if a skill would have to guess, or would have to re-implement something a recipient must
> trust. Otherwise put it in a skill.**

### 3.2 The verdict: C

**No new verb, no menu item, no dialog.** The recipient half already works: three shipped `open` verbs and
`walk {play}`. The analyser adds only what the rule assigns it. Skills do the assembly, the choosing and the
narrative.

The reason for keeping the UI at zero is the owner's: every new dialog is a state machine nobody has exercised, and
the 1.26.0 demo broke on one surface interacting badly with another.

### 3.3 Where r2 argues back: the whole bundle format belongs in the analyser, not only verification

The review puts **verification** in the binary and lets a skill write the manifest and the zip. By the review's own
rule, that is one piece short.

- **The manifest is the thing a recipient must trust.** Its exact bytes are the identity (D-2). If a skill writes
  it, the format has two implementations, skill prose and `--verify`, and a bundle that verifies may still be
  malformed in ways only the writer knows. For example, a member the skill forgot to list is refused by verify, but
  a skill that listed a path with `..` is caught only if the reader thinks of it.
- **Unpacking is also trust-relevant.** A zip can carry path escapes and links, and extracting one safely into a
  fresh working copy is exactly what a recipient must not have to re-implement correctly in prose.

So **the CLI owns the format end to end**, in three flags on the existing binary, all headless:

| flag | does | exits |
|---|---|---|
| `--pack <folder> <out.fexp>` | writes `manifest.json` from the folder's files (sha256, bytes, the fixed `limits`), then zips the folder and the manifest | non-zero if a member path escapes the folder |
| `--verify <bundle.fexp>` | prints the identity (`sha256:` of the manifest bytes) and a per-member verdict, and says in words that this does not authenticate the sender | non-zero on any mismatch, missing, unlisted or escaping member |
| `--unpack <bundle.fexp>` | verifies, then extracts into a **fresh** working-copy directory and prints its path; the received file is never touched | non-zero, and nothing extracted, if verification fails |

That is one implementation of the format, with pinned fixtures, still no UI and still no verb. **What stays in the
skill** is choosing what goes in the folder: the log, the graph, the allow-listed Settings export. That is judgement,
and it varies per investigation.

**r3, a fourth flag by the same rule: `--bundle-profile <settings> <out>`.** Building the capture showed that "the
allow-listed Settings export" has no headless route: the export is a dialog. A skill filtering profile keys itself
would re-implement `SettingsShare`'s categories, and the first key it missed would carry a source root or a key off
the machine. That is the rule's "re-implement something a recipient must trust", so the analyser writes it
(`BundleProfile`): GRAPHS, REPORTS and VIEW only; a chart with external series or markers left out and named, with
each walk step and report section that showed it; and a refusal, naming the key, if any kept value is shaped like a
machine path. It reads the open project's profile or, with none open, the person's own settings file.

### 3.4 The audience: decided, so it is not relitigated

**The first demo's recipient is technical or has an agent**: the owner presents it, and drives it with an agent.
So the recipient path is `open-evidence-bundle` (the skill), or by hand `--unpack` then `open {project, log,
graphml}`. **No menu item in the first delivery.**

**What would change that:** a demo whose recipient is handed a laptop with no agent and no terminal. Then add
**exactly one** menu item, *Audit log ▸ Open evidence bundle…*, reusing the existing file chooser and calling the
same `--unpack` code path, with the verdict on the status line and **no new dialog**. It is a separate, small
decision, made on evidence. The owner can override this assumption at any time; the spec records it so the next
reader starts from it.

## 4. Format (D-2, D-3)

### 4.1 Capture is one coherent transaction, and the skill runs it

The `capture-evidence-bundle` skill:
1. **Pauses Follow** if it is on (`open {follow: false}`), and restores it at the end. This was a refusal in r1; it is
   now something capture just does (review §7).
2. Reads `context` and **refuses**, naming the reason, if:
   - no log is open;
   - `inFlight` is present (a load is pending);
   - `log.identity.state` is `unverified` or `replacement` (the file is not the one that was read);
   - the log's store is not a plain file.
3. Records **`log.generation`**, assembles the folder, and runs `--pack`.

**r3 corrections, from driving it:**
- **`log.identity` may be absent**: it is published only once an identity check has run. Absence is not a refusal;
  the skill says the bundle's sha256 is then the only statement of the bytes read. The always-present signal is
  **`log.freshness`**: `changed-on-disk` refuses, and "not one plain file" is read from its `members` (exactly one,
  not a directory).
- **The profile FILE lags the session.** Project writes are debounced (800 ms, `ProjectSession`), and a walk saved
  just before capture was missing from the first driven bundle. The analyser now publishes
  **`context.project.unsavedEdits`**, read from the one owner of that fact, and the skill waits for it to clear,
  refusing if it never does (a failed write). That is a second field, beside `log.generation`.
4. Re-reads `context` and **refuses, deleting the bundle, if `log.generation` moved.** It is the same rule the
   analyser already enforces for a walk save, exposed rather than duplicated. **The analyser change is one field.**

### 4.2 Layout — `.fexp`, a zip

```
manifest.json                        the FIRST entry: the member list and facts; its exact bytes are the identity
log/<name>                           the WHOLE log file, byte for byte (v1: no excerpts)
graph/<name>.graphml                 when a graph was open
profile/project.fluxtion-settings    the allow-listed Settings export: GRAPHS, REPORTS (walks), VIEW
```

**r4, bounded verification (review F1).** The manifest is the first entry, and a reader requires it: its declared
sizes then bound every member while it streams. An unlisted member is refused before any of its bytes are read. A
listed member is refused the moment it exceeds its declared size. Each member is digested through a 64 KiB buffer,
so memory does not grow with the log. The one absolute bound is on the manifest itself, 4 MiB (a v1 manifest is
well under a kilobyte). **No member has an absolute cap**: v1 carries the whole log, and a 160 MiB log verifies and
unpacks in a 64 MiB heap. `--unpack` makes two passes: the first verifies and writes nothing; only then does the
second stream each member to disk, digesting it again, and it deletes the working copy if the file changed between
the passes.

- **No flags.** They persist nowhere today (§2). A walk step does the same job, "look at this record, here is my
  note", and unlike a flag it persists, travels in the profile and re-resolves with an identity verdict. Building
  flag persistence is a real feature with the per-file row-index problem attached, so it is not in the first
  delivery (§9).
- **No charts with external series or markers.** Their CSV paths are home-relative and would re-anchor silently on
  another machine (§2). The skill leaves them out of the export and names them. Carrying the CSV as a member is the
  second delivery's.
- **No machine-tier settings, source roots, Maven repos, assistant settings, keys, runbook pointers or workspace
  anchors.** These are also the keys that re-anchor, so the allow-list makes the bundle immune to the failure the
  owner's capture skill recorded ("a missing source root is not an error, so navigation silently stops working").
- **Whole log only, and it has a cost.** Taking the whole log keeps every record digest valid and needs no index
  remapping, which is why it is right for v1. But real logs here run to **64 MB and 142 MB**, so a real incident's
  `.fexp` may be **a 140 MB file**. That is fine for a demo on one machine, and for the demo's DEMO log (well under a
  megabyte). It **breaks a demo that has to send the bundle** through email or a chat attachment. **Excerpts** (a
  time window, with the cut recorded, as the owner's capture skill already does) are therefore explicitly **the second
  delivery's**. The first demo uses the DEMO log.

### 4.3 Manifest (format v1)

```json
{
  "format": 1,
  "createdAt": "2026-09-28T12:00:00Z",
  "analyser": "1.27.0",
  "provenance": "DEMO-quote-service",
  "log": {"member": "log/demo-quote-audit.yaml", "records": 726},
  "graph": {"member": "graph/demo-quote-processor.graphml"},
  "members": [
    {"path": "log/demo-quote-audit.yaml", "sha256": "…", "bytes": 12345}
  ],
  "limits": ["unsigned: verification detects a changed member; it does not authenticate the sender",
             "no replay: this bundle shows an investigation; it does not reproduce or fix it"]
}
```

`--pack` writes it, and `--verify` prints its identity.

**r3, the manifest as shipped:** `format`, `createdAt`, `analyser`, `log.member`, `graph.member`, `members` and
`limits`, in that fixed key order. `provenance` and `log.records` in the example above are **not written**:
`--pack` sees only a folder, and anything it wrote about the log would have to come from the skill unverified. The
walk and report fingerprints in the profile already carry both. `limits` is fixed text, and every surface that shows a
verified bundle shows it.

## 5. Open (the `open-evidence-bundle` skill)

1. `--unpack <bundle>`: verify, then extract into a fresh working copy. The skill stops if the exit code is non-zero.
2. `open {project: <copy>/profile/…}`, then `open {log: <copy>/log/…}`, then `open {graphml: …}`. All three are
   shipped routes.
3. `walk {play: true}` on the bundle's walk. An agent steps with `walk {play: true, step: n}`, the verb's only way to
   step; **r3:** a play of the walk already showing, on the same log, continues that showing, so the M69.F3 caveat is
   not re-stated on every step (found by the driver; `WalkPlayback`, `eb-b4-play-continues-the-showing`).
4. **The view:** the walk's first step restores its own (M69 §3.3). The sender's live filter and selection are not
   carried in v1.

**Still to check (EP-A7):** whether opening a project from the working copy writes to the recipient's **machine**
config, for example a recent-projects list. The recipient's own project settings must be untouched; a recents entry
may be acceptable, but it must be known rather than assumed.

## 6. The walk inside a bundle

Walks travel in the profile's `REPORTS` category (L-32). From a bundle:
- **record targets** match their digests, because the bytes are the same;
- **chart targets:** the same whole log gives the same run basis;
- **graph targets** are current when the graph member is the same file.

The review ran this on the same machine: four steps, every target CURRENT.

**Prerequisite, kept unchanged: M69.F3.** A bundle opens with Follow off, so today every record and chart step shows
the unassessed-log caveat. The review confirmed it fires per step, and correctly not on a step with no record or
chart basis. Show it **once per walk**.

## 7. Acceptance (each needs a regression and a registered control; rule 8)

| id | check |
|---|---|
| EP-A1 | The capture skill refuses each §4.1 condition by name and leaves no bundle; a moved `log.generation` deletes the bundle. |
| EP-A2 | `--pack`: members are byte-identical to the folder's files, and the manifest lists exactly them. |
| EP-A3 | The identity is `sha256` of the manifest's exact bytes; re-serialising the manifest changes it. |
| EP-A4 | `--verify` and `--unpack` refuse a changed, missing, unlisted, escaping (`..`, absolute) or linked member, each naming it; `--unpack` extracts nothing on refusal. **r4:** also a member before the manifest, an oversized manifest and a member larger than declared, all as refusals in bounded memory, never a crash (review F1). |
| EP-A5 | The received file is byte-identical after unpack, open, a walk, and a second unpack. |
| EP-A6 | A cold recipient: unpack and open from **another path and another home**. The project, log, graph, walk and report load. (**ASSUMED by the review; must be RAN.**) |
| EP-A7 | The recipient's own project settings are byte-identical after opening a bundle and closing it; any machine-config write (recents) is known and listed. |
| EP-A8 | The walk plays from the bundle, with its record, chart and graph targets current and lit, numbered as in the strip. |
| EP-A9 | The captured profile holds only GRAPHS, REPORTS and VIEW; **no allow-listed key holds a path**, and a chart with external series is left out and named. |
| EP-A10 | Every surface that shows a verified bundle shows the `limits`, and nothing says the sender is authenticated or the incident is reproduced. |
| EP-A11 | **Changed in r2:** a by-eye check of a captured DEMO bundle's images and rendered report. The rule-1 text sweep is a release gate that already runs on everything, so it is not repeated here (review §7). |
| EP-A12 | **New in r2:** with none of the sender's source roots, the bundle opens usefully. The walk and report work, and source navigation is absent without error. |

## 8. Plan

| slice | what | exit |
|---|---|---|
| **B0 · M69.F3** | the caveat once per walk | its own regression and control |
| **B1 · `log.generation`** | one field in `context.log`, projected from the session snapshot | a `ContextSections` entry; a runtime frame check; a control |
| **B2 · the CLI** | `--pack`, `--verify`, `--unpack`: the format, headless, with pinned fixtures | EP-A2…A5 |
| **B3 · the skills** | `capture-evidence-bundle` and `open-evidence-bundle`, built from the owner's capture and load experiments minus the profile pointer | EP-A1, A8, A9, A12 |
| **B4 · rehearsal** | a cold recipient on a DEMO incident, from another path and another home, timed | EP-A6, A7, A10, A11, measured timings |

**r3, status (evidence: `RESULTS.md`, `tools/evidence-bundle-demo.py`, the named tests and controls):**

| id | status | how it is shown |
|---|---|---|
| EP-A1 | RAN | `test_evidence_bundle_demo.py`: every §4.1 refusal by name, on contexts shaped like real ones; live, the driver's capture on a real session with no log refuses and writes nothing. The moved-generation deletion is code only, not provoked live. |
| EP-A2…A5 | RAN | `EvidenceBundleTest`, `MainBundleTest`; eleven `eb-b2-*` controls; the driver re-hashes the received file after unpack, open, the walk and a second unpack |
| EP-A6 | RAN, same machine | two isolated homes on two paths; the recipient has none of the sender's settings or files. **Not** another machine. |
| EP-A7 | RAN | the recipient's own profile is byte-identical; the machine-tier keys that change are listed: last-opened log and graph, three recents lists, the active project |
| EP-A8 | RAN | all three steps SHOWN, every target CURRENT and lit, at a 1440×900 window. **At the default 1200×800 the chart step is not lit** (§12) |
| EP-A9 | RAN | `BundleProfileTest` on a real sender profile; seven `eb-b3-*` controls |
| EP-A10 | RAN | `MainBundleTest`, `EvidenceBundleSkillsTest`, the driver |
| EP-A11 | RAN, by eye | the recipient's three walk-step screenshots, painted by the app: DEMO data and neutral paths only |
| EP-A12 | RAN | the recipient has no source roots; the walk and report work |

Predictions are committed before code, as usual.

## 9. Not in the first delivery, and why

- **Replay and comparison.** Deferred by D-0 as amended; they are the second delivery's.
- **Signatures** (D-3).
- **Flags.** They persist nowhere. Persisting them per file, with row indices stable across reopen, is its own
  feature. A walk step carries the same meaning.
- **Excerpts.** They are needed to *send* real incidents (64–142 MB logs), so they are the second delivery's.
- **Charts with external series or markers.** Their CSVs would need to travel as members, with paths rewritten.
- **A menu item.** Deferred until a demo's recipient has no agent (§3.4).
- **Readers other than a plain file**, a browser viewer, and a returned bundle B.

## 10. Questions for the next review

1. §3.3: does the CLI owning `--pack` and `--unpack`, not only `--verify`, follow the rule, or is it more than the
   rule needs?
2. EP-A7: what does opening a project write to the machine config, and is a recents entry acceptable?
3. Should the capture skill **refuse** a chart with external series, or leave it out and name it? r2 leaves it out,
   because refusing would make one chart block a whole capture.

## 11. Revision history

| rev | date | by | what |
|---|---|---|---|
| r1 | 2026-09-28 | Claude (analyser session) | First draft, from the combined proposal and the owner's L-33 decisions. The placement question was left open. |
| r4 | 2026-09-28 | Claude (analyser session), fixing the review's REQUIRED findings | **F1:** verification streams in bounded memory; the manifest is the first entry and bounds every member; unpack verifies, then writes, in two passes (§4.2). |
| r3 | 2026-09-28 | Claude (analyser session), after implementing B0–B4 | **Implemented.** A fourth headless flag, `--bundle-profile`, by §3.1's rule (§3.3). A second `context` field, `project.unsavedEdits`, because the profile file lags the session (§4.1). The refusal fields as they really are: `log.identity` may be absent, `log.freshness` is the constant signal (§4.1). The manifest as shipped, without `provenance`/`records` (§4.3). An agent stepping a walk by `play` continues the showing (§5). The skills live in `docs/evidence-bundle/`, not the bundle-seeding `docs/skills/` library (§12). Acceptance status in §7. |
| r2 | 2026-09-28 | Claude (analyser session), after the review | The placement question is decided: **C**, with no verb, menu or dialog. The review's rule is adopted (§3.1). **Argued back:** the CLI owns the whole format, `--pack`, `--verify` and `--unpack`, because the manifest and unpacking are trust-relevant too (§3.3). **The audience is decided:** technical or agent-led, no menu item, with the trigger that would add one (§3.4). **Flags are dropped:** they persist nowhere. **The paths question is restated:** GRAPHS carries home-relative external-series paths, so those charts are excluded and named. The whole-log cost is stated (64–142 MB) and **excerpts move to the second delivery**. Follow is paused and restored rather than refused. Coherence is **one `context` field** (`log.generation`); a pending load is already `inFlight`. EP-A11 is reduced to the by-eye check; **EP-A12** is added (no source roots). The plan is B0–B4. |

## 12. Where the review was wrong, or not yet shown

- **"`context` does not expose a pending load" is wrong.** `context.inFlight` (log section) is present while an
  open is outstanding. The review listed only `context.log`'s own keys. Only the generation is missing.
- **The GRAPHS path question, which the review named but did not check, is answered:** yes, GRAPHS carries
  external-series and marker paths, home-relative (`SettingsShare:182`).
- **EP-A6 (another machine, another home) is ASSUMED by the review**, not shown. r2 keeps it as an acceptance that
  must be RAN in B4.
- **r3, found by driving it (none was predicted):**
  - the profile file lags the session by the save debounce (§4.1);
  - at the default window (1200×800) a chart spotlight reports *"no room at 192×247 px — widen the window"*: a
    cold recipient cannot see a chart step until they enlarge the window. Pre-existing, not a bundle defect, but it
    is the demo's first impression; tracker ▸ EB follow-ups;
  - the empty "Graph 1" chart a log open creates travels in the profile;
  - a hand-written project profile is normalised (and given a nonce) on the analyser's first write of it, which a
    naive before/after check blames on whatever happened in between. EP-A7's baseline is the analyser's own write.
- **The skills are not in `docs/skills/`.** That library is what the playground seeds a generated project with,
  through a published, pinned index, and `CanonicalSkillsTest` requires every skill in it to be indexed. Whether a
  generated project carries these two is that contract's decision (a v3 index), so they live in
  `docs/evidence-bundle/`, with `EvidenceBundleSkillsTest` holding them to what the analyser publishes.
- **Capture hashing in a skill** would be a second implementation of a trust-relevant format; see §3.3. This is a
  disagreement with the review's split, not an error in its facts.
