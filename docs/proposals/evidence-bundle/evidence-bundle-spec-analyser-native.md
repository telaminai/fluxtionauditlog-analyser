# Evidence bundles — draft B: analyser-native, and the v0.1 we can ship in two days

**Status: DRAFT r2 (2026-09-27), for argument, not final.** Several reviewers, human and AI, will contest this file.
It sits beside **draft A** ([`evidence-bundle-spec.md`](evidence-bundle-spec.md)), and the two disagree on purpose. **A**
builds v0.1 as an external tool over the analyser's socket; **B** (this file) builds it inside the analyser.
[§14](#14-draft-a-and-draft-b-where-they-agree-where-they-differ-and-what-i-recommend) sets them side by side and says
which I would build first. Both merge the two source documents beside them:
- [`evidence-bundle-positioning.md`](evidence-bundle-positioning.md): the product model — why, who signs, packaging.
- [`evidence-bundles.md`](evidence-bundles.md): what a working prototype taught — the gaps, the static page, the walk.

Those two stay as source material. Where this file disagrees with them, it says so and why.

**How to argue with this file:**
- Cite IDs: **D-** a decision, **F-** a verified fact, **A-** an acceptance check, **Q-** an open question, **P-** a
  prediction.
- Every fact is labelled with how it is known:
  - **[read]** read in the code by this file's author, with `file:line`;
  - **[mapped]** reported with `file:line` by a code-mapping pass and **not re-read** — check it before building on it;
  - **[assumed]** believed, and not yet checked.
- A decision lists its alternatives and what would change it. Dispute the reasoning, not only the conclusion.
- Amend in place and add a line to the [revision log](#revision-log). Don't fork a copy.

---

## 1. The claim

> **An investigation should end with evidence someone else can open, check and argue with, not a description of
> evidence.**

A bundle establishes something narrow and defensible (positioning ▸ *Core idea*):

> **These exact inputs, artefacts, configuration and starting conditions produced these exact recorded results.**

**It does not establish that the software is correct.** That is a separate assertion, supported by tests, invariants,
review and other assurance. The rule, used throughout this file: **sign facts of execution, not conclusions.**

## 2. Terms

| term | meaning |
|---|---|
| **bundle** | One file holding one investigation's evidence and testimony. |
| **member** | A file inside the bundle, declared in the manifest with its role and SHA-256. |
| **evidence** | A member trusted because it is checkable: the excerpt, the topology, the recorded inputs, the captured pairing verdict. |
| **testimony** | A member trusted only because the author said so: the hypothesis, the outcome, the caveats, the walk. Always labelled as such. |
| **excerpt** | The records selected from a source log, with a machine-readable record of how they were selected and what was dropped. |
| **capture** | Writing a bundle from the current analyser state. |
| **open** | Verifying a bundle, extracting a **working copy**, and opening it as a project. |
| **verify** | Recomputing every member's hash against the manifest, and reporting what matches, what differs, and what is not asserted. |
| **working copy** | The extracted directory. It may change (the analyser auto-saves a project). **The bundle file itself never changes.** |

## 3. What v0.1 ships, and what it does not

The v0.1 goal: **a bundle can leave the machine that made it, and the receiver can tell whether it arrived intact.**
That is the first 30 seconds of every workflow in both source documents. Nothing later works without it.

| | v0.1 (two days) | why |
|---|---|---|
| **In** | capture from the menu: excerpt, topology, profile, reports, testimony, optional recorded inputs | the prototype's two skills, made a product action |
| **In** | a versioned manifest with SHA-256 per member and a bundle id | makes a received bundle checkable (proposal §5.3) |
| **In** | `analyser verify <bundle>`, headless | the positioning doc's `experiment verify`; runs in CI and on a machine with no display |
| **In** | open from the menu: verify, extract a working copy, open it as a project, one saved analysis that restores the view | fixes the prototype's blocker (proposal §5.1) with no new session orchestration (D-5) |
| **In** | evidence and testimony as separate member roles, and separate in `verify`'s output | proposal Part 7, positioning ▸ *Facts vs interpretation* |
| **In** | `respondsTo`: a response bundle names the bundle it answers | the first link of an experiment chain (positioning ▸ *Experiment chains*); one field |
| **Out** | signatures | who signs, and how a key is trusted, is the hard part; a signature over an unsettled identity model is theatre (D-3) |
| **Out** | saved walks with step-through | the highest-value next item (§9); v0.1's saved analysis is a crude single-step walk |
| **Out** | executing replay, and comparing two bundles | v0.1 carries recorded inputs as a declared member; replay runs outside the analyser, as it does today (positioning ▸ *What a bundle contains today*) |
| **Out** | the static HTML page | depends on PR #53's drawing layer, and must meet the untrusted-content rules in §7 |
| **Out** | redaction, run receipts, a registry, read-only mode | §9 |

## 4. The v0.1 format

### Container

A ZIP archive with a distinct extension (**Q-1**: the name). Members are stored flat or in the directories below. No
nested archives, no symbolic links. It stays inspectable: unzip it and read it.

```
<bundle>
  manifest.json                         REQUIRED — §4.2
  TESTIMONY.md                          testimony: hypothesis · method · outcome · caveats
  log/excerpt.yaml                      evidence: the excerpt, in audit-log format
  topology/<name>.graphml               evidence: the topology the excerpt pairs with
  inputs/<name>                         evidence, optional: the recorded input stream, declared, not executed
  reports/<name>.pdf                    testimony: the author's argument, rendered at capture (D-8)
  .analyser/project.bundle.fluxtion-settings
                                        the profile: charts, focuses, reports, one environment, one analysis
```

The profile sits at `.analyser/project.*.fluxtion-settings` on purpose. That exact location makes the analyser resolve
the profile's relative paths against the **bundle root** (**F-1**). That is the whole fix for the prototype's blocker
(proposal §5.1): the profile no longer re-anchors to a directory that does not exist.

### The manifest (the only part specified formally, per proposal Part 8)

```json
{
  "format": "fluxtion-evidence-bundle",
  "formatVersion": "0.1",
  "bundleId": "sha256:…",
  "capturedAt": "2026-09-28T10:14:03Z",
  "capturedBy": { "tool": "fluxtion-auditlog-analyser", "version": "1.26.0" },
  "title": "…",
  "respondsTo": null,
  "members": [
    { "path": "log/excerpt.yaml", "role": "evidence", "kind": "excerpt", "sha256": "…", "bytes": 148213 },
    { "path": "TESTIMONY.md", "role": "testimony", "kind": "narrative", "sha256": "…", "bytes": 2210 }
  ],
  "excerpt": {
    "source": { "name": "quote-service-audit.yaml", "sha256": "…", "records": 9210 },
    "included": 34,
    "filter": { "…": "the FilterSnapshot in force at capture" },
    "window": { "firstLogTime": 1770000053000, "lastLogTime": 1770000299000 },
    "completeness": "an excerpt; it makes no claim that the source was whole"
  },
  "pairing": { "…": "context.graphPairing as captured" },
  "inputs": { "carried": false, "boundary": null },
  "source": { "carried": false, "note": "source roots are stripped at capture" },
  "signature": null,
  "notAsserted": ["semantic correctness", "that the source log was complete", "the author's interpretation"]
}
```

**Rules:**
- **R-1.** Every member except `manifest.json` is listed exactly once, with SHA-256 over its exact bytes.
- **R-2.** An undeclared member is a verify failure, not something to ignore.
- **R-3.** `bundleId` is SHA-256 over the manifest serialised canonically (sorted keys, no insignificant whitespace,
  UTF-8), with `bundleId` itself removed. Changing any member changes its hash, which changes the manifest, which
  changes the id.
- **R-4.** Unknown keys are preserved on rewrite, as the analyser already does for profiles. An older reader lists what
  it cannot interpret rather than refusing the bundle.
- **R-5.** `formatVersion` changes on any change to R-1…R-4. A reader refuses a major version it does not know, and
  says which version it found.

## 5. Behaviour

### 5.1 Capture — *Project ▸ Capture evidence bundle…*

1. **Precondition:** a log is open, with no load pending. That second part avoids M44.6's overlapping loads.
2. **The excerpt** is the records the current filter shows. They are written through the existing YAML export
   (**F-4**), which refuses logs that cannot be written as YAML (a binary `.flxa`) and says why. The source log's
   SHA-256 comes from the load (**F-5**).
3. **The topology** is the loaded `.graphml`, copied byte for byte, if one is loaded from a file. A graph supplied from
   source is declared as not carried. Its SHA-256 comes from **F-6**.
4. **The pairing verdict** is copied from `context.graphPairing` as it stands at capture (**F-7**).
5. **The profile** is the current project-scoped settings, rewritten for the bundle:
   - source roots and Maven repositories are stripped (D-10);
   - one environment is added, `logDir=log`, with provenance "evidence bundle — captured excerpt", so the excerpt
     labels itself when opened (**F-9**);
   - one analysis is added, "Open this bundle's evidence", whose steps open `log/excerpt.yaml` and
     `topology/<name>.graphml` (**F-2**).
6. **Reports** are rendered to PDF with the existing report path.
7. **Testimony:** the person writes it in the capture dialog: hypothesis, outcome, caveats. It is saved to
   `TESTIMONY.md`. Empty sections are allowed, and are stated as empty.
8. **Recorded inputs** (optional): the person picks a file and states its **boundary** in words, for example "the raw
   venue feed, before the vendor's mapper". It is carried and hashed, never interpreted (D-11).
9. **The write is a transaction.** The bundle is built under a temporary name and moved into place only when it is
   complete. A partial capture never looks complete, which fixes proposal Part 4's "checklist, not a transaction".

### 5.2 Verify — `analyser verify <bundle>`

- It runs headless. `Main` dispatches it before any Swing starts, the way `--mcp` is dispatched today (**F-10**).
- Its output follows the positioning doc's table, split by role:

  ```
  Bundle        demo-incident · sha256:3f1c…            format 0.1
  EVIDENCE      log/excerpt.yaml                        VERIFIED
                topology/QuotePricer.graphml            VERIFIED
                inputs/venue-feed.yaml                  VERIFIED
  TESTIMONY     TESTIMONY.md                            VERIFIED   (the author's words — not checked for truth)
  Signature                                             NOT PRESENT (integrity only: detects accidental change and
                                                                     inconsistent edits, not deliberate tampering)
  Semantic correctness                                  NOT ASSERTED
  ```
- **Exit codes:** 0 when every member is verified; 1 when a member differs, is missing, or is undeclared, naming each
  one; 2 when the file is unreadable, or is not a bundle; 3 when the format version is unsupported.
- **"NOT ASSERTED" is always printed.** A verifier that goes quiet about what it does not check invites the reading
  that it checked it.

### 5.3 Open — *Project ▸ Open evidence bundle…*

1. **Verify first** (5.2). A failing bundle is not opened silently. The person sees the failures, and may still open it
   for inspection, with the failures stated in the Project panel.
2. **Extract a working copy** under the analyser's configuration directory, at `bundles/<bundleId>/`, reusing the
   template archive's hardened extraction (**F-11**). The same bundle opened twice reuses its verified copy.
3. **Open the working copy as a project**, with the existing project open. The environment labels the excerpt's
   provenance.
4. **The person, or the agent, runs "Open this bundle's evidence"** from *Project ▸ Run analysis*. It opens the
   excerpt and the topology. v0.1 deliberately adds no automatic second step (D-5).

A bundle's analysis cannot reach outside the working copy: its paths are refused if absolute, `~`-relative, or
containing `..` (**F-2**).

## 6. Decisions

**D-1 — the name.** User-facing: **evidence bundle**. The file extension is open (Q-1).
- *Alternatives:* "experiment" (the positioning doc's `.fexp`); "exhibit" (proposal Part 7).
- *Would change if:* a naming review picks one term for product and file alike.

**D-2 — ZIP, flat and inspectable.** Not a custom container, and not encrypted in v0.1.
- *Would change if:* transport encryption becomes a v0.1 requirement. Then it would wrap the file, not the format.

**D-3 — unsigned in v0.1, and says so.** Hashes and a canonical manifest now. `"signature": null`, and `verify` states
what that means.
- *Why not sign now:* Ed25519 is in the JDK, so signing is cheap. The *identity* is not: who signs, how a receiver
  trusts that key, and what the signature asserts (positioning ▸ *Who signs?*). A signature with no trust model reads
  as more than it is, which is the failure this product exists to avoid.
- *Would change if:* an owner decision fixes v0.1's signer (for example "the capturing machine's key, trust on first
  use"). The format already reserves the field.
- *r2, on draft A's optional Ed25519:* my objection is weaker than r1 made it. Draft A labels the signer
  **`declared: true`** and prints `(declared)` beside the name. That states the gap honestly. I would accept an optional
  signature in v0.1 on one condition: `verify` never prints a signer's name without saying the key is not vouched for.

**D-4 — the excerpt is the filtered view.** Content-based cutting (proposal Part 3: "excerpt by content, not by time")
is done by the person, with the filter, before capture. The manifest records the filter, so the cut is a property of
the artefact rather than a sentence in prose (proposal §5.7).
- *Would change if:* the filter cannot express the cuts investigators actually make. The prototype cut on "records
  mentioning a node" — **Q-3**.

**D-5 — open reuses existing verbs. No new session orchestration in v0.1.** Opening a project is an existing session
transition, and so is running an analysis. The menu action does only the first.
- *Why:* CLAUDE.md rule 9. A menu action that opens a project *and then* runs its analysis is a hand-placed sequence of
  two session transitions, which the owner has ruled out, however small.
- *Cost:* one extra click, or one extra agent call.
- *Would change if:* a `BundleOpening` session node is built (v0.2). It makes "open bundle" one transition the
  processor sequences.

**D-6 — capture is a menu action in v0.1, not a verb.** A `bundle` verb would be the seventeenth, and would touch the
manifest, schemas, prompt, MCP contract tests and assistant docs.
- *Would change if:* the demo requires the assistant to capture. Then `bundle {capture, verify}` is the day-2 stretch.
  Reviewers: argue this one hardest — it decides whether an agent can close the loop unaided.

**D-7 — `verify` is a headless command.** It follows `ScoreCommand`'s pattern (exit codes, no display) and is
dispatched in `Main`.

**D-8 — evidence and testimony are member roles, and a report is testimony.** Every member declares one.
- *r2, conceded to draft A:* r1 labelled a report PDF as evidence, because its tables and charts come from the excerpt.
  Draft A's rule is cleaner: **nothing the author wrote in prose appears under evidence.** Choosing which tables and
  charts make the argument is itself the author's act. A report is the author's argument, even when it cites evidence.
- *Would change if:* a report is split into generated members (evidence) and a narrative member (testimony). That is
  a v0.2 option, not needed now.

**D-9 — the bundle file is immutable; the working copy is not.**
- *Why:* the analyser auto-saves project edits. Rather than block edits in v0.1, `verify` of the working copy
  distinguishes "the bundle is intact" from "the working copy changed since it was opened".
- Read-only mode (proposal §5.10) is v0.2.

**D-10 — source does not travel.** Roots are stripped and declared as not carried (proposal §5.4). Source coordinates
(remote plus revision) are v0.2.

**D-11 — recorded inputs are carried, not executed.** This keeps v0.1 free of replay semantics, which must be read from
Fluxtion's replay guide before they are built on (CLAUDE.md rule 6). `boundary` is free text in v0.1 (Q-6).

**D-12 — `respondsTo`.** A response bundle names the `bundleId` it answers. That starts the chain of custody
(positioning ▸ *Experiment chains*) for the price of one field.

## 7. Security and privacy rules

- **S-1. A bundle is untrusted input.** Extraction reuses the template archive's guards (**F-11**):
  - refuse entries that escape the root, duplicate paths, symbolic links and absolute paths;
  - cap entry count, entry size and expanded size;
  - stage, then move atomically.
- **S-2. Nothing in a bundle is executed.** Recorded inputs are bytes. Analysis steps are limited to project-relative
  paths (**F-2**).
- **S-3. No redaction in v0.1, and capture says so.** The capture dialog states that the excerpt carries whatever the
  records contain, and the manifest records `"redaction": "none"`. Declared redaction is v0.2 (proposal §5.8,
  positioning ▸ *Security and privacy*).
- **S-4. Requirement for the static page, before it is built.** A page that inlines records from an untrusted bundle
  must:
  - escape `</` in inlined data;
  - render records as text, never as markup;
  - carry a restrictive CSP (Content Security Policy) that forbids remote loads.

  #53's review found control characters breaking SVG export, which is this class.
- **S-5. Public-repo rule.** Fixtures and examples use the demo fixture and `DEMO`/`com.acme` names only (CLAUDE.md
  rule 1).

## 8. Facts this spec rests on

| id | fact | how known |
|---|---|---|
| F-1 | A profile at `<root>/.analyser/project*.fluxtion-settings` resolves relative paths against `<root>`; any other settings file resolves against its own directory. The name test is `startsWith("project.") && endsWith(".fluxtion-settings")`, so `project.fluxtion-settings` qualifies. | [read] `config/ProjectProfile.java` `isProjectProfileFileName`, `baseDirFor` |
| F-2 | Saved-analysis steps are analyser verbs. `open`'s `log`/`logs`/`graphml` resolve against the project root at run time, and every path is refused unless project-relative, with no `..`. | [read] `config/AnalysisSpec.java` `PATH_PARAMS`, `PROJECT_RELATIVE_ACTIONS`, `resolvePaths`; `Runbooks.refusePointer` |
| F-3 | A project does not remember its log or graph. `graphmlFile` is deliberately global session state (open question O3, deferred), and no project-scoped category holds a log or graph path. One `open` call cannot carry a project together with a log. | [read] `ProjectProfile.java` class javadoc and `PROJECT_SCOPED`; [mapped] `ui/ActionExecutor.java:1168-1182` |
| F-4 | *Records ▸ Export records (YAML)* writes the filtered rows as `---` plus each record's raw text, and refuses logs that cannot be written as YAML. No verb writes records to a file. | [mapped] `export/RecordExporter.java:60-82`; `MainFrame.java:1822-1848` |
| F-5 | A loaded log has a SHA-256 over the exact bytes read, or null plus a reason if the file changed during the read. | [mapped] `parse/FileReadIdentity.java:10, 63-71` |
| F-6 | A loaded graph has a SHA-256 when the read was stable; its path is known when it came from a file. | [mapped] `ui/TopologyPanel.java:737-848` |
| F-7 | The pairing verdict is data: `context.graphPairing`, from `SessionSnapshot.publishedPairing()`. | [mapped] `MainFrame.java:6853-6898`; `session/SessionSnapshot.java:68` |
| F-8 | Flags are held in memory and in session recovery only, not in the profile. A report's FINDING section resolves against a live flag. The applied focus is not in the profile either. | [mapped] `MainFrame.java:131, 7266`; `report/ReportResolver.java:136-147`; `ui/TopologyPanel.java:1371-1386` |
| F-9 | A project environment whose `logDir` contains the log supplies the log's provenance automatically; it shows in the status bar, `context`, the Project panel and report headers. | [mapped] `config/Environment.java:69-88`; `MainFrame.java:4089-4099` |
| F-10 | `Main` dispatches `--mcp` headless before Swing; `score/ScoreCommand` is a headless command with exit codes 0–3. | [mapped] `Main.java:25-58`; `score/ScoreCommand.java:69` |
| F-11 | `template/TemplateArchive` extracts ZIPs with path-escape, duplicate, count and size guards, staging and an atomic move. Nothing in main code writes a ZIP. | [mapped] `template/TemplateArchive.java:24-25, 114-157` |
| F-12 | Spotlight targets include `records:row:<recordIndex>`, `detail:node:<instanceId>`, `graph[:<name>]:note:<n>` and `graph[:<name>]:series:<label>`; a named chart is reached as `graph:<name>…`. Draft A's walk example uses only valid forms. | [read] `ui/SpotlightTarget.java` `Family`; `llm/SpotlightVocabulary.java` |
| F-13 | A direct `open {log}` or `open {graphml}` with a RELATIVE path resolves against the analyser's working directory, not the project. Pass absolute paths. | [mapped] `MainFrame.java:6113-6141, 6378-6384` |

**F-8 is the gap that bites hardest.** Without flags, a bundled report's finding sections open unresolved ("record N has
no flag"). v0.1 handles this in two ways:
- the PDF carries the rendered findings, so nothing is lost as evidence;
- **P-2** tests whether `flag` steps in the bundle's analysis can re-apply them. If they cannot, restoring flags is the
  first v0.2 item.

## 9. After v0.1, in dependency order

| | item | source | notes |
|---|---|---|---|
| 1 | **Saved walks + step-through** | proposal §5.11 | Replaces the author being in the room. The view-model spike (PR #55) is the substrate: a walk step is a set of view states plus spotlights. |
| 2 | **Flags and applied focus carried and restored** | F-8, proposal §5.9 | Needed by walks anyway. |
| 3 | **Signing and identity** | positioning ▸ *Signing model*, *Who signs?* | Ed25519, once D-3's trust model is decided. |
| 4 | **Replay executed, and two bundles compared** | proposal §5.5/§5.6, positioning ▸ *Investigation → fix → response*, **M12.2/M12.3** | M12.2's test fixture is what turns a replay into a *test*: a green replay is not a test until something asserts on it (proposal ▸ Non-goals). Needs the replay guide read first (rule 6), and a recording made at the right boundary. |
| 5 | **A `bundle` verb** | D-6 | If not pulled into v0.1. |
| 6 | **Read-only open** | proposal §5.10 | Replaces D-9's detect-after-the-fact. |
| 7 | **The static page** | proposal §5.12 | After #53, and under S-4. |
| 8 | **Redaction, run receipts, source coordinates** | proposal §5.4/§5.8, positioning ▸ *Run receipt* | A run receipt is producer-side: an upstream ask to the runtime and Mongoose. |
| 9 | **A registry** | positioning ▸ *Possible assurance registry* | Hashes may cross; content may not. |

**Relationship to M12 (diagnose → fix → prove).** Bundles are M12's delivery vehicle, not a competing milestone:
- M12.1's replay reference is item 4's recorded inputs;
- M12.2's test fixture is item 4's assertion;
- M12.3's diff is item 4's comparison;
- M12.4's evidence-linked PR is the response bundle.

## 10. Acceptance for v0.1

Each check is executable. Headless unless marked.

| id | check |
|---|---|
| A-1 | Capture on the demo fixture writes a bundle whose manifest lists every member exactly once, with SHA-256 matching its bytes; `bundleId` recomputes. |
| A-2 | `verify` on an untouched bundle exits 0, prints every member VERIFIED, prints `Semantic correctness NOT ASSERTED`, and prints `Signature NOT PRESENT` with its meaning. |
| A-3 | One byte flipped in any member exits 1 and names that member. A removed member does the same, and so does an added undeclared member. Each has a mutation control. |
| A-4 | Hostile archives are refused **before anything is written**: an escaping path, a duplicate entry, an absolute path, a symbolic link, too many entries, an oversized entry. |
| A-5 | *(display)* Open from cold under an isolated `user.home`, following the capture-docs rule: the project opens from the working copy, and running its analysis loads the excerpt and the topology. The record count equals `excerpt.included`; the pairing verdict equals the captured one; provenance reads as a captured excerpt. |
| A-6 | A bundle whose analysis names `../x` or an absolute path is refused, and says which step. |
| A-7 | An excerpt containing control characters and `</script>` survives capture → verify → open byte for byte. |
| A-8 | After open and chart edits, the bundle **file**'s bytes are unchanged. `verify` of the working copy reports the profile as changed since open, and the evidence as intact. |
| A-9 | A capture that fails part-way (for example, the disk fills) leaves no bundle behind under the target name. |
| A-10 | A response bundle's `respondsTo` equals the answered bundle's `bundleId`, and `verify` prints the link. |
| A-11 | The CLAUDE.md rule-1 sweep passes. Any image is checked by eye. There is a user-guide page, and a CHANGELOG line. |

## 11. Predictions — to commit before code

- **P-1.** The excerpt written by the YAML export reloads with the same record count and the same pairing verdict.
  *Risk:* a log header or stream-end marker is not carried, so completeness reads `unknown` — which is correct for an
  excerpt.
- **P-2.** A saved analysis may contain `flag` steps, and `filter`/`graph`/`topology` steps, so the bundle's analysis
  can restore findings and the applied focus. *If false:* F-8 becomes v0.1's documented limitation.
- **P-3.** The demo fixture's bundle is under 200 KB without images.
- **P-4.** `verify` on it takes under one second.
- **P-5.** An environment with `logDir=log` labels the excerpt's provenance with no other change (F-9 in practice).

## 12. The two days

**Day 1: the format and verify.** All pure logic, headless.
- `BundleManifest`: canonical serialisation, R-1…R-5.
- `BundleWriter`: the transaction, members, hashes.
- `BundleVerifier`.
- `analyser verify`, dispatched in `Main`.
- A-1–A-4 and A-7, with their mutation controls.

**Day 2: capture and open.**
- The capture dialog (testimony fields, optional inputs and their boundary) and the profile rewrite (5.1 step 5).
- Open: verify, extract, open the project.
- A-5, A-6 and A-8–A-10.
- The user-guide page and CHANGELOG line, and the predictions scored in a RESULTS file.
- **Stretch:** D-6's verb.

**What would make two days wrong:** P-1 failing (a new excerpt writer), or F-1 not holding as mapped (the anchor
itself). Both are checked first thing on day 1.

## 13. Open questions

- **Q-1.** The extension and the one noun: evidence bundle, experiment (`.fexp`), or exhibit.
- **Q-2.** Should v0.1 capture be available to the assistant (D-6)?
- **Q-3.** Can the filter express the content cuts investigators make ("records mentioning node X")? If not, what is
  the smallest addition?
- **Q-4.** Who signs v0.2 bundles, and how does a receiver trust the key (D-3)?
- **Q-5.** Is a report PDF one member, or evidence plus testimony (D-8)?
- **Q-6.** Does `inputs.boundary` need a vocabulary ("raw feed", "post-mapper", "processor input"), or is free text
  enough until replay exists?
- **Q-7.** M44.6: v0.1 requires no pending load at capture and at open. Is that enough until M44.6 is decided?

## 14. Draft A and draft B: where they agree, where they differ, and what I recommend

**They agree on:**
- the format core: a ZIP with a hashed, canonical `manifest.json`, an id derived from it, `respondsTo`, unknown keys
  preserved, and "semantic correctness NOT ASSERTED" always printed;
- evidence and testimony as separate lists, now including reports (D-8, conceded);
- the fix for proposal §5.1: a profile named `project*.fluxtion-settings` inside `.analyser/` anchors on its parent
  (F-1);
- the bundle file is never modified: open extracts a copy;
- re-run, not reproduce, until replay exists;
- the static page waits for PR #53.

**Where they differ:**

| | draft A | draft B (this file) | assessment |
|---|---|---|---|
| **Vehicle** | `tools/bundle/fexp.py`, driving released 1.25.0 over REST | Java inside the analyser: menu capture, headless `verify` in `Main` | **A wins for two days.** No product code, so no mutation-gate, frame-suite or release cost, and it proves the primitives before committing to a product shape (proposal Part 8's N=1 caution). B is the durable home: a free viewer that is part of the analyser (positioning ▸ *Product packaging*). |
| **Scope** | capture, verify, open, **walk**, **compare**, optional signing | capture, verify, open | **A delivers more of the demo.** The walk is the proposal's highest-value item (§5.11), and A gets it from existing verbs. |
| **Rule 9** | untouched, because it lives outside the app | kept by D-5 (no new orchestration) | Both comply. A's open order (project → graph → log) is the tool's, not the app's, which is legitimate for a client. |
| **Excerpt** | the tool cuts the YAML itself (`text:`/`dimension:`/`time:`/`records:`), then re-opens the excerpt in the analyser to check it | the analyser's filtered view, through the existing YAML export (F-4) | **B's is safer; A's is more expressive.** A Python cutter is a second reader of the audit format, and this repo keeps a conformance suite precisely because readers drift. A's re-open check catches drift in counts, not in framing. **Proposed for A:** the cut must agree with the analyser's `aggregate` count on the same rule, or capture refuses. |
| **Compare** | computes per-node first/last value and counts from each log | not in v0.1 | **The same second-reader risk.** Proposed for A: compute behaviour rows with the analyser's `aggregate`/`series` over each opened bundle, not by parsing YAML in Python. |
| **Charts and reports** | copied "through `context`/`report`, and chart definitions via their `graph` echo" | the profile's project-scoped settings, rewritten | **Unverified for A:** whether a `graph` echo carries a chart's whole definition (series, formulas, guides, bands, markers, notes, explanation). Copying and rewriting the active profile file is more faithful, and it is already on disk. |
| **Layout** | profile at `analyser/.analyser/project.fluxtion-settings`, log at `../log/…` | profile at the bundle root's `.analyser/` | **B's avoids `..`.** Under A's layout, the project root is `analyser/`, so anything in the profile that names the log needs `..`, and saved analyses refuse `..` (F-2). A's §4.3 pre-build check anticipates this. B's layout makes it moot, and costs nothing. |
| **Log and graph in the profile** | "log and graph paths are written project-relative to `analyser/`" | not stored; opened by an analysis (B) or by explicit calls (A) | **A's §4.3 bullet cannot be implemented:** a profile has no log or graph key (F-3). A's §5.2 already opens both by explicit calls, so the bullet can simply go. Those calls must pass **absolute** paths (F-13). |
| **Flags** | compares "flagged-record notes" | flags not carried in v0.1 (F-8) | Flags are not in the profile (F-8). A needs a stated source for them: `context`, if it exposes them — **unverified** — or a `flags.json` member written at capture. |
| **Signing** | optional Ed25519, signer declared | unsigned | see D-3, r2: acceptable with the "not vouched for" condition. |

**What I recommend.**
1. **Build draft A as v0.1**, with the corrections in the table above:
   - the excerpt cut agrees with `aggregate`, or capture refuses;
   - compare goes through the analyser's verbs;
   - the profile is copied and rewritten rather than reassembled from echoes;
   - B's layout (profile at the bundle root);
   - drop the log/graph-path bullet, and pass absolute paths;
   - state where flags come from;
   - `verify` never prints a signer without "not vouched for".
2. **Adopt B as v0.2, "analyser-native"**, once A has shown which parts are general: the format rules R-1…R-5, the
   facts F-1…F-13, the headless `verify` in `Main`, the menu actions, and the `BundleOpening` session node. That is
   draft A's own phase-2 row ("analyser-native `bundle` verb and read-only project mode"). The two drafts therefore
   converge on one sequence rather than competing.
3. **Keep B's acceptance checks that A lacks:**
   - A-4: hostile archives, refused before any write;
   - A-7: hostile content round-trips byte for byte;
   - A-9: a failed capture leaves nothing behind.

   A's tool opens bundles from other people, so these apply to it as much as to B.

## Where the source documents disagree, and what this file does

| topic | positioning | proposal | this spec |
|---|---|---|---|
| name | "experiment", `.fexp` | "evidence bundle", or "exhibit" | Q-1; "evidence bundle" in prose meanwhile |
| a re-run as a test | a response experiment carries the replay evidence | "a green re-run is not a passing test", yet also "a regression test for free" | a replay becomes a test only with an assertion (M12.2); §9 item 4 |
| signing | Ed25519 over the manifest, from the start | "no integrity or identity" as a gap | hashes now; signing waits for the trust model (D-3) |
| layout | `inputs/ execution/ topology/ analysis/ signatures/` | a prototype layout, with `rerun/` and `images/` | §4, the smallest set v0.1 fills; the manifest's roles, not directory names, carry the meaning (Part 8: don't specify directory names yet) |
| replay | runs against a local Mongoose today | the biggest missing piece | carried in v0.1, executed after (D-11) |

## Revision log

| rev | date | who | change |
|---|---|---|---|
| r1 | 2026-09-27 | Claude (analyser session), for the owner | First merge of the positioning doc and the prototype proposal; v0.1 scope; F-1…F-11 from a code-mapping pass, F-2 re-read. |
| r2 | 2026-09-27 | the same | Renamed draft B beside draft A. F-1 and F-3 re-read; F-12 and F-13 added. D-8 conceded to A (reports are testimony). D-3 softened (A's declared signer is acceptable, with one condition). §14 added. |
