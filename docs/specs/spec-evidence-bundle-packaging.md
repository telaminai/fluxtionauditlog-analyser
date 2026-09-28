# Evidence bundle packaging — first delivery (package, verify, open, walk; no replay)

**Status: DRAFT r1 (2026-09-28), for review.** It is not yet accepted, and no code exists.
Branch `spec/evidence-bundle-packaging`.

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

A person investigates an incident in the analyser, and leaves flags, a report and a spotlight walk. They hand
**one file** to someone else, who opens it on their own machine and follows the walk. The recipient sees the same
records, the same charts and the same explanation, and can tell that nothing in the file was changed on the way.

**The demo, about 3 minutes, not yet measured:**

| step | target | what may be claimed |
|---|---|---|
| a `.fexp` arrives and is verified | 30 s | every member's bytes match the manifest; provenance and limits are shown; **no claim about who sent it** |
| open it | 30 s | it opens into a disposable working copy; the received file is never modified |
| follow the walk | 90 s | the walk's record, chart and topology steps show the incident, numbered, with the author's captions |
| flags and the report | 30 s | they are there, as the sender left them |

## 2. What already exists (inventory)

The first delivery is mostly **assembly** of shipped parts. Each row needs confirming at the implementation head,
in the EB-0 probe (§8).

| need | exists today | gap |
|---|---|---|
| walks, reports, saved charts and focuses as portable definitions | `SettingsShare` categories `GRAPHS` ("Graphs and named focuses") and `REPORTS` ("Investigation reports and spotlight walks (definitions + commentary — never log data)"), with export, preview and apply; walks since 1.26.0 | none for definitions |
| the view | `SettingsShare.VIEW` (hidden columns), `context.filter` / `showing` / `selection` | the filter and selection are not a share category. Decide whether the bundle restores them, or the walk's first step does (§5) |
| flags | `flag` verb; `context.flags` | **where flags persist is not yet established.** EB-0 must find out whether they survive a reopen, and in what file |
| the log | the opened file; a read identity with per-file `sha256` (`SessionResumeStore.Identity`) | a coherent capture (§4.1) |
| the graph | the opened graphml, its digest (`TopologyPanel.loadedGraphSha256`) | none |
| profile paths that open anywhere | M38.6 path anchors (project-relative) | confirm there are no absolute paths in a captured profile |
| open a project, log and graph | `open {project}`, `open {log}`, `open {graphml}` | an `open {bundle}` entry, or a skill that composes the three (§3) |
| agent-driven steps | skills `load-audit-log`, `guided-start`, `point-at-the-fault`, `replay-a-run`; runbooks (M38.1) | a capture skill, and an open skill if §3 chooses it |
| files written by an agent | the exchange directory (Settings ▸ Assistant), used by `report` and `screenshot` | a bundle is a new kind of written artefact |

## 3. The placement question — how much belongs in the UI, and the sweet spot

**This is the question the next review should answer.** It is recorded here, not decided. There are three shapes:

| shape | capture | verify | open |
|---|---|---|---|
| **A. UI-native** | menu *Audit log ▸ Export evidence bundle…* | on open, in the app | menu *Open evidence bundle…* |
| **B. One core, two thin entrances** | the core, in the analyser, reached by a `bundle` verb operation (or `open {bundle}` / `report`-style options) **and** a thin menu item | the core, the same code for both | the core; the menu and the verb both call it |
| **C. Skill- and runbook-driven** | a skill assembles the bundle from existing verbs (`context`, a Settings export, the file) plus the filesystem | a skill, or a small CLI (`sha256` over the members) | a skill that extracts it, then calls `open {project, log, graphml}` |

**What the choice turns on:**
- **Trust in verification.** Verification is the one thing a recipient must be able to rely on, so it should be
  **one implementation**, with fixtures pinned across readers (D-2). C spreads it into skill prose, unless the
  hashing lives in a CLI the skill calls.
- **What the analyser alone can know.** Only the analyser knows its coherent state: which log generation, whether
  Follow is paused, whether a load is pending, the read identity, and what a flag's `recordIndex` refers to. A skill
  can ask for these through `context`, but it cannot make the capture atomic. The proposal's A-3 / L-16 work says
  coherence is the hard part.
- **Demo friction.** For a person with no agent, A or B is one click. For an agent-led demo, C or B is one sentence.
  B gives both, at the cost of a verb.
- **Surface growth.** A new verb is a compatibility decision (`CloseVerbTest`'s rule). C adds none. B can add none,
  if `bundle` is an operation on an existing verb.
- **The UI is the risk the owner asked about.** A (and B's menu) add dialogs: a file chooser, a verification result,
  an "opened a copy" notice. Every one is a new surface with its own states, which is exactly how the 1.26.0 demo
  bugs arrived. C keeps the UI to what already exists.

**Draft recommendation, to be contested:** **B-minimal.**
- A **core** in the analyser: capture, verify and open, as pure code with a narrow file boundary. It is tested
  headless, and verification is one implementation.
- **Entrances:** `open {bundle: <path>}` for the recipient, and a capture operation reachable by the agent through
  the existing exchange directory rules. There is **no new verb** if `open` and `report` can carry it.
- **UI:** at most **one** menu item each way, reusing the existing file chooser and status line, and **no new
  dialog**. The verification verdict goes to the status line and `context`.
- **Skills:** a `capture-evidence-bundle` skill and an `open-evidence-bundle` skill guide an agent-led flow. They
  call the core and never re-implement hashing.

If the reviewer finds the skill experiments already do capture and load well, the sweet spot may be **C, plus
verification as a CLI**, with the UI untouched. That would be the smallest delivery, and it is a legitimate answer.

## 4. Format (D-2, D-3)

### 4.1 Capture is one coherent transaction

Capture is refused, naming the reason, unless:
- a log is open, and **Follow is paused**;
- no load is pending;
- the read identity is **not** `UNVERIFIED` or `REPLACEMENT` (the file must be the one that was read);
- the log's store is one whose bytes the analyser can copy (a file on disk). Other readers are refused in v1.

All members are read in one EDT turn, from the same log generation, and the generation is re-checked at the end. If
it moved, the capture is refused and nothing is written (the M69 walk-save rule, reused).

### 4.2 Layout — `.fexp`, a zip

```
manifest.json                   the member list and facts; its exact bytes are the identity
log/<name>                      the WHOLE log file, byte for byte (v1: no excerpts)
graph/<name>.graphml            when a graph was open
profile/project.fluxtion-settings   the allow-listed profile: GRAPHS, REPORTS (walks), VIEW, and flags if EB-0 shows where they live
```

- **Whole log only in v1.** That avoids remapping a flag's `recordIndex`, and it keeps a walk's record digests valid
  (same bytes, so *current*).
- **No machine-tier settings, no source roots, no Maven repos, no assistant settings, no keys.** The allow-list is the
  categories above, and nothing else.

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

- **Identity:** `sha256:` of `manifest.json`'s exact bytes, stated beside the file (in the chooser, the status line
  and `context`), never inside it.
- **`limits` is mandatory**, and every surface that shows a verified bundle shows it.

## 5. Verify and open

1. **Verify.** Read the manifest and check every listed member's `sha256` and `bytes`. Refuse any unlisted member,
   any listed member that is missing, and any path that escapes the archive (`..`, absolute paths, links). A refusal
   names the member. There is no partial open.
2. **Open into a working copy.** Extract to a **fresh** directory under the analyser's own data directory, never
   next to the received file. The received file is never modified. The working copy is disposable: a second open of
   the same bundle makes another copy.
3. **Load.** Open the extracted profile as the project, then the log, then the graph, through the existing
   `open {project}` / `open {log}` / `open {graphml}` routes. The recipient's own settings are untouched (the M35.7
   project-as-session-boundary rule).
4. **State it.** The status line and `context` say:
   - it is an evidence bundle, with its identity;
   - it was verified, and what that does and does not mean;
   - it is a working copy.
5. **The view.** The walk's first step restores its own view (M69 §3.3). The bundle does **not** separately restore
   the sender's live filter or selection in v1, because the walk is how an author states a view.

## 6. The walk inside a bundle

Walks travel in the profile's `REPORTS` category (L-32). Opened from a bundle:
- **record targets** match their digests, because the bytes are the same, so they are *current*;
- **chart targets:** the run basis is the opening file digests plus the record count, so the same whole log gives
  the same basis and they are *current*;
- **graph targets** are *current* when the graph member is the same file.

**Prerequisite: M69.F3.** A bundle opens with Follow off, so today every record and chart step would repeat the
unassessed-log caveat. Show it **once per walk** before the first demo.

## 7. Acceptance (each needs a regression and a registered control; rule 8)

| id | check |
|---|---|
| EP-A1 | Capture refuses each §4.1 precondition by name, and writes nothing. |
| EP-A2 | A captured bundle's members are byte-identical to the sources, and the manifest lists exactly them. |
| EP-A3 | The identity is `sha256` of the manifest's exact bytes. Re-serialising the manifest changes the identity. |
| EP-A4 | Verify refuses a changed member, a missing member, an unlisted member, and a path escape, each naming the member. |
| EP-A5 | Open never modifies the received file. The file's bytes are unchanged after open, a walk, and a second open. |
| EP-A6 | Open from another path **and another home** (a cold recipient) loads the project, log and graph. |
| EP-A7 | The recipient's own settings files are byte-identical after opening a bundle and closing it again. |
| EP-A8 | The walk plays from the bundle, with its record, chart and graph targets *current* and lit, numbered as in the strip. |
| EP-A9 | The captured profile contains only the allow-listed categories: no source roots, repos, assistant settings or keys. |
| EP-A10 | Every surface that shows a verified bundle shows the `limits`, and nothing says the sender is authenticated or the incident is reproduced. |
| EP-A11 | The rule-1 sweep and a by-eye check of a captured DEMO bundle show no non-DEMO data. |

## 8. Plan

| slice | what | exit |
|---|---|---|
| **EB-0p** | Probe, no product code. Where do flags persist? Are there any absolute paths in a captured profile? Can capture and verify in the skill experiments be reused? | a short findings note; §2's gaps closed or named |
| **M69.F3** | Show the caveat once per walk | its own regression and control |
| **EB-1** | The core: capture, manifest, verify, working copy | EP-A1…A5, A9, headless |
| **EB-2** | The entrances chosen in §3: `open {bundle}`, capture, and at most one menu item each way | EP-A6…A8, A10, on a real frame |
| **EB-4-lite** | A cold recipient rehearsal on a DEMO incident, timed | EP-A11, measured timings |

Predictions are committed before any code, as usual.

## 9. Not in the first delivery, and why

- **Replay and comparison** (EB-3). Deferred by D-0 as amended; they are the second delivery's.
- **Signatures** (D-3). Later, with a separately reviewed envelope and trust rule.
- **Excerpts**, meaning a partial log. Whole log only in v1, which keeps flags and walk digests valid.
- **Readers other than a plain file**, such as plugin stores and rolled sets. They are refused with a reason.
- **A browser viewer** (EB-X).
- **A returned bundle B** with a parent link. That belongs with replay.

## 10. Questions for the next review

1. **The sweet spot (§3).** A, B-minimal or C? Is anything in B-minimal's UI still too much?
2. Should capture be an **agent-only** operation first (a skill plus the core), with the menu item following only if
   the demo needs it?
3. Can the existing skill experiments for capture and load be adopted as the skills of §3, and do they already
   handle §4.1's coherence?
4. Is "the walk states the view" (§5.5) enough, or does the recipient need the sender's live filter restored too?

## 11. Revision history

| rev | date | by | what |
|---|---|---|---|
| r1 | 2026-09-28 | Claude (analyser session) | First draft, from the combined proposal and the owner's L-33 decisions. The placement question (§3) is left open for review. |
